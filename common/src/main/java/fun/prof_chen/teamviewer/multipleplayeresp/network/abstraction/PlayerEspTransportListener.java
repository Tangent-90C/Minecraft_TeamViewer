package fun.prof_chen.teamviewer.multipleplayeresp.network.abstraction;

public interface PlayerEspTransportListener {
    void onOpen(String negotiatedExtensions);

    void onTextMessage(String text);

    void onBinaryMessage(byte[] payload);

    void onClosed(int statusCode, String reason);

    void onFailure(Throwable error);
}
