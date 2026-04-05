package fun.prof_chen.teamviewer.main_code.network.abstraction;

public interface TransportProcess {
    SocketProcess connect(String uri, TransportOptions options, TransportListener listener);
}
