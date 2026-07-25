package fun.prof_chen.teamviewer.client;

import fun.prof_chen.teamviewer.main_code.client.ClientApplication;
import fun.prof_chen.teamviewer.main_code.client.sdk.AdapterRuntimeTck;
import fun.prof_chen.teamviewer.main_code.client.sdk.AdapterTck;
import fun.prof_chen.teamviewer.main_code.client.sdk.AdapterTckReport;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterBundle;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterFactory;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiSessions;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/** Shared Java 17 Fabric bootstrap; Minecraft-version code contributes one typed adapter factory. */
public final class TeamviewerClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("team-view-relay");
    private ClientApplication<?, ?> application;

    @Override
    public void onInitializeClient() {
        List<ClientAdapterFactory<?, ?>> factories = new ArrayList<>();
        ServiceLoader.load(ClientAdapterFactory.class)
                .forEach(factory -> factories.add((ClientAdapterFactory<?, ?>) factory));
        if (factories.size() != 1) {
            throw new IllegalStateException("Expected exactly one Minecraft adapter factory, found " + factories.size());
        }
        application = start(factories.get(0));
    }

    private static <W, H> ClientApplication<W, H> start(ClientAdapterFactory<W, H> factory) {
        ClientAdapterBundle<W, H> adapters = factory.create();
        ClientApplication<W, H> application = ClientApplication.start(adapters);
        LOGGER.info("TeamViewRelay adapter {} initialized", adapters.adapterVersion());
        AdapterTckReport report = AdapterTck.inspect(adapters, ConfigUiSessions.create());
        Path reportPath = adapters.runtimeGateway().getLogsDirectory().resolve("teamviewer-adapter-capabilities.json");
        AdapterRuntimeTck.install(report, reportPath);
        if (!report.passed()) {
            LOGGER.error("Adapter {} failed TCK: {}", adapters.adapterVersion(), report.issues());
            if (Boolean.getBoolean("teamviewer.adapterTck.strict")) {
                throw new IllegalStateException("Adapter TCK failed: " + report.issues());
            }
        } else {
            LOGGER.info("Adapter {} passed TCK", adapters.adapterVersion());
        }
        return application;
    }
}
