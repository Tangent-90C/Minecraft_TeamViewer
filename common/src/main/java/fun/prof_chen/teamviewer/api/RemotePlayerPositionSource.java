package fun.prof_chen.teamviewer.api;

import java.util.Objects;

/** Immutable metadata describing the source selected for one remote position. */
public record RemotePlayerPositionSource(
        RemotePlayerPositionSourceKind kind,
        String sourceId,
        String displayName,
        Double positionResolution) {

    public RemotePlayerPositionSource {
        Objects.requireNonNull(kind, "kind");
        if (positionResolution != null
                && (!Double.isFinite(positionResolution) || positionResolution <= 0.0)) {
            positionResolution = null;
        }
    }

    public static RemotePlayerPositionSource unknown() {
        return new RemotePlayerPositionSource(
                RemotePlayerPositionSourceKind.UNKNOWN, null, null, null);
    }
}
