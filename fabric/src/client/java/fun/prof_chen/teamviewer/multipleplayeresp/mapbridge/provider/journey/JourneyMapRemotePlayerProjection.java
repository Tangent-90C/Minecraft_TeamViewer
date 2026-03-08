package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.multipleplayeresp.core.StandaloneMultiPlayerESP;
import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.multipleplayeresp.model.RemotePlayerInfo;

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
		boolean journeyMapRemotePlayersEnabled = StandaloneMultiPlayerESP.getConfig().isShowJourneyMapRemotePlayerWaypoints();
		JourneyMapRemotePlayerBridge.tick(players, enabled && journeyMapRemotePlayersEnabled);
	}
}