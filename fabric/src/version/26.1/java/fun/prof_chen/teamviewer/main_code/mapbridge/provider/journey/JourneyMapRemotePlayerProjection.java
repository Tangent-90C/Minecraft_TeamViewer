package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

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
        return JourneyMapClientPlugin.isAvailable();
    }

    @Override
    public Kind kind() { return Kind.JOURNEYMAP_MAP_MARKER; }

    @Override
    public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
        JourneyMapRemotePlayerMarkerBridge.tick(players, enabled);
    }

    @Override
    public void clear() {
        JourneyMapRemotePlayerMarkerBridge.clear();
    }
}
