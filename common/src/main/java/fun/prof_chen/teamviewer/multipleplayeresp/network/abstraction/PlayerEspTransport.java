package fun.prof_chen.teamviewer.multipleplayeresp.network.abstraction;

public interface PlayerEspTransport {
    PlayerEspSocket connect(String uri, boolean useSystemProxy, PlayerEspTransportListener listener);
}
