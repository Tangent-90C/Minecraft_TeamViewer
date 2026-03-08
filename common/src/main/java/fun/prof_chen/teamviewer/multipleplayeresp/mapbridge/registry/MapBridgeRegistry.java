package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.registry;

import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.implementor.SharedWaypointMapAdapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MapBridgeRegistry {
	private final Map<String, RemotePlayerProjection> remotePlayerProjections = new LinkedHashMap<>();
	private final Map<String, SharedWaypointMapAdapter> sharedWaypointAdapters = new LinkedHashMap<>();

	public MapBridgeRegistry registerRemotePlayerProjection(RemotePlayerProjection projection) {
		if (projection == null) {
			return this;
		}
		remotePlayerProjections.put(requireId(projection.id(), "remotePlayerProjection"), projection);
		return this;
	}

	public MapBridgeRegistry registerSharedWaypointAdapter(SharedWaypointMapAdapter adapter) {
		if (adapter == null) {
			return this;
		}
		sharedWaypointAdapters.put(requireId(adapter.id(), "sharedWaypointAdapter"), adapter);
		return this;
	}

	public List<RemotePlayerProjection> remotePlayerProjections() {
		return List.copyOf(remotePlayerProjections.values());
	}

	public List<SharedWaypointMapAdapter> sharedWaypointAdapters() {
		return List.copyOf(sharedWaypointAdapters.values());
	}

	private static String requireId(String id, String role) {
		String normalizedId = Objects.requireNonNull(id, role + " id").trim();
		if (normalizedId.isEmpty()) {
			throw new IllegalArgumentException(role + " id must not be blank");
		}
		return normalizedId;
	}
}
