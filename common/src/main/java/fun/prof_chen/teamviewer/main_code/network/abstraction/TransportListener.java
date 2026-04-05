package fun.prof_chen.teamviewer.main_code.network.abstraction;

public interface TransportListener {
    void onOpen(String negotiatedExtensions);

    void onTextMessage(String text);

    void onBinaryMessage(byte[] payload);

    void onTrafficEvent(TransportTrafficEvent event);

    void onClosed(int statusCode, String reason);

    void onFailure(Throwable error);
}
