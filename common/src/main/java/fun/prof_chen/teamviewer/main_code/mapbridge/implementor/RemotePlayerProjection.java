package fun.prof_chen.teamviewer.main_code.mapbridge.implementor;

import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerRelationView;
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

	/** Relationship-aware extension point; existing projections remain source compatible. */
	default void syncResolved(
			Map<UUID, RemotePlayerInfo> players,
			Map<UUID, PlayerRelationView> relations,
			boolean enabled) {
		sync(players, enabled);
	}

	default void clear() {
		sync(Map.of(), false);
	}

	/** True when the native integration retained an object whose deletion must be retried. */
	default boolean needsReconcile() {
		return false;
	}
}
