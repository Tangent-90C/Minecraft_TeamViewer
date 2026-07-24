package fun.prof_chen.teamviewer.main_code.mapbridge.implementor;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** No-op port used when an optional integration cannot be activated in this runtime. */
public record UnavailableRemotePlayerProjection(
        String id,
        Kind kind,
        IntegrationSupportStatus supportStatus,
        String supportDetail) implements RemotePlayerProjection {
    public UnavailableRemotePlayerProjection {
        id = Objects.requireNonNull(id, "id");
        kind = kind == null ? Kind.OTHER : kind;
        supportStatus = Objects.requireNonNull(supportStatus, "supportStatus");
        if (supportStatus == IntegrationSupportStatus.AVAILABLE) {
            throw new IllegalArgumentException("An unavailable adapter cannot report AVAILABLE");
        }
        supportDetail = supportDetail == null ? "" : supportDetail;
    }

    @Override public boolean isAvailable() { return false; }
    @Override public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) { }
}
