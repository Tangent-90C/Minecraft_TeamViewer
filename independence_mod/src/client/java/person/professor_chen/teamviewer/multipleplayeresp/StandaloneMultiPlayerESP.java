package person.professor_chen.teamviewer.multipleplayeresp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private static final int UPDATE_INTERVAL = 20; // 每20tick更新一次位置（约每秒1次）
	
	// 用于控制注册尝试
	private static int registrationAttemptCounter = 0;
	private static final int REGISTRATION_INTERVAL = 100; // 每100tick尝试注册一次
	
	@Override
	public void onInitializeClient() {
		// 初始化客户端功能
		LOGGER.info("Initializing MultiPlayer ESP mod");
		
		// 加载配置
		config = Config.load();
		
		// 初始化网络管理器
		networkManager = new PlayerESPNetworkManager(playerPositions);
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
		registrationAttemptCounter = 0;
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
		
		// 增加计数器
		tickCounter++;
		registrationAttemptCounter++;
		
		// 尝试注册玩家
		if (!networkManager.isRegistered() && registrationAttemptCounter >= REGISTRATION_INTERVAL) {
			registrationAttemptCounter = 0;
			networkManager.sendRegistration(client.player.getUuid());
		}
		
		// 发送位置更新
		if (networkManager.isRegistered() && tickCounter >= UPDATE_INTERVAL) {
			tickCounter = 0;
			
			// 获取玩家位置
			Vec3d pos = client.player.getPos();
			UUID playerId = client.player.getUuid();
			String dimension = client.player.getWorld().getRegistryKey().getValue().toString();
			
			// 发送位置更新到服务器
			networkManager.sendPositionUpdate(playerId, pos, dimension);
		}
	}
	
	private void renderESP(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) return;
		
		Vec3d cameraPos = context.camera().getPos();
		Map<UUID, Vec3d> positions = useServerPositions ? serverPlayerPositions : playerPositions;
		
		// 渲染玩家位置框
		for (Map.Entry<UUID, Vec3d> entry : positions.entrySet()) {
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
					RenderUtils.drawOutlinedBox(context.matrixStack(), box, config.getBoxColor(), true);
				}
				
				// 绘制连线
				if (config.isShowLines()) {
					Vec3d cameraRelativePos = client.player.getCameraPosVec(1.0F).subtract(cameraPos);
					RenderUtils.drawLine(context.matrixStack(), cameraRelativePos, relativePos.add(0, 1, 0), config.getLineColor());
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
}