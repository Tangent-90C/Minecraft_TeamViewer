package fun.prof_chen.teamviewer.main_code.client.sdk;

import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigPageId;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigPageView;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiController;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Runtime-safe contract checks shared by every Minecraft adapter. */
public final class AdapterTck {
    private AdapterTck() { }

    public static <W, H> AdapterTckReport inspect(
            ClientAdapterBundle<W, H> adapters,
            ConfigUiController configUi) {
        List<String> issues = new ArrayList<>();
        probe("report snapshot", issues, () -> validateReport(adapters.gameClientBridge().captureReportSnapshot(false)));
        probe("world snapshot", issues, () -> validateWorld(adapters.gameClientBridge().captureWorldSnapshot()));
        probe("scoreboard snapshot", issues, () -> {
            if (adapters.gameClientBridge().captureScoreboardSnapshot() == null) {
                throw new IllegalStateException("returned null");
            }
        });
        EnumSet<ClientEventType> missingEvents = EnumSet.allOf(ClientEventType.class);
        missingEvents.removeAll(adapters.eventBridge().registeredEvents());
        if (!missingEvents.isEmpty()) issues.add("missing registered events: " + missingEvents);
        for (ConfigPageId pageId : ConfigPageId.values()) {
            probe("config page " + pageId, issues, () -> validatePage(configUi.page(pageId, 854, 480), pageId));
        }
        List<IntegrationCapability> integrations = adapters.mapAdapters().capabilities();
        return new AdapterTckReport(adapters.adapterVersion(), issues.isEmpty(), issues, integrations, false, false);
    }

    private static void validateReport(ClientReportSnapshot snapshot) {
        if (snapshot == null) throw new IllegalStateException("returned null");
        if (snapshot.players() == null || snapshot.entities() == null || snapshot.tabPlayers() == null) {
            throw new IllegalStateException("returned null collection");
        }
    }

    private static void validateWorld(ClientWorldSnapshot snapshot) {
        if (snapshot == null) throw new IllegalStateException("returned null");
        if (snapshot.players() == null || snapshot.entities() == null) {
            throw new IllegalStateException("returned null collection");
        }
    }

    private static void validatePage(ConfigPageView page, ConfigPageId expected) {
        if (page == null) throw new IllegalStateException("returned null");
        if (page.pageId() != expected) throw new IllegalStateException("returned " + page.pageId());
        if (page.controls() == null || page.controls().isEmpty()) throw new IllegalStateException("has no controls");
    }

    private static void probe(String name, List<String> issues, Runnable probe) {
        try {
            probe.run();
        } catch (Throwable error) {
            issues.add(name + ": " + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
        }
    }
}
