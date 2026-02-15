package person.professor_chen.teamviewer.multipleplayeresp;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public class PlayerESPNetworkManager implements WebSocket.Listener {
	/**
	 * 连接状态监听器接口
	 */
	public interface ConnectionStatusListener {
		void onConnectionStatusChanged(boolean connected);
	}

	public interface WaypointUpdateListener {
		void onWaypointsReceived(Map<String, SharedWaypointInfo> waypoints);
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerESPNetworkManager.class);
	private static Config config;

	private final Map<UUID, Vec3d> playerPositions;
	private final Map<UUID, RemotePlayerInfo> remotePlayers;
	private WebSocket webSocket;
	private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
	private boolean isConnected = false;
	private final Gson gson = new Gson();
	private final HttpClient httpClient;

	// 连接状态监听器列表
	private final List<ConnectionStatusListener> statusListeners = new CopyOnWriteArrayList<>();
	private final List<WaypointUpdateListener> waypointListeners = new CopyOnWriteArrayList<>();

	// 用于处理分段消息的缓冲区
	private StringBuilder messageBuffer = new StringBuilder();

	// 二进制消息缓冲区
	private final ByteArrayOutputStream binaryBuffer = new ByteArrayOutputStream();

	// 压缩相关状态
	private boolean compressionEnabled = false;
	private volatile String lastConnectionError = "";

	public PlayerESPNetworkManager(Map<UUID, Vec3d> playerPositions, Map<UUID, RemotePlayerInfo> remotePlayers) {
		this.playerPositions = playerPositions;
		this.remotePlayers = remotePlayers;
		this.httpClient = HttpClient.newHttpClient();
	}

	public static void setConfig(Config config) {
		PlayerESPNetworkManager.config = config;
	}

	public void connect() {
		if (config == null)
			return;

		String uri = config.getServerURL();

		httpClient.newWebSocketBuilder()
				.buildAsync(URI.create(uri), this)
				.whenComplete((webSocket, throwable) -> {
					if (throwable != null) {
						this.isConnected = false;
						this.lastConnectionError = formatThrowableReason(throwable);
						LOGGER.error("Failed to connect to PlayerESP server at {}: {}", config.getServerURL(),
								throwable.getMessage());
						notifyConnectionStatusChanged(false);
						scheduleReconnect();
					} else {
						this.webSocket = webSocket;
						isConnected = true;
						lastConnectionError = "";
						notifyConnectionStatusChanged(true);
						// 重置消息缓冲区
						messageBuffer = new StringBuilder();
						binaryBuffer.reset();
						compressionEnabled = false;
						LOGGER.info("Connected to PlayerESP server at {}", config.getServerURL());
						// 发送握手消息
						sendHandshake();
					}
				});
	}

	private void scheduleReconnect() {
		reconnectExecutor.schedule(this::connect, 5, TimeUnit.SECONDS);
	}

	public void disconnect() {
		if (webSocket != null) {
			webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnect");
			webSocket = null;
		}
		isConnected = false;
		lastConnectionError = "";
		notifyConnectionStatusChanged(false);
		// 清空消息缓冲区
		messageBuffer = new StringBuilder();
		binaryBuffer.reset();
		compressionEnabled = false;
		reconnectExecutor.shutdown();
	}

	/**
	 * 批量发送玩家更新到云端（type: players_update）
	 * 云端期望：submitPlayerId（提交者UUID），players 为 playerId -> { x, y, z, vx, vy, vz,
	 * dimension, playerName, playerUUID, health, maxHealth, armor, width, height }
	 */
	public void sendPlayersUpdate(UUID submitPlayerId, Map<UUID, Map<String, Object>> players) {
		if (webSocket == null || !isConnected)
			return;
		if (players == null || players.isEmpty())
			return;
		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "players_update");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			JsonObject playersJson = new JsonObject();
			for (Map.Entry<UUID, Map<String, Object>> e : players.entrySet()) {
				playersJson.add(e.getKey().toString(), mapToJsonObject(e.getValue()));
			}
			obj.add("players", playersJson);
			webSocket.sendText(gson.toJson(obj), true);
		} catch (Exception e) {
			LOGGER.error("Failed to send players_update to PlayerESP server: {}", e.getMessage());
		}
	}

	/**
	 * 发送实体更新到云端（type: entities_update）
	 * 云端期望：submitPlayerId（提交者UUID），entities 为 entity_id -> { x, y, z, vx, vy, vz,
	 * dimension, entityType, entityName, width, height, ... }
	 */
	public void sendEntitiesUpdate(UUID submitPlayerId, Map<String, Map<String, Object>> entities) {
		if (webSocket == null || !isConnected)
			return;
		if (entities == null || entities.isEmpty())
			return;
		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "entities_update");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			JsonObject entitiesJson = new JsonObject();
			for (Map.Entry<String, Map<String, Object>> e : entities.entrySet()) {
				entitiesJson.add(e.getKey(), mapToJsonObject(e.getValue()));
			}
			obj.add("entities", entitiesJson);
			webSocket.sendText(gson.toJson(obj), true);
		} catch (Exception e) {
			LOGGER.error("Failed to send entities_update to PlayerESP server: {}", e.getMessage());
		}
	}

	/**
	 * 发送 waypoint 更新到云端（type: waypoints_update）
	 * waypoints 为 waypointId -> { x, y, z, dimension, name, symbol, color, ownerId, ownerName, createdAt }
	 */
	public void sendWaypointsUpdate(UUID submitPlayerId, Map<String, Map<String, Object>> waypoints) {
		if (webSocket == null || !isConnected)
			return;
		if (waypoints == null || waypoints.isEmpty())
			return;
		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "waypoints_update");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			JsonObject waypointsJson = new JsonObject();
			for (Map.Entry<String, Map<String, Object>> e : waypoints.entrySet()) {
				waypointsJson.add(e.getKey(), mapToJsonObject(e.getValue()));
			}
			obj.add("waypoints", waypointsJson);
			webSocket.sendText(gson.toJson(obj), true);
		} catch (Exception e) {
			LOGGER.error("Failed to send waypoints_update to PlayerESP server: {}", e.getMessage());
		}
	}

	private static JsonObject mapToJsonObject(Map<String, Object> map) {
		JsonObject o = new JsonObject();
		for (Map.Entry<String, Object> e : map.entrySet()) {
			if (e.getValue() == null) {
				o.add(e.getKey(), JsonNull.INSTANCE);
			} else {
				o.add(e.getKey(), new JsonPrimitive(e.getValue().toString()));
			}
		}
		return o;
	}

	@Override
	public void onOpen(WebSocket webSocket) {
		WebSocket.Listener.super.onOpen(webSocket);
		isConnected = true;
		lastConnectionError = "";
		LOGGER.info("WebSocket connection opened to PlayerESP server");
		notifyConnectionStatusChanged(true);
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		// 将接收到的数据添加到缓冲区
		messageBuffer.append(data);

		// 如果这是最后一个片段，处理完整的消息
		if (last) {
			String completeMessage = messageBuffer.toString();
			messageBuffer = new StringBuilder(); // 重置缓冲区

			// 处理完整的消息
			processCompleteMessage(completeMessage);
		}

		return WebSocket.Listener.super.onText(webSocket, data, last);
	}

	private void processCompleteMessage(String message) {
		try {
			// 检查消息是否为空
			if (message == null || message.trim().isEmpty()) {
				LOGGER.warn("Received empty message");
				return;
			}

			// 尝试解析JSON
			JsonObject json;
			try {
				json = JsonParser.parseString(message).getAsJsonObject();
			} catch (JsonSyntaxException e) {
				LOGGER.error("Failed to parse JSON message: {}, message: {}", e.getMessage(), message);
				return;
			}

			// 处理握手确认消息
			if (json.has("type") && "handshake_ack".equals(json.get("type").getAsString())) {
				handleHandshakeAck(json);
				return;
			}

			String messageType = json.has("type") ? json.get("type").getAsString() : "";

			if ("waypoints_update".equals(messageType)) {
				Map<String, SharedWaypointInfo> receivedWaypoints = parseWaypoints(json);
				if (!receivedWaypoints.isEmpty()) {
					notifyWaypointsReceived(receivedWaypoints);
				}
				return;
			}

			// 解析服务器发送的位置信息
			if ("positions".equals(messageType)) {
				// 处理players对象（注意是对象而不是数组）
				if (json.has("players") && json.get("players").isJsonObject()) {
					JsonObject players = json.getAsJsonObject("players");
					Map<UUID, Vec3d> newPositions = new HashMap<>();
					Map<UUID, RemotePlayerInfo> newRemotePlayers = new HashMap<>();
					RegistryKey<World> fallbackDimension = getCurrentDimension();

					for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
						try {
							String playerIdStr = entry.getKey();
							JsonObject playerData = entry.getValue().getAsJsonObject();

							// 直接获取data字段中的数据
							JsonObject actualPlayerData = playerData.getAsJsonObject("data");

							// 检查必要字段是否存在
							if (!actualPlayerData.has("x") || !actualPlayerData.has("y")
									|| !actualPlayerData.has("z")) {
								LOGGER.warn("Player data missing required fields: {}, data: {}", playerIdStr,
										playerData);
								continue;
							}

							UUID playerId = UUID.fromString(playerIdStr);
							double x = actualPlayerData.get("x").getAsDouble();
							double y = actualPlayerData.get("y").getAsDouble();
							double z = actualPlayerData.get("z").getAsDouble();

							String dimensionId = actualPlayerData.has("dimension")
									? actualPlayerData.get("dimension").getAsString()
									: null;
							RegistryKey<World> dimension = RemotePlayerInfo.parseDimension(dimensionId, fallbackDimension);
							String playerName = actualPlayerData.has("playerName")
									? actualPlayerData.get("playerName").getAsString()
									: playerIdStr;

							Vec3d position = new Vec3d(x, y, z);
							newPositions.put(playerId, position);
							newRemotePlayers.put(playerId, new RemotePlayerInfo(playerId, position, dimension, playerName));
						} catch (Exception e) {
							LOGGER.error("PlayerESP Network - Error parsing player data: {}", e.getMessage());
						}
					}

					reconcilePlayerPositions(newPositions);
					reconcileRemotePlayers(newRemotePlayers);
				}

				// 处理entities对象（注意是对象而不是数组）
				if (json.has("entities") && json.get("entities").isJsonObject()) {
					JsonObject entities = json.getAsJsonObject("entities");
					int playerCount = 0;
					int entityCount = 0;

					for (Map.Entry<String, JsonElement> entry : entities.entrySet()) {
						try {
							String entityId = entry.getKey();
							JsonObject entityData = entry.getValue().getAsJsonObject();

							// 直接获取data字段中的数据
							JsonObject actualData = entityData.getAsJsonObject("data");

							// 检查必要字段是否存在
							if (!actualData.has("x") || !actualData.has("y") || !actualData.has("z")) {
								LOGGER.warn("Entity data missing required fields: {}, data: {}", entityId, entityData);
								continue;
							}

							String entityType = actualData.has("entityType")
									? actualData.get("entityType").getAsString()
									: null;

							if (entityId != null && entityType != null) {
								if ("player".equals(entityType)) {
									// 这里我们已经有了players字段，所以不需要重复处理
									entityCount++;
								}
								entityCount++;
							} else {
								LOGGER.error("PlayerESP Network - Incomplete entity data for {}", entityId);
							}
						} catch (Exception e) {
							LOGGER.error("PlayerESP Network - Error parsing entity data: {}", e.getMessage());
						}
					}

					LOGGER.debug("PlayerESP Network - Processed: {} players, {} entities", playerCount, entityCount);
				}

				if (json.has("waypoints") && json.get("waypoints").isJsonObject()) {
					Map<String, SharedWaypointInfo> receivedWaypoints = parseWaypoints(json);
					if (!receivedWaypoints.isEmpty()) {
						notifyWaypointsReceived(receivedWaypoints);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("PlayerESP Network - Error processing complete message: {}, message: {}", e.getMessage(),
					message);
		}
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		isConnected = false;
		if (statusCode != WebSocket.NORMAL_CLOSURE) {
			lastConnectionError = "WebSocket closed (" + statusCode + "): "
					+ (reason == null || reason.isBlank() ? "unknown reason" : reason);
		} else {
			lastConnectionError = ""; 
		}
		notifyConnectionStatusChanged(false);
		// 清空消息缓冲区
		messageBuffer = new StringBuilder();
		binaryBuffer.reset();
		compressionEnabled = false;
		LOGGER.info("Disconnected from PlayerESP server. Status: {}, Reason: {}", statusCode, reason);
		scheduleReconnect();
		return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		LOGGER.error("PlayerESP network error: {}", error.getMessage());
		isConnected = false;
		lastConnectionError = formatThrowableReason(error);
		notifyConnectionStatusChanged(false);
		// 清空消息缓冲区
		binaryBuffer.reset();
		compressionEnabled = false;
		scheduleReconnect();
	}

	// Getter and Setter methods
	public static String getServerURL() {
		return config != null ? config.getServerURL() : "ws://localhost:8080/playeresp";
	}

	public static void setServerURL(String serverURL) {
		if (config != null) {
			config.setServerURL(serverURL);
		}
	}

	public boolean isConnected() {
		return isConnected;
	}

	public String getLastConnectionError() {
		return lastConnectionError;
	}

	private String formatThrowableReason(Throwable throwable) {
		if (throwable == null) {
			return "Unknown error";
		}

		StringBuilder details = new StringBuilder();
		Throwable current = throwable;
		int depth = 0;
		while (current != null && depth < 6) {
			String message = current.getMessage();
			String type = current.getClass().getSimpleName();
			if (message != null && !message.isBlank()) {
				if (details.length() > 0) {
					details.append(" | caused by: ");
				}
				details.append(type).append(": ").append(message.trim());
			}
			current = current.getCause();
			depth++;
		}

		if (details.length() > 0) {
			return details.toString();
		}

		String fallback = throwable.toString();
		if (fallback != null && !fallback.isBlank()) {
			return fallback;
		}
		return throwable.getClass().getSimpleName();
	}

	private RegistryKey<World> getCurrentDimension() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null) {
			return client.world.getRegistryKey();
		}
		return World.OVERWORLD;
	}

	/**
	 * 发送握手消息，声明客户端是否支持压缩
	 */
	private void sendHandshake() {
		if (webSocket == null || !isConnected)
			return;

		try {
			JsonObject handshake = new JsonObject();
			handshake.addProperty("type", "handshake");
			// 使用本地玩家UUID作为submitPlayerId
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				handshake.addProperty("submitPlayerId", client.player.getUuid().toString());
			}
			handshake.addProperty("enableCompression", config != null && config.isEnableCompression());

			webSocket.sendText(gson.toJson(handshake), true);
			LOGGER.info("Sent handshake message, compression: {}", config != null && config.isEnableCompression());
		} catch (Exception e) {
			LOGGER.error("Failed to send handshake message: {}", e.getMessage());
		}
	}

	/**
	 * 处理握手确认消息
	 */
	private void handleHandshakeAck(JsonObject json) {
		if (json.has("ready") && json.get("ready").getAsBoolean()) {
			compressionEnabled = json.has("compressionEnabled") && json.get("compressionEnabled").getAsBoolean();
			LOGGER.info("Handshake completed. Compression enabled: {}", compressionEnabled);
		}
	}

	/**
	 * 解压GZIP数据
	 */
	private String decompressGzip(byte[] compressedData) throws IOException {
		ByteArrayInputStream bis = new ByteArrayInputStream(compressedData);
		GZIPInputStream gis = new GZIPInputStream(bis);
		StringBuilder sb = new StringBuilder();
		byte[] buffer = new byte[1024];
		int len;
		while ((len = gis.read(buffer)) > 0) {
			sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
		}
		gis.close();
		bis.close();
		return sb.toString();
	}

	/**
	 * 处理二进制消息（压缩数据）
	 * 支持分段传输和压缩标志位处理
	 */
	@Override
	public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
		try {
			byte[] bytes = new byte[data.remaining()];
			data.get(bytes);

			if (bytes.length == 0) {
				return WebSocket.Listener.super.onBinary(webSocket, data, last);
			}

			// 处理分段消息
			if (!last) {
				// 不是最后一段，缓存数据
				binaryBuffer.write(bytes);
				LOGGER.debug("Buffering binary segment, current buffer size: {}", binaryBuffer.size());
				return WebSocket.Listener.super.onBinary(webSocket, data, false);
			}

			// 这是最后一段，处理完整消息
			byte[] completeData;
			if (binaryBuffer.size() > 0) {
				// 有缓存的数据，合并处理
				binaryBuffer.write(bytes);
				completeData = binaryBuffer.toByteArray();
				binaryBuffer.reset();
				LOGGER.debug("Processing complete binary message from segments, total size: {}", completeData.length);
			} else {
				// 单段消息
				completeData = bytes;
				LOGGER.debug("Processing single segment binary message, size: {}", completeData.length);
			}

			// 解析压缩标志位和数据
			if (completeData.length > 0) {
				byte flag = completeData[0];
				byte[] payload = new byte[completeData.length - 1];
				System.arraycopy(completeData, 1, payload, 0, payload.length);

				String message;
				if (flag == 0x01) {
					// 压缩数据
					try {
						message = decompressGzip(payload);
						LOGGER.debug("Decompressed message: {} bytes -> {} chars", payload.length, message.length());
					} catch (IOException e) {
						LOGGER.error("Failed to decompress GZIP data: {}, raw data size: {}", e.getMessage(),
								payload.length);
						// 打印原始数据用于调试（根据用户偏好）
						if (LOGGER.isDebugEnabled()) {
							StringBuilder hexDump = new StringBuilder();
							for (int i = 0; i < Math.min(payload.length, 100); i++) {
								hexDump.append(String.format("%02X ", payload[i]));
							}
							LOGGER.debug("Raw payload (first 100 bytes): {}", hexDump);
						}
						return WebSocket.Listener.super.onBinary(webSocket, data, true);
					}
				} else if (flag == 0x00) {
					// 未压缩数据
					message = new String(payload, StandardCharsets.UTF_8);
					LOGGER.debug("Received uncompressed message, size: {} -> {}", payload.length, message.length());
				} else {
					// 无效标志位，尝试兼容处理
					LOGGER.warn("Invalid compression flag: 0x{}, treating as uncompressed data",
							String.format("%02X", flag));
					message = new String(completeData, StandardCharsets.UTF_8);
				}

				processCompleteMessage(message);
			}
		} catch (Exception e) {
			LOGGER.error("Error processing binary message: {}", e.getMessage(), e);
			// 重置缓冲区状态
			binaryBuffer.reset();
		}

		return WebSocket.Listener.super.onBinary(webSocket, data, last);
	}

	/**
	 * 注册连接状态监听器
	 */
	public void addConnectionStatusListener(ConnectionStatusListener listener) {
		if (listener != null && !statusListeners.contains(listener)) {
			statusListeners.add(listener);
		}
	}

	/**
	 * 移除连接状态监听器
	 */
	public void removeConnectionStatusListener(ConnectionStatusListener listener) {
		statusListeners.remove(listener);
	}

	public void addWaypointUpdateListener(WaypointUpdateListener listener) {
		if (listener != null && !waypointListeners.contains(listener)) {
			waypointListeners.add(listener);
		}
	}

	public void removeWaypointUpdateListener(WaypointUpdateListener listener) {
		waypointListeners.remove(listener);
	}

	/**
	 * 通知所有监听器连接状态变化
	 */
	private void notifyConnectionStatusChanged(boolean connected) {
		for (ConnectionStatusListener listener : statusListeners) {
			try {
				listener.onConnectionStatusChanged(connected);
			} catch (Exception e) {
				LOGGER.error("Error notifying connection status listener: {}", e.getMessage());
			}
		}
	}

	private void notifyWaypointsReceived(Map<String, SharedWaypointInfo> waypoints) {
		for (WaypointUpdateListener listener : waypointListeners) {
			try {
				listener.onWaypointsReceived(waypoints);
			} catch (Exception e) {
				LOGGER.error("Error notifying waypoint listener: {}", e.getMessage());
			}
		}
	}

	private Map<String, SharedWaypointInfo> parseWaypoints(JsonObject json) {
		if (!json.has("waypoints") || !json.get("waypoints").isJsonObject()) {
			return Map.of();
		}

		JsonObject waypointsJson = json.getAsJsonObject("waypoints");
		Map<String, SharedWaypointInfo> result = new HashMap<>();

		for (Map.Entry<String, JsonElement> entry : waypointsJson.entrySet()) {
			try {
				String waypointId = entry.getKey();
				if (!entry.getValue().isJsonObject()) {
					continue;
				}

				JsonObject node = entry.getValue().getAsJsonObject();
				JsonObject data = node.has("data") && node.get("data").isJsonObject()
						? node.getAsJsonObject("data")
						: node;

				if (!data.has("x") || !data.has("y") || !data.has("z")) {
					continue;
				}

				UUID ownerId = null;
				if (data.has("ownerId") && !data.get("ownerId").isJsonNull()) {
					ownerId = UUID.fromString(data.get("ownerId").getAsString());
				}

				String name = data.has("name") ? data.get("name").getAsString() : "Waypoint";
				String symbol = data.has("symbol") ? data.get("symbol").getAsString() : "W";
				String ownerName = data.has("ownerName") ? data.get("ownerName").getAsString() : "Unknown";
				String dimension = data.has("dimension") ? data.get("dimension").getAsString() : null;
				int color = data.has("color") ? data.get("color").getAsInt() : 0x55FF55;
				long createdAt = data.has("createdAt") ? data.get("createdAt").getAsLong()
						: System.currentTimeMillis();

				SharedWaypointInfo waypoint = new SharedWaypointInfo(
						waypointId,
						ownerId,
						ownerName,
						name,
						symbol,
						data.get("x").getAsInt(),
						data.get("y").getAsInt(),
						data.get("z").getAsInt(),
						dimension,
						color,
						createdAt);
				result.put(waypointId, waypoint);
			} catch (Exception e) {
				LOGGER.error("Failed to parse shared waypoint {}: {}", entry.getKey(), e.getMessage());
			}
		}

		return result;
	}

	private void reconcilePlayerPositions(Map<UUID, Vec3d> latestPositions) {
		playerPositions.entrySet().removeIf(entry -> !latestPositions.containsKey(entry.getKey()));

		for (Map.Entry<UUID, Vec3d> entry : latestPositions.entrySet()) {
			UUID playerId = entry.getKey();
			Vec3d latest = entry.getValue();
			Vec3d existing = playerPositions.get(playerId);
			if (!Objects.equals(existing, latest)) {
				playerPositions.put(playerId, latest);
			}
		}
	}

	private void reconcileRemotePlayers(Map<UUID, RemotePlayerInfo> latestRemotePlayers) {
		remotePlayers.entrySet().removeIf(entry -> !latestRemotePlayers.containsKey(entry.getKey()));

		for (Map.Entry<UUID, RemotePlayerInfo> entry : latestRemotePlayers.entrySet()) {
			UUID playerId = entry.getKey();
			RemotePlayerInfo latest = entry.getValue();
			RemotePlayerInfo existing = remotePlayers.get(playerId);
			if (!Objects.equals(existing, latest)) {
				remotePlayers.put(playerId, latest);
			}
		}
	}
}