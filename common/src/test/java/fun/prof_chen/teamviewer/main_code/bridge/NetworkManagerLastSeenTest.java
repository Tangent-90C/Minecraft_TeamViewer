package fun.prof_chen.teamviewer.main_code.bridge;

import fun.prof_chen.teamviewer.main_code.model.LastSeenPlayerInfo;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import fun.prof_chen.teamviewer.main_code.network.protocol.ProtocolPackets;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkManagerLastSeenTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Test
    void replacesPatchesAndSuppressesLastSeenWhilePlayerIsOnline() throws Exception {
        Map<UUID, RemotePlayerInfo> online = new HashMap<>();
        Map<UUID, LastSeenPlayerInfo> history = new HashMap<>();
        NetworkManager manager = new NetworkManager(online, history, runtime(),
                (uri, options, listener) -> null);

        ProtocolPackets.SnapshotFullInboundPacket snapshot = new ProtocolPackets.SnapshotFullInboundPacket();
        snapshot.lastSeenPlayers = Map.of(PLAYER.toString(), lastSeenData());
        invoke(manager, "applySnapshot", ProtocolPackets.SnapshotFullInboundPacket.class, snapshot);
        assertEquals(1, manager.getLastSeenPlayerSnapshots().size());
        assertEquals(1_700_000_005_000L,
                manager.getLastSeenPlayerSnapshots().get(0).offlineDetectedAtUtcMs());

        ProtocolPackets.PatchInboundPacket onlinePatch = new ProtocolPackets.PatchInboundPacket();
        onlinePatch.players = Map.of(
                "upsert", Map.of(PLAYER.toString(), Map.of(
                        "x", 4D, "y", 65D, "z", 9D,
                        "dimension", "minecraft:overworld",
                        "playerName", "Remote", "playerUUID", PLAYER.toString())),
                "delete", List.of());
        invoke(manager, "applyPatch", ProtocolPackets.PatchInboundPacket.class, onlinePatch);
        assertTrue(manager.getLastSeenPlayerSnapshots().isEmpty());
        assertTrue(history.isEmpty());

        ProtocolPackets.PatchInboundPacket offlinePatch = new ProtocolPackets.PatchInboundPacket();
        offlinePatch.players = Map.of("upsert", Map.of(), "delete", List.of(PLAYER.toString()));
        offlinePatch.lastSeenPlayers = Map.of(
                "upsert", Map.of(PLAYER.toString(), lastSeenData()), "delete", List.of());
        invoke(manager, "applyPatch", ProtocolPackets.PatchInboundPacket.class, offlinePatch);
        assertEquals(1, manager.getLastSeenPlayerSnapshots().size());
    }

    private static Map<String, Object> lastSeenData() {
        return Map.of(
                "x", 1.5D, "y", 64D, "z", -2.5D,
                "dimension", "minecraft:overworld", "playerName", "Remote",
                "playerUUID", PLAYER.toString(),
                "positionObservedAtUtcMs", 1_700_000_000_000L,
                "lastSeenAtUtcMs", 1_700_000_004_000L,
                "offlineDetectedAtUtcMs", 1_700_000_005_000L);
    }

    private static void invoke(NetworkManager manager, String name, Class<?> type, Object value) throws Exception {
        Method method = NetworkManager.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(manager, value);
    }

    private static RuntimeGateway runtime() {
        return new RuntimeGateway() {
            @Override public String getCurrentDimensionId() { return "minecraft:overworld"; }
            @Override public UUID getLocalPlayerId() { return UUID.randomUUID(); }
            @Override public String getClientProgramVersion() { return "test"; }
            @Override public Path getLogsDirectory() { return Path.of("build", "test-logs"); }
        };
    }
}
