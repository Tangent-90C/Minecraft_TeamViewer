package fun.prof_chen.teamviewer.main_code.network.abstraction;

public record TransportOptions(
        boolean useSystemProxy,
        boolean enableCompression
) {
}
