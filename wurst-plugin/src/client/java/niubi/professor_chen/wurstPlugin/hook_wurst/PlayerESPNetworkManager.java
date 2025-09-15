package niubi.professor_chen.wurstPlugin.hook_wurst;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.wurstclient.WurstClient;
import net.wurstclient.util.ChatUtils;
import niubi.professor_chen.wurstPlugin.network.PlayerESPNetworkHandler;

import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

/**
 * PlayerESPNetworkManager handles the WebSocket connection to a server that
 * shares player positions between clients.
 * This is the Wurst adapter for the base PlayerESPNetworkHandler.
 */
public class PlayerESPNetworkManager extends PlayerESPNetworkHandler {
    private static final Gson GSON = new Gson();

    private final WurstClient wurst = WurstClient.INSTANCE;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public PlayerESPNetworkManager(String serverIP, String serverPort) {
        super(serverIP, serverPort);
    }

    @Override
    public void connect() {
        try {
            super.connect();
        } catch (Exception e) {
            ChatUtils.error(
                    "Failed to connect to PlayerESP server: " + e.getMessage());
        }
    }

    @Override
    protected void sendMessage(String message) {
        // 只有当有玩家和世界实例时才发送真实数据
        if (isConnected() && mc.player != null && mc.world != null) {
            // 解析原始消息以确定类型
            try {
                JsonObject json = com.google.gson.JsonParser.parseString(message).getAsJsonObject();
                String type = json.has("type") ? json.get("type").getAsString() : "";
                
                if ("players_update".equals(type)) {
                    // 发送真实的玩家位置更新
                    JsonObject updateMsg = new JsonObject();
                    updateMsg.addProperty("type", "players_update");
                    updateMsg.addProperty("playerUUID", getPlayerId());
                    updateMsg.addProperty("x", Double.valueOf(mc.player.getX()));
                    updateMsg.addProperty("y", Double.valueOf(mc.player.getY()));
                    updateMsg.addProperty("z", Double.valueOf(mc.player.getZ()));
                    updateMsg.addProperty("dimension",
                            mc.world.getRegistryKey().getValue().toString());
                    
                    // 添加玩家名称，包括格式（颜色等）
                    updateMsg.addProperty("name", GSON.toJson(mc.player.getName()));
                    
                    // 添加玩家血量和最大血量
                    updateMsg.addProperty("health", Float.valueOf(mc.player.getHealth()));
                    updateMsg.addProperty("maxHealth", Float.valueOf(mc.player.getMaxHealth()));
                    
                    // 添加玩家护甲值
                    updateMsg.addProperty("armor", Integer.valueOf(mc.player.getArmor()));
                    
                    // 添加玩家碰撞箱尺寸
                    updateMsg.addProperty("width", Double.valueOf(mc.player.getWidth()));
                    updateMsg.addProperty("height", Double.valueOf(mc.player.getHeight()));
                    
                    updateMsg.addProperty("timestamp", Long.valueOf(System.currentTimeMillis()));
                    
                    super.sendMessage(updateMsg.toString());
                    return;
                } else if ("entities_update".equals(type)) {
                    // 发送真实的实体位置更新
                    sendEntitiesUpdate();
                    return;
                }
            } catch (Exception e) {
                // 如果解析失败，继续发送原始消息
            }
        }
        
        // 发送原始消息
        super.sendMessage(message);
    }

    private void sendEntitiesUpdate() {
        if (isConnected() && mc.world != null && mc.player != null) {
            try {
                JsonObject entitiesMsg = new JsonObject();
                entitiesMsg.addProperty("type", "entities_update");
                entitiesMsg.addProperty("submitPlayerId", getPlayerId());
                entitiesMsg.addProperty("dimension",
                        mc.world.getRegistryKey().getValue().toString());
                entitiesMsg.addProperty("timestamp",
                        Long.valueOf(System.currentTimeMillis()));

                // 收集附近的生物实体
                JsonObject entitiesData = new JsonObject();
                for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
                    if (entity instanceof LivingEntity
                            && !(entity instanceof PlayerEntity)) {
                        // 只发送相对靠近玩家的生物实体（128格以内）
                        if (entity.distanceTo(mc.player) <= 128) {
                            String entityId = entity.getUuid().toString();
                            JsonObject entityInfo = new JsonObject();
                            entityInfo.addProperty("x", Double.valueOf(entity.getX()));
                            entityInfo.addProperty("y", Double.valueOf(entity.getY()));
                            entityInfo.addProperty("z", Double.valueOf(entity.getZ()));
                            entityInfo.addProperty("dimension", mc.world
                                    .getRegistryKey().getValue().toString());
                            entityInfo.addProperty("entityType",
                                    Registries.ENTITY_TYPE.getId(entity.getType())
                                            .toString());
                            
                            // 添加实体碰撞箱尺寸
                            entityInfo.addProperty("width", Double.valueOf(entity.getWidth()));
                            entityInfo.addProperty("height", Double.valueOf(entity.getHeight()));
                            
                            entityInfo.addProperty("timestamp",
                                    Long.valueOf(System.currentTimeMillis()));

                            entitiesData.add(entityId, entityInfo);
                        }
                    }
                }

                entitiesMsg.add("entities", entitiesData);
                super.sendMessage(entitiesMsg.toString());
            } catch (Exception e) {
                // 忽略发送错误
            }
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        return super.onText(webSocket, data, last);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        ChatUtils.message("Disconnected from PlayerESP server.");
        return super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        super.onError(webSocket, error);
        ChatUtils.error("PlayerESP network error: " + error.getMessage());
    }
}