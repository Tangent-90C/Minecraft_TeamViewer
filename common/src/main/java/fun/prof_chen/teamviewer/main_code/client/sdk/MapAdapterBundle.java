package fun.prof_chen.teamviewer.main_code.client.sdk;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Mandatory JourneyMap and Xaero ports. Absence of an optional mod is reported by isAvailable(). */
public record MapAdapterBundle(
        List<RemotePlayerProjection> remotePlayerProjections,
        List<SharedWaypointMapAdapter> sharedWaypointAdapters) {
    public MapAdapterBundle {
        remotePlayerProjections = List.copyOf(Objects.requireNonNull(remotePlayerProjections, "remotePlayerProjections"));
        sharedWaypointAdapters = List.copyOf(Objects.requireNonNull(sharedWaypointAdapters, "sharedWaypointAdapters"));
        requireProviders(remotePlayerProjections.stream().map(RemotePlayerProjection::id).toList(), "remote player");
        requireProviders(sharedWaypointAdapters.stream().map(SharedWaypointMapAdapter::id).toList(), "shared waypoint");
    }

    private static void requireProviders(List<String> ids, String role) {
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(role + " adapter id must not be null");
        }
        Set<String> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw new IllegalArgumentException("Duplicate " + role + " adapter ids: " + ids);
        }
        requireProvider(ids, "journey", role);
        requireProvider(ids, "xaero", role);
    }

    private static void requireProvider(List<String> ids, String provider, String role) {
        boolean present = ids.stream().map(String::toLowerCase).anyMatch(id -> id.contains(provider));
        if (!present) {
            throw new IllegalArgumentException("Missing " + provider + " " + role + " adapter; supplied " + ids);
        }
    }
}
