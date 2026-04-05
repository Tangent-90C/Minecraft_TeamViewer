package fun.prof_chen.teamviewer.main_code.network.abstraction;

public record TransportTrafficEvent(
        Direction direction,
        FrameKind frameKind,
        long applicationPayloadBytes,
        long wireBytes
) {
    public enum Direction {
        OUTBOUND,
        INBOUND
    }

    public enum FrameKind {
        TEXT,
        BINARY,
        PING,
        PONG,
        CLOSE
    }
}
