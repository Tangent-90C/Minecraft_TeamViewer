package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.main_code.core.PlayerProcesses;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;

import java.util.Map;
import java.util.UUID;

public final class JourneyMapRemotePlayerProjection implements RemotePlayerProjection {
	@Override
	public String id() {
		return "journeymap-players";
	}

	@Override
	public boolean isAvailable() {
		return JourneyMapRemotePlayerBridge.isAvailable();
	}

	@Override
	public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
		boolean journeyMapRemotePlayersEnabled = PlayerProcesses.getConfig().isShowJourneyMapRemotePlayerWaypoints();
		JourneyMapRemotePlayerBridge.tick(players, enabled && journeyMapRemotePlayersEnabled);
	}
}