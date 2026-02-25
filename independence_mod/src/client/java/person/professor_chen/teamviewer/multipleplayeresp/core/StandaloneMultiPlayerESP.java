package person.professor_chen.teamviewer.multipleplayeresp.core;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import person.professor_chen.teamviewer.multipleplayeresp.bridge.XaeroWaypointShareBridge;
import person.professor_chen.teamviewer.multipleplayeresp.bridge.XaeroWorldMapBridge;
import person.professor_chen.teamviewer.multipleplayeresp.config.Config;
import person.professor_chen.teamviewer.multipleplayeresp.model.RemotePlayerInfo;
import person.professor_chen.teamviewer.multipleplayeresp.model.SharedWaypointInfo;
import person.professor_chen.teamviewer.multipleplayeresp.network.PlayerESPNetworkManager;
import person.professor_chen.teamviewer.multipleplayeresp.ui.PlayerESPConfigScreen;
import person.professor_chen.teamviewer.multipleplayeresp.render.UnifiedRenderModule;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
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
	private static KeyBinding markKey;
	
	// Player position tracking
	private static final Map<UUID, Vec3d> playerPositions = new ConcurrentHashMap<>();
	private static final Map<UUID, RemotePlayerInfo> remotePlayers = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3d> serverPlayerPositions = new ConcurrentHashMap<>();
	private static final Map<String, SharedWaypointInfo> sharedWaypoints = new ConcurrentHashMap<>();
	private static final Map<String, Vec3d> trackedEntityWaypointLastPositions = new ConcurrentHashMap<>();
	
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

	// 鼠标中键双击报点
	private static final long MARK_DOUBLE_CLICK_MS = 300L;
	private static final double MARK_RAYCAST_DISTANCE = 256.0D;
	private static final double MARK_CANCEL_BASE_RADIUS = 1.2D;
	private static final double MARK_CANCEL_RADIUS_PER_BLOCK = 0.02D;
	private static final double MARK_CANCEL_MAX_RADIUS = 4.0D;
	private static final int MARKER_COLOR_RGB = 0xFF8C00;
	private static boolean middlePressedLastTick = false;
	private static long lastMiddleClickTs = 0L;
	
	@Override
	public void onInitializeClient() {
		// 初始化客户端功能
		LOGGER.info("Initializing MultiPlayer ESP mod");
		
		// 加载配置
		config = Config.load();
		
		// 初始化网络管理器
		networkManager = new PlayerESPNetworkManager(playerPositions, remotePlayers);
		PlayerESPNetworkManager.setConfig(config);
		registerWaypointSyncListener();
		
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

		// 注册报点按键（可在游戏控制设置中自定义）
		markKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.multipleplayeresp.mark",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN, // 默认不绑定，玩家自行设置
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

			// 处理报点按键（单击触发）
			while (markKey.wasPressed()) {
				if (canCreateMark(client)) {
					createAndSyncQuickMark(client);
				}
			}
			
			// 更新玩家位置信息
			updatePlayerPositions();
			handleMiddleMouseDoubleClickMarking(client);

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
			sharedWaypoints.clear();
			trackedEntityWaypointLastPositions.clear();
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

		renderSharedWaypointMarkers(context, cameraPos);
	}

	private void registerWaypointSyncListener() {
		networkManager.addWaypointUpdateListener(new PlayerESPNetworkManager.WaypointUpdateListener() {
			@Override
			public void onWaypointsReceived(Map<String, SharedWaypointInfo> waypoints) {
				if (waypoints == null || waypoints.isEmpty()) {
					return;
				}
				sharedWaypoints.putAll(waypoints);
			}

			@Override
			public void onWaypointsDeleted(List<String> waypointIds) {
				if (waypointIds == null || waypointIds.isEmpty()) {
					return;
				}
				for (String waypointId : waypointIds) {
					sharedWaypoints.remove(waypointId);
					trackedEntityWaypointLastPositions.remove(waypointId);
				}
				XaeroWaypointShareBridge.deleteSharedWaypoints(waypointIds);
			}
		});
	}

	private void handleMiddleMouseDoubleClickMarking(MinecraftClient client) {
		if (config == null) {
			middlePressedLastTick = false;
			return;
		}

		boolean enableDoubleClickMark = config.isEnableMiddleDoubleClickMark();
		boolean enableClickCancel = config.isEnableMiddleClickCancelWaypoint();
		if (!enableDoubleClickMark && !enableClickCancel) {
			middlePressedLastTick = false;
			return;
		}

		if (!canCreateMark(client)) {
			middlePressedLastTick = false;
			return;
		}

		long windowHandle = client.getWindow().getHandle();
		boolean middlePressed = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

		if (middlePressed && !middlePressedLastTick) {
			if (enableClickCancel && tryCancelTargetedWaypoint(client)) {
				lastMiddleClickTs = 0L;
				middlePressedLastTick = middlePressed;
				return;
			}
			if (!enableDoubleClickMark) {
				middlePressedLastTick = middlePressed;
				return;
			}

			long now = System.currentTimeMillis();
			if (now - lastMiddleClickTs <= MARK_DOUBLE_CLICK_MS) {
				lastMiddleClickTs = 0L;
				createAndSyncQuickMark(client);
			} else {
				lastMiddleClickTs = now;
			}
		}

		middlePressedLastTick = middlePressed;
	}

	private boolean tryCancelTargetedWaypoint(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null || sharedWaypoints.isEmpty()) {
			return false;
		}

		UUID localPlayerId = client.player.getUuid();
		String currentDimension = client.world.getRegistryKey().getValue().toString();
		Vec3d eyePos = client.player.getCameraPosVec(1.0F);
		Vec3d lookVec = client.player.getRotationVec(1.0F).normalize();
		double maxDistance = Math.max(config.getRenderDistance(), MARK_RAYCAST_DISTANCE);

		String selectedWaypointId = null;
		double selectedScore = Double.MAX_VALUE;

		for (Map.Entry<String, SharedWaypointInfo> entry : sharedWaypoints.entrySet()) {
			SharedWaypointInfo waypoint = entry.getValue();
			if (waypoint == null) {
				continue;
			}
			if (waypoint.ownerId() == null || !localPlayerId.equals(waypoint.ownerId())) {
				continue;
			}
			if (waypoint.dimension() != null && !waypoint.dimension().isBlank() && !waypoint.dimension().equals(currentDimension)) {
				continue;
			}

			Vec3d waypointPos = resolveWaypointWorldPosition(client, waypoint, currentDimension);
			if (waypointPos == null) {
				continue;
			}

			Vec3d toWaypoint = waypointPos.subtract(eyePos);
			double alongRay = toWaypoint.dotProduct(lookVec);
			if (alongRay <= 0.0D || alongRay > maxDistance) {
				continue;
			}

			Vec3d closestPoint = eyePos.add(lookVec.multiply(alongRay));
			double lateralDistance = waypointPos.distanceTo(closestPoint);
			double allowedRadius = Math.min(
				MARK_CANCEL_MAX_RADIUS,
				MARK_CANCEL_BASE_RADIUS + alongRay * MARK_CANCEL_RADIUS_PER_BLOCK
			);
			if (lateralDistance > allowedRadius) {
				continue;
			}

			double score = lateralDistance + alongRay * 0.0025D;
			if (score < selectedScore) {
				selectedScore = score;
				selectedWaypointId = entry.getKey();
			}
		}

		if (selectedWaypointId == null) {
			return false;
		}

		SharedWaypointInfo removed = sharedWaypoints.remove(selectedWaypointId);
		trackedEntityWaypointLastPositions.remove(selectedWaypointId);
		XaeroWaypointShareBridge.deleteSharedWaypoint(selectedWaypointId);
		networkManager.sendWaypointsDelete(localPlayerId, List.of(selectedWaypointId));

		if (removed != null) {
			String removedName = removed.name() == null || removed.name().isBlank() ? selectedWaypointId : removed.name();
			client.player.sendMessage(Text.literal("§e[TV] 已取消报点: " + removedName), true);
		}
		return true;
	}

	private boolean canCreateMark(MinecraftClient client) {
		return espEnabled
			&& client != null
			&& client.player != null
			&& client.world != null
			&& client.currentScreen == null
			&& networkManager != null
			&& networkManager.isConnected();
	}

	private void createAndSyncQuickMark(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}

		MarkTarget target = resolveMarkTarget(client);
		if (target == null) {
			client.player.sendMessage(Text.literal("§c[TV] 报点失败：未命中方块或实体"), true);
			return;
		}

		Vec3d worldPos = target.position();
		int markX = (int) Math.floor(worldPos.x);
		int markY = (int) Math.floor(worldPos.y);
		int markZ = (int) Math.floor(worldPos.z);

		UUID ownerId = client.player.getUuid();
		String ownerName = client.player.getName().getString();
		String dimension = client.world.getRegistryKey().getValue().toString();
		long createdAt = System.currentTimeMillis();
		String idSource = ownerId + "|" + createdAt + "|" + dimension + "|" + markX + "|" + markY + "|" + markZ;
		String waypointId = UUID.nameUUIDFromBytes(idSource.getBytes(StandardCharsets.UTF_8)).toString();

		int maxQuickMarkCount = config.getMaxQuickMarkCount();
		List<String> overflowQuickWaypointIds = collectOverflowQuickWaypointIdsByOwner(ownerId, waypointId, maxQuickMarkCount);
		if (!overflowQuickWaypointIds.isEmpty()) {
			for (String oldId : overflowQuickWaypointIds) {
				sharedWaypoints.remove(oldId);
			}
			networkManager.sendWaypointsDelete(ownerId, overflowQuickWaypointIds);
		}

		String targetType = target.targetEntity() == null ? "block" : "entity";
		String targetEntityId = target.targetEntity() == null ? null : target.targetEntity().getUuidAsString();
		String targetEntityType = target.targetEntity() == null
				? null
				: target.targetEntity().getType().getRegistryEntry().registryKey().getValue().toString();
		String targetEntityName = target.targetEntity() == null
				? null
				: target.targetEntity().getDisplayName().getString();

		String markName;
		if (target.targetEntity() != null) {
			String displayName = targetEntityName == null || targetEntityName.isBlank() ? "Entity" : targetEntityName;
			markName = "报点[实体] " + displayName + " @ " + markX + " " + markY + " " + markZ;
		} else {
			markName = "报点 " + markX + " " + markY + " " + markZ;
		}

		SharedWaypointInfo waypoint = new SharedWaypointInfo(
			waypointId,
			ownerId,
			ownerName,
			markName,
			"!",
			markX,
			markY,
			markZ,
			dimension,
			MARKER_COLOR_RGB,
			createdAt,
			targetType,
			targetEntityId,
			targetEntityType,
			targetEntityName,
			"quick"
		);
		sharedWaypoints.put(waypointId, waypoint);

		Map<String, Object> payload = new HashMap<>();
		payload.put("x", markX);
		payload.put("y", markY);
		payload.put("z", markZ);
		payload.put("dimension", dimension);
		payload.put("name", waypoint.name());
		payload.put("symbol", waypoint.symbol());
		payload.put("color", waypoint.color());
		payload.put("ownerId", ownerId.toString());
		payload.put("ownerName", ownerName);
		payload.put("createdAt", createdAt);
		payload.put("ttlSeconds", config.getWaypointTimeoutSeconds());
		payload.put("waypointKind", "quick");
		payload.put("maxQuickMarks", maxQuickMarkCount);
		payload.put("targetType", targetType);
		payload.put("targetEntityId", targetEntityId);
		payload.put("targetEntityType", targetEntityType);
		payload.put("targetEntityName", targetEntityName);

		Map<String, Map<String, Object>> toUpload = new HashMap<>();
		toUpload.put(waypointId, payload);
		networkManager.sendWaypointsUpdate(ownerId, toUpload);

		if (target.targetEntity() != null) {
			String displayName = targetEntityName == null || targetEntityName.isBlank() ? "实体" : targetEntityName;
			client.player.sendMessage(Text.literal("§6[TV] 已报点实体: " + displayName + " @ " + markX + " " + markY + " " + markZ), true);
		} else {
			client.player.sendMessage(Text.literal("§6[TV] 已报点方块: " + markX + " " + markY + " " + markZ), true);
		}
	}

	private List<String> collectOverflowQuickWaypointIdsByOwner(UUID ownerId, String exceptWaypointId, int maxKeepCount) {
		List<Map.Entry<String, SharedWaypointInfo>> quickEntries = new java.util.ArrayList<>();
		for (Map.Entry<String, SharedWaypointInfo> entry : sharedWaypoints.entrySet()) {
			SharedWaypointInfo waypoint = entry.getValue();
			if (waypoint == null) {
				continue;
			}
			if (exceptWaypointId != null && exceptWaypointId.equals(entry.getKey())) {
				continue;
			}
			if (!ownerId.equals(waypoint.ownerId())) {
				continue;
			}
			if (!"quick".equalsIgnoreCase(waypoint.waypointKind())) {
				continue;
			}
			quickEntries.add(entry);
		}

		int normalizedMax = Math.max(1, maxKeepCount);
		int removeCount = quickEntries.size() - normalizedMax + 1;
		if (removeCount <= 0) {
			return List.of();
		}

		quickEntries.sort((left, right) -> Long.compare(left.getValue().createdAt(), right.getValue().createdAt()));
		List<String> overflowIds = new java.util.ArrayList<>();
		for (int index = 0; index < removeCount && index < quickEntries.size(); index++) {
			overflowIds.add(quickEntries.get(index).getKey());
		}
		return overflowIds;
	}

	private MarkTarget resolveMarkTarget(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return null;
		}

		Vec3d eyePos = client.player.getCameraPosVec(1.0F);
		Vec3d lookVec = client.player.getRotationVec(1.0F).normalize();
		double maxDistance = Math.max(config.getRenderDistance(), MARK_RAYCAST_DISTANCE);
		Vec3d rayEnd = eyePos.add(lookVec.multiply(maxDistance));

		BlockHitResult blockHit = client.world.raycast(new RaycastContext(
			eyePos,
			rayEnd,
			RaycastContext.ShapeType.OUTLINE,
			RaycastContext.FluidHandling.NONE,
			client.player
		));

		double blockDistSq = Double.MAX_VALUE;
		Vec3d blockMarkPos = null;
		if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
			blockDistSq = eyePos.squaredDistanceTo(blockHit.getPos());
			blockMarkPos = new Vec3d(
				blockHit.getBlockPos().getX() + 0.5D,
				blockHit.getBlockPos().getY() + 1.0D,
				blockHit.getBlockPos().getZ() + 0.5D
			);
		}

		Box searchBox = client.player.getBoundingBox().stretch(lookVec.multiply(maxDistance)).expand(1.0D);
		EntityHitResult entityHit = ProjectileUtil.raycast(
			client.player,
			eyePos,
			rayEnd,
			searchBox,
			entity -> entity != null && entity.isAlive() && !entity.isSpectator() && entity.canHit(),
			maxDistance * maxDistance
		);

		double entityDistSq = Double.MAX_VALUE;
		Entity entityTarget = null;
		Vec3d entityMarkPos = null;
		if (entityHit != null && entityHit.getEntity() != null) {
			entityTarget = entityHit.getEntity();
			entityMarkPos = entityTarget.getPos();
			entityDistSq = eyePos.squaredDistanceTo(entityHit.getPos());
		}

		if (entityMarkPos == null && blockMarkPos == null) {
			return null;
		}
		if (entityMarkPos != null && entityDistSq <= blockDistSq) {
			return new MarkTarget(entityMarkPos, entityTarget);
		}
		return new MarkTarget(blockMarkPos, null);
	}

	private void renderSharedWaypointMarkers(WorldRenderContext context, Vec3d cameraPos) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			return;
		}
		if (!config.isShowSharedWaypoints() || sharedWaypoints.isEmpty()) {
			return;
		}

		String currentDimension = client.world.getRegistryKey().getValue().toString();
		double maxDistance = Math.max(config.getRenderDistance(), 16.0);

		for (SharedWaypointInfo waypoint : sharedWaypoints.values()) {
			if (waypoint == null) {
				continue;
			}
			if (waypoint.dimension() != null && !waypoint.dimension().isBlank() && !waypoint.dimension().equals(currentDimension)) {
				continue;
			}

			Vec3d worldPos = resolveWaypointWorldPosition(client, waypoint, currentDimension);
			if (worldPos == null) {
				continue;
			}
			if (client.player.getPos().distanceTo(worldPos) > maxDistance) {
				continue;
			}

			Vec3d relativePos = worldPos.subtract(cameraPos);
			int color = withAlpha(waypoint.color(), 0xCC);
			renderWaypointMarkerStyle(context, relativePos, color);
		}
	}

	private Vec3d resolveWaypointWorldPosition(MinecraftClient client, SharedWaypointInfo waypoint, String currentDimension) {
		if (waypoint == null) {
			return null;
		}

		String targetType = waypoint.targetType();
		String targetEntityId = waypoint.targetEntityId();
		if (!"entity".equalsIgnoreCase(targetType) || targetEntityId == null || targetEntityId.isBlank()) {
			return new Vec3d(waypoint.x() + 0.5D, waypoint.y(), waypoint.z() + 0.5D);
		}

		Vec3d localEntityPos = resolveLocalEntityPosition(client, targetEntityId, currentDimension);
		if (localEntityPos != null) {
			trackedEntityWaypointLastPositions.put(waypoint.waypointId(), localEntityPos);
			return localEntityPos;
		}

		Vec3d remoteEntityPos = networkManager == null ? null : networkManager.getRemoteEntityPosition(targetEntityId, currentDimension);
		if (remoteEntityPos != null) {
			trackedEntityWaypointLastPositions.put(waypoint.waypointId(), remoteEntityPos);
			return remoteEntityPos;
		}

		Vec3d lastKnown = trackedEntityWaypointLastPositions.get(waypoint.waypointId());
		if (lastKnown != null) {
			return lastKnown;
		}

		Vec3d initial = new Vec3d(waypoint.x() + 0.5D, waypoint.y(), waypoint.z() + 0.5D);
		trackedEntityWaypointLastPositions.put(waypoint.waypointId(), initial);
		return initial;
	}

	private Vec3d resolveLocalEntityPosition(MinecraftClient client, String entityUuid, String currentDimension) {
		if (client == null || client.world == null || entityUuid == null || entityUuid.isBlank()) {
			return null;
		}

		String worldDimension = client.world.getRegistryKey().getValue().toString();
		if (currentDimension != null && !currentDimension.isBlank() && !currentDimension.equals(worldDimension)) {
			return null;
		}

		for (Entity entity : client.world.getEntities()) {
			if (entity == null) {
				continue;
			}
			if (entityUuid.equals(entity.getUuidAsString())) {
				return entity.getPos();
			}
		}

		return null;
	}

	private void renderWaypointMarkerStyle(WorldRenderContext context, Vec3d basePos, int color) {
		String style = config.getWaypointUiStyle();
		if (Config.WAYPOINT_UI_RING.equals(style)) {
			renderWaypointRingStyle(context, basePos, color);
			return;
		}
		if (Config.WAYPOINT_UI_PIN.equals(style)) {
			renderWaypointPinStyle(context, basePos, color);
			return;
		}
		renderWaypointBeaconStyle(context, basePos, color);
	}

	private void renderWaypointBeaconStyle(WorldRenderContext context, Vec3d basePos, int color) {
		Vec3d center = basePos.add(0.0D, 0.2D, 0.0D);
		Vec3d top = basePos.add(0.0D, 8.0D, 0.0D);
		UnifiedRenderModule.drawLine(context.matrixStack(), center, top, color);

		double radius = 0.28D;
		for (int i = 0; i < 4; i++) {
			double angle = (Math.PI / 2.0D) * i;
			Vec3d p = center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
			Vec3d pTop = p.add(0.0D, 6.5D, 0.0D);
			UnifiedRenderModule.drawLine(context.matrixStack(), p, pTop, withAlpha(color, 0x90));
		}

		renderCircle(context, center.add(0.0D, 0.02D, 0.0D), 0.7D, color, 18);
		renderCircle(context, center.add(0.0D, 7.2D, 0.0D), 0.4D, withAlpha(color, 0xAA), 14);
	}

	private void renderWaypointRingStyle(WorldRenderContext context, Vec3d basePos, int color) {
		Vec3d center = basePos.add(0.0D, 0.05D, 0.0D);
		renderCircle(context, center, 0.95D, color, 24);
		renderCircle(context, center.add(0.0D, 0.3D, 0.0D), 0.65D, withAlpha(color, 0x9A), 18);

		for (int i = 0; i < 4; i++) {
			double angle = (Math.PI / 2.0D) * i;
			Vec3d start = center.add(Math.cos(angle) * 0.3D, 0.0D, Math.sin(angle) * 0.3D);
			Vec3d end = center.add(Math.cos(angle) * 1.2D, 0.0D, Math.sin(angle) * 1.2D);
			UnifiedRenderModule.drawLine(context.matrixStack(), start, end, withAlpha(color, 0x88));
		}

		UnifiedRenderModule.drawLine(context.matrixStack(), center.add(0.0D, 0.1D, 0.0D), center.add(0.0D, 3.0D, 0.0D), withAlpha(color, 0xB5));
	}

	private void renderWaypointPinStyle(WorldRenderContext context, Vec3d basePos, int color) {
		Vec3d center = basePos.add(0.0D, 0.1D, 0.0D);
		Vec3d head = basePos.add(0.0D, 2.8D, 0.0D);
		UnifiedRenderModule.drawLine(context.matrixStack(), center, head, color);

		double size = 0.42D;
		Vec3d north = head.add(0.0D, 0.0D, -size);
		Vec3d south = head.add(0.0D, 0.0D, size);
		Vec3d east = head.add(size, 0.0D, 0.0D);
		Vec3d west = head.add(-size, 0.0D, 0.0D);
		UnifiedRenderModule.drawLine(context.matrixStack(), north, south, color);
		UnifiedRenderModule.drawLine(context.matrixStack(), east, west, color);
		UnifiedRenderModule.drawLine(context.matrixStack(), north, east, withAlpha(color, 0x9A));
		UnifiedRenderModule.drawLine(context.matrixStack(), east, south, withAlpha(color, 0x9A));
		UnifiedRenderModule.drawLine(context.matrixStack(), south, west, withAlpha(color, 0x9A));
		UnifiedRenderModule.drawLine(context.matrixStack(), west, north, withAlpha(color, 0x9A));

		renderCircle(context, center.add(0.0D, 0.02D, 0.0D), 0.35D, withAlpha(color, 0xB0), 12);
	}

	private void renderCircle(WorldRenderContext context, Vec3d center, double radius, int color, int segments) {
		if (segments < 3) {
			return;
		}
		double step = (Math.PI * 2.0D) / segments;
		Vec3d prev = null;
		for (int i = 0; i <= segments; i++) {
			double angle = step * i;
			Vec3d current = new Vec3d(
				center.x + Math.cos(angle) * radius,
				center.y,
				center.z + Math.sin(angle) * radius
			);
			if (prev != null) {
				UnifiedRenderModule.drawLine(context.matrixStack(), prev, current, color);
			}
			prev = current;
		}
	}

	private int withAlpha(int rgb, int alpha) {
		return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
	}

	private record MarkTarget(Vec3d position, Entity targetEntity) {
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