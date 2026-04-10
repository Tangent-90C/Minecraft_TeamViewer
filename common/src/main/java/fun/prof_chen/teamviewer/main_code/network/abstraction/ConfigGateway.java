package fun.prof_chen.teamviewer.main_code.network.abstraction;

public interface ConfigGateway {
    String getServerURL();

    void setServerURL(String serverURL);

    String getRoomCode();

    void setRoomCode(String roomCode);

    boolean isUseSystemProxy();

    void setUseSystemProxy(boolean useSystemProxy);

    boolean isAllowInsecureTls();

    void setAllowInsecureTls(boolean allowInsecureTls);

    boolean isEnableCompression();

    int getUpdateIntervalTicks();
}
