package fun.prof_chen.teamviewer.main_code.network.abstraction;

public interface PlayerEspTransport {
    PlayerEspSocket connect(String uri, boolean useSystemProxy, PlayerEspTransportListener listener);
}
