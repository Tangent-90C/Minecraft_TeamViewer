package fun.prof_chen.teamviewer.main_code.client.sdk;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnsupportedRemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnsupportedSharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnavailableRemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnavailableSharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.MapWaypointCommand;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.NativeMapWaypointSnapshot;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapAdapterBundleTest {
    @Test
    void reportsPresentOptionalProvidersWithoutRequiringEveryProvider() {
        MapAdapterBundle bundle = new MapAdapterBundle(
                List.of(remote("journeymap-players")),
                List.of(shared("journeymap-waypoints")));
        assertEquals(1, bundle.sharedWaypointAdapters().size());
        assertEquals(2, bundle.capabilities().size());
        assertTrue(bundle.capabilities().stream()
                .allMatch(value -> value.status() == IntegrationSupportStatus.MOD_NOT_INSTALLED));
    }

    @Test
    void reportsAnExplicitUnsupportedPluginWithoutBlockingTheBundle() {
        MapAdapterBundle bundle = new MapAdapterBundle(
                List.of(new UnsupportedRemotePlayerProjection("future-map", RemotePlayerProjection.Kind.OTHER, "no release")),
                List.of(new UnsupportedSharedWaypointMapAdapter("future-map-waypoints", "no release")));
        assertTrue(bundle.capabilities().stream()
                .allMatch(value -> value.status() == IntegrationSupportStatus.UNSUPPORTED_VERSION));
    }

    @Test
    void reportsMissingAndFailedOptionalPluginsThroughNoOpPorts() {
        MapAdapterBundle bundle = new MapAdapterBundle(
                List.of(new UnavailableRemotePlayerProjection(
                        "missing-map", RemotePlayerProjection.Kind.OTHER,
                        IntegrationSupportStatus.MOD_NOT_INSTALLED, "not installed")),
                List.of(new UnavailableSharedWaypointMapAdapter(
                        "failed-waypoints", IntegrationSupportStatus.FAILED, "initialization failed")));

        assertEquals(IntegrationSupportStatus.MOD_NOT_INSTALLED, bundle.capabilities().get(0).status());
        assertEquals(IntegrationSupportStatus.FAILED, bundle.capabilities().get(1).status());
        assertFalse(bundle.remotePlayerProjections().get(0).isAvailable());
        assertFalse(bundle.sharedWaypointAdapters().get(0).isAvailable());
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
