package fun.prof_chen.teamviewer.api;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.ClientServices;
import fun.prof_chen.teamviewer.main_code.client.bridge.ClientControlGateway;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import fun.prof_chen.teamviewer.main_code.network.protocol.ProtocolPackets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamViewRelayApiTest {
    private ClientControlGateway installedControl;

    @AfterEach
    void clearServices() {
        if (installedControl != null) {
            ClientServices.clear(installedControl);
        }
    }

    @Test
    void exposesValidatedImmutableSnapshotsAndTracksPatches() throws Exception {
        NetworkManager manager = manager();
        install(manager);
        UUID playerId = UUID.randomUUID();

        ProtocolPackets.SnapshotFullInboundPacket full = new ProtocolPackets.SnapshotFullInboundPacket();
        full.players = Map.of(playerId.toString(), Map.of("data", playerData(playerId, 10.5)));
        full.playerMarks = Map.of(playerId.toString(), Map.of("data", Map.of("team", "hostile")));
        invoke(manager, "applySnapshot", ProtocolPackets.SnapshotFullInboundPacket.class, full);
        setField(manager, "isConnected", true);

        RemotePlayerBatch first = TeamViewRelayApi.remotePlayers();
        assertEquals(TeamViewRelayApi.API_VERSION, first.apiVersion());
        assertTrue(first.connected());
        assertEquals(1, first.players().size());
        RemotePlayerSnapshot player = first.players().get(0);
        assertEquals(playerId, player.uuid());
        assertEquals("Remote", player.name());
        assertEquals(10.5, player.x());
        assertEquals(0.25, player.velocityX());
        assertEquals(20F, player.health());
        assertEquals(PlayerRelation.ENEMY, player.relation());
        assertThrows(UnsupportedOperationException.class, () -> first.players().clear());

        ProtocolPackets.PatchInboundPacket patch = new ProtocolPackets.PatchInboundPacket();
        patch.players = Map.of("upsert", Map.of(
                playerId.toString(), Map.of("data", Map.of("x", 11.25))));
        patch.playerMarks = Map.of("upsert", Map.of(
                playerId.toString(), Map.of("data", Map.of("team", "ally"))));
        invoke(manager, "applyPatch", ProtocolPackets.PatchInboundPacket.class, patch);

        RemotePlayerSnapshot patched = TeamViewRelayApi.remotePlayers().players().get(0);
        assertEquals(11.25, patched.x());
        assertEquals(PlayerRelation.FRIENDLY, patched.relation());

        ProtocolPackets.PatchInboundPacket delete = new ProtocolPackets.PatchInboundPacket();
        delete.players = Map.of("delete", java.util.List.of(playerId.toString()));
        invoke(manager, "applyPatch", ProtocolPackets.PatchInboundPacket.class, delete);
        assertTrue(TeamViewRelayApi.remotePlayers().players().isEmpty());
    }

    @Test
    void rejectsIncompleteRecordsAndFailsClosedWhenDisconnected() throws Exception {
        NetworkManager manager = manager();
        install(manager);
        UUID playerId = UUID.randomUUID();
        ProtocolPackets.SnapshotFullInboundPacket full = new ProtocolPackets.SnapshotFullInboundPacket();
        full.players = Map.of(playerId.toString(), Map.of("data", Map.of(
                "x", 1D, "y", 2D, "z", 3D,
                "dimension", "minecraft:overworld", "playerName", "Incomplete")));
        invoke(manager, "applySnapshot", ProtocolPackets.SnapshotFullInboundPacket.class, full);
        setField(manager, "isConnected", true);
        assertTrue(TeamViewRelayApi.remotePlayers().players().isEmpty());

        setField(manager, "isConnected", false);
        RemotePlayerBatch disconnected = TeamViewRelayApi.remotePlayers();
        assertFalse(disconnected.connected());
        assertTrue(disconnected.players().isEmpty());
    }

    private NetworkManager manager() {
        return new NetworkManager(new HashMap<UUID, RemotePlayerInfo>(), runtime(),
                (uri, options, listener) -> null);
    }

    private void install(NetworkManager manager) {
        installedControl = new ClientControlGateway() {
            @Override public Config getConfig() { return null; }
            @Override public NetworkManager getNetworkManager() { return manager; }
            @Override public boolean isEnabled() { return true; }
            @Override public void setEnabled(boolean enabled) { }
            @Override public void reconnect() { }
            @Override public void showActionBar(String message) { }
        };
        ClientServices.install(installedControl);
    }

    private Map<String, Object> playerData(UUID playerId, double x) {
        return Map.ofEntries(
                Map.entry("x", x), Map.entry("y", 64D), Map.entry("z", -3.5D),
                Map.entry("vx", 0.25D), Map.entry("vy", 0D), Map.entry("vz", -0.5D),
                Map.entry("dimension", "minecraft:overworld"),
                Map.entry("playerName", "Remote"),
                Map.entry("playerUUID", playerId.toString()),
                Map.entry("health", 20F), Map.entry("maxHealth", 20F),
                Map.entry("armor", 8F), Map.entry("isRiding", false),
                Map.entry("width", 0.6F), Map.entry("height", 1.8F));
    }

    private static void invoke(NetworkManager manager, String name, Class<?> argumentType, Object argument)
            throws Exception {
        Method method = NetworkManager.class.getDeclaredMethod(name, argumentType);
        method.setAccessible(true);
        method.invoke(manager, argument);
    }

    private static void setField(NetworkManager manager, String name, Object value) throws Exception {
        Field field = NetworkManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private static RuntimeGateway runtime() {
        return new RuntimeGateway() {
            @Override public String getCurrentDimensionId() { return "minecraft:overworld"; }
            @Override public UUID getLocalPlayerId() { return null; }
            @Override public String getClientProgramVersion() { return "test"; }
            @Override public String getClientProtocolVersion() { return "test"; }
            @Override public String getClientMinCompatibleProtocolVersion() { return "test"; }
            @Override public String getServerProtocolFallbackVersion() { return "test"; }
            @Override public String getProgramVersionUnknown() { return "unknown"; }
            @Override public Path getLogsDirectory() { return Path.of("build", "test-logs"); }
        };
    }
}
