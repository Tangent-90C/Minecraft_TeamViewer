package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.main_code.bridge.MinecraftDimensionAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.MapWaypointCommand;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.NativeMapWaypointSnapshot;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Minecraft 26.1 JourneyMap native waypoint CRUD port. */
public final class JourneyMapSharedWaypointAdapter implements SharedWaypointMapAdapter {
    private final Map<String, Waypoint> managed = new ConcurrentHashMap<>();

    @Override
    public String id() { return "journeymap-shared-waypoints"; }

    @Override
    public boolean isAvailable() { return JourneyMapClientPlugin.isAvailable(); }

    @Override
    public IntegrationSupportStatus supportStatus() { return JourneyMapClientPlugin.supportStatus(); }

    @Override
    public List<NativeMapWaypointSnapshot> listLocalWaypoints() {
        if (!isAvailable()) return List.of();
        List<NativeMapWaypointSnapshot> result = new ArrayList<>();
        for (Waypoint value : JourneyMapClientPlugin.clientApi().getAllWaypoints()) {
            if (value == null || JourneyMapClientPlugin.TEAMVIEWER_MOD_ID.equals(value.getModId())) continue;
            BlockPos pos = value.getBlockPos();
            result.add(new NativeMapWaypointSnapshot(value.getGuid(), value.getName(), firstSymbol(value.getName()),
                    pos.getX(), pos.getY(), pos.getZ(), value.getPrimaryDimension(), value.getColor()));
        }
        return result;
    }

    @Override
    public void upsertRemoteWaypoint(MapWaypointCommand command) {
        if (!isAvailable() || command == null) return;
        Waypoint value = managed.get(command.waypointId());
        if (value != null && !Objects.equals(value.getName(), command.name())) {
            deleteRemoteWaypoint(command.waypointId());
            value = null;
        }
        if (value == null) {
            value = WaypointFactory.createClientWaypoint(JourneyMapClientPlugin.TEAMVIEWER_MOD_ID,
                    new BlockPos(command.x(), command.y(), command.z()), command.name(),
                    MinecraftDimensionAdapter.toResourceKey(command.dimension(), Level.OVERWORLD), false);
            if (value == null) return;
            value.setPersistent(false);
            JourneyMapClientPlugin.clientApi().addWaypoint(JourneyMapClientPlugin.TEAMVIEWER_MOD_ID, value);
            managed.put(command.waypointId(), value);
        }
        value.setPos(command.x(), command.y(), command.z());
        value.setColor(command.color());
        value.setEnabled(true);
    }

    @Override
    public void deleteRemoteWaypoint(String waypointId) {
        Waypoint value = managed.remove(waypointId);
        if (value != null && isAvailable()) {
            JourneyMapClientPlugin.clientApi().removeWaypoint(JourneyMapClientPlugin.TEAMVIEWER_MOD_ID, value);
        }
    }

    @Override
    public void clearRemoteWaypoints() {
        for (String id : new ArrayList<>(managed.keySet())) deleteRemoteWaypoint(id);
    }

    private static String firstSymbol(String value) {
        return value == null || value.isBlank() ? "W" : value.substring(0, 1).toUpperCase();
    }
}
