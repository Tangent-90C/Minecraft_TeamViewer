package fun.prof_chen.teamviewer.main_code.bridge;

import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import fun.prof_chen.teamviewer.main_code.network.abstraction.SocketProcess;
import fun.prof_chen.teamviewer.main_code.network.protocol.ProtocolPackets;
import fun.prof_chen.teamviewer.main_code.network.proto.WireEnvelope;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkManagerTabDiffTest {
    @Test
    void finiteReconnectBudgetIsClaimedAtomically() throws Exception {
        AtomicInteger remaining = new AtomicInteger(10);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> claims = new ArrayList<>();
        try {
            for (int index = 0; index < 100; index++) {
                claims.add(executor.submit(() -> {
                    start.await();
                    return NetworkManager.claimReconnectAttempt(remaining);
                }));
            }
            start.countDown();
            long accepted = 0L;
            for (Future<Integer> claim : claims) {
                if (claim.get() > 0) accepted++;
            }
            assertEquals(10L, accepted);
            assertEquals(0, remaining.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void unlimitedReconnectBudgetIsNeverConsumed() {
        AtomicInteger remaining = new AtomicInteger(-1);
        for (int index = 0; index < 100; index++) {
            assertEquals(-1, NetworkManager.claimReconnectAttempt(remaining));
        }
        assertEquals(-1, remaining.get());
    }

    @Test
    void decodedInboundMapsApplyWithoutJsonRoundTrip() throws Exception {
        Map<UUID, RemotePlayerInfo> remotePlayers = new HashMap<>();
        NetworkManager manager = new NetworkManager(
                remotePlayers, runtime(), (uri, options, listener) -> null);
        UUID playerId = UUID.randomUUID();
        ProtocolPackets.SnapshotFullInboundPacket snapshot = new ProtocolPackets.SnapshotFullInboundPacket();
        snapshot.players = Map.of(playerId.toString(), Map.of("data", Map.of(
                "x", 10.5, "y", 64.0, "z", -3.5,
                "dimension", "minecraft:overworld", "playerName", "Remote")));
        snapshot.entities = Map.of();
        snapshot.waypoints = Map.of();
        snapshot.battleChunks = Map.of();
        snapshot.playerMarks = Map.of();
        invoke(manager, "applySnapshot", ProtocolPackets.SnapshotFullInboundPacket.class, snapshot);

        assertEquals(new fun.prof_chen.teamviewer.main_code.model.Position3D(10.5, 64.0, -3.5),
                remotePlayers.get(playerId).position());

        ProtocolPackets.PatchInboundPacket patch = new ProtocolPackets.PatchInboundPacket();
        patch.players = Map.of("upsert", Map.of(
                playerId.toString(), Map.of("data", Map.of("x", 11.25))));
        invoke(manager, "applyPatch", ProtocolPackets.PatchInboundPacket.class, patch);
        assertEquals(new fun.prof_chen.teamviewer.main_code.model.Position3D(11.25, 64.0, -3.5),
                remotePlayers.get(playerId).position());

        ProtocolPackets.SnapshotFullInboundPacket empty = new ProtocolPackets.SnapshotFullInboundPacket();
        empty.players = Map.of();
        invoke(manager, "applySnapshot", ProtocolPackets.SnapshotFullInboundPacket.class, empty);
        assertTrue(remotePlayers.isEmpty(), "an explicit empty full snapshot clears the prior state");
    }

    @Test
    void mainThreadTaskBudgetPreservesOrderWithoutDroppingTasks() throws Exception {
        NetworkManager manager = new NetworkManager(
                new HashMap<UUID, RemotePlayerInfo>(), runtime(), (uri, options, listener) -> null);
        Method enqueue = NetworkManager.class.getDeclaredMethod("enqueueMainThreadTask", Runnable.class);
        enqueue.setAccessible(true);
        List<Integer> applied = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            int value = index;
            enqueue.invoke(manager, (Runnable) () -> applied.add(value));
        }

        manager.pumpMainThreadTasks();
        assertTrue(applied.size() >= 1 && applied.size() <= 32);
        while (applied.size() < 100) {
            manager.pumpMainThreadTasks();
        }
        assertEquals(java.util.stream.IntStream.range(0, 100).boxed().toList(), applied);
    }

    @Test
    void largeTabSnapshotSendsOnlyChangesAndDeletes() throws Exception {
        RecordingSocket socket = new RecordingSocket();
        NetworkManager manager = new NetworkManager(
                new HashMap<UUID, RemotePlayerInfo>(), runtime(), (uri, options, listener) -> socket);
        setField(manager, "socket", socket);
        setField(manager, "isConnected", true);
        UUID submitPlayerId = UUID.randomUUID();
        List<Map<String, Object>> baseline = tabPlayers(800);

        manager.sendTabPlayersUpdate(submitPlayerId, baseline);
        assertEquals(1, socket.payloads.size());
        assertEquals(800, lastTabPatch(socket).getUpsertCount());

        manager.sendTabPlayersUpdate(submitPlayerId, baseline);
        assertEquals(1, socket.payloads.size(), "unchanged 800-player list must not be resent");

        List<Map<String, Object>> changed = new ArrayList<>(baseline);
        changed.set(42, tabPlayer(42, "[Changed]"));
        manager.sendTabPlayersUpdate(submitPlayerId, changed);
        assertEquals(2, socket.payloads.size());
        assertEquals(1, lastTabPatch(socket).getUpsertCount());
        assertEquals(0, lastTabPatch(socket).getDeleteCount());

        manager.sendTabPlayersUpdate(submitPlayerId, changed.subList(0, 799));
        assertEquals(3, socket.payloads.size());
        assertEquals(0, lastTabPatch(socket).getUpsertCount());
        assertEquals(1, lastTabPatch(socket).getDeleteCount());
    }

    private static fun.prof_chen.teamviewer.main_code.network.proto.TabPlayersPatchScope lastTabPatch(
            RecordingSocket socket) throws Exception {
        WireEnvelope envelope = WireEnvelope.parseFrom(socket.payloads.get(socket.payloads.size() - 1));
        return envelope.getPlayerReportBundle().getTabPlayersPatch();
    }

    private static List<Map<String, Object>> tabPlayers(int count) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add(tabPlayer(index, "[T]"));
        }
        return values;
    }

    private static Map<String, Object> tabPlayer(int index, String prefix) {
        return Map.of(
                "playerUUID", UUID.nameUUIDFromBytes(("tab-" + index).getBytes()).toString(),
                "name", "Player" + index,
                "prefixText", "team",
                "prefixColored", prefix);
    }

    private static void setField(NetworkManager manager, String name, Object value) throws Exception {
        Field field = NetworkManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private static void invoke(NetworkManager manager, String name, Class<?> argumentType, Object argument)
            throws Exception {
        Method method = NetworkManager.class.getDeclaredMethod(name, argumentType);
        method.setAccessible(true);
        method.invoke(manager, argument);
    }

    private static RuntimeGateway runtime() {
        return new RuntimeGateway() {
            @Override
            public String getCurrentDimensionId() { return "minecraft:overworld"; }
            @Override
            public UUID getLocalPlayerId() { return null; }
            @Override
            public String getClientProgramVersion() { return "test"; }
            @Override
            public String getClientProtocolVersion() { return "test"; }
            @Override
            public String getClientMinCompatibleProtocolVersion() { return "test"; }
            @Override
            public String getServerProtocolFallbackVersion() { return "test"; }
            @Override
            public String getProgramVersionUnknown() { return "unknown"; }
            @Override
            public Path getLogsDirectory() { return Path.of("build", "test-logs"); }
        };
    }

    private static final class RecordingSocket implements SocketProcess {
        private final List<byte[]> payloads = new ArrayList<>();

        @Override public void send(byte[] payload) {
            payloads.add(payload.clone());
        }

        @Override public void close(int statusCode, String reason) { }
    }
}
