package fun.prof_chen.teamviewer.main_code.mapbridge.implementor;

import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;

import java.util.Map;
import java.util.UUID;

public interface RemotePlayerProjection {
	String id();

	boolean isAvailable();

	void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled);

	default void clear() {
		sync(Map.of(), false);
	}
}