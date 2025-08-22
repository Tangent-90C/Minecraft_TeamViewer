package person.professor_chen.teamviewer.multipleplayeresp;

import com.google.gson.*;
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
	private boolean isRegistered = false;
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
						isRegistered = false; // 重置注册状态
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
		isRegistered = false;
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
	
	// 发送注册消息
	public void sendRegistration(UUID playerId) {
		if (webSocket != null && isConnected && !isRegistered) {
			try {
				String registrationMessage = String.format(
					"{\"type\":\"register\",\"id\":\"%s\"}",
					playerId.toString()
				);
				webSocket.sendText(registrationMessage, true);
				isRegistered = true;
				LOGGER.info("Registered player " + playerId + " with server");
			} catch (Exception e) {
				LOGGER.error("Failed to send registration to PlayerESP server: " + e.getMessage());
			}
		}
	}
	
	// 发送位置更新消息
	public void sendPositionUpdate(UUID playerId, Vec3d position, String dimension) {
		if (webSocket != null && isConnected && isRegistered) {
			try {
				String updateMessage = String.format(
					"{\"type\":\"update\",\"id\":\"%s\",\"x\":%f,\"y\":%f,\"z\":%f,\"dimension\":\"%s\"}",
					playerId.toString(),
					position.x,
					position.y,
					position.z,
					dimension
				);
				webSocket.sendText(updateMessage, true);
			} catch (Exception e) {
				LOGGER.error("Failed to send position update to PlayerESP server: " + e.getMessage());
			}
		}
	}
	
	@Override
	public void onOpen(WebSocket webSocket) {
		WebSocket.Listener.super.onOpen(webSocket);
		isConnected = true;
		isRegistered = false;
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
			
			// 检查是否是注册确认消息
			if (json.has("type") && "registration_confirmed".equals(json.get("type").getAsString())) {
				isRegistered = true;
				LOGGER.info("Registration confirmed by server");
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
		isRegistered = false;
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
		isRegistered = false;
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
	
	public boolean isRegistered() {
		return isRegistered;
	}
}