package person.professor_chen.teamviewer.multipleplayeresp;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PlayerESPNetworkManager implements WebSocket.Listener {
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerESPNetworkManager.class);
	private static Config config;
	
	private final Map<UUID, Vec3d> playerPositions;
	private WebSocket webSocket;
	private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
	private boolean isConnected = false;
	private final Gson gson = new Gson();
	
	// 用于处理分段消息的缓冲区
	private StringBuilder messageBuffer = new StringBuilder();
	private boolean isProcessingMessage = false;
	
	public PlayerESPNetworkManager(Map<UUID, Vec3d> playerPositions) {
		this.playerPositions = playerPositions;
	}
	
	public static void setConfig(Config config) {
		PlayerESPNetworkManager.config = config;
	}
	
	public void connect() {
		if (config == null) return;
		
		HttpClient client = HttpClient.newHttpClient();
		String uri = "ws://" + config.getServerIP() + ":" + config.getServerPort() + "/playeresp";
		
		client.newWebSocketBuilder()
				.buildAsync(URI.create(uri), this)
				.whenComplete((webSocket, throwable) -> {
					if (throwable != null) {
						LOGGER.error("Failed to connect to PlayerESP server at " + config.getServerIP() + ":" + config.getServerPort() + ": " + throwable.getMessage());
						scheduleReconnect();
					} else {
						this.webSocket = webSocket;
						isConnected = true;
						// 重置消息缓冲区
						messageBuffer = new StringBuilder();
						isProcessingMessage = false;
						LOGGER.info("Connected to PlayerESP server at " + config.getServerIP() + ":" + config.getServerPort());
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
		// 清空消息缓冲区
		messageBuffer = new StringBuilder();
		isProcessingMessage = false;
		reconnectExecutor.shutdown();
	}
	
	public void sendMessage(String message) {
		if (webSocket != null && isConnected) {
			try {
				webSocket.sendText(message, true);
			} catch (Exception e) {
				LOGGER.error("Failed to send message to PlayerESP server: " + e.getMessage());
			}
		}
	}
	
	/**
	 * 批量发送玩家更新到云端（type: players_update）
	 * 云端期望：submitPlayerId（提交者UUID），players 为 playerId -> { x, y, z, dimension, playerName, playerUUID, health, maxHealth, armor, width, height }
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
			LOGGER.error("Failed to send players_update to PlayerESP server: " + e.getMessage());
		}
	}

	/**
	 * 发送实体更新到云端（type: entities_update）
	 * 云端期望：submitPlayerId（提交者UUID），entities 为 entity_id -> { x, y, z, dimension, entityType, entityName, width, height, ... }
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
			LOGGER.error("Failed to send entities_update to PlayerESP server: " + e.getMessage());
		}
	}

	private static JsonElement mapToJsonElement(Object value) {
		if (value == null) return JsonNull.INSTANCE;
		if (value instanceof Number) return new JsonPrimitive((Number) value);
		if (value instanceof Boolean) return new JsonPrimitive((Boolean) value);
		if (value instanceof String) return new JsonPrimitive((String) value);
		if (value instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m = (Map<String, Object>) value;
			return mapToJsonObject(m);
		}
		return new JsonPrimitive(value.toString());
	}

	private static JsonObject mapToJsonObject(Map<String, Object> map) {
		JsonObject o = new JsonObject();
		for (Map.Entry<String, Object> e : map.entrySet()) {
			o.add(e.getKey(), mapToJsonElement(e.getValue()));
		}
		return o;
	}
	
	@Override
	public void onOpen(WebSocket webSocket) {
		WebSocket.Listener.super.onOpen(webSocket);
		isConnected = true;
		LOGGER.info("WebSocket connection opened to PlayerESP server");
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
				LOGGER.error("Failed to parse JSON message: " + e.getMessage() + ", message: " + message);
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
							
							// 检查必要字段是否存在
							if (!playerData.has("x") || !playerData.has("y") || !playerData.has("z")) {
								LOGGER.warn("Player data missing required fields: " + playerIdStr);
								continue;
							}
							
							UUID playerId = UUID.fromString(playerIdStr);
							double x = playerData.get("x").getAsDouble();
							double y = playerData.get("y").getAsDouble();
							double z = playerData.get("z").getAsDouble();
							newPositions.put(playerId, new Vec3d(x, y, z));
						} catch (Exception e) {
							LOGGER.error("PlayerESP Network - Error parsing player data: " + e.getMessage());
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
							
							// 检查必要字段是否存在
							if (!entityData.has("x") || !entityData.has("y") || !entityData.has("z")) {
								LOGGER.warn("Entity data missing required fields: " + entityId);
								continue;
							}
							
							String entityType = entityData.has("entityType") ? entityData.get("entityType").getAsString() : null;
							double x = entityData.has("x") ? entityData.get("x").getAsDouble() : 0;
							double y = entityData.has("y") ? entityData.get("y").getAsDouble() : 0;
							double z = entityData.has("z") ? entityData.get("z").getAsDouble() : 0;
							
							if (entityId != null && entityType != null) {
								if ("player".equals(entityType)) {
									// 这里我们已经有了players字段，所以不需要重复处理
									entityCount++;
								}
								entityCount++;
							} else {
								LOGGER.error("PlayerESP Network - Incomplete entity data for " + entityId);
							}
						} catch (Exception e) {
							LOGGER.error("PlayerESP Network - Error parsing entity data: " + e.getMessage());
						}
					}
					
					LOGGER.info("PlayerESP Network - Processed: " + playerCount + " players, " + entityCount + " entities");
				}
			}
		} catch (Exception e) {
			LOGGER.error("PlayerESP Network - Error processing complete message: " + e.getMessage() + ", message: " + message);
		}
	}
	
	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		isConnected = false;
		// 清空消息缓冲区
		messageBuffer = new StringBuilder();
		isProcessingMessage = false;
		LOGGER.info("Disconnected from PlayerESP server. Status: " + statusCode + ", Reason: " + reason);
		scheduleReconnect();
		return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
	}
	
	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		LOGGER.error("PlayerESP network error: " + error.getMessage());
		isConnected = false;
		// 清空消息缓冲区
		messageBuffer = new StringBuilder();
		isProcessingMessage = false;
		scheduleReconnect();
	}
	
	// Getter and Setter methods
	public static String getServerIP() {
		return config != null ? config.getServerIP() : "localhost";
	}
	
	public static void setServerIP(String serverIP) {
		if (config != null) {
			config.setServerIP(serverIP);
			config.save();
		}
	}
	
	public static int getServerPort() {
		return config != null ? config.getServerPort() : 8080;
	}
	
	public static void setServerPort(int serverPort) {
		if (config != null) {
			config.setServerPort(serverPort);
			config.save();
		}
	}
	
	public boolean isConnected() {
		return isConnected;
	}
}