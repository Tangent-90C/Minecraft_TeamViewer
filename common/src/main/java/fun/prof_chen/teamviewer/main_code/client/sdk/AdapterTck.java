package fun.prof_chen.teamviewer.main_code.client.sdk;

import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigPageId;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigPageView;
import fun.prof_chen.teamviewer.main_code.config.ui.ClientUiSession;
import fun.prof_chen.teamviewer.main_code.client.entity.EntityCaptureFrame;
import fun.prof_chen.teamviewer.main_code.client.entity.EntityUploadFilter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Runtime-safe contract checks shared by every Minecraft adapter. */
public final class AdapterTck {
    private AdapterTck() { }

    public static <W, H> AdapterTckReport inspect(
            ClientAdapterBundle<W, H> adapters,
            ClientUiSession clientUi) {
        List<String> issues = new ArrayList<>();
        probe("report snapshot", issues, () -> validateReport(adapters.gameClientBridge().captureReportSnapshot(false)));
        probe("world snapshot", issues, () -> validateWorld(adapters.gameClientBridge().captureWorldSnapshot(false)));
        probe("tab player snapshot", issues, () -> {
            if (adapters.gameClientBridge().captureTabPlayerSnapshot() == null) {
                throw new IllegalStateException("returned null");
            }
        });
        probe("typed entity snapshot", issues, () -> {
            EntityCaptureFrame frame = new EntityCaptureFrame();
            adapters.gameClientBridge().captureEntityFrame(frame, EntityUploadFilter.ALLOW_ALL);
            if (!frame.complete()) throw new IllegalStateException("capture did not finish");
            for (int index = 0; index < frame.size(); index++) {
                if (frame.id(index) == null) throw new IllegalStateException("entity UUID is null");
                String type = frame.type(index);
                if (type == null || type.isBlank() || !type.contains(":")) {
                    throw new IllegalStateException("entity type is not a stable namespaced ID: " + type);
                }
            }
        });
        probe("scoreboard snapshot", issues, () -> {
            if (adapters.gameClientBridge().captureScoreboardSnapshot() == null) {
                throw new IllegalStateException("returned null");
            }
        });
        EnumSet<ClientEventType> missingEvents = EnumSet.allOf(ClientEventType.class);
        missingEvents.removeAll(adapters.eventBridge().registeredEvents());
        if (!missingEvents.isEmpty()) issues.add("missing registered events: " + missingEvents);
        for (ConfigPageId pageId : ConfigPageId.values()) {
            probe("config page " + pageId, issues,
                    () -> validatePage(clientUi.config().page(pageId, 854, 480), pageId));
        }
        probe("plugin manager", issues, () -> {
            if (clientUi.plugins().view(854, 480) == null) {
                throw new IllegalStateException("returned null");
            }
        });
        List<String> integrationIssues = adapters.integrationRegistry().issues();
        integrationIssues.forEach(issue -> issues.add("integration: " + issue));
        List<IntegrationCapability> integrations = adapters.integrationRegistry().capabilities();
        Map<String, IntegrationCapability> byId = integrations.stream()
                .collect(Collectors.toMap(IntegrationCapability::id, value -> value));
        IntegrationIds.expectedRoles().forEach((id, role) -> {
            IntegrationCapability capability = byId.get(id);
            if (capability == null) {
                issues.add("integration: missing expected capability " + id);
            } else if (!role.equals(capability.role())) {
                issues.add("integration: role mismatch for " + id + ": " + capability.role());
            } else if (capability.status() != IntegrationSupportStatus.AVAILABLE
                    && capability.detail().isBlank()) {
                issues.add("integration: unavailable capability has no detail " + id);
            }
        });
        return new AdapterTckReport(
                adapters.adapterVersion(),
                adapters.runtimeGateway().getClientProgramVersion(),
                adapters.runtimeGateway().getMinecraftVersion(),
                System.getProperty("java.version", "unknown"),
                issues.isEmpty(), issues, integrations, false, false);
    }

    private static void validateReport(ClientReportSnapshot snapshot) {
        if (snapshot == null) throw new IllegalStateException("returned null");
        if (snapshot.players() == null || snapshot.entities() == null) {
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
