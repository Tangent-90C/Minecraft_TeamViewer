package fun.prof_chen.teamviewer.main_code.client.sdk;

/** Version/Loader-owned factory discovered by the shared client bootstrap. */
public interface ClientAdapterFactory<W, H> {
    ClientAdapterBundle<W, H> create();
}
