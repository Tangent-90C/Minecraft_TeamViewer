package fun.prof_chen.teamviewer.main_code.client.sdk;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.MapWaypointCommand;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.NativeMapWaypointSnapshot;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapAdapterBundleTest {
    @Test
    void requiresBothOptionalMapProvidersForBothRoles() {
        assertThrows(IllegalArgumentException.class,
                () -> new MapAdapterBundle(List.of(remote("journeymap")), List.of(shared("journeymap"))));
        MapAdapterBundle bundle = new MapAdapterBundle(
                List.of(remote("journeymap-players"), remote("xaero-worldmap")),
                List.of(shared("journeymap-waypoints"), shared("xaero-minimap")));
        assertEquals(2, bundle.sharedWaypointAdapters().size());
    }

    private static RemotePlayerProjection remote(String id) {
        return new RemotePlayerProjection() {
            public String id() { return id; }
            public boolean isAvailable() { return false; }
            public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) { }
        };
    }

    private static SharedWaypointMapAdapter shared(String id) {
        return new SharedWaypointMapAdapter() {
            public String id() { return id; }
            public boolean isAvailable() { return false; }
            public List<NativeMapWaypointSnapshot> listLocalWaypoints() { return List.of(); }
            public void upsertRemoteWaypoint(MapWaypointCommand command) { }
            public void deleteRemoteWaypoint(String waypointId) { }
            public void clearRemoteWaypoints() { }
        };
    }
}
