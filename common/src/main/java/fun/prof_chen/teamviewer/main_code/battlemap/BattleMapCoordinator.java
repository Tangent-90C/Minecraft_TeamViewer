package fun.prof_chen.teamviewer.main_code.battlemap;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.model.ReportDataSchemas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Common battle-map sampling, parsing, deduplication, refresh and keepalive state machine. */
public final class BattleMapCoordinator {
    private final Config config;
    private final NetworkManager network;
    private final GameClientBridge game;
    private final BattleMapNativeBridge nativeBridge;
    private final ScoreboardBattleMapParser parser = new ScoreboardBattleMapParser();
    private final BattleMapPositionHistory history = new BattleMapPositionHistory(120);
    private int tickCounter;
    private BattleMapMode activeMode;
    private boolean observationPending = true;
    private String lastSemanticHash = "";
    private long lastKeepaliveAt;

    public BattleMapCoordinator(Config config, NetworkManager network, GameClientBridge game, BattleMapNativeBridge nativeBridge) {
        this.config = Objects.requireNonNull(config, "config");
        this.network = Objects.requireNonNull(network, "network");
        this.game = Objects.requireNonNull(game, "game");
        this.nativeBridge = Objects.requireNonNull(nativeBridge, "nativeBridge");
    }

    public void tick(boolean enabled) {
        ClientWorldSnapshot world = game.captureWorldSnapshot();
        history.record(world, System.currentTimeMillis());
        if (!enabled || !network.isConnected() || !config.isBattleMapSyncEnabled() || !world.available()) return;
        BattleMapMode mode = BattleMapMode.fromId(config.getBattleMapMode());
        if (mode != activeMode) {
            activeMode = mode;
            resetObservation();
        }
        if (++tickCounter < Math.max(1, config.getBattleMapUpdateIntervalTicks())) return;
        tickCounter = 0;
        Optional<Observation> observation = mode == BattleMapMode.SIMMC ? collectNative() : collectScoreboard();
        if (observation.isEmpty() || observation.get().cells().isEmpty()) return;
        send(world, observation.get());
    }

    public void markPending() { observationPending = true; }

    public void reset() {
        activeMode = null;
        history.clear();
        BattleMapObservationClock.reset();
        resetObservation();
    }

    private Optional<Observation> collectScoreboard() {
        ScoreboardSnapshot snapshot = game.captureScoreboardSnapshot();
        Optional<ScoreboardBattleMapParser.ParsedSnapshot> parsed = parser.parse(snapshot);
        if (parsed.isEmpty()) return Optional.empty();
        ScoreboardBattleMapParser.ParsedSnapshot value = parsed.get();
        long observedAt = value.observedAt() > 0 ? value.observedAt() : System.currentTimeMillis();
        List<BattleMapPositionHistory.Candidate> candidates = history.select(observedAt, value.dimension());
        if (candidates.isEmpty()) return Optional.empty();
        List<Map<String, Object>> candidatePayloads = candidates.stream()
                .map(candidate -> new ReportDataSchemas.BattleMapObservationCandidatePayload(
                        candidate.baseChunkX(), candidate.baseChunkZ(), candidate.sampledAt(), candidate.source()).toMap())
                .toList();
        List<Map<String, Object>> cells = value.cells().stream()
                .map(cell -> new ReportDataSchemas.BattleMapObservationCellPayload(
                        cell.relChunkX(), cell.relChunkZ(), cell.symbol(), cell.colorRaw()).toMap())
                .toList();
        BattleMapProjection.Result projection = BattleMapProjection.build(value.dimension(),
                candidates.getFirst().baseChunkX(), candidates.getFirst().baseChunkZ(), cells);
        return Optional.of(new Observation(BattleMapMode.NODEMC.id(), value.dimension(), value.size(),
                value.anchorRow(), value.anchorColumn(), observedAt, System.currentTimeMillis(), candidatePayloads,
                cells, projection.semanticHash(), projection.chunkIds()));
    }

    private Optional<Observation> collectNative() {
        if (!nativeBridge.isAvailable()) return Optional.empty();
        Optional<NativeBattleMapSnapshot> snapshot = nativeBridge.capture();
        if (snapshot.isEmpty() || snapshot.get().cells().isEmpty()) return Optional.empty();
        NativeBattleMapSnapshot value = snapshot.get();
        List<NativeBattleMapSnapshot.Cell> sorted = value.cells().stream()
                .sorted(Comparator.comparingInt(NativeBattleMapSnapshot.Cell::chunkZ)
                        .thenComparingInt(NativeBattleMapSnapshot.Cell::chunkX)).toList();
        int minX = sorted.stream().mapToInt(NativeBattleMapSnapshot.Cell::chunkX).min().orElse(0);
        int minZ = sorted.stream().mapToInt(NativeBattleMapSnapshot.Cell::chunkZ).min().orElse(0);
        int maxX = sorted.stream().mapToInt(NativeBattleMapSnapshot.Cell::chunkX).max().orElse(minX);
        int maxZ = sorted.stream().mapToInt(NativeBattleMapSnapshot.Cell::chunkZ).max().orElse(minZ);
        long now = value.observedAt() > 0 ? value.observedAt() : System.currentTimeMillis();
        List<Map<String, Object>> candidates = List.of(new ReportDataSchemas.BattleMapObservationCandidatePayload(
                minX, minZ, now, "history_primary").toMap());
        List<Map<String, Object>> cells = new ArrayList<>();
        for (NativeBattleMapSnapshot.Cell cell : sorted) {
            cells.add(new ReportDataSchemas.BattleMapObservationCellPayload(
                    cell.chunkX() - minX, cell.chunkZ() - minZ, cell.symbol(), cell.colorRaw()).toMap());
        }
        BattleMapProjection.Result projection = BattleMapProjection.build(value.dimension(), minX, minZ, cells);
        return Optional.of(new Observation(BattleMapMode.SIMMC.id(), value.dimension(),
                Math.max(maxX - minX + 1, maxZ - minZ + 1), 0, 0, now, System.currentTimeMillis(),
                candidates, cells, projection.semanticHash(), projection.chunkIds()));
    }

    private void send(ClientWorldSnapshot world, Observation observation) {
        Set<String> forced = network.drainPendingBattleChunkRefreshIds();
        long now = System.currentTimeMillis();
        boolean semanticChanged = !Objects.equals(lastSemanticHash, observation.semanticHash());
        boolean keepaliveDue = now - lastKeepaliveAt >= Math.max(1_000L, network.getBattleChunkKeepaliveIntervalMs());
        if (forced.isEmpty() && !observationPending && !semanticChanged) {
            if (keepaliveDue && !observation.projectedChunkIds().isEmpty()) {
                network.sendBattleChunkKeepalive(world.localPlayerId(), observation.projectedChunkIds());
                lastKeepaliveAt = now;
            }
            return;
        }
        ReportDataSchemas.BattleMapObservationPayload payload = new ReportDataSchemas.BattleMapObservationPayload(
                observation.mode(), observation.dimension(), observation.mapSize(), observation.anchorRow(),
                observation.anchorColumn(), observation.observedAt(), observation.parsedAt(),
                observation.candidates(), observation.cells());
        network.sendBattleMapObservation(world.localPlayerId(), payload.toMap());
        lastSemanticHash = observation.semanticHash();
        lastKeepaliveAt = observation.parsedAt();
        observationPending = false;
    }

    private void resetObservation() {
        tickCounter = 0;
        observationPending = true;
        lastSemanticHash = "";
        lastKeepaliveAt = 0L;
    }

    private record Observation(String mode, String dimension, int mapSize, int anchorRow, int anchorColumn,
                               long observedAt, long parsedAt, List<Map<String, Object>> candidates,
                               List<Map<String, Object>> cells, String semanticHash, Set<String> projectedChunkIds) { }
}
