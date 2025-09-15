package niubi.professor_chen.wurstPlugin.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * PlayerESPNetworkHandler handles the WebSocket connection to a server that
 * shares player positions between clients.
 * This is the base network component without Wurst dependencies.
 */
public class PlayerESPNetworkHandler implements WebSocket.Listener {
    private static final Gson GSON = new Gson();

    private WebSocket webSocket;
    private ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    private final String playerId;
    private boolean connected = false;
    private final String serverIP;
    private final String serverPort;

    // 存储从服务器接收到的其他玩家位置
    private final Map<String, RemotePlayer> remotePlayers = new ConcurrentHashMap<>();

    // 存储从服务器接收到的生物实体位置
    private final Map<String, RemoteEntity> remoteEntities = new ConcurrentHashMap<>();

    // 用于处理分段传输的WebSocket消息的缓冲区
    private StringBuilder messageBuffer = new StringBuilder();
    private boolean isBuffering = false;

    public PlayerESPNetworkHandler(String serverIP, String serverPort) {
        this.playerId = UUID.randomUUID().toString();
        this.serverIP = serverIP;
        this.serverPort = serverPort;
    }

    public void connect() {
        try {
            String serverUri = "ws://" + serverIP + ":" + serverPort;
            HttpClient client = HttpClient.newHttpClient();
            CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
                    .buildAsync(URI.create(serverUri), this);

            wsFuture.thenAccept(ws -> {
                this.webSocket = ws;
                this.connected = true;
                System.out.println("Connected to PlayerESP server at " + serverIP
                        + ":" + serverPort);

                // 发送注册消息
                JsonObject registerMsg = new JsonObject();
                registerMsg.addProperty("type", "register");
                registerMsg.addProperty("playerUUID", playerId);
                sendMessage(registerMsg.toString());

                // 开始定期发送位置更新
                scheduler.scheduleAtFixedRate(this::sendPositionUpdate, 0, 1,
                        TimeUnit.SECONDS);
            }).exceptionally(throwable -> {
                System.err.println(
                        "Failed to connect to PlayerESP server at " + serverIP + ":"
                                + serverPort + ": " + throwable.getMessage());
                return null;
            });
        } catch (Exception e) {
            System.err.println(
                    "Failed to connect to PlayerESP server: " + e.getMessage());
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data,
                                     boolean last) {
        try {
            // 将接收到的数据添加到缓冲区
            messageBuffer.append(data);

            // 如果这是最后一段数据，则处理整个消息
            if (last) {
                String completeMessage = messageBuffer.toString();
                messageBuffer.setLength(0); // 清空缓冲区
                isBuffering = false;

                // 处理完整消息
                processCompleteMessage(completeMessage);
            } else {
                // 如果还有更多数据段，继续缓冲
                isBuffering = true;
            }
        } catch (Exception e) {
            System.err.println("PlayerESP Network - Error buffering message: "
                    + e.getMessage());
            messageBuffer.setLength(0); // 出错时清空缓冲区
            isBuffering = false;
        }

        webSocket.request(1);
        return CompletableFuture.completedStage(null);
    }

    /**
     * 处理完整的WebSocket消息
     */
    private void processCompleteMessage(String dataString) {
        try {
            // 尝试修复JSON字符串（如果可能）
            String fixedDataString = fixJsonString(dataString);

            JsonObject json =
                    JsonParser.parseString(fixedDataString).getAsJsonObject();
            String type =
                    json.has("type") ? json.get("type").getAsString() : "unknown";

            if ("positions".equals(type)) {
                remotePlayers.clear();
                remoteEntities.clear();

                JsonObject players = json.has("players") ? json.getAsJsonObject("players") : new JsonObject();
                JsonObject entities = json.has("entities") ? json.getAsJsonObject("entities") : new JsonObject();
                long currentTime = System.currentTimeMillis();

                int playerCount = 0;
                int entityCount = 0;

                for (String playerId : players.keySet()) {
                    if (playerId.equals(this.playerId))
                        continue; // 跳过自己

                    try {
                        JsonObject playerData = players.getAsJsonObject(playerId);

                        String playerName = "";
                        if (playerData.has("name")) {
                            playerName = playerData.get("name").getAsString();
                        }
                        
                        float health = 0.0f;
                        if (playerData.has("health")) {
                            health = playerData.get("health").getAsFloat();
                        }
                        
                        float maxHealth = 0.0f;
                        if (playerData.has("maxHealth")) {
                            maxHealth = playerData.get("maxHealth").getAsFloat();
                        }
                        
                        int armor = 0;
                        if (playerData.has("armor")) {
                            armor = playerData.get("armor").getAsInt();
                        }
                        
                        // 获取碰撞箱尺寸信息
                        double width = 0.6;
                        double height = 1.8;
                        if (playerData.has("width")) {
                            width = playerData.get("width").getAsDouble();
                        }
                        if (playerData.has("height")) {
                            height = playerData.get("height").getAsDouble();
                        }

                        RemotePlayer remotePlayer = new RemotePlayer(playerId,
                                playerData.get("x").getAsDouble(),
                                playerData.get("y").getAsDouble(),
                                playerData.get("z").getAsDouble(),
                                playerData.get("dimension").getAsString(),
                                (long) (playerData.get("timestamp").getAsDouble() * 1000),
                                playerName,
                                health,
                                maxHealth,
                                armor,
                                width,
                                height); // 转换为毫秒

                        // 只添加最近更新的玩家（5秒内）
                        if (currentTime - remotePlayer.timestamp < 5000) {
                            remotePlayers.put(playerId, remotePlayer);
                            playerCount++;
                        }
                    } catch (Exception e) {
                        System.err.println("PlayerESP Network - Error parsing player data for " + playerId + ": " + e.getMessage());
                    }
                }

                for (String entityId : entities.keySet()) {
                    try {
                        JsonObject entityData = entities.getAsJsonObject(entityId);
                        if (entityData.has("x") && entityData.has("y") && entityData.has("z") &&
                                entityData.has("dimension") && entityData.has("entityType") && entityData.has("timestamp")) {

                            // 获取碰撞箱尺寸信息
                            double width = 0.6;
                            double height = 1.8;
                            if (entityData.has("width")) {
                                width = entityData.get("width").getAsDouble();
                            }
                            if (entityData.has("height")) {
                                height = entityData.get("height").getAsDouble();
                            }

                            RemoteEntity remoteEntity = new RemoteEntity(entityId,
                                    entityData.get("x").getAsDouble(),
                                    entityData.get("y").getAsDouble(),
                                    entityData.get("z").getAsDouble(),
                                    entityData.get("dimension").getAsString(),
                                    entityData.get("entityType").getAsString(),
                                    (long) (entityData.get("timestamp").getAsDouble() * 1000),
                                    width,
                                    height); // 转换为毫秒

                            // 只添加最近更新的实体（5秒内）
                            if (currentTime - remoteEntity.timestamp < 5000) {
                                remoteEntities.put(entityId, remoteEntity);
                                entityCount++;
                            }
                        } else {
                            System.err.println("PlayerESP Network - Incomplete entity data for " + entityId);
                        }
                    } catch (Exception e) {
                        System.err.println("PlayerESP Network - Error parsing entity data for " + entityId + ": " + e.getMessage());
                    }
                }

                // 显示处理后的数据统计
                // System.out.println("PlayerESP Network - Processed: " + playerCount + " players, " + entityCount + " entities");
            }
        } catch (Exception e) {
            System.err.println(
                    "PlayerESP Network - Error parsing message: " + e.getMessage());
            e.printStackTrace(); // 打印完整的堆栈跟踪
        }
    }

    /**
     * 尝试修复常见的JSON格式问题
     */
    private String fixJsonString(String json) {
        // 移除可能导致解析错误的控制字符
        return json.replaceAll("[\\x00-\\x1F&&[^\n\r\t]]", "");
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode,
                                      String reason) {
        this.connected = false;
        System.out.println("Disconnected from PlayerESP server.");
        scheduler.shutdown();
        return CompletableFuture.completedStage(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("PlayerESP network error: " + error.getMessage());
        this.connected = false;
    }

    private void sendPositionUpdate() {
        if (connected && webSocket != null) {
            try {
                JsonObject updateMsg = new JsonObject();
                updateMsg.addProperty("type", "players_update");
                updateMsg.addProperty("playerUUID", playerId);
                updateMsg.addProperty("x", 0.0);
                updateMsg.addProperty("y", 0.0);
                updateMsg.addProperty("z", 0.0);
                updateMsg.addProperty("dimension", "minecraft:overworld");
                updateMsg.addProperty("name", "");
                updateMsg.addProperty("health", 0.0f);
                updateMsg.addProperty("maxHealth", 0.0f);
                updateMsg.addProperty("armor", 0);
                updateMsg.addProperty("width", 0.6);
                updateMsg.addProperty("height", 1.8);
                updateMsg.addProperty("timestamp", Long.valueOf(System.currentTimeMillis()));

                sendMessage(updateMsg.toString());

                // 发送附近生物实体的位置更新
                sendEntitiesUpdate();
            } catch (Exception e) {
                // 忽略发送错误
            }
        }
    }

    private void sendEntitiesUpdate() {
        if (connected && webSocket != null) {
            try {
                JsonObject entitiesMsg = new JsonObject();
                entitiesMsg.addProperty("type", "entities_update");
                entitiesMsg.addProperty("submitPlayerId", playerId);
                entitiesMsg.addProperty("dimension", "minecraft:overworld");
                entitiesMsg.addProperty("timestamp",
                        Long.valueOf(System.currentTimeMillis()));

                // 收集附近的生物实体
                JsonObject entitiesData = new JsonObject();
                // 添加实体数据的逻辑需要在适配器中实现

                entitiesMsg.add("entities", entitiesData);
                sendMessage(entitiesMsg.toString());
            } catch (Exception e) {
                // 忽略发送错误
            }
        }
    }

    protected void sendMessage(String message) {
        if (connected && webSocket != null) {
            try {
                webSocket.sendText(message, true);
            } catch (Exception e) {
                // 忽略发送错误
            }
        }
    }

    public Map<String, RemotePlayer> getRemotePlayers() {
        // 创建一个副本以避免并发修改异常
        return new HashMap<>(remotePlayers);
    }

    public Map<String, RemoteEntity> getRemoteEntities() {
        // 创建一个副本以避免并发修改异常
        return new HashMap<>(remoteEntities);
    }

    public boolean isConnected() {
        return connected;
    }
    
    // 获取服务器IP
    public String getServerIP() {
        return serverIP;
    }
    
    // 获取服务器端口
    public String getServerPort() {
        return serverPort;
    }
    
    // 获取玩家ID
    public String getPlayerId() {
        return playerId;
    }
    
    // Properly shutdown the network manager
    public void shutdown() {
        connected = false;
        
        // Clear maps first to stop any processing
        remotePlayers.clear();
        remoteEntities.clear();
        
        // Clear message buffer
        if (messageBuffer != null) {
            messageBuffer.setLength(0);
            messageBuffer = null;
        }
        
        // Properly shutdown the scheduler
        if (scheduler != null) {
            try {
                // Attempt to shutdown the scheduler
                scheduler.shutdownNow();
                
                // Wait for termination
                if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    // Force shutdown if not terminated
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                // Re-interrupt the thread
                Thread.currentThread().interrupt();
            } finally {
                scheduler = null;
            }
        }
        
        // Properly close the WebSocket connection
        if (webSocket != null) {
            try {
                // Send close message and wait for response
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client shutdown");
                
                // Wait a bit for close to complete
                Thread.sleep(100);
            } catch (Exception e) {
                // Ignore exceptions during shutdown
            } finally {
                webSocket = null;
            }
        }
    }

    public static class RemotePlayer {
        public final String id;
        public final double x, y, z;
        public final String dimension;
        public final long timestamp;
        public final String name; // 添加名称字段
        public final float health; // 当前血量
        public final float maxHealth; // 最大血量
        public final int armor; // 护甲值
        public final double width; // 碰撞箱宽度
        public final double height; // 碰撞箱高度

        public RemotePlayer(String id, double x, double y, double z,
                            String dimension, long timestamp) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.timestamp = timestamp;
            this.name = ""; // 默认空名称
            this.health = 0.0f; // 默认血量
            this.maxHealth = 0.0f; // 默认最大血量
            this.armor = 0; // 默认护甲值
            this.width = 0.6; // 默认宽度
            this.height = 1.8; // 默认高度
        }
        
        // 添加新的构造函数以支持名称
        public RemotePlayer(String id, double x, double y, double z,
                            String dimension, long timestamp, String name) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.timestamp = timestamp;
            this.name = name;
            this.health = 0.0f; // 默认血量
            this.maxHealth = 0.0f; // 默认最大血量
            this.armor = 0; // 默认护甲值
            this.width = 0.6; // 默认宽度
            this.height = 1.8; // 默认高度
        }
        
        // 添加新的构造函数以支持血量、最大血量和护甲值
        public RemotePlayer(String id, double x, double y, double z,
                            String dimension, long timestamp, String name,
                            float health, float maxHealth, int armor) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.timestamp = timestamp;
            this.name = name;
            this.health = health;
            this.maxHealth = maxHealth;
            this.armor = armor;
            this.width = 0.6; // 默认宽度
            this.height = 1.8; // 默认高度
        }
        
        // 添加新的构造函数以支持碰撞箱尺寸
        public RemotePlayer(String id, double x, double y, double z,
                            String dimension, long timestamp, String name,
                            float health, float maxHealth, int armor,
                            double width, double height) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.timestamp = timestamp;
            this.name = name;
            this.health = health;
            this.maxHealth = maxHealth;
            this.armor = armor;
            this.width = width;
            this.height = height;
        }
    }

    public static class RemoteEntity {
        public final String id;
        public final double x, y, z;
        public final String dimension;
        public final String entityType;
        public final long timestamp;
        public final double width; // 碰撞箱宽度
        public final double height; // 碰撞箱高度

        public RemoteEntity(String id, double x, double y, double z,
                            String dimension, String entityType, long timestamp) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.entityType = entityType;
            this.timestamp = timestamp;
            this.width = 0.6; // 默认宽度
            this.height = 1.8; // 默认高度
        }
        
        // 添加新的构造函数以支持碰撞箱尺寸
        public RemoteEntity(String id, double x, double y, double z,
                            String dimension, String entityType, long timestamp,
                            double width, double height) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.entityType = entityType;
            this.timestamp = timestamp;
            this.width = width;
            this.height = height;
        }
    }
}