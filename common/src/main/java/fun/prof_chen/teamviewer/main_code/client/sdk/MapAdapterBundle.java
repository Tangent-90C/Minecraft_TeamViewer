package fun.prof_chen.teamviewer.main_code.client.sdk;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Optional native-map ports. Unsupported providers remain explicit through capability reports. */
public record MapAdapterBundle(
        List<RemotePlayerProjection> remotePlayerProjections,
        List<SharedWaypointMapAdapter> sharedWaypointAdapters) {
    public MapAdapterBundle {
        remotePlayerProjections = List.copyOf(Objects.requireNonNull(remotePlayerProjections, "remotePlayerProjections"));
        sharedWaypointAdapters = List.copyOf(Objects.requireNonNull(sharedWaypointAdapters, "sharedWaypointAdapters"));
        requireUniqueIds(remotePlayerProjections.stream().map(RemotePlayerProjection::id).toList(), "remote player");
        requireUniqueIds(sharedWaypointAdapters.stream().map(SharedWaypointMapAdapter::id).toList(), "shared waypoint");
    }

    public List<IntegrationCapability> capabilities() {
        List<IntegrationCapability> result = new ArrayList<>();
        remotePlayerProjections.forEach(adapter -> result.add(new IntegrationCapability(
                adapter.id(), "remote-player", adapter.supportStatus(), adapter.supportDetail())));
        sharedWaypointAdapters.forEach(adapter -> result.add(new IntegrationCapability(
                adapter.id(), "shared-waypoint", adapter.supportStatus(), adapter.supportDetail())));
        return List.copyOf(result);
    }

    private static void requireUniqueIds(List<String> ids, String role) {
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(role + " adapter id must not be null");
        }
        Set<String> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw new IllegalArgumentException("Duplicate " + role + " adapter ids: " + ids);
        }
    }
}
