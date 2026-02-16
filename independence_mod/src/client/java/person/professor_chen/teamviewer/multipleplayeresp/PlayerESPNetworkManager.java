package person.professor_chen.teamviewer.multipleplayeresp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PlayerESPNetworkManager extends WebSocketListener {
	/**
	 * 连接状态监听器接口
	 */
	public interface ConnectionStatusListener {
		void onConnectionStatusChanged(boolean connected);
	}

	public interface WaypointUpdateListener {
		void onWaypointsReceived(Map<String, SharedWaypointInfo> waypoints);

		default void onWaypointsDeleted(List<String> waypointIds) {
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerESPNetworkManager.class);
	private static Config config;

	private final Map<UUID, Vec3d> playerPositions;
	private final Map<UUID, RemotePlayerInfo> remotePlayers;
	private WebSocket webSocket;
	private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
	private boolean isConnected = false;
	private volatile boolean shouldReconnect = false;
	private final Gson gson = new Gson();
	private final OkHttpClient httpClient;

	// 连接状态监听器列表
	private final List<ConnectionStatusListener> statusListeners = new CopyOnWriteArrayList<>();
	private final List<WaypointUpdateListener> waypointListeners = new CopyOnWriteArrayList<>();

	private volatile String lastConnectionError = "";

	public PlayerESPNetworkManager(Map<UUID, Vec3d> playerPositions, Map<UUID, RemotePlayerInfo> remotePlayers) {
		this.playerPositions = playerPositions;
		this.remotePlayers = remotePlayers;
		this.httpClient = new OkHttpClient();
	}

	public static void setConfig(Config config) {
		PlayerESPNetworkManager.config = config;
	}

	public void connect() {
		if (config == null)
			return;
		shouldReconnect = true;

		String uri = config.getServerURL();
		Request.Builder builder = new Request.Builder().url(uri);

		try {
			this.webSocket = httpClient.newWebSocket(builder.build(), this);
		} catch (Exception e) {
			this.isConnected = false;
			this.lastConnectionError = formatThrowableReason(e);
			LOGGER.error("Failed to connect to PlayerESP server at {}: {}", config.getServerURL(), e.getMessage());
			notifyConnectionStatusChanged(false);
			scheduleReconnect();
		}
	}

	private void scheduleReconnect() {
		if (!shouldReconnect) {
			return;
		}
		try {
			reconnectExecutor.schedule(this::connect, 5, TimeUnit.SECONDS);
		} catch (RejectedExecutionException e) {
			LOGGER.warn("Reconnect scheduler is unavailable: {}", e.getMessage());
		}
	}

	public void disconnect() {
		shouldReconnect = false;
		if (webSocket != null) {
			webSocket.close(1000, "Client disconnect");
			webSocket = null;
		}
		isConnected = false;
		lastConnectionError = "";
		notifyConnectionStatusChanged(false);
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
			webSocket.send(gson.toJson(obj));
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
			webSocket.send(gson.toJson(obj));
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
			webSocket.send(gson.toJson(obj));
		} catch (Exception e) {
			LOGGER.error("Failed to send waypoints_update to PlayerESP server: {}", e.getMessage());
		}
	}

	/**
	 * 发送 waypoint 删除通知到云端（type: waypoints_delete）
	 */
	public void sendWaypointsDelete(UUID submitPlayerId, List<String> waypointIds) {
		if (webSocket == null || !isConnected)
			return;
		if (waypointIds == null || waypointIds.isEmpty())
			return;
		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "waypoints_delete");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			JsonArray ids = new JsonArray();
			for (String waypointId : waypointIds) {
				if (waypointId != null && !waypointId.isBlank()) {
					ids.add(waypointId);
				}
			}
			if (ids.isEmpty()) {
				return;
			}
			obj.add("waypointIds", ids);
			webSocket.send(gson.toJson(obj));
		} catch (Exception e) {
			LOGGER.error("Failed to send waypoints_delete to PlayerESP server: {}", e.getMessage());
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
	public void onOpen(WebSocket webSocket, Response response) {
		isConnected = true;
		lastConnectionError = "";
		LOGGER.info("WebSocket connection opened to PlayerESP server");
		if (response != null) {
			String negotiatedExtensions = response.header("Sec-WebSocket-Extensions", "");
			if (!negotiatedExtensions.isBlank()) {
				LOGGER.info("Negotiated WebSocket extensions: {}", negotiatedExtensions);
			}
		}
		notifyConnectionStatusChanged(true);
		sendHandshake();
	}

	@Override
	public void onMessage(WebSocket webSocket, String text) {
		processCompleteMessage(text);
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

			if ("waypoints_delete".equals(messageType)) {
				List<String> waypointIds = parseWaypointDeleteIds(json);
				if (!waypointIds.isEmpty()) {
					notifyWaypointsDeleted(waypointIds);
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
	public void onClosed(WebSocket webSocket, int statusCode, String reason) {
		isConnected = false;
		if (statusCode != 1000) {
			lastConnectionError = "WebSocket closed (" + statusCode + "): "
					+ (reason == null || reason.isBlank() ? "unknown reason" : reason);
		} else {
			lastConnectionError = ""; 
		}
		notifyConnectionStatusChanged(false);
		LOGGER.info("Disconnected from PlayerESP server. Status: {}, Reason: {}", statusCode, reason);
		if (shouldReconnect) {
			scheduleReconnect();
		}
	}

	@Override
	public void onFailure(WebSocket webSocket, Throwable error, Response response) {
		LOGGER.error("PlayerESP network error: {}", error.getMessage());
		isConnected = false;
		lastConnectionError = formatThrowableReason(error);
		notifyConnectionStatusChanged(false);
		if (shouldReconnect) {
			scheduleReconnect();
		}
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
	 * 发送握手消息
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

			webSocket.send(gson.toJson(handshake));
			LOGGER.info("Sent handshake message");
		} catch (Exception e) {
			LOGGER.error("Failed to send handshake message: {}", e.getMessage());
		}
	}

	/**
	 * 处理握手确认消息
	 */
	private void handleHandshakeAck(JsonObject json) {
		if (json.has("ready") && json.get("ready").getAsBoolean()) {
			LOGGER.info("Handshake completed");
		}
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

	private void notifyWaypointsDeleted(List<String> waypointIds) {
		for (WaypointUpdateListener listener : waypointListeners) {
			try {
				listener.onWaypointsDeleted(waypointIds);
			} catch (Exception e) {
				LOGGER.error("Error notifying waypoint delete listener: {}", e.getMessage());
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

	private List<String> parseWaypointDeleteIds(JsonObject json) {
		if (!json.has("waypointIds") || !json.get("waypointIds").isJsonArray()) {
			return List.of();
		}

		List<String> result = new java.util.ArrayList<>();
		for (JsonElement idElement : json.getAsJsonArray("waypointIds")) {
			if (idElement != null && idElement.isJsonPrimitive()) {
				String id = idElement.getAsString();
				if (id != null && !id.isBlank()) {
					result.add(id);
				}
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