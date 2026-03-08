package fun.prof_chen.teamviewer.multipleplayeresp.network.abstraction;

public interface PlayerEspSocket {
    void send(byte[] payload);

    void close(int statusCode, String reason);
}
