package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** JourneyMap in-world beacon projection, separate from map markers. */
public final class JourneyMapRemotePlayerBeaconProjection implements RemotePlayerProjection {
    private static final Map<String, Waypoint> MANAGED = new ConcurrentHashMap<>();

    @Override
    public String id() { return "journeymap-player-beacons"; }

    @Override
    public Kind kind() { return Kind.JOURNEYMAP_BEACON; }

    @Override
    public boolean isAvailable() { return JourneyMapClientPlugin.isAvailable(); }

    @Override
    public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
        if (!enabled) {
            clear();
            return;
        }
        if (!isAvailable()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        Set<String> active = ConcurrentHashMap.newKeySet();
        for (RemotePlayerInfo player : players.values()) {
            if (player == null || player.uuid() == null || player.position() == null
                    || player.uuid().equals(client.player.getUUID())
                    || (player.dimension() != null && !player.dimension().isBlank()
                    && !player.dimension().equals(client.level.dimension().identifier().toString()))) continue;
            String id = "player-beacon:" + player.uuid();
            active.add(id);
            String name = "[TV] " + (player.name() == null || player.name().isBlank() ? "Player" : player.name());
            int x = (int) Math.floor(player.position().x());
            int y = (int) Math.floor(player.position().y());
            int z = (int) Math.floor(player.position().z());
            Waypoint value = MANAGED.get(id);
            if (value != null && !Objects.equals(value.getName(), name)) {
                remove(id);
                value = null;
            }
            if (value == null) {
                value = WaypointFactory.createClientWaypoint(JourneyMapClientPlugin.TEAMVIEWER_MOD_ID,
                        new BlockPos(x, y, z), name, client.level.dimension(), false);
                if (value == null) continue;
                value.setPersistent(false);
                value.setShowBeacon(true);
                value.setShowOnMap(false);
                value.setShowInWorld(true);
                JourneyMapClientPlugin.clientApi().addWaypoint(JourneyMapClientPlugin.TEAMVIEWER_MOD_ID, value);
                MANAGED.put(id, value);
            }
            value.setPos(x, y, z);
            value.setColor(0xFF5555);
            value.setEnabled(true);
        }
        for (String id : new ArrayList<>(MANAGED.keySet())) if (!active.contains(id)) remove(id);
    }

    @Override
    public void clear() {
        for (String id : new ArrayList<>(MANAGED.keySet())) remove(id);
    }

    private static void remove(String id) {
        Waypoint value = MANAGED.remove(id);
        if (value != null && JourneyMapClientPlugin.isAvailable()) {
            JourneyMapClientPlugin.clientApi().removeWaypoint(JourneyMapClientPlugin.TEAMVIEWER_MOD_ID, value);
        }
    }
}
