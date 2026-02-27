package fun.prof_chen.teamviewer.multipleplayeresp.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
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
import fun.prof_chen.teamviewer.multipleplayeresp.config.Config;
import fun.prof_chen.teamviewer.multipleplayeresp.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.multipleplayeresp.model.SharedWaypointInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Proxy;
import java.net.ProxySelector;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * PlayerESP 网络层管理器。
 * <p>
 * 业务职责：
 * 1) 维护与服务端的 WebSocket 连接（握手、重连、断线恢复）；
 * 2) 负责玩家/实体/共享路标的数据收发、增量同步与校验重同步；
 * 3) 维护本地缓存与上行快照，供渲染与业务模块读取。
 * <p>
 * 线程模型：
 * - OkHttp 的 WebSocket 回调不在 Minecraft 渲染主线程执行；
 * - 所有会修改共享缓存的逻辑必须切回主线程执行，避免并发修改异常。
 */
public class PlayerESPNetworkManager extends WebSocketListener {
	public interface ConnectionStatusListener {
		void onConnectionStatusChanged(boolean connected);
	}

	public interface WaypointUpdateListener {
		void onWaypointsReceived(Map<String, SharedWaypointInfo> waypoints);

		default void onWaypointsDeleted(List<String> waypointIds) {
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerESPNetworkManager.class);
	private static final String CLIENT_PROTOCOL_VERSION = "0.2.0";
	private static final String CLIENT_PROGRAM_VERSION = resolveLocalProgramVersion();
	private static final long RESYNC_COOLDOWN_MS = 3_000L;
	private static final long FORCE_FULL_REFRESH_MS = 25_000L;

	private static Config config;

	private final Map<UUID, Vec3d> playerPositions;
	private final Map<UUID, RemotePlayerInfo> remotePlayers;
	private final Map<UUID, Map<String, Object>> remotePlayerDataCache = new HashMap<>();
	private final Map<String, Map<String, Object>> remoteEntityDataCache = new HashMap<>();
	private final Map<String, Map<String, Object>> remoteWaypointDataCache = new HashMap<>();
	private final Map<String, SharedWaypointInfo> remoteWaypointCache = new HashMap<>();
	private final Map<String, PlayerMarkState> remotePlayerMarks = new HashMap<>();
	private final Map<String, Map<String, Object>> lastSentPlayersSnapshot = new HashMap<>();
	private final Map<String, Map<String, Object>> lastSentEntitiesSnapshot = new HashMap<>();

	private WebSocket webSocket;
	private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
	private volatile boolean isConnected = false;
	private volatile boolean shouldReconnect = false;
	private final Gson gson = new Gson();
	private OkHttpClient httpClient;
	private volatile boolean currentUseSystemProxy = true;

	private final List<ConnectionStatusListener> statusListeners = new CopyOnWriteArrayList<>();
	private final List<WaypointUpdateListener> waypointListeners = new CopyOnWriteArrayList<>();

	private volatile String lastConnectionError = "";
	private volatile boolean serverSupportsDelta = false;
	private volatile String serverProtocolVersion = "0.0.0";
	private volatile String serverProgramVersion = "unknown";
	private volatile int digestIntervalSec = 10;
	private volatile long lastServerRevision = 0;
	private volatile long lastResyncRequestMs = 0L;
	private volatile long lastPlayersPacketSentMs = 0L;
	private volatile long lastEntitiesPacketSentMs = 0L;
	private volatile long lastTabPlayersPacketSentMs = 0L;
	private volatile String lastTabPlayersSignature = "";
	private final Set<String> pendingPlayerRefreshIds = new HashSet<>();
	private final Set<String> pendingEntityRefreshIds = new HashSet<>();
	/**
	 * 主线程任务队列。
	 * <p>
	 * WebSocket 回调线程只负责“投递任务”，真正的状态变更在客户端 tick 中统一执行。
	 * 这样可确保对 lastSent*Snapshot、remote*Cache 等共享集合的读写在同一线程完成。
	 */
	private final Queue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();

	private record PlayerMarkState(String team, Integer color, String label) {
	}

	public PlayerESPNetworkManager(Map<UUID, Vec3d> playerPositions, Map<UUID, RemotePlayerInfo> remotePlayers) {
		this.playerPositions = playerPositions;
		this.remotePlayers = remotePlayers;
		this.httpClient = createHttpClient(true);
	}

	public static void setConfig(Config config) {
		PlayerESPNetworkManager.config = config;
	}

	/**
	 * 在客户端主线程中消费网络任务队列。
	 * <p>
	 * 调用方：StandaloneMultiPlayerESP 的 END_CLIENT_TICK。
	 * 职责：把网络回调产生的状态更新串行化，避免跨线程修改共享集合。
	 */
	public void pumpMainThreadTasks() {
		Runnable task;
		while ((task = mainThreadTasks.poll()) != null) {
			try {
				task.run();
			} catch (Exception e) {
				LOGGER.error("Error while processing queued network task: {}", e.getMessage());
			}
		}
	}

	/**
	 * 由网络回调线程调用，将需要主线程执行的任务入队。
	 */
	private void enqueueMainThreadTask(Runnable task) {
		if (task != null) {
			mainThreadTasks.offer(task);
		}
	}

	public void connect() {
		if (config == null) {
			return;
		}
		shouldReconnect = true;

		boolean useSystemProxy = config.isUseSystemProxy();
		if (this.httpClient == null || this.currentUseSystemProxy != useSystemProxy) {
			this.httpClient = createHttpClient(useSystemProxy);
			this.currentUseSystemProxy = useSystemProxy;
		}

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

	private OkHttpClient createHttpClient(boolean useSystemProxy) {
		OkHttpClient.Builder builder = new OkHttpClient.Builder();
		if (useSystemProxy) {
			builder.proxySelector(ProxySelector.getDefault());
		} else {
			builder.proxy(Proxy.NO_PROXY);
		}
		return builder.build();
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
		resetNegotiationState();
		clearLocalOutboundSnapshots();
		isConnected = false;
		lastConnectionError = "";
		notifyConnectionStatusChanged(false);
	}

	public void addConnectionStatusListener(ConnectionStatusListener listener) {
		if (listener != null) {
			statusListeners.add(listener);
		}
	}

	public void removeConnectionStatusListener(ConnectionStatusListener listener) {
		if (listener != null) {
			statusListeners.remove(listener);
		}
	}

	public void addWaypointUpdateListener(WaypointUpdateListener listener) {
		if (listener != null) {
			waypointListeners.add(listener);
		}
	}

	public void removeWaypointUpdateListener(WaypointUpdateListener listener) {
		if (listener != null) {
			waypointListeners.remove(listener);
		}
	}

	public void sendPlayersUpdate(UUID submitPlayerId, Map<UUID, Map<String, Object>> players) {
		if (webSocket == null || !isConnected || submitPlayerId == null || players == null) {
			return;
		}

		if (!serverSupportsDelta) {
			sendPlayersUpdateLegacy(submitPlayerId, players);
			return;
		}

		Map<String, Map<String, Object>> currentSnapshot = new HashMap<>();
		for (Map.Entry<UUID, Map<String, Object>> entry : players.entrySet()) {
			currentSnapshot.put(entry.getKey().toString(), copyValueMap(entry.getValue()));
		}

		Map<String, Map<String, Object>> upsert = new HashMap<>();
		List<String> delete = new ArrayList<>();
		boolean forceFullRefresh = shouldForcePlayersFullRefresh();

		for (Map.Entry<String, Map<String, Object>> entry : currentSnapshot.entrySet()) {
			Map<String, Object> previous = lastSentPlayersSnapshot.get(entry.getKey());
			if (forceFullRefresh || previous == null) {
				upsert.put(entry.getKey(), entry.getValue());
				continue;
			}

			Map<String, Object> fieldDelta = computeFieldDelta(previous, entry.getValue());
			if (!fieldDelta.isEmpty()) {
				upsert.put(entry.getKey(), fieldDelta);
			}
		}

		for (String previousId : lastSentPlayersSnapshot.keySet()) {
			if (!currentSnapshot.containsKey(previousId)) {
				delete.add(previousId);
			}
		}

		applyPendingPlayerRefresh(currentSnapshot, upsert, delete);

		if (upsert.isEmpty() && delete.isEmpty()) {
			return;
		}

		try {
			long sentAt = System.currentTimeMillis();
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "players_patch");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			obj.addProperty("ackRev", lastServerRevision);
			obj.add("upsert", mapOfMapToJsonObject(upsert));
			obj.add("delete", toStringArray(delete));
			webSocket.send(gson.toJson(obj));
			lastSentPlayersSnapshot.clear();
			lastSentPlayersSnapshot.putAll(currentSnapshot);
			lastPlayersPacketSentMs = sentAt;
		} catch (Exception e) {
			LOGGER.error("Failed to send players_patch: {}", e.getMessage());
		}
	}

	public void sendEntitiesUpdate(UUID submitPlayerId, Map<String, Map<String, Object>> entities) {
		if (webSocket == null || !isConnected || submitPlayerId == null || entities == null) {
			return;
		}

		if (!serverSupportsDelta) {
			sendEntitiesUpdateLegacy(submitPlayerId, entities);
			return;
		}

		Map<String, Map<String, Object>> currentSnapshot = new HashMap<>();
		for (Map.Entry<String, Map<String, Object>> entry : entities.entrySet()) {
			currentSnapshot.put(entry.getKey(), copyValueMap(entry.getValue()));
		}

		Map<String, Map<String, Object>> upsert = new HashMap<>();
		List<String> delete = new ArrayList<>();
		boolean forceFullRefresh = shouldForceEntitiesFullRefresh();

		for (Map.Entry<String, Map<String, Object>> entry : currentSnapshot.entrySet()) {
			Map<String, Object> previous = lastSentEntitiesSnapshot.get(entry.getKey());
			if (forceFullRefresh || previous == null) {
				upsert.put(entry.getKey(), entry.getValue());
				continue;
			}

			Map<String, Object> fieldDelta = computeFieldDelta(previous, entry.getValue());
			if (!fieldDelta.isEmpty()) {
				upsert.put(entry.getKey(), fieldDelta);
			}
		}

		for (String previousId : lastSentEntitiesSnapshot.keySet()) {
			if (!currentSnapshot.containsKey(previousId)) {
				delete.add(previousId);
			}
		}

		applyPendingEntityRefresh(currentSnapshot, upsert, delete);

		if (upsert.isEmpty() && delete.isEmpty()) {
			return;
		}

		try {
			long sentAt = System.currentTimeMillis();
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "entities_patch");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			obj.addProperty("ackRev", lastServerRevision);
			obj.add("upsert", mapOfMapToJsonObject(upsert));
			obj.add("delete", toStringArray(delete));
			webSocket.send(gson.toJson(obj));
			lastSentEntitiesSnapshot.clear();
			lastSentEntitiesSnapshot.putAll(currentSnapshot);
			lastEntitiesPacketSentMs = sentAt;
		} catch (Exception e) {
			LOGGER.error("Failed to send entities_patch: {}", e.getMessage());
		}
	}

	public void sendWaypointsUpdate(UUID submitPlayerId, Map<String, Map<String, Object>> waypoints) {
		if (webSocket == null || !isConnected)
			return;
		if (waypoints == null || waypoints.isEmpty())
			return;
		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "waypoints_update");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			obj.add("waypoints", mapOfMapToJsonObject(waypoints));
			webSocket.send(gson.toJson(obj));
		} catch (Exception e) {
			LOGGER.error("Failed to send waypoints_update to PlayerESP server: {}", e.getMessage());
		}
	}

	public void sendTabPlayersUpdate(UUID submitPlayerId, List<Map<String, Object>> tabPlayers) {
		if (webSocket == null || !isConnected || submitPlayerId == null || tabPlayers == null) {
			return;
		}

		try {
			List<Map<String, Object>> normalized = new ArrayList<>();
			for (Map<String, Object> raw : tabPlayers) {
				if (raw == null || raw.isEmpty()) {
					continue;
				}
				Map<String, Object> copy = new HashMap<>();
				Object idValue = raw.get("playerUUID");
				Object nameValue = raw.get("name");
				Object displayName = raw.get("prefixColored");

				if (idValue != null && !String.valueOf(idValue).isBlank()) {
					copy.put("id", String.valueOf(idValue));
				}
				if (nameValue != null && !String.valueOf(nameValue).isBlank()) {
					copy.put("name", String.valueOf(nameValue));
				}
				if (displayName != null && !String.valueOf(displayName).isBlank()) {
					copy.put("displayName", String.valueOf(displayName));
				}

				if (!copy.isEmpty()) {
					normalized.add(copy);
				}
			}

			String signature = gson.toJson(normalized);
			long now = System.currentTimeMillis();
			if (Objects.equals(signature, lastTabPlayersSignature)
					&& now - lastTabPlayersPacketSentMs < FORCE_FULL_REFRESH_MS) {
				return;
			}

			JsonObject obj = new JsonObject();
			obj.addProperty("type", "tab_players_update");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			obj.addProperty("ackRev", lastServerRevision);
			obj.add("tabPlayers", gson.toJsonTree(normalized));
			webSocket.send(gson.toJson(obj));

			lastTabPlayersSignature = signature;
			lastTabPlayersPacketSentMs = now;
		} catch (Exception e) {
			LOGGER.error("Failed to send tab_players_update: {}", e.getMessage());
		}
	}

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

	public void sendWaypointEntityDeathCancel(UUID submitPlayerId, List<String> targetEntityIds) {
		if (webSocket == null || !isConnected)
			return;
		if (submitPlayerId == null || targetEntityIds == null || targetEntityIds.isEmpty())
			return;
		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "waypoints_entity_death_cancel");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			JsonArray ids = new JsonArray();
			for (String entityId : targetEntityIds) {
				if (entityId != null && !entityId.isBlank()) {
					ids.add(entityId);
				}
			}
			if (ids.isEmpty()) {
				return;
			}
			obj.add("targetEntityIds", ids);
			webSocket.send(gson.toJson(obj));
		} catch (Exception e) {
			LOGGER.error("Failed to send waypoints_entity_death_cancel to PlayerESP server: {}", e.getMessage());
		}
	}

	private void sendPlayersUpdateLegacy(UUID submitPlayerId, Map<UUID, Map<String, Object>> players) {
		if (players.isEmpty()) {
			return;
		}
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

	private void sendEntitiesUpdateLegacy(UUID submitPlayerId, Map<String, Map<String, Object>> entities) {
		if (entities.isEmpty()) {
			return;
		}
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

	private JsonObject mapToJsonObject(Map<String, Object> map) {
		JsonObject object = new JsonObject();
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			if (entry.getValue() == null) {
				object.add(entry.getKey(), JsonNull.INSTANCE);
			} else {
				object.add(entry.getKey(), gson.toJsonTree(entry.getValue()));
			}
		}
		return object;
	}

	private JsonObject mapOfMapToJsonObject(Map<String, Map<String, Object>> map) {
		JsonObject object = new JsonObject();
		for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
			object.add(entry.getKey(), mapToJsonObject(entry.getValue()));
		}
		return object;
	}

	private JsonArray toStringArray(List<String> list) {
		JsonArray array = new JsonArray();
		for (String value : list) {
			if (value != null && !value.isBlank()) {
				array.add(value);
			}
		}
		return array;
	}

	@Override
	public void onOpen(WebSocket webSocket, Response response) {
		// 连接建立事件来自 OkHttp 线程，这里只投递任务，避免直接跨线程改共享状态。
		enqueueMainThreadTask(() -> {
			isConnected = true;
			lastConnectionError = "";
			resetNegotiationState();
			clearLocalOutboundSnapshots();
			LOGGER.info("WebSocket connection opened to PlayerESP server");
			if (response != null) {
				String negotiatedExtensions = response.header("Sec-WebSocket-Extensions", "");
				if (!negotiatedExtensions.isBlank()) {
					LOGGER.info("Negotiated WebSocket extensions: {}", negotiatedExtensions);
				}
			}
			notifyConnectionStatusChanged(true);
			sendHandshake();
		});
	}

	@Override
	public void onMessage(WebSocket webSocket, String text) {
		// 消息解析与缓存更新在主线程执行，确保与渲染/tick 读写同线程。
		enqueueMainThreadTask(() -> processCompleteMessage(text));
	}

	/**
	 * 处理服务端完整消息。
	 * <p>
	 * 业务任务：
	 * - 根据 type 分发到握手、全量快照、增量补丁、摘要校验、路标同步等分支；
	 * - 更新本地 revision 与缓存；
	 * - 触发上层监听器通知 UI/功能模块。
	 */
	private void processCompleteMessage(String message) {
		try {
			if (message == null || message.trim().isEmpty()) {
				LOGGER.warn("Received empty message");
				return;
			}

			JsonObject json;
			try {
				json = JsonParser.parseString(message).getAsJsonObject();
			} catch (JsonSyntaxException e) {
				LOGGER.error("Failed to parse JSON message: {}, message: {}", e.getMessage(), message);
				return;
			}

			String messageType = json.has("type") ? json.get("type").getAsString() : "";

			if (json.has("rev") && !json.get("rev").isJsonNull()) {
				try {
					lastServerRevision = Math.max(lastServerRevision, json.get("rev").getAsLong());
				} catch (Exception ignored) {
				}
			}

			if ("handshake_ack".equals(messageType)) {
				handleHandshakeAck(json);
				return;
			}

			if ("snapshot_full".equals(messageType)) {
				applySnapshot(json);
				return;
			}

			if ("patch".equals(messageType)) {
				applyPatch(json);
				return;
			}

			if ("digest".equals(messageType)) {
				handleDigest(json);
				return;
			}

			if ("refresh_req".equals(messageType)) {
				handleRefreshRequest(json);
				return;
			}

			if ("waypoints_update".equals(messageType)) {
				Map<String, SharedWaypointInfo> receivedWaypoints = parseWaypointsNode(json, "waypoints");
				if (!receivedWaypoints.isEmpty()) {
					remoteWaypointCache.putAll(receivedWaypoints);
					notifyWaypointsReceived(receivedWaypoints);
				}
				return;
			}

			if ("waypoints_delete".equals(messageType)) {
				List<String> waypointIds = parseWaypointDeleteIds(json);
				if (!waypointIds.isEmpty()) {
					for (String id : waypointIds) {
						remoteWaypointCache.remove(id);
					}
					notifyWaypointsDeleted(waypointIds);
				}
				return;
			}

			if ("positions".equals(messageType)) {
				applyLegacyPositions(json);
			}
		} catch (Exception e) {
			LOGGER.error("PlayerESP Network - Error processing complete message: {}, message: {}", e.getMessage(), message);
		}
	}

	private void applyLegacyPositions(JsonObject json) {
		if (json.has("players") && json.get("players").isJsonObject()) {
			Map<UUID, RemotePlayerInfo> latestRemotePlayers = parseRemotePlayers(json.getAsJsonObject("players"), true);
			reconcileRemotePlayers(latestRemotePlayers);
		}

		if (json.has("entities") && json.get("entities").isJsonObject()) {
			replaceEntityCache(json.getAsJsonObject("entities"));
		}

		if (json.has("waypoints") && json.get("waypoints").isJsonObject()) {
			remoteWaypointDataCache.clear();
			Map<String, SharedWaypointInfo> receivedWaypoints = parseWaypointsNode(json, "waypoints");
			remoteWaypointCache.clear();
			remoteWaypointCache.putAll(receivedWaypoints);
			if (!receivedWaypoints.isEmpty()) {
				notifyWaypointsReceived(receivedWaypoints);
			}
		}

		if (json.has("playerMarks") && json.get("playerMarks").isJsonObject()) {
			replacePlayerMarks(json.getAsJsonObject("playerMarks"));
		}
	}

	private void applySnapshot(JsonObject json) {
		if (json.has("players") && json.get("players").isJsonObject()) {
			Map<UUID, RemotePlayerInfo> latestRemotePlayers = parseRemotePlayers(json.getAsJsonObject("players"), true);
			reconcileRemotePlayers(latestRemotePlayers);
		}

		if (json.has("entities") && json.get("entities").isJsonObject()) {
			replaceEntityCache(json.getAsJsonObject("entities"));
		}

		if (json.has("waypoints") && json.get("waypoints").isJsonObject()) {
			remoteWaypointDataCache.clear();
			Map<String, SharedWaypointInfo> receivedWaypoints = parseWaypointsNode(json, "waypoints");
			remoteWaypointCache.clear();
			remoteWaypointCache.putAll(receivedWaypoints);
			if (!receivedWaypoints.isEmpty()) {
				notifyWaypointsReceived(receivedWaypoints);
			}
		}

		if (json.has("playerMarks") && json.get("playerMarks").isJsonObject()) {
			replacePlayerMarks(json.getAsJsonObject("playerMarks"));
		}
	}

	private void applyPatch(JsonObject json) {
		if (json.has("players") && json.get("players").isJsonObject()) {
			JsonObject playersPatch = json.getAsJsonObject("players");

			if (playersPatch.has("delete") && playersPatch.get("delete").isJsonArray()) {
				for (JsonElement idElement : playersPatch.getAsJsonArray("delete")) {
					if (idElement != null && idElement.isJsonPrimitive()) {
						try {
							String playerIdRaw = idElement.getAsString();
							UUID playerId = UUID.fromString(playerIdRaw);
							remotePlayers.remove(playerId);
							playerPositions.remove(playerId);
							remotePlayerDataCache.remove(playerId);
							lastSentPlayersSnapshot.remove(playerIdRaw);
						} catch (Exception ignored) {
						}
					}
				}
			}

			if (playersPatch.has("upsert") && playersPatch.get("upsert").isJsonObject()) {
				applyPlayerPatchUpserts(playersPatch.getAsJsonObject("upsert"));
			}
		}

		if (json.has("entities") && json.get("entities").isJsonObject()) {
			JsonObject entitiesPatch = json.getAsJsonObject("entities");
			if (entitiesPatch.has("delete") && entitiesPatch.get("delete").isJsonArray()) {
				for (JsonElement idElement : entitiesPatch.getAsJsonArray("delete")) {
					if (idElement != null && idElement.isJsonPrimitive()) {
						String entityId = idElement.getAsString();
						if (entityId != null && !entityId.isBlank()) {
							remoteEntityDataCache.remove(entityId);
							lastSentEntitiesSnapshot.remove(entityId);
						}
					}
				}
			}

			if (entitiesPatch.has("upsert") && entitiesPatch.get("upsert").isJsonObject()) {
				mergeEntityPatchUpsert(entitiesPatch.getAsJsonObject("upsert"));
			}
		}

		if (json.has("waypoints") && json.get("waypoints").isJsonObject()) {
			JsonObject waypointPatch = json.getAsJsonObject("waypoints");

			if (waypointPatch.has("delete") && waypointPatch.get("delete").isJsonArray()) {
				List<String> deleteIds = new ArrayList<>();
				for (JsonElement idElement : waypointPatch.getAsJsonArray("delete")) {
					if (idElement != null && idElement.isJsonPrimitive()) {
						String id = idElement.getAsString();
						if (id != null && !id.isBlank()) {
							remoteWaypointCache.remove(id);
							remoteWaypointDataCache.remove(id);
							deleteIds.add(id);
						}
					}
				}
				if (!deleteIds.isEmpty()) {
					notifyWaypointsDeleted(deleteIds);
				}
			}

			if (waypointPatch.has("upsert") && waypointPatch.get("upsert").isJsonObject()) {
				Map<String, SharedWaypointInfo> upserts = parseWaypointsFromObject(waypointPatch.getAsJsonObject("upsert"));
				if (!upserts.isEmpty()) {
					remoteWaypointCache.putAll(upserts);
					notifyWaypointsReceived(upserts);
				}
			}
		}

		if (json.has("playerMarks") && json.get("playerMarks").isJsonObject()) {
			JsonObject playerMarksNode = json.getAsJsonObject("playerMarks");
			if (playerMarksNode.has("upsert") || playerMarksNode.has("delete")) {
				applyPlayerMarksPatch(playerMarksNode);
			} else {
				replacePlayerMarks(playerMarksNode);
			}
		}
	}

	private void replacePlayerMarks(JsonObject marksJson) {
		remotePlayerMarks.clear();
		mergePlayerMarkUpserts(marksJson);
	}

	private void applyPlayerMarksPatch(JsonObject patchNode) {
		if (patchNode.has("delete") && patchNode.get("delete").isJsonArray()) {
			for (JsonElement idElement : patchNode.getAsJsonArray("delete")) {
				if (idElement == null || !idElement.isJsonPrimitive()) {
					continue;
				}
				String normalized = normalizePlayerMarkId(idElement.getAsString());
				if (normalized != null) {
					remotePlayerMarks.remove(normalized);
				}
			}
		}

		if (patchNode.has("upsert") && patchNode.get("upsert").isJsonObject()) {
			mergePlayerMarkUpserts(patchNode.getAsJsonObject("upsert"));
		}
	}

	private void mergePlayerMarkUpserts(JsonObject upsertNode) {
		for (Map.Entry<String, JsonElement> entry : upsertNode.entrySet()) {
			try {
				if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
					continue;
				}
				String normalizedId = normalizePlayerMarkId(entry.getKey());
				if (normalizedId == null) {
					continue;
				}

				JsonObject markNode = extractDataNode(entry.getValue().getAsJsonObject());
				String team = normalizeMarkTeam(getOptionalString(markNode, "team"));
				Integer color = parseColorValue(markNode.get("color"));
				String label = getOptionalString(markNode, "label");
				if (label != null && label.isBlank()) {
					label = null;
				}

				remotePlayerMarks.put(normalizedId, new PlayerMarkState(team, color, label));
			} catch (Exception e) {
				LOGGER.warn("Failed to parse player mark {}: {}", entry.getKey(), e.getMessage());
			}
		}
	}

	private String normalizePlayerMarkId(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value.trim()).toString().toLowerCase();
		} catch (Exception e) {
			return null;
		}
	}

	private String normalizeMarkTeam(String value) {
		if (value == null) {
			return "neutral";
		}
		String text = value.trim().toLowerCase();
		if ("friendly".equals(text) || "friend".equals(text) || "ally".equals(text) || "blue".equals(text)) {
			return "friendly";
		}
		if ("enemy".equals(text) || "hostile".equals(text) || "red".equals(text)) {
			return "enemy";
		}
		return "neutral";
	}

	private Integer parseColorValue(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		try {
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
				return element.getAsInt();
			}
			if (element.isJsonPrimitive()) {
				String text = element.getAsString();
				if (text == null || text.isBlank()) {
					return null;
				}
				String normalized = text.trim();
				if (normalized.startsWith("#")) {
					String hex = normalized.substring(1);
					if (hex.length() == 6) {
						return (0xFF << 24) | Integer.parseInt(hex, 16);
					}
					if (hex.length() == 8) {
						return (int) Long.parseLong(hex, 16);
					}
				}
				if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
					return (int) Long.parseLong(normalized.substring(2), 16);
				}
				return (int) Long.parseLong(normalized, 16);
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private void handleDigest(JsonObject json) {
		if (!json.has("hashes") || !json.get("hashes").isJsonObject()) {
			return;
		}

		JsonObject hashes = json.getAsJsonObject("hashes");
		String serverPlayerHash = getOptionalString(hashes, "players");
		String serverEntityHash = getOptionalString(hashes, "entities");
		String serverWaypointHash = getOptionalString(hashes, "waypoints");

		String localPlayerHash = computePlayersDigest();
		String localEntityHash = computeEntitiesDigest();
		String localWaypointHash = computeWaypointDigest();

		boolean mismatch = !Objects.equals(serverPlayerHash, localPlayerHash)
				|| !Objects.equals(serverEntityHash, localEntityHash)
				|| !Objects.equals(serverWaypointHash, localWaypointHash);

		if (!mismatch) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastResyncRequestMs < RESYNC_COOLDOWN_MS) {
			return;
		}

		lastResyncRequestMs = now;
		sendResyncRequest("digest_mismatch");
	}

	private void sendResyncRequest(String reason) {
		if (webSocket == null || !isConnected) {
			return;
		}
		try {
			JsonObject req = new JsonObject();
			req.addProperty("type", "resync_req");
			req.addProperty("reason", reason);
			req.addProperty("ackRev", lastServerRevision);
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				req.addProperty("submitPlayerId", client.player.getUuid().toString());
			}
			webSocket.send(gson.toJson(req));
		} catch (Exception e) {
			LOGGER.warn("Failed to send resync request: {}", e.getMessage());
		}
	}

	@Override
	public void onClosed(WebSocket webSocket, int statusCode, String reason) {
		// 关闭事件也切回主线程，统一处理状态重置与重连调度。
		enqueueMainThreadTask(() -> {
			isConnected = false;
			if (statusCode != 1000) {
				lastConnectionError = "WebSocket closed (" + statusCode + "): "
						+ (reason == null || reason.isBlank() ? "unknown reason" : reason);
			} else {
				lastConnectionError = "";
			}
			resetNegotiationState();
			clearLocalOutboundSnapshots();
			notifyConnectionStatusChanged(false);
			LOGGER.info("Disconnected from PlayerESP server. Status: {}, Reason: {}", statusCode, reason);
			if (shouldReconnect) {
				scheduleReconnect();
			}
		});
	}

	@Override
	public void onFailure(WebSocket webSocket, Throwable error, Response response) {
		// 失败事件在网络线程触发，这里只入队，保证状态清理和通知时序一致。
		enqueueMainThreadTask(() -> {
			LOGGER.error("PlayerESP network error: {}", error.getMessage());
			isConnected = false;
			lastConnectionError = formatThrowableReason(error);
			resetNegotiationState();
			clearLocalOutboundSnapshots();
			notifyConnectionStatusChanged(false);
			if (shouldReconnect) {
				scheduleReconnect();
			}
		});
	}

	public static String getServerURL() {
		return config != null ? config.getServerURL() : "ws://localhost:8080/playeresp";
	}

	public static void setServerURL(String serverURL) {
		if (config != null) {
			config.setServerURL(serverURL);
		}
	}

		public static String getRoomCode() {
			return config != null ? config.getRoomCode() : "default";
		}

		public static void setRoomCode(String roomCode) {
			if (config != null) {
				config.setRoomCode(roomCode);
			}
		}

	public static boolean isUseSystemProxy() {
		return config == null || config.isUseSystemProxy();
	}

	public static void setUseSystemProxy(boolean useSystemProxy) {
		if (config != null) {
			config.setUseSystemProxy(useSystemProxy);
		}
	}

	public boolean isConnected() {
		return isConnected;
	}

	public String getLastConnectionError() {
		return lastConnectionError;
	}

	public Vec3d getRemoteEntityPosition(String entityId, String expectedDimension) {
		if (entityId == null || entityId.isBlank()) {
			return null;
		}

		Map<String, Object> data = remoteEntityDataCache.get(entityId);
		if (data == null) {
			return null;
		}

		if (expectedDimension != null && !expectedDimension.isBlank()) {
			Object dimension = data.get("dimension");
			if (dimension == null || !expectedDimension.equals(String.valueOf(dimension))) {
				return null;
			}
		}

		Double x = getAsDouble(data.get("x"));
		Double y = getAsDouble(data.get("y"));
		Double z = getAsDouble(data.get("z"));
		if (x == null || y == null || z == null) {
			return null;
		}

		return new Vec3d(x, y, z);
	}

	public Vec3d getRemotePlayerPosition(String playerId, String playerName, String expectedDimension) {
		UUID expectedUuid = null;
		if (playerId != null && !playerId.isBlank()) {
			try {
				expectedUuid = UUID.fromString(playerId);
			} catch (IllegalArgumentException ignored) {
			}
		}

		if (expectedUuid != null) {
			RemotePlayerInfo info = remotePlayers.get(expectedUuid);
			if (isRemotePlayerMatch(info, playerName, expectedDimension)) {
				return info.position();
			}
		}

		for (RemotePlayerInfo info : remotePlayers.values()) {
			if (isRemotePlayerMatch(info, playerName, expectedDimension)) {
				return info.position();
			}
		}

		return null;
	}

	public String getPlayerMarkTeam(UUID playerId) {
		if (playerId == null) {
			return null;
		}
		PlayerMarkState mark = remotePlayerMarks.get(playerId.toString().toLowerCase());
		return mark == null ? null : mark.team();
	}

	private boolean isRemotePlayerMatch(RemotePlayerInfo info, String expectedPlayerName, String expectedDimension) {
		if (info == null || info.position() == null || info.dimension() == null) {
			return false;
		}

		if (expectedDimension != null && !expectedDimension.isBlank()) {
			String actualDimension = info.dimension().getValue().toString();
			if (!expectedDimension.equals(actualDimension)) {
				return false;
			}
		}

		if (expectedPlayerName == null || expectedPlayerName.isBlank()) {
			return true;
		}

		String actualName = info.name();
		return actualName != null && actualName.equalsIgnoreCase(expectedPlayerName);
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

	private void sendHandshake() {
		if (webSocket == null || !isConnected)
			return;

		try {
			JsonObject handshake = new JsonObject();
			handshake.addProperty("type", "handshake");
			handshake.addProperty("networkProtocolVersion", CLIENT_PROTOCOL_VERSION);
			handshake.addProperty("protocolVersion", CLIENT_PROTOCOL_VERSION);
			handshake.addProperty("localProgramVersion", CLIENT_PROGRAM_VERSION);
			handshake.addProperty("roomCode", getRoomCode());
			handshake.addProperty("supportsDelta", true);

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

	private void handleHandshakeAck(JsonObject json) {
		if (json.has("ready") && json.get("ready").getAsBoolean()) {
			serverProtocolVersion = readProtocolVersionFromHandshakeAck(json);
			serverProgramVersion = readProgramVersionFromHandshakeAck(json);
			serverSupportsDelta = json.has("deltaEnabled") && json.get("deltaEnabled").getAsBoolean();
			digestIntervalSec = json.has("digestIntervalSec") ? json.get("digestIntervalSec").getAsInt() : 10;
			if (json.has("rev") && !json.get("rev").isJsonNull()) {
				lastServerRevision = json.get("rev").getAsLong();
			}
			LOGGER.info("Handshake completed: protocol={}, serverProgramVersion={}, delta={}, digestInterval={}s",
					serverProtocolVersion, serverProgramVersion, serverSupportsDelta, digestIntervalSec);
		}
	}

	private String readProtocolVersionFromHandshakeAck(JsonObject json) {
		try {
			if (json.has("networkProtocolVersion") && !json.get("networkProtocolVersion").isJsonNull()) {
				String value = json.get("networkProtocolVersion").getAsString();
				if (value != null && !value.isBlank()) {
					return value;
				}
			}
			if (json.has("protocolVersion") && !json.get("protocolVersion").isJsonNull()) {
				String value = json.get("protocolVersion").getAsString();
				if (value != null && !value.isBlank()) {
					return value;
				}
			}
		} catch (Exception ignored) {
		}
		return "0.0.0";
	}

	private String readProgramVersionFromHandshakeAck(JsonObject json) {
		if (json.has("localProgramVersion") && !json.get("localProgramVersion").isJsonNull()) {
			try {
				String value = json.get("localProgramVersion").getAsString();
				if (value != null && !value.isBlank()) {
					return value;
				}
			} catch (Exception ignored) {
			}
		}
		if (json.has("programVersion") && !json.get("programVersion").isJsonNull()) {
			try {
				String value = json.get("programVersion").getAsString();
				if (value != null && !value.isBlank()) {
					return value;
				}
			} catch (Exception ignored) {
			}
		}
		return "unknown";
	}

	private static String resolveLocalProgramVersion() {
		try {
			return FabricLoader.getInstance()
					.getModContainer("teamviewer")
					.map(container -> container.getMetadata().getVersion().getFriendlyString())
					.orElse("teamviewer-mod-dev");
		} catch (Exception ignored) {
			return "teamviewer-mod-dev";
		}
	}

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

	private Map<UUID, RemotePlayerInfo> parseRemotePlayers(JsonObject playersJson, boolean replaceCache) {
		Map<UUID, RemotePlayerInfo> newRemotePlayers = new HashMap<>();
		RegistryKey<World> fallbackDimension = getCurrentDimension();

		if (replaceCache) {
			remotePlayerDataCache.clear();
		}

		for (Map.Entry<String, JsonElement> entry : playersJson.entrySet()) {
			try {
				String playerIdStr = entry.getKey();
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject playerDataNode = entry.getValue().getAsJsonObject();
				JsonObject actualData = extractDataNode(playerDataNode);
				UUID playerId = UUID.fromString(playerIdStr);
				Map<String, Object> mergedData = new HashMap<>();
				if (!replaceCache && remotePlayerDataCache.containsKey(playerId)) {
					mergedData.putAll(remotePlayerDataCache.get(playerId));
				}
				mergedData.putAll(jsonObjectToValueMap(actualData));

				RemotePlayerInfo info = buildRemotePlayerInfo(playerId, mergedData, fallbackDimension, playerIdStr);
				if (info == null) {
					continue;
				}

				remotePlayerDataCache.put(playerId, mergedData);
				newRemotePlayers.put(playerId, info);
			} catch (Exception e) {
				LOGGER.error("PlayerESP Network - Error parsing player data: {}", e.getMessage());
			}
		}

		return newRemotePlayers;
	}

	private void applyPlayerPatchUpserts(JsonObject upsertJson) {
		RegistryKey<World> fallbackDimension = getCurrentDimension();

		for (Map.Entry<String, JsonElement> entry : upsertJson.entrySet()) {
			try {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}

				UUID playerId = UUID.fromString(entry.getKey());
				Map<String, Object> mergedData = new HashMap<>();
				Map<String, Object> existing = remotePlayerDataCache.get(playerId);
				if (existing != null) {
					mergedData.putAll(existing);
				}

				JsonObject dataNode = extractDataNode(entry.getValue().getAsJsonObject());
				mergedData.putAll(jsonObjectToValueMap(dataNode));

				RemotePlayerInfo info = buildRemotePlayerInfo(playerId, mergedData, fallbackDimension, entry.getKey());
				if (info == null) {
					continue;
				}

				remotePlayerDataCache.put(playerId, mergedData);
				remotePlayers.put(playerId, info);
				playerPositions.put(playerId, info.position());
			} catch (Exception e) {
				LOGGER.error("PlayerESP Network - Error applying player patch: {}", e.getMessage());
			}
		}
	}

	private RemotePlayerInfo buildRemotePlayerInfo(UUID playerId, Map<String, Object> mergedData,
			RegistryKey<World> fallbackDimension, String fallbackName) {
		Double x = getAsDouble(mergedData.get("x"));
		Double y = getAsDouble(mergedData.get("y"));
		Double z = getAsDouble(mergedData.get("z"));
		if (x == null || y == null || z == null) {
			return null;
		}

		String dimensionId = mergedData.get("dimension") == null ? null : String.valueOf(mergedData.get("dimension"));
		RegistryKey<World> dimension = RemotePlayerInfo.parseDimension(dimensionId, fallbackDimension);
		String playerName = mergedData.get("playerName") == null ? fallbackName : String.valueOf(mergedData.get("playerName"));

		Vec3d position = new Vec3d(x, y, z);
		return new RemotePlayerInfo(playerId, position, dimension, playerName);
	}

	private Double getAsDouble(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}
		try {
			return Double.parseDouble(String.valueOf(value));
		} catch (Exception e) {
			return null;
		}
	}

	private Map<String, Object> jsonObjectToValueMap(JsonObject jsonObject) {
		Map<String, Object> values = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
			values.put(entry.getKey(), jsonElementToValue(entry.getValue()));
		}
		return values;
	}

	private Object jsonElementToValue(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (element.isJsonPrimitive()) {
			if (element.getAsJsonPrimitive().isBoolean()) {
				return element.getAsBoolean();
			}
			if (element.getAsJsonPrimitive().isNumber()) {
				return element.getAsDouble();
			}
			return element.getAsString();
		}
		return element.toString();
	}

	private Map<String, Object> computeFieldDelta(Map<String, Object> previous, Map<String, Object> current) {
		Map<String, Object> delta = new HashMap<>();
		for (Map.Entry<String, Object> entry : current.entrySet()) {
			if (!Objects.equals(previous.get(entry.getKey()), entry.getValue())) {
				delta.put(entry.getKey(), entry.getValue());
			}
		}
		return delta;
	}

	private boolean shouldForcePlayersFullRefresh() {
		long now = System.currentTimeMillis();
		return now - lastPlayersPacketSentMs >= FORCE_FULL_REFRESH_MS;
	}

	private boolean shouldForceEntitiesFullRefresh() {
		long now = System.currentTimeMillis();
		return now - lastEntitiesPacketSentMs >= FORCE_FULL_REFRESH_MS;
	}

	private void handleRefreshRequest(JsonObject json) {
		List<String> players = parseStringArrayField(json, "players");
		List<String> entities = parseStringArrayField(json, "entities");

		pendingPlayerRefreshIds.addAll(players);
		pendingEntityRefreshIds.addAll(entities);

		if (!players.isEmpty() || !entities.isEmpty()) {
			LOGGER.info("Received refresh_req: players={}, entities={}", players.size(), entities.size());
		}
	}

	private List<String> parseStringArrayField(JsonObject json, String fieldName) {
		if (!json.has(fieldName) || !json.get(fieldName).isJsonArray()) {
			return List.of();
		}

		List<String> result = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray(fieldName)) {
			if (element == null || !element.isJsonPrimitive()) {
				continue;
			}
			String value = element.getAsString();
			if (value != null && !value.isBlank()) {
				result.add(value);
			}
		}
		return result;
	}

	private void applyPendingPlayerRefresh(
			Map<String, Map<String, Object>> currentSnapshot,
			Map<String, Map<String, Object>> upsert,
			List<String> delete
	) {
		if (pendingPlayerRefreshIds.isEmpty()) {
			return;
		}

		Set<String> deleteSet = new HashSet<>(delete);
		for (String playerId : new ArrayList<>(pendingPlayerRefreshIds)) {
			Map<String, Object> fullData = currentSnapshot.get(playerId);
			if (fullData != null) {
				upsert.put(playerId, fullData);
			} else {
				deleteSet.add(playerId);
			}
			pendingPlayerRefreshIds.remove(playerId);
		}

		delete.clear();
		delete.addAll(deleteSet);
	}

	private void applyPendingEntityRefresh(
			Map<String, Map<String, Object>> currentSnapshot,
			Map<String, Map<String, Object>> upsert,
			List<String> delete
	) {
		if (pendingEntityRefreshIds.isEmpty()) {
			return;
		}

		Set<String> deleteSet = new HashSet<>(delete);
		for (String entityId : new ArrayList<>(pendingEntityRefreshIds)) {
			Map<String, Object> fullData = currentSnapshot.get(entityId);
			if (fullData != null) {
				upsert.put(entityId, fullData);
			} else {
				deleteSet.add(entityId);
			}
			pendingEntityRefreshIds.remove(entityId);
		}

		delete.clear();
		delete.addAll(deleteSet);
	}

	private JsonObject extractDataNode(JsonObject node) {
		if (node.has("data") && node.get("data").isJsonObject()) {
			return node.getAsJsonObject("data");
		}
		return node;
	}

	private Map<String, SharedWaypointInfo> parseWaypointsNode(JsonObject json, String fieldName) {
		if (!json.has(fieldName) || !json.get(fieldName).isJsonObject()) {
			return Map.of();
		}
		return parseWaypointsFromObject(json.getAsJsonObject(fieldName));
	}

	private Map<String, SharedWaypointInfo> parseWaypointsFromObject(JsonObject waypointsJson) {
		Map<String, SharedWaypointInfo> result = new HashMap<>();

		for (Map.Entry<String, JsonElement> entry : waypointsJson.entrySet()) {
			try {
				String waypointId = entry.getKey();
				if (!entry.getValue().isJsonObject()) {
					continue;
				}

				JsonObject node = entry.getValue().getAsJsonObject();
				JsonObject data = extractDataNode(node);
				Map<String, Object> rawData = jsonObjectToValueMap(data);
				remoteWaypointDataCache.put(waypointId, rawData);

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
				long createdAt = data.has("createdAt") ? data.get("createdAt").getAsLong() : System.currentTimeMillis();
				String targetType = data.has("targetType") && !data.get("targetType").isJsonNull()
						? data.get("targetType").getAsString()
						: null;
				String targetEntityId = data.has("targetEntityId") && !data.get("targetEntityId").isJsonNull()
						? data.get("targetEntityId").getAsString()
						: null;
				String targetEntityType = data.has("targetEntityType") && !data.get("targetEntityType").isJsonNull()
						? data.get("targetEntityType").getAsString()
						: null;
				String targetEntityName = data.has("targetEntityName") && !data.get("targetEntityName").isJsonNull()
						? data.get("targetEntityName").getAsString()
						: null;
				String waypointKind = data.has("waypointKind") && !data.get("waypointKind").isJsonNull()
						? data.get("waypointKind").getAsString()
						: null;

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
						createdAt,
						targetType,
						targetEntityId,
						targetEntityType,
						targetEntityName,
						waypointKind);
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

		List<String> result = new ArrayList<>();
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

		Map<UUID, Vec3d> latestPositions = new HashMap<>();
		for (Map.Entry<UUID, RemotePlayerInfo> entry : remotePlayers.entrySet()) {
			latestPositions.put(entry.getKey(), entry.getValue().position());
		}
		reconcilePlayerPositions(latestPositions);
	}

	private String computePlayersDigest() {
		Map<String, Map<String, Object>> state = new HashMap<>();
		for (Map.Entry<UUID, Map<String, Object>> entry : remotePlayerDataCache.entrySet()) {
			state.put(entry.getKey().toString(), entry.getValue());
		}
		return stateDigest(state);
	}

	private String computeEntitiesDigest() {
		return stateDigest(remoteEntityDataCache);
	}

	private String computeWaypointDigest() {
		return stateDigest(remoteWaypointDataCache);
	}

	private void replaceEntityCache(JsonObject entitiesJson) {
		remoteEntityDataCache.clear();
		mergeEntityPatchUpsert(entitiesJson);
	}

	private void mergeEntityPatchUpsert(JsonObject upsertJson) {
		for (Map.Entry<String, JsonElement> entry : upsertJson.entrySet()) {
			try {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				String entityId = entry.getKey();
				JsonObject dataNode = extractDataNode(entry.getValue().getAsJsonObject());
				Map<String, Object> merged = new HashMap<>();
				Map<String, Object> existing = remoteEntityDataCache.get(entityId);
				if (existing != null) {
					merged.putAll(existing);
				}
				merged.putAll(jsonObjectToValueMap(dataNode));
				remoteEntityDataCache.put(entityId, merged);
			} catch (Exception e) {
				LOGGER.error("PlayerESP Network - Error applying entity patch: {}", e.getMessage());
			}
		}
	}

	private String stateDigest(Map<String, Map<String, Object>> state) {
		try {
			List<String> ids = new ArrayList<>(state.keySet());
			Collections.sort(ids);

			List<String> lines = new ArrayList<>();
			for (String id : ids) {
				Map<String, Object> data = state.get(id);
				lines.add(gson.toJson(id) + ":" + canonicalValue(data == null ? Map.of() : data));
			}

			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			for (String line : lines) {
				digest.update(line.getBytes(StandardCharsets.UTF_8));
				digest.update((byte) '\n');
			}
			byte[] bytes = digest.digest();
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 8 && i < bytes.length; i++) {
				hex.append(String.format("%02x", bytes[i]));
			}
			return hex.toString();
		} catch (Exception e) {
			return "hash_error";
		}
	}

	private String canonicalValue(Object value) {
		if (value == null) {
			return "null";
		}
		if (value instanceof Boolean boolValue) {
			return boolValue ? "true" : "false";
		}
		if (value instanceof Number numberValue) {
			return canonicalNumber(numberValue);
		}
		if (value instanceof String stringValue) {
			return gson.toJson(stringValue);
		}
		if (value instanceof Map<?, ?> mapValue) {
			List<String> keys = new ArrayList<>();
			for (Object key : mapValue.keySet()) {
				keys.add(String.valueOf(key));
			}
			Collections.sort(keys);

			StringBuilder builder = new StringBuilder("{");
			for (int i = 0; i < keys.size(); i++) {
				String key = keys.get(i);
				if (i > 0) {
					builder.append(',');
				}
				builder.append(gson.toJson(key)).append(':').append(canonicalValue(mapValue.get(key)));
			}
			builder.append('}');
			return builder.toString();
		}
		if (value instanceof List<?> listValue) {
			StringBuilder builder = new StringBuilder("[");
			for (int i = 0; i < listValue.size(); i++) {
				if (i > 0) {
					builder.append(',');
				}
				builder.append(canonicalValue(listValue.get(i)));
			}
			builder.append(']');
			return builder.toString();
		}

		return gson.toJson(value);
	}

	private String canonicalNumber(Number numberValue) {
		if (numberValue instanceof Byte || numberValue instanceof Short
				|| numberValue instanceof Integer || numberValue instanceof Long) {
			return String.valueOf(numberValue.longValue());
		}

		double value = numberValue.doubleValue();
		if (!Double.isFinite(value)) {
			return "null";
		}

		BigDecimal decimal = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
		String text = decimal.toPlainString();
		if ("-0".equals(text) || "".equals(text)) {
			return "0";
		}
		return text;
	}

	private String getOptionalString(JsonObject json, String key) {
		if (!json.has(key) || json.get(key).isJsonNull()) {
			return "";
		}
		try {
			return json.get(key).getAsString();
		} catch (Exception e) {
			return "";
		}
	}

	private Map<String, Object> copyValueMap(Map<String, Object> source) {
		Map<String, Object> copy = new HashMap<>();
		if (source == null) {
			return copy;
		}
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			copy.put(entry.getKey(), entry.getValue());
		}
		return copy;
	}

	private void resetNegotiationState() {
		serverSupportsDelta = false;
		serverProtocolVersion = "0.0.0";
		serverProgramVersion = "unknown";
		digestIntervalSec = 10;
		lastServerRevision = 0;
		lastResyncRequestMs = 0L;
		lastPlayersPacketSentMs = 0L;
		lastEntitiesPacketSentMs = 0L;
		lastTabPlayersPacketSentMs = 0L;
	}

	private void clearLocalOutboundSnapshots() {
		lastSentPlayersSnapshot.clear();
		lastSentEntitiesSnapshot.clear();
		lastTabPlayersSignature = "";
		pendingPlayerRefreshIds.clear();
		pendingEntityRefreshIds.clear();
		remotePlayerDataCache.clear();
		remoteEntityDataCache.clear();
		remoteWaypointDataCache.clear();
		remoteWaypointCache.clear();
		remotePlayerMarks.clear();
	}
}
