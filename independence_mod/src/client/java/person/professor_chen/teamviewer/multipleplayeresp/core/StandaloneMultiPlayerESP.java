package person.professor_chen.teamviewer.multipleplayeresp.core;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import person.professor_chen.teamviewer.multipleplayeresp.bridge.XaeroWaypointShareBridge;
import person.professor_chen.teamviewer.multipleplayeresp.bridge.XaeroWorldMapBridge;
import person.professor_chen.teamviewer.multipleplayeresp.config.Config;
import person.professor_chen.teamviewer.multipleplayeresp.model.RemotePlayerInfo;
import person.professor_chen.teamviewer.multipleplayeresp.network.PlayerESPNetworkManager;
import person.professor_chen.teamviewer.multipleplayeresp.ui.PlayerESPConfigScreen;
import person.professor_chen.teamviewer.multipleplayeresp.render.UnifiedRenderModule;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StandaloneMultiPlayerESP implements ClientModInitializer {
	public static final String MOD_ID = "multipleplayeresp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	public static final MinecraftClient MC = MinecraftClient.getInstance();
	
	// Mod keybinding
	private static KeyBinding toggleKey;
	private static KeyBinding configKey;
	
	// Player position tracking
	private static final Map<UUID, Vec3d> playerPositions = new ConcurrentHashMap<>();
	private static final Map<UUID, RemotePlayerInfo> remotePlayers = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3d> serverPlayerPositions = new ConcurrentHashMap<>();
	
	// Network manager
	private static PlayerESPNetworkManager networkManager;
	
	// Config
	private static Config config;
	
	// ESP settings
	private static boolean espEnabled = false;
	private static boolean useServerPositions = false;
	private static boolean showNames = true;
	
	// 用于控制位置更新频率
	private static int tickCounter = 0;
	
	@Override
	public void onInitializeClient() {
		// 初始化客户端功能
		LOGGER.info("Initializing MultiPlayer ESP mod");
		
		// 加载配置
		config = Config.load();
		
		// 初始化网络管理器
		networkManager = new PlayerESPNetworkManager(playerPositions, remotePlayers);
		PlayerESPNetworkManager.setConfig(config);
		
		// 注册按键绑定
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.multipleplayeresp.toggle",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_O, // 默认绑定O键
			"category.multipleplayeresp.general"
		));
		
		// 注册配置界面按键
		configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.multipleplayeresp.config",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_P, // 默认绑定P键
			"category.multipleplayeresp.general"
		));
		
		// 注册客户端tick事件
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// 处理按键输入
			while (toggleKey.wasPressed()) {
				toggleESP();
			}
			
			// 处理配置界面按键
			while (configKey.wasPressed()) {
				openConfigScreen();
			}
			
			// 更新玩家位置信息
			updatePlayerPositions();

			// 同步远程玩家到Xaero世界地图
			XaeroWorldMapBridge.tick(remotePlayers, espEnabled);
			XaeroWaypointShareBridge.tick(networkManager, espEnabled, config);
			
			// 发送玩家位置到服务器
			if (espEnabled && networkManager != null) {
				handleRegistrationAndPositionUpdates();
			}
		});
		
		// 注册世界渲染事件
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if (espEnabled) {
				renderESP(context);
			}
		});
		
		LOGGER.info("MultiPlayer ESP mod initialized");
	}
	
	private void toggleESP() {
		espEnabled = !espEnabled;
		
		if (espEnabled) {
			// 启用ESP，连接到服务器
			networkManager.connect();
			LOGGER.info("MultiPlayer ESP enabled");
		} else {
			// 禁用ESP，断开连接
			networkManager.disconnect();
			LOGGER.info("MultiPlayer ESP disabled");
		}
		
		// 重置计数器
		tickCounter = 0;
	}
	
	private void openConfigScreen() {
		MC.setScreen(new PlayerESPConfigScreen(MC.currentScreen));
	}
	
	private void updatePlayerPositions() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null && client.player != null) {
			// 更新服务器端玩家位置
			serverPlayerPositions.clear();
			for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
				if (player != client.player) { // 不包括自己
					serverPlayerPositions.put(player.getUuid(), player.getPos());
				}
			}
		}
	}
	
	private void handleRegistrationAndPositionUpdates() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !client.player.isAlive()) {
			return;
		}
		
		tickCounter++;
		
		// 连接成功后按间隔发送玩家更新与实体更新（submitPlayerId 为本地玩家 UUID）
		if (networkManager.isConnected() && tickCounter >= config.getUpdateInterval()) {
			tickCounter = 0;
			UUID submitPlayerId = client.player.getUuid();

			// 批量收集所有玩家（含本地）并上传，格式：playerId -> { x, y, z, vx, vy, vz, dimension, playerName, playerUUID, health, maxHealth, armor, width, height }
			Map<UUID, Map<String, Object>> players = new HashMap<>();
			if (client.world != null) {
				for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
					UUID pid = p.getUuid();
					Vec3d pos = p.getPos();
					Vec3d vel = p.getVelocity();
					Map<String, Object> data = new HashMap<>();
					data.put("x", pos.x);
					data.put("y", pos.y);
					data.put("z", pos.z);
					data.put("vx", vel.x);
					data.put("vy", vel.y);
					data.put("vz", vel.z);
					data.put("dimension", p.getWorld().getRegistryKey().getValue().toString());
					data.put("playerName", p.getName().getString());
					data.put("playerUUID", pid.toString());
					data.put("health", p.getHealth());
					data.put("maxHealth", p.getMaxHealth());
					data.put("armor", 0);
					data.put("width", p.getWidth());
					data.put("height", p.getHeight());
					players.put(pid, data);
				}
			}
			networkManager.sendPlayersUpdate(submitPlayerId, players);

			// 收集并上报当前世界中的实体（带 submitPlayerId）
			if (config.isUploadEntities() && client.world != null) {
				Map<String, Map<String, Object>> entities = new HashMap<>();
				for (Entity entity : client.world.getEntities()) {
					if (entity == client.player) continue;
					String entityId = entity.getUuid().toString();
					Vec3d ePos = entity.getPos();
					Vec3d eVel = entity.getVelocity();
					String eDim = entity.getWorld().getRegistryKey().getValue().toString();
					String entityType = entity.getType().getRegistryEntry().registryKey().getValue().toString();
					Map<String, Object> data = new HashMap<>();
					data.put("x", ePos.x);
					data.put("y", ePos.y);
					data.put("z", ePos.z);
					data.put("vx", eVel.x);
					data.put("vy", eVel.y);
					data.put("vz", eVel.z);
					data.put("dimension", eDim);
					data.put("entityType", entityType);
					data.put("entityName", entity.hasCustomName() ? entity.getDisplayName().getString() : null);
					data.put("width", entity.getWidth());
					data.put("height", entity.getHeight());
					entities.put(entityId, data);
				}
				networkManager.sendEntitiesUpdate(submitPlayerId, entities);
			}
		}
	}
	
	private void renderESP(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) return;
		
		Vec3d cameraPos = context.camera().getPos();
		Map<UUID, Vec3d> positions = useServerPositions ? serverPlayerPositions : playerPositions;
		
		// 渲染玩家位置框
		for (Map.Entry<UUID, Vec3d> entry : positions.entrySet()) {
			if (entry.getKey().equals(client.player.getUuid())) {
				continue; // 跳过自己
			}

			Vec3d playerPos = entry.getValue();
			
			// 检查距离
			if (client.player.getPos().distanceTo(playerPos) <= config.getRenderDistance()) {
				// 计算相对位置
				Vec3d relativePos = playerPos.subtract(cameraPos);
				
				// 创建玩家包围盒
				Box box = new Box(
					relativePos.x - 0.3, relativePos.y, relativePos.z - 0.3,
					relativePos.x + 0.3, relativePos.y + 1.8, relativePos.z + 0.3
				);
				
				// 绘制包围盒
				if (config.isShowBoxes()) {
					UnifiedRenderModule.drawOutlinedBox(context.matrixStack(), box, config.getBoxColor(), true);
				}
				
				// 绘制连线
				if (config.isShowLines()) {
					Vec3d targetPos = relativePos.add(0, 1.0, 0);
					Vec3d lookVec = client.player.getRotationVec(1.0F).normalize();
					Vec3d worldUp = new Vec3d(0, 1, 0);
					Vec3d rightVec = lookVec.crossProduct(worldUp);
					if (rightVec.lengthSquared() < 1.0E-6) {
						rightVec = new Vec3d(1, 0, 0);
					}
					Vec3d cameraUpVec = rightVec.normalize().crossProduct(lookVec).normalize();

					Vec3d tracerStart;
					if (config.isTracerStartTop()) {
						tracerStart = lookVec.multiply(0.6).add(cameraUpVec.multiply(config.getTracerTopOffset()));
					} else {
						tracerStart = lookVec.multiply(0.6);
					}
					UnifiedRenderModule.drawTracerLine(context.matrixStack(), tracerStart, targetPos, config.getLineColor());
				}
			}
		}
	}
	
	// 网络管理方法
	public static void reconnectToServer() {
		if (networkManager != null) {
			networkManager.disconnect();
			if (espEnabled) {
				networkManager.connect();
			}
		}
	}
	
	// Getter和Setter方法
	public static boolean isEspEnabled() {
		return espEnabled;
	}
	
	public static void setEspEnabled(boolean espEnabled) {
		StandaloneMultiPlayerESP.espEnabled = espEnabled;
	}
	
	
	public static Config getConfig() {
		return config;
	}
	
	public static Map<UUID, Vec3d> getPlayerPositions() {
		return playerPositions;
	}
	
	public static Map<UUID, Vec3d> getServerPlayerPositions() {
		return serverPlayerPositions;
	}
	
	public static PlayerESPNetworkManager getNetworkManager() {
		return networkManager;
	}
}