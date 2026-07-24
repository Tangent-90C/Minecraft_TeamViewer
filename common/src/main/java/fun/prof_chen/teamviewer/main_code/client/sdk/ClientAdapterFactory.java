package fun.prof_chen.teamviewer.main_code.client.sdk;

/** Version-owned factory discovered by the shared Fabric bootstrap. */
public interface ClientAdapterFactory<W, H> {
    ClientAdapterBundle<W, H> create();
}
