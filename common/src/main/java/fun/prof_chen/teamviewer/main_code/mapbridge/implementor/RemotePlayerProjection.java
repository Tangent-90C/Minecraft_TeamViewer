package fun.prof_chen.teamviewer.main_code.mapbridge.implementor;

import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;

import java.util.Map;
import java.util.UUID;

public interface RemotePlayerProjection {
	String id();

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
