package fun.prof_chen.teamviewer.main_code.hud;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.hud.core.HudPlanner;
import fun.prof_chen.teamviewer.main_code.hud.model.LocalMarkedState;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudPlannerTest {
    @Test
    void createsTrafficAndMarkedPanelsWithStableFormatting() {
        Config config = new Config();
        config.setShowNetworkTrafficHud(true);
        NetworkManager network = new NetworkManager(new HashMap<UUID, RemotePlayerInfo>(), runtime(),
                (uri, options, listener) -> null);
        var frame = new HudPlanner().plan(config, network, true, new LocalMarkedState(true, 2, "Alice 等2人"));
        assertEquals(2, frame.panels().size());
        assertTrue(frame.panels().stream().anyMatch(panel -> panel.id().equals("network-traffic")));
        assertTrue(frame.panels().stream().anyMatch(panel -> panel.id().equals("local-marked")));
        assertEquals("1.5K/s", HudPlanner.formatRate(1536));
    }

    private static RuntimeGateway runtime() {
        return new RuntimeGateway() {
            public String getCurrentDimensionId() { return "minecraft:overworld"; }
            public UUID getLocalPlayerId() { return null; }
            public String getClientProgramVersion() { return "test"; }
            public String getClientProtocolVersion() { return "0.6.2"; }
            public String getClientMinCompatibleProtocolVersion() { return "0.6.2"; }
            public String getServerProtocolFallbackVersion() { return "0.6.2"; }
            public String getProgramVersionUnknown() { return "unknown"; }
            public Path getLogsDirectory() { return Path.of("build", "test-logs"); }
        };
    }
}
