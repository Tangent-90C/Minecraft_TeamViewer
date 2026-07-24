package fun.prof_chen.teamviewer.main_code.mapbridge.implementor;

import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;

import java.util.Map;
import java.util.UUID;

public interface RemotePlayerProjection {
	enum Kind {
		JOURNEYMAP_BEACON,
		JOURNEYMAP_MAP_MARKER,
		XAERO_WORLD_MAP_MARKER,
		OTHER
	}

	String id();

	default Kind kind() {
		return Kind.OTHER;
	}

	boolean isAvailable();

	void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled);

	default void clear() {
		sync(Map.of(), false);
	}
}
