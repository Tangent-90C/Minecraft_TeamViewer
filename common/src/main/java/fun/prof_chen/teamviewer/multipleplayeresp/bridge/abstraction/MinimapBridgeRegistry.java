package fun.prof_chen.teamviewer.multipleplayeresp.bridge.abstraction;

import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.RemotePlayerProjection;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.SharedWaypointMinimapAdapter;

import java.util.ArrayList;
import java.util.List;

public final class MinimapBridgeRegistry {
	private final List<RemotePlayerProjection> remotePlayerProjections = new ArrayList<>();
	private final List<SharedWaypointMinimapAdapter> sharedWaypointAdapters = new ArrayList<>();

	public MinimapBridgeRegistry registerRemotePlayerProjection(RemotePlayerProjection projection) {
		if (projection != null) {
			remotePlayerProjections.add(projection);
		}
		return this;
	}

	public MinimapBridgeRegistry registerSharedWaypointAdapter(SharedWaypointMinimapAdapter adapter) {
		if (adapter != null) {
			sharedWaypointAdapters.add(adapter);
		}
		return this;
	}

	public List<RemotePlayerProjection> remotePlayerProjections() {
		return List.copyOf(remotePlayerProjections);
	}

	public List<SharedWaypointMinimapAdapter> sharedWaypointAdapters() {
		return List.copyOf(sharedWaypointAdapters);
	}
}
