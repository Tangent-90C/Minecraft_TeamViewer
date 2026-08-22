package fun.prof_chen.teamviewer.main_code.renderbridge.core;

import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntitySnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerRelationView;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.model.LastSeenPlayerInfo;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.AxisAlignedBox3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderCommand;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncGateway;
import fun.prof_chen.teamviewer.main_code.time.LastSeenTimeFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** All TeamViewRelay world-render decisions, independent of Minecraft rendering APIs. */
public final class WorldRenderPlanner {
    private static final int LAST_SEEN_BOX_COLOR = 0xBFFF9A26;
    private static final int LAST_SEEN_LINE_COLOR = 0xFFFFB347;
    private static final double LAST_SEEN_LABEL_DISTANCE = 512D;
    private static final int LAST_SEEN_INDEX_CELL_SIZE = 64;
    private final Config config;
    private final Function<UUID, PlayerRelationView> relationResolver;
    private final WaypointSyncGateway waypointGateway;
    private final Map<String, Position3D> trackedEntityWaypointLastPositions = new HashMap<>();
    private Map<UUID, LastSeenPlayerInfo> indexedLastSeenPlayers = Map.of();
    private Map<String, Map<Long, List<LastSeenPlayerInfo>>> lastSeenSpatialIndex = Map.of();
    private Map<UUID, String> lastSeenLabels = Map.of();

    public WorldRenderPlanner(
            Config config,
            Function<UUID, PlayerRelationView> relationResolver,
            WaypointSyncGateway waypointGateway) {
        this.config = config;
        this.relationResolver = relationResolver == null ? ignored -> null : relationResolver;
        this.waypointGateway = waypointGateway;
    }

    public WorldRenderFrame plan(
            boolean enabled,
            ClientWorldSnapshot world,
            Map<UUID, RemotePlayerInfo> remotePlayers,
            Map<String, SharedWaypointInfo> sharedWaypoints) {
        return plan(enabled, world, remotePlayers, Map.of(), sharedWaypoints);
    }

    public WorldRenderFrame plan(
            boolean enabled,
            ClientWorldSnapshot world,
            Map<UUID, RemotePlayerInfo> remotePlayers,
            Map<UUID, LastSeenPlayerInfo> lastSeenPlayers,
            Map<String, SharedWaypointInfo> sharedWaypoints) {
        if (!enabled || world == null || !world.available()) {
            return WorldRenderFrame.empty();
        }
        List<WorldRenderCommand> commands = new ArrayList<>();
        boolean depthTest = !config.isXrayMarkersAndBoxes();
        planPlayers(world, remotePlayers == null ? Map.of() : remotePlayers, depthTest, commands);
        planLastSeenPlayers(world, remotePlayers == null ? Map.of() : remotePlayers,
                lastSeenPlayers == null ? Map.of() : lastSeenPlayers, depthTest, commands);
        planWaypoints(world, sharedWaypoints == null ? Map.of() : sharedWaypoints, depthTest, commands);
        return new WorldRenderFrame(world.cameraPosition(), commands);
    }

    private void planLastSeenPlayers(
            ClientWorldSnapshot world,
            Map<UUID, RemotePlayerInfo> remotePlayers,
            Map<UUID, LastSeenPlayerInfo> lastSeenPlayers,
            boolean depthTest,
            List<WorldRenderCommand> commands) {
        if (!config.isShowLastSeenPlayers() || lastSeenPlayers.isEmpty()) return;
        for (LastSeenPlayerInfo player : nearbyLastSeenPlayers(world, lastSeenPlayers)) {
            if (player == null || player.position() == null || player.uuid() == null
                    || player.uuid().equals(world.localPlayerId()) || remotePlayers.containsKey(player.uuid())
                    || !sameDimension(world.dimension(), player.dimension())) {
                continue;
			}
			Position3D position = player.position();
			double playerDistance = distance(world.localPlayerPosition(), position);
			if (playerDistance > config.getRenderDistance()) continue;
			PlayerRelationView relation = relationResolver.apply(player.uuid());
			int boxColor = relationColor(relation, LAST_SEEN_BOX_COLOR);
			int lineColor = relationColor(relation, LAST_SEEN_LINE_COLOR);
			if (config.isShowLastSeenBoxes()) {
				commands.add(new WorldRenderCommand.Box(new AxisAlignedBox3D(
						position.x() - 0.35, position.y(), position.z() - 0.35,
						position.x() + 0.35, position.y() + 1.8, position.z() + 0.35),
						boxColor, depthTest));
            }
            if (config.isShowLastSeenLines()) {
                Position3D start = add(world.cameraPosition(), multiply(normalize(world.lookDirection()), 0.6));
                if (config.isTracerStartTop()) {
                    start = add(start, multiply(normalize(world.cameraUpDirection()), config.getTracerTopOffset()));
				}
				commands.add(new WorldRenderCommand.Line(start,
						add(position, new Position3D(0, 1, 0)), lineColor, depthTest, 1.0F));
            }
            if (playerDistance <= LAST_SEEN_LABEL_DISTANCE) {
				WorldLabelVectorizer.append(commands, lastSeenLabels.getOrDefault(player.uuid(), lastSeenLabel(player)),
						add(position, new Position3D(0, 2.15, 0)), world.lookDirection(),
						world.cameraUpDirection(), playerDistance, lineColor, depthTest);
            }
        }
    }

    public void clear() {
        trackedEntityWaypointLastPositions.clear();
        indexedLastSeenPlayers = Map.of();
        lastSeenSpatialIndex = Map.of();
        lastSeenLabels = Map.of();
    }

    private void planPlayers(ClientWorldSnapshot world, Map<UUID, RemotePlayerInfo> remotePlayers,
                             boolean depthTest, List<WorldRenderCommand> commands) {
        Map<UUID, RemotePlayerInfo> resolved = remotePlayers;
        if (config.isPreferLocalDataForRender()) {
            resolved = new HashMap<>(remotePlayers);
            for (PlayerSnapshot player : world.players()) {
                if (!player.id().equals(world.localPlayerId())) {
                    resolved.put(player.id(), new RemotePlayerInfo(player.id(), player.position(), player.dimension(), player.name()));
                }
            }
        }
        for (RemotePlayerInfo remote : resolved.values()) {
            if (remote == null || remote.uuid() == null || remote.position() == null
                    || remote.uuid().equals(world.localPlayerId()) || !sameDimension(world.dimension(), remote.dimension())) {
                continue;
            }
            double remoteDistance = distance(world.localPlayerPosition(), remote.position());
            if (remoteDistance > config.getRenderDistance()) continue;
            PlayerRelationView relation = relationResolver.apply(remote.uuid());
            int boxColor = relationColor(relation, config.getBoxColor());
            int lineColor = relationColor(relation, config.getLineColor());
            Position3D position = remote.position();
            if (config.isShowBoxes()) {
                commands.add(new WorldRenderCommand.Box(new AxisAlignedBox3D(
                        position.x() - 0.3, position.y(), position.z() - 0.3,
                        position.x() + 0.3, position.y() + 1.8, position.z() + 0.3), boxColor, depthTest));
            }
            if (config.isShowLines()) {
                Position3D start = add(world.cameraPosition(), multiply(normalize(world.lookDirection()), 0.6));
                if (config.isTracerStartTop()) {
                    start = add(start, multiply(normalize(world.cameraUpDirection()), config.getTracerTopOffset()));
                }
                commands.add(new WorldRenderCommand.Line(start, add(position, new Position3D(0, 1, 0)), lineColor, depthTest, 1.0F));
            }
        }
    }

    private void planWaypoints(ClientWorldSnapshot world, Map<String, SharedWaypointInfo> sharedWaypoints,
                               boolean depthTest, List<WorldRenderCommand> commands) {
        if (!config.isShowSharedWaypoints() || sharedWaypoints.isEmpty()) {
            return;
        }
        double maxDistance = Math.max(config.getRenderDistance(), 16.0);
        for (SharedWaypointInfo waypoint : sharedWaypoints.values()) {
            if (waypoint == null || isLocalPlayerTarget(world, waypoint)
                    || !sameDimension(world.dimension(), waypoint.dimension())) {
                continue;
            }
            Position3D position = resolveWaypointPosition(world, waypoint);
            if (position == null) {
                continue;
            }
            boolean tactical = isTactical(waypoint);
            if (!tactical && distance(world.localPlayerPosition(), position) > maxDistance) {
                continue;
            }
            int color = withAlpha(waypoint.color(), 0xCC);
            if (tactical) {
                planTacticalPillar(world, position, color, depthTest, commands);
            } else {
                planWaypointStyle(position, color, depthTest, commands);
            }
        }
    }

    private void planTacticalPillar(ClientWorldSnapshot world, Position3D position, int color,
                                    boolean depthTest, List<WorldRenderCommand> commands) {
        Position3D base = new Position3D(position.x(), world.worldBottomY() + 0.08, position.z());
        double height = config.getTampermonkeyBeamHeight();
        commands.add(new WorldRenderCommand.VerticalBeam(base, height, config.getTampermonkeyBeamWidth(), withAlpha(color, 0x55), depthTest));
        commands.add(new WorldRenderCommand.HorizontalPlane(new Position3D(position.x(), world.worldBottomY() + 0.03, position.z()),
                1.8, withAlpha(color, 0x4C), depthTest));
        commands.add(new WorldRenderCommand.Circle(base, 1.15, 24, withAlpha(color, 0xA6), depthTest));
        commands.add(new WorldRenderCommand.Circle(add(base, new Position3D(0, height, 0)), 0.48, 18, withAlpha(color, 0x9A), depthTest));
    }

    private void planWaypointStyle(Position3D position, int color, boolean depthTest,
                                   List<WorldRenderCommand> commands) {
        if (Config.WAYPOINT_UI_RING.equals(config.getWaypointUiStyle())) {
            Position3D center = add(position, new Position3D(0, 0.05, 0));
            commands.add(new WorldRenderCommand.Circle(center, 0.95, 24, color, depthTest));
            commands.add(new WorldRenderCommand.Circle(add(center, new Position3D(0, 0.3, 0)), 0.65, 18, withAlpha(color, 0x9A), depthTest));
            for (int i = 0; i < 4; i++) {
                double angle = Math.PI * 0.5 * i;
                Position3D direction = new Position3D(Math.cos(angle), 0, Math.sin(angle));
                commands.add(new WorldRenderCommand.Line(add(center, multiply(direction, 0.3)), add(center, multiply(direction, 1.2)),
                        withAlpha(color, 0x88), depthTest, 1.0F));
            }
            commands.add(new WorldRenderCommand.Line(add(center, new Position3D(0, 0.1, 0)),
                    add(center, new Position3D(0, 3, 0)), withAlpha(color, 0xB5), depthTest, 1.0F));
            return;
        }
        if (Config.WAYPOINT_UI_PIN.equals(config.getWaypointUiStyle())) {
            Position3D center = add(position, new Position3D(0, 0.1, 0));
            Position3D head = add(position, new Position3D(0, 2.8, 0));
            commands.add(new WorldRenderCommand.Line(center, head, color, depthTest, 1.0F));
            double size = 0.42;
            Position3D north = add(head, new Position3D(0, 0, -size));
            Position3D south = add(head, new Position3D(0, 0, size));
            Position3D east = add(head, new Position3D(size, 0, 0));
            Position3D west = add(head, new Position3D(-size, 0, 0));
            commands.add(new WorldRenderCommand.Line(north, south, color, depthTest, 1.0F));
            commands.add(new WorldRenderCommand.Line(east, west, color, depthTest, 1.0F));
            commands.add(new WorldRenderCommand.Line(north, east, withAlpha(color, 0x9A), depthTest, 1.0F));
            commands.add(new WorldRenderCommand.Line(east, south, withAlpha(color, 0x9A), depthTest, 1.0F));
            commands.add(new WorldRenderCommand.Line(south, west, withAlpha(color, 0x9A), depthTest, 1.0F));
            commands.add(new WorldRenderCommand.Line(west, north, withAlpha(color, 0x9A), depthTest, 1.0F));
            commands.add(new WorldRenderCommand.Circle(add(center, new Position3D(0, 0.02, 0)), 0.35, 12, withAlpha(color, 0xB0), depthTest));
            return;
        }
        Position3D center = add(position, new Position3D(0, 0.1, 0));
        double height = config.getWaypointBeaconBeamHeight();
        commands.add(new WorldRenderCommand.VerticalBeam(center, height, config.getWaypointBeaconBeamWidth(), withAlpha(color, 0x66), depthTest));
        commands.add(new WorldRenderCommand.Circle(add(center, new Position3D(0, 0.02, 0)), 0.75, 18, withAlpha(color, 0xB0), depthTest));
        commands.add(new WorldRenderCommand.Circle(add(center, new Position3D(0, height, 0)), 0.42, 14, withAlpha(color, 0xA0), depthTest));
    }

    private Position3D resolveWaypointPosition(ClientWorldSnapshot world, SharedWaypointInfo waypoint) {
        if (!"entity".equalsIgnoreCase(waypoint.targetType()) || isBlank(waypoint.targetEntityId())) {
            return new Position3D(waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5);
        }
        Position3D local = findLocalEntity(world, waypoint.targetEntityId(), waypoint.targetEntityName());
        if (local != null) {
            trackedEntityWaypointLastPositions.put(waypoint.waypointId(), local);
            return local;
        }
        if (waypointGateway != null) {
            Position3D remote = waypointGateway.getRemoteEntityPosition(waypoint.targetEntityId(), world.dimension());
            if (remote == null && isPlayerTarget(waypoint)) {
                remote = waypointGateway.getRemotePlayerPosition(waypoint.targetEntityId(), waypoint.targetEntityName(), world.dimension());
            }
            if (remote != null) {
                trackedEntityWaypointLastPositions.put(waypoint.waypointId(), remote);
                return remote;
            }
        }
        return trackedEntityWaypointLastPositions.computeIfAbsent(waypoint.waypointId(),
                ignored -> new Position3D(waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5));
    }

    private static Position3D findLocalEntity(ClientWorldSnapshot world, String id, String name) {
        for (EntitySnapshot entity : world.entities()) {
            if (id.equals(entity.id()) || (!isBlank(name) && name.equalsIgnoreCase(entity.name()))) return entity.position();
        }
        for (PlayerSnapshot player : world.players()) {
            if (id.equals(player.id().toString()) || (!isBlank(name) && name.equalsIgnoreCase(player.name()))) return player.position();
        }
        return null;
    }

    private static boolean isLocalPlayerTarget(ClientWorldSnapshot world, SharedWaypointInfo waypoint) {
        if (!"entity".equalsIgnoreCase(waypoint.targetType())) return false;
        return world.localPlayerId().toString().equals(waypoint.targetEntityId())
                || (!isBlank(world.localPlayerName()) && world.localPlayerName().equalsIgnoreCase(waypoint.targetEntityName()));
    }

    private int relationColor(PlayerRelationView relation, int fallback) {
        return relation == null || !relation.resolved() ? fallback : relation.color();
    }

    private static boolean isTactical(SharedWaypointInfo waypoint) {
        return isTacticalKind(waypoint.sourceType()) || isTacticalKind(waypoint.waypointKind());
    }

    private static boolean isTacticalKind(String value) {
        return "web_map_tactical".equalsIgnoreCase(value) || "admin_tactical".equalsIgnoreCase(value);
    }

    private static boolean isPlayerTarget(SharedWaypointInfo waypoint) {
        return "minecraft:player".equalsIgnoreCase(waypoint.targetEntityType());
    }

    private static boolean sameDimension(String expected, String actual) {
        return isBlank(actual) || actual.equals(expected);
    }

    private List<LastSeenPlayerInfo> nearbyLastSeenPlayers(
            ClientWorldSnapshot world, Map<UUID, LastSeenPlayerInfo> players) {
        if (players != indexedLastSeenPlayers) rebuildLastSeenIndex(players);
        Map<Long, List<LastSeenPlayerInfo>> dimensionBuckets = lastSeenSpatialIndex.get(world.dimension());
        Map<Long, List<LastSeenPlayerInfo>> wildcardBuckets = lastSeenSpatialIndex.get("");
        if ((dimensionBuckets == null || dimensionBuckets.isEmpty())
                && (wildcardBuckets == null || wildcardBuckets.isEmpty())) return List.of();
        List<LastSeenPlayerInfo> result = new ArrayList<>();
        collectNearby(result, dimensionBuckets, world.localPlayerPosition(), config.getRenderDistance());
        if (wildcardBuckets != dimensionBuckets) {
            collectNearby(result, wildcardBuckets, world.localPlayerPosition(), config.getRenderDistance());
        }
        return result;
    }

    private void rebuildLastSeenIndex(Map<UUID, LastSeenPlayerInfo> players) {
        Map<String, Map<Long, List<LastSeenPlayerInfo>>> mutableIndex = new HashMap<>();
        Map<UUID, String> labels = new HashMap<>();
        for (LastSeenPlayerInfo player : players.values()) {
            if (player == null || player.uuid() == null || player.position() == null) continue;
            String dimension = player.dimension() == null ? "" : player.dimension();
            long cell = cellKey(cellCoordinate(player.position().x()), cellCoordinate(player.position().z()));
            mutableIndex.computeIfAbsent(dimension, ignored -> new HashMap<>())
                    .computeIfAbsent(cell, ignored -> new ArrayList<>()).add(player);
            labels.put(player.uuid(), lastSeenLabel(player));
        }
        Map<String, Map<Long, List<LastSeenPlayerInfo>>> frozenIndex = new HashMap<>();
        for (Map.Entry<String, Map<Long, List<LastSeenPlayerInfo>>> entry : mutableIndex.entrySet()) {
            Map<Long, List<LastSeenPlayerInfo>> buckets = new HashMap<>();
            entry.getValue().forEach((key, value) -> buckets.put(key, List.copyOf(value)));
            frozenIndex.put(entry.getKey(), Map.copyOf(buckets));
        }
        indexedLastSeenPlayers = players;
        lastSeenSpatialIndex = Map.copyOf(frozenIndex);
        lastSeenLabels = Map.copyOf(labels);
    }

    private static void collectNearby(
            List<LastSeenPlayerInfo> target,
            Map<Long, List<LastSeenPlayerInfo>> buckets,
            Position3D origin,
            double radius) {
        if (buckets == null || buckets.isEmpty() || origin == null || radius < 0) return;
        int minX = cellCoordinate(origin.x() - radius);
        int maxX = cellCoordinate(origin.x() + radius);
        int minZ = cellCoordinate(origin.z() - radius);
        int maxZ = cellCoordinate(origin.z() + radius);
        long spanX = (long) maxX - minX + 1L;
        long spanZ = (long) maxZ - minZ + 1L;
        if (spanX > 0 && spanZ > 0 && spanX <= Long.MAX_VALUE / spanZ
                && spanX * spanZ <= (long) buckets.size() * 2L) {
            for (long x = minX; x <= maxX; x++) {
                for (long z = minZ; z <= maxZ; z++) {
                    List<LastSeenPlayerInfo> bucket = buckets.get(cellKey((int) x, (int) z));
                    if (bucket != null) target.addAll(bucket);
                }
            }
            return;
        }
        for (List<LastSeenPlayerInfo> bucket : buckets.values()) target.addAll(bucket);
    }

    private static int cellCoordinate(double coordinate) {
        return (int) Math.floor(coordinate / LAST_SEEN_INDEX_CELL_SIZE);
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static String lastSeenLabel(LastSeenPlayerInfo player) {
        String name = player.name().length() > 16 ? player.name().substring(0, 16) : player.name();
        return name + "\n" + LastSeenTimeFormatter.format(player.lastSeenAtUtcMs());
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static double distance(Position3D a, Position3D b) {
        double x = a.x() - b.x();
        double y = a.y() - b.y();
        double z = a.z() - b.z();
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static Position3D add(Position3D a, Position3D b) {
        if (a == null) return b;
        if (b == null) return a;
        return new Position3D(a.x() + b.x(), a.y() + b.y(), a.z() + b.z());
    }

    private static Position3D multiply(Position3D value, double scale) {
        if (value == null) return new Position3D(0, 0, 0);
        return new Position3D(value.x() * scale, value.y() * scale, value.z() * scale);
    }

    private static Position3D normalize(Position3D value) {
        if (value == null) return new Position3D(0, 0, 0);
        double length = Math.sqrt(value.x() * value.x() + value.y() * value.y() + value.z() * value.z());
        return length < 1.0E-9 ? new Position3D(0, 0, 0) : multiply(value, 1.0 / length);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
