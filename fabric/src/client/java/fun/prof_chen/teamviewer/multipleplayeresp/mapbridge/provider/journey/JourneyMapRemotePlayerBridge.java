package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.provider.journey;

import journeymap.api.v2.common.waypoint.Waypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import fun.prof_chen.teamviewer.multipleplayeresp.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.multipleplayeresp.util.TeamRelationHelper;
import fun.prof_chen.teamviewer.multipleplayeresp.config.Config;
import fun.prof_chen.teamviewer.multipleplayeresp.core.StandaloneMultiPlayerESP;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class JourneyMapRemotePlayerBridge {
	private static final String PLAYER_PREFIX = "player:";
	private static final String WAYPOINT_PREFIX = "[TV] ";
	private static final int DEFAULT_PLAYER_COLOR = 0xFF5555;
	private static final Map<String, Waypoint> MANAGED_WAYPOINTS = new ConcurrentHashMap<>();

	private JourneyMapRemotePlayerBridge() {
	}

	static boolean isAvailable() {
		return JourneyMapWaypointAccess.isAvailable();
	}

	static void tick(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
		if (!enabled) {
			clear();
			return;
		}
		if (!isAvailable()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			return;
		}

		Config config = StandaloneMultiPlayerESP.getConfig();
		boolean enablePlayerMarker = config != null && config.isEnablePlayerWaypointMarker();

		UUID localPlayerId = client.player.getUuid();
		String currentDimension = client.world.getRegistryKey().getValue().toString();
		Set<String> activeIds = ConcurrentHashMap.newKeySet();

		for (RemotePlayerInfo info : players == null ? Collections.<RemotePlayerInfo>emptyList() : players.values()) {
			if (info == null || info.uuid() == null || info.position() == null || info.uuid().equals(localPlayerId)) {
				continue;
			}
			if (info.dimension() != null && !info.dimension().isBlank() && !info.dimension().equals(currentDimension)) {
				continue;
			}

			if (!enablePlayerMarker) {
				continue;
			}

			TeamRelationHelper.TeamRelation relation = TeamRelationHelper.determineRelation(client, info.uuid(), info.name(), config);
			if (!shouldShowPlayerByRelation(relation, config)) {
				continue;
			}

			int playerColor = resolveTeamColor(relation, config);

			String waypointId = PLAYER_PREFIX + info.uuid();
			activeIds.add(waypointId);
			JourneyMapWaypointAccess.upsertWaypoint(
					MANAGED_WAYPOINTS,
					waypointId,
					WAYPOINT_PREFIX + (info.name() == null || info.name().isBlank() ? "Player" : info.name()),
					(int) Math.floor(info.position().x()),
					(int) Math.floor(info.position().y()),
					(int) Math.floor(info.position().z()),
					client.world.getRegistryKey(),
					playerColor);
		}

		for (String existingId : Set.copyOf(MANAGED_WAYPOINTS.keySet())) {
			if (!activeIds.contains(existingId)) {
				JourneyMapWaypointAccess.removeWaypoint(MANAGED_WAYPOINTS, existingId);
			}
		}
	}

	private static int resolveTeamColor(TeamRelationHelper.TeamRelation relation, Config config) {
		if (relation == null || config == null) {
			return DEFAULT_PLAYER_COLOR;
		}
		return switch (relation) {
			case FRIENDLY -> config.getFriendlyTeamColor();
			case ENEMY -> config.getEnemyTeamColor();
			case NEUTRAL -> config.getNeutralTeamColor();
			case UNKNOWN -> DEFAULT_PLAYER_COLOR;
		};
	}

	private static boolean shouldShowPlayerByRelation(TeamRelationHelper.TeamRelation relation, Config config) {
		if (config == null || !config.isEnablePlayerWaypointMarker()) {
			return false;
		}
		if (relation == null || relation == TeamRelationHelper.TeamRelation.UNKNOWN) {
			return config.isShowNeutralPlayerMarkers();
		}
		return switch (relation) {
			case FRIENDLY -> config.isShowFriendlyPlayerMarkers();
			case ENEMY -> config.isShowEnemyPlayerMarkers();
			case NEUTRAL -> config.isShowNeutralPlayerMarkers();
			case UNKNOWN -> config.isShowNeutralPlayerMarkers();
		};
	}

	static void clear() {
		JourneyMapWaypointAccess.clearWaypoints(MANAGED_WAYPOINTS);
	}
}