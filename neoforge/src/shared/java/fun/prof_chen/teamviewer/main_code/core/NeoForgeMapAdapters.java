package fun.prof_chen.teamviewer.main_code.core;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.client.sdk.MapAdapterBundle;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnavailableRemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnavailableSharedWaypointMapAdapter;

import java.util.List;

/** Non-null and machine-readable optional-map ports for the initial NeoForge release. */
final class NeoForgeMapAdapters {
    private static final String DETAIL = "Optional map integration is not supported by the NeoForge adapter yet";

    private NeoForgeMapAdapters() { }

    static MapAdapterBundle unsupported() {
        return new MapAdapterBundle(
                List.of(
                        unavailablePlayer("journeymap-players", RemotePlayerProjection.Kind.JOURNEYMAP_MAP_MARKER),
                        unavailablePlayer("journeymap-player-beacons", RemotePlayerProjection.Kind.JOURNEYMAP_BEACON),
                        unavailablePlayer("xaero-worldmap", RemotePlayerProjection.Kind.XAERO_WORLD_MAP_MARKER)),
                List.of(
                        new UnavailableSharedWaypointMapAdapter("journeymap-shared-waypoints",
                                IntegrationSupportStatus.UNSUPPORTED_VERSION, DETAIL),
                        new UnavailableSharedWaypointMapAdapter("xaero-minimap",
                                IntegrationSupportStatus.NOT_IMPLEMENTED, DETAIL)));
    }

    private static UnavailableRemotePlayerProjection unavailablePlayer(
            String id, RemotePlayerProjection.Kind kind) {
        return new UnavailableRemotePlayerProjection(
                id, kind, IntegrationSupportStatus.NOT_IMPLEMENTED, DETAIL);
    }
}
