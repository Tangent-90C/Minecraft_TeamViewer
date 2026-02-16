package person.professor_chen.teamviewer.multipleplayeresp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
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
	public interface ConnectionStatusListener {
		void onConnectionStatusChanged(boolean connected);
	}

	public interface WaypointUpdateListener {
		void onWaypointsReceived(Map<String, SharedWaypointInfo> waypoints);

		default void onWaypointsDeleted(List<String> waypointIds) {
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerESPNetworkManager.class);
	private static final int CLIENT_PROTOCOL_VERSION = 2;
	private static final long RESYNC_COOLDOWN_MS = 3_000L;

	private static Config config;

	private final Map<UUID, Vec3d> playerPositions;
	private final Map<UUID, RemotePlayerInfo> remotePlayers;
	private final Map<String, SharedWaypointInfo> remoteWaypointCache = new HashMap<>();
	private final Map<String, Map<String, Object>> lastSentPlayersSnapshot = new HashMap<>();
	private final Map<String, Map<String, Object>> lastSentEntitiesSnapshot = new HashMap<>();

	private WebSocket webSocket;
	private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
	private volatile boolean isConnected = false;
	private volatile boolean shouldReconnect = false;
	private final Gson gson = new Gson();
	private final OkHttpClient httpClient;

	private final List<ConnectionStatusListener> statusListeners = new CopyOnWriteArrayList<>();
	private final List<WaypointUpdateListener> waypointListeners = new CopyOnWriteArrayList<>();

	private volatile String lastConnectionError = "";
	private volatile boolean serverSupportsDelta = false;
	private volatile int serverProtocolVersion = 1;
	private volatile int digestIntervalSec = 10;
	private volatile long lastServerRevision = 0;
	private volatile long lastResyncRequestMs = 0L;

	public PlayerESPNetworkManager(Map<UUID, Vec3d> playerPositions, Map<UUID, RemotePlayerInfo> remotePlayers) {
		this.playerPositions = playerPositions;
		this.remotePlayers = remotePlayers;
		this.httpClient = new OkHttpClient();
	}

	public static void setConfig(Config config) {
		PlayerESPNetworkManager.config = config;
	}

	public void connect() {
		if (config == null) {
			return;
		}
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

		for (Map.Entry<String, Map<String, Object>> entry : currentSnapshot.entrySet()) {
			Map<String, Object> previous = lastSentPlayersSnapshot.get(entry.getKey());
			if (!Objects.equals(previous, entry.getValue())) {
				upsert.put(entry.getKey(), entry.getValue());
			}
		}

		for (String previousId : lastSentPlayersSnapshot.keySet()) {
			if (!currentSnapshot.containsKey(previousId)) {
				delete.add(previousId);
			}
		}

		if (upsert.isEmpty() && delete.isEmpty()) {
			return;
		}

		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "players_patch");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			obj.addProperty("ackRev", lastServerRevision);
			obj.add("upsert", mapOfMapToJsonObject(upsert));
			obj.add("delete", toStringArray(delete));
			webSocket.send(gson.toJson(obj));
			lastSentPlayersSnapshot.clear();
			lastSentPlayersSnapshot.putAll(currentSnapshot);
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

		for (Map.Entry<String, Map<String, Object>> entry : currentSnapshot.entrySet()) {
			Map<String, Object> previous = lastSentEntitiesSnapshot.get(entry.getKey());
			if (!Objects.equals(previous, entry.getValue())) {
				upsert.put(entry.getKey(), entry.getValue());
			}
		}

		for (String previousId : lastSentEntitiesSnapshot.keySet()) {
			if (!currentSnapshot.containsKey(previousId)) {
				delete.add(previousId);
			}
		}

		if (upsert.isEmpty() && delete.isEmpty()) {
			return;
		}

		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "entities_patch");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			obj.addProperty("ackRev", lastServerRevision);
			obj.add("upsert", mapOfMapToJsonObject(upsert));
			obj.add("delete", toStringArray(delete));
			webSocket.send(gson.toJson(obj));
			lastSentEntitiesSnapshot.clear();
			lastSentEntitiesSnapshot.putAll(currentSnapshot);
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
	}

	@Override
	public void onMessage(WebSocket webSocket, String text) {
		processCompleteMessage(text);
	}

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
			Map<UUID, RemotePlayerInfo> latestRemotePlayers = parseRemotePlayers(json.getAsJsonObject("players"));
			reconcileRemotePlayers(latestRemotePlayers);
		}

		if (json.has("waypoints") && json.get("waypoints").isJsonObject()) {
			Map<String, SharedWaypointInfo> receivedWaypoints = parseWaypointsNode(json, "waypoints");
			remoteWaypointCache.clear();
			remoteWaypointCache.putAll(receivedWaypoints);
			if (!receivedWaypoints.isEmpty()) {
				notifyWaypointsReceived(receivedWaypoints);
			}
		}
	}

	private void applySnapshot(JsonObject json) {
		if (json.has("players") && json.get("players").isJsonObject()) {
			Map<UUID, RemotePlayerInfo> latestRemotePlayers = parseRemotePlayers(json.getAsJsonObject("players"));
			reconcileRemotePlayers(latestRemotePlayers);
		}

		if (json.has("waypoints") && json.get("waypoints").isJsonObject()) {
			Map<String, SharedWaypointInfo> receivedWaypoints = parseWaypointsNode(json, "waypoints");
			remoteWaypointCache.clear();
			remoteWaypointCache.putAll(receivedWaypoints);
			if (!receivedWaypoints.isEmpty()) {
				notifyWaypointsReceived(receivedWaypoints);
			}
		}
	}

	private void applyPatch(JsonObject json) {
		if (json.has("players") && json.get("players").isJsonObject()) {
			JsonObject playersPatch = json.getAsJsonObject("players");

			if (playersPatch.has("delete") && playersPatch.get("delete").isJsonArray()) {
				for (JsonElement idElement : playersPatch.getAsJsonArray("delete")) {
					if (idElement != null && idElement.isJsonPrimitive()) {
						try {
							UUID playerId = UUID.fromString(idElement.getAsString());
							remotePlayers.remove(playerId);
							playerPositions.remove(playerId);
						} catch (Exception ignored) {
						}
					}
				}
			}

			if (playersPatch.has("upsert") && playersPatch.get("upsert").isJsonObject()) {
				Map<UUID, RemotePlayerInfo> upsertPlayers = parseRemotePlayers(playersPatch.getAsJsonObject("upsert"));
				for (Map.Entry<UUID, RemotePlayerInfo> entry : upsertPlayers.entrySet()) {
					remotePlayers.put(entry.getKey(), entry.getValue());
					playerPositions.put(entry.getKey(), entry.getValue().position());
				}
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
	}

	private void handleDigest(JsonObject json) {
		if (!json.has("hashes") || !json.get("hashes").isJsonObject()) {
			return;
		}

		JsonObject hashes = json.getAsJsonObject("hashes");
		String serverPlayerHash = getOptionalString(hashes, "players");
		String serverWaypointHash = getOptionalString(hashes, "waypoints");

		String localPlayerHash = computePlayersDigest();
		String localWaypointHash = computeWaypointDigest();

		boolean mismatch = !Objects.equals(serverPlayerHash, localPlayerHash)
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
	}

	@Override
	public void onFailure(WebSocket webSocket, Throwable error, Response response) {
		LOGGER.error("PlayerESP network error: {}", error.getMessage());
		isConnected = false;
		lastConnectionError = formatThrowableReason(error);
		resetNegotiationState();
		clearLocalOutboundSnapshots();
		notifyConnectionStatusChanged(false);
		if (shouldReconnect) {
			scheduleReconnect();
		}
	}

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

	private void sendHandshake() {
		if (webSocket == null || !isConnected)
			return;

		try {
			JsonObject handshake = new JsonObject();
			handshake.addProperty("type", "handshake");
			handshake.addProperty("protocolVersion", CLIENT_PROTOCOL_VERSION);
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
			serverProtocolVersion = json.has("protocolVersion") ? json.get("protocolVersion").getAsInt() : 1;
			serverSupportsDelta = json.has("deltaEnabled") && json.get("deltaEnabled").getAsBoolean();
			digestIntervalSec = json.has("digestIntervalSec") ? json.get("digestIntervalSec").getAsInt() : 10;
			if (json.has("rev") && !json.get("rev").isJsonNull()) {
				lastServerRevision = json.get("rev").getAsLong();
			}
			LOGGER.info("Handshake completed: protocol={}, delta={}, digestInterval={}s",
					serverProtocolVersion, serverSupportsDelta, digestIntervalSec);
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

	private Map<UUID, RemotePlayerInfo> parseRemotePlayers(JsonObject playersJson) {
		Map<UUID, RemotePlayerInfo> newRemotePlayers = new HashMap<>();
		RegistryKey<World> fallbackDimension = getCurrentDimension();

		for (Map.Entry<String, JsonElement> entry : playersJson.entrySet()) {
			try {
				String playerIdStr = entry.getKey();
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject playerDataNode = entry.getValue().getAsJsonObject();
				JsonObject actualData = extractDataNode(playerDataNode);

				if (!actualData.has("x") || !actualData.has("y") || !actualData.has("z")) {
					continue;
				}

				UUID playerId = UUID.fromString(playerIdStr);
				double x = actualData.get("x").getAsDouble();
				double y = actualData.get("y").getAsDouble();
				double z = actualData.get("z").getAsDouble();

				String dimensionId = actualData.has("dimension")
						? actualData.get("dimension").getAsString()
						: null;
				RegistryKey<World> dimension = RemotePlayerInfo.parseDimension(dimensionId, fallbackDimension);
				String playerName = actualData.has("playerName")
						? actualData.get("playerName").getAsString()
						: playerIdStr;

				Vec3d position = new Vec3d(x, y, z);
				newRemotePlayers.put(playerId, new RemotePlayerInfo(playerId, position, dimension, playerName));
			} catch (Exception e) {
				LOGGER.error("PlayerESP Network - Error parsing player data: {}", e.getMessage());
			}
		}

		return newRemotePlayers;
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
		List<String> lines = new ArrayList<>();
		for (Map.Entry<UUID, RemotePlayerInfo> entry : remotePlayers.entrySet()) {
			RemotePlayerInfo info = entry.getValue();
			Vec3d pos = info.position();
			String dimension = info.dimension() != null ? info.dimension().getValue().toString() : "";
			lines.add(entry.getKey() + "|" + quantize(pos.x) + "|" + quantize(pos.y) + "|" + quantize(pos.z)
					+ "|" + dimension + "|" + info.name());
		}
		return stableHash(lines);
	}

	private String computeWaypointDigest() {
		List<String> lines = new ArrayList<>();
		for (Map.Entry<String, SharedWaypointInfo> entry : remoteWaypointCache.entrySet()) {
			SharedWaypointInfo waypoint = entry.getValue();
			lines.add(entry.getKey() + "|" + waypoint.x() + "|" + waypoint.y() + "|" + waypoint.z() + "|"
					+ Objects.toString(waypoint.dimension(), "") + "|" + waypoint.color() + "|"
					+ Objects.toString(waypoint.name(), ""));
		}
		return stableHash(lines);
	}

	private long quantize(double value) {
		return Math.round(value * 1000.0);
	}

	private String stableHash(List<String> lines) {
		try {
			Collections.sort(lines);
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
		serverProtocolVersion = 1;
		digestIntervalSec = 10;
		lastServerRevision = 0;
		lastResyncRequestMs = 0L;
	}

	private void clearLocalOutboundSnapshots() {
		lastSentPlayersSnapshot.clear();
		lastSentEntitiesSnapshot.clear();
		remoteWaypointCache.clear();
	}
}
