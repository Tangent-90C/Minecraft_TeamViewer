package person.professor_chen.teamviewer.multipleplayeresp;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
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
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerESPNetworkManager.class);
	private static Config config;
	
	private final Map<UUID, Vec3d> playerPositions;
	private WebSocket webSocket;
	private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
	private boolean isConnected = false;
	private final Gson gson = new Gson();
	private final HttpClient httpClient;
	
	// 连接状态监听器列表
	private final List<ConnectionStatusListener> statusListeners = new CopyOnWriteArrayList<>();
	
	// 用于处理分段消息的缓冲区
	private StringBuilder messageBuffer = new StringBuilder();
	
	// 二进制消息缓冲区
	private final ByteArrayOutputStream binaryBuffer = new ByteArrayOutputStream();
	
	// 压缩相关状态
	private boolean compressionEnabled = false;
	
	public PlayerESPNetworkManager(Map<UUID, Vec3d> playerPositions) {
		this.playerPositions = playerPositions;
		this.httpClient = HttpClient.newHttpClient();
	}
	
	public static void setConfig(Config config) {
		PlayerESPNetworkManager.config = config;
	}
	
	public void connect() {
		if (config == null) return;
		
		String uri = config.getServerURL();
		
		httpClient.newWebSocketBuilder()
				.buildAsync(URI.create(uri), this)
				.whenComplete((webSocket, throwable) -> {
					if (throwable != null) {
                        LOGGER.error("Failed to connect to PlayerESP server at {}: {}", config.getServerURL(), throwable.getMessage());
						scheduleReconnect();
					} else {
						this.webSocket = webSocket;
						isConnected = true;
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
		notifyConnectionStatusChanged(false);
		// 清空消息缓冲区
		messageBuffer = new StringBuilder();
		binaryBuffer.reset();
		compressionEnabled = false;
		reconnectExecutor.shutdown();
	}

	/**
	 * 批量发送玩家更新到云端（type: players_update）
	 * 云端期望：submitPlayerId（提交者UUID），players 为 playerId -> { x, y, z, vx, vy, vz, dimension, playerName, playerUUID, health, maxHealth, armor, width, height }
	 */
	public void sendPlayersUpdate(UUID submitPlayerId, Map<UUID, Map<String, Object>> players) {
		if (webSocket == null || !isConnected) return;
		if (players == null || players.isEmpty()) return;
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
	 * 云端期望：submitPlayerId（提交者UUID），entities 为 entity_id -> { x, y, z, vx, vy, vz, dimension, entityType, entityName, width, height, ... }
	 */
	public void sendEntitiesUpdate(UUID submitPlayerId, Map<String, Map<String, Object>> entities) {
		if (webSocket == null || !isConnected) return;
		if (entities == null || entities.isEmpty()) return;
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

	private static JsonObject mapToJsonObject(Map<String, Object> map) {
		JsonObject o = new JsonObject();
		for (Map.Entry<String, Object> e : map.entrySet()) {
			if (e.getValue() == null) {
				o.add(e.getKey(), JsonNull.INSTANCE);
			}
			else {
				o.add(e.getKey(), new JsonPrimitive(e.getValue().toString()));
			}
		}
		return o;
	}
	
	@Override
	public void onOpen(WebSocket webSocket) {
		WebSocket.Listener.super.onOpen(webSocket);
		isConnected = true;
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
			
			// 解析服务器发送的位置信息
			if (json.has("type") && "positions".equals(json.get("type").getAsString())) {
				// 处理players对象（注意是对象而不是数组）
				if (json.has("players") && json.get("players").isJsonObject()) {
					JsonObject players = json.getAsJsonObject("players");
					Map<UUID, Vec3d> newPositions = new HashMap<>();
					
					for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
						try {
							String playerIdStr = entry.getKey();
							JsonObject playerData = entry.getValue().getAsJsonObject();
							
							// 直接获取data字段中的数据
							JsonObject actualPlayerData = playerData.getAsJsonObject("data");
							
							// 检查必要字段是否存在
							if (!actualPlayerData.has("x") || !actualPlayerData.has("y") || !actualPlayerData.has("z")) {
                                LOGGER.warn("Player data missing required fields: {}, data: {}", playerIdStr, playerData);
								continue;
							}
							
							UUID playerId = UUID.fromString(playerIdStr);
							double x = actualPlayerData.get("x").getAsDouble();
							double y = actualPlayerData.get("y").getAsDouble();
							double z = actualPlayerData.get("z").getAsDouble();
							newPositions.put(playerId, new Vec3d(x, y, z));
						} catch (Exception e) {
                            LOGGER.error("PlayerESP Network - Error parsing player data: {}", e.getMessage());
						}
					}
					
					playerPositions.clear();
					playerPositions.putAll(newPositions);
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
							
							String entityType = actualData.has("entityType") ? actualData.get("entityType").getAsString() : null;
//							double x = actualData.has("x") ? actualData.get("x").getAsDouble() : 0;
//							double y = actualData.has("y") ? actualData.get("y").getAsDouble() : 0;
//							double z = actualData.has("z") ? actualData.get("z").getAsDouble() : 0;
							
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
			}
		} catch (Exception e) {
            LOGGER.error("PlayerESP Network - Error processing complete message: {}, message: {}", e.getMessage(), message);
		}
	}
	
	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		isConnected = false;
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
        LOGGER.
				error("PlayerESP network error: {}", error.getMessage());
		isConnected = false;
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
			config.save();
		}
	}
	
	public boolean isConnected() {
		return isConnected;
	}
	
	/**
	 * 发送握手消息，声明客户端是否支持压缩
	 */
	private void sendHandshake() {
		if (webSocket == null || !isConnected) return;
		
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
                        LOGGER.error("Failed to decompress GZIP data: {}, raw data size: {}", e.getMessage(), payload.length);
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
                    LOGGER.
							debug("Received uncompressed message, size: {} -> {}", payload.length, message.length());
				} else {
					// 无效标志位，尝试兼容处理
                    LOGGER.warn("Invalid compression flag: 0x{}, treating as uncompressed data", String.format("%02X", flag));
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
}