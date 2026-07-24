package fun.prof_chen.teamviewer.main_code.mapbridge.implementor;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Explicit no-op port for an optional map integration unsupported by one Minecraft target. */
public record UnsupportedRemotePlayerProjection(String id, Kind kind, String supportDetail)
        implements RemotePlayerProjection {
    public UnsupportedRemotePlayerProjection {
        id = Objects.requireNonNull(id, "id");
        kind = kind == null ? Kind.OTHER : kind;
        supportDetail = supportDetail == null ? "unsupported Minecraft version" : supportDetail;
    }

    @Override public boolean isAvailable() { return false; }
    @Override public IntegrationSupportStatus supportStatus() { return IntegrationSupportStatus.UNSUPPORTED_VERSION; }
    @Override public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) { }
}
