package fun.prof_chen.teamviewer.main_code.mapbridge.implementor;

import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;

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

	default IntegrationSupportStatus supportStatus() {
		return isAvailable() ? IntegrationSupportStatus.AVAILABLE : IntegrationSupportStatus.MOD_NOT_INSTALLED;
	}

	default String supportDetail() {
		return "";
	}

	void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled);

	default void clear() {
		sync(Map.of(), false);
	}
}
