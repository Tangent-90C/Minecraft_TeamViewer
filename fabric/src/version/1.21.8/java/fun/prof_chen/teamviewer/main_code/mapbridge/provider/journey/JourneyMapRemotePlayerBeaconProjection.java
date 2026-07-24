package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;

import java.util.Map;
import java.util.UUID;

public final class JourneyMapRemotePlayerBeaconProjection implements RemotePlayerProjection {
    @Override
    public String id() { return "journeymap-player-beacons"; }

    @Override
    public Kind kind() { return Kind.JOURNEYMAP_BEACON; }

    @Override
    public boolean isAvailable() { return JourneyMapRemotePlayerBridge.isAvailable(); }

    @Override
    public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
        JourneyMapRemotePlayerBridge.tick(players, enabled);
    }
}
