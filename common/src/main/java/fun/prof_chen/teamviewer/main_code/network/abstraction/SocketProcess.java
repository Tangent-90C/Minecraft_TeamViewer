package fun.prof_chen.teamviewer.main_code.network.abstraction;

public interface SocketProcess {
    void send(byte[] payload);

    void close(int statusCode, String reason);
}
