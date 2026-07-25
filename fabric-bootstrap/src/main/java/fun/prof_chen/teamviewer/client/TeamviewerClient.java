package fun.prof_chen.teamviewer.client;

import fun.prof_chen.teamviewer.client.bootstrap.ClientBootstrap;
import net.fabricmc.api.ClientModInitializer;

/** Thin Fabric Loader entrypoint. Loader-neutral startup lives in client-bootstrap. */
public final class TeamviewerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientBootstrap.start();
    }
}
