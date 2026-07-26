package fun.prof_chen.teamviewer.main_code.battlemap;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.BattleMapSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.BattleMapSourceSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.model.ReportDataSchemas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The single protocol bridge for every Java or Lua {@code BattleMapSource}: source selection,
 * history alignment, projection, deduplication, refresh, keepalive and battle_map_observation output.
 */
public final class BattleMapCoordinator {
    private static final String NODEMC_PROTOCOL_MODE = "nodemc";
    private static final String SIMMC_PROTOCOL_MODE = "simmc";
    private final Config config;
    private final NetworkManager network;
    private final IntegrationRegistry integrations;
    private final BattleMapPositionHistory history = new BattleMapPositionHistory(120);
    private int tickCounter;
    private String activeSourceId;
    private boolean observationPending = true;
    private String lastSemanticHash = "";
    private long lastKeepaliveAt;

    public BattleMapCoordinator(Config config, NetworkManager network, IntegrationRegistry integrations) {
        this.config = Objects.requireNonNull(config, "config");
        this.network = Objects.requireNonNull(network, "network");
        this.integrations = Objects.requireNonNull(integrations, "integrations");
    }

    public void tick(boolean enabled, ClientWorldSnapshot world) {
        if (!enabled || !network.isConnected() || !config.isBattleMapSyncEnabled()
                || world == null || !world.available()) return;
        String sourceId = config.getBattleMapSourceId();
        BattleMapSource source = integrations.activeBattleMapSource(sourceId);
        if (source == null) return;
        history.record(world, System.currentTimeMillis());
        if (!Objects.equals(sourceId, activeSourceId)) {
            activeSourceId = sourceId;
            resetObservation();
        }
        if (++tickCounter < Math.max(1, config.getBattleMapUpdateIntervalTicks())) return;
        tickCounter = 0;
        Optional<BattleMapSourceSnapshot> snapshot = source.capture();
        Optional<Observation> observation = snapshot.flatMap(value ->
                value.coordinateSpace() == BattleMapSourceSnapshot.CoordinateSpace.ABSOLUTE_CHUNK
                        ? collectAbsolute(value) : collectRelative(value));
        if (observation.isEmpty() || observation.get().cells().isEmpty()) return;
        send(world, observation.get());
    }

    public void markPending() { observationPending = true; }

    public void reset() {
        activeSourceId = null;
        history.clear();
        BattleMapObservationClock.reset();
        resetObservation();
    }

    private Optional<Observation> collectRelative(BattleMapSourceSnapshot value) {
        long observedAt = value.observedAt() > 0 ? value.observedAt() : System.currentTimeMillis();
        List<BattleMapPositionHistory.Candidate> candidates = history.select(observedAt, value.dimension());
        if (candidates.isEmpty()) return Optional.empty();
        List<Map<String, Object>> candidatePayloads = candidates.stream()
                .map(candidate -> new ReportDataSchemas.BattleMapObservationCandidatePayload(
                        candidate.baseChunkX(), candidate.baseChunkZ(), candidate.sampledAt(), candidate.source()).toMap())
                .toList();
        List<Map<String, Object>> cells = value.cells().stream()
                .map(cell -> new ReportDataSchemas.BattleMapObservationCellPayload(
                        cell.chunkX(), cell.chunkZ(), cell.symbol(), cell.colorRaw()).toMap())
                .toList();
        BattleMapProjection.Result projection = BattleMapProjection.build(value.dimension(),
                candidates.get(0).baseChunkX(), candidates.get(0).baseChunkZ(), cells);
        return Optional.of(new Observation(protocolMode(value.sourceId()), value.dimension(), value.mapSize(),
                value.anchorRow(), value.anchorColumn(), observedAt, System.currentTimeMillis(), candidatePayloads,
                cells, projection.semanticHash(), projection.chunkIds()));
    }

    private Optional<Observation> collectAbsolute(BattleMapSourceSnapshot value) {
        if (value.cells().isEmpty()) return Optional.empty();
        List<BattleMapSourceSnapshot.Cell> sorted = value.cells().stream()
                .sorted(Comparator.comparingInt(BattleMapSourceSnapshot.Cell::chunkZ)
                        .thenComparingInt(BattleMapSourceSnapshot.Cell::chunkX)).toList();
        int minX = sorted.stream().mapToInt(BattleMapSourceSnapshot.Cell::chunkX).min().orElse(0);
        int minZ = sorted.stream().mapToInt(BattleMapSourceSnapshot.Cell::chunkZ).min().orElse(0);
        int maxX = sorted.stream().mapToInt(BattleMapSourceSnapshot.Cell::chunkX).max().orElse(minX);
        int maxZ = sorted.stream().mapToInt(BattleMapSourceSnapshot.Cell::chunkZ).max().orElse(minZ);
        long now = value.observedAt() > 0 ? value.observedAt() : System.currentTimeMillis();
        List<Map<String, Object>> candidates = List.of(new ReportDataSchemas.BattleMapObservationCandidatePayload(
                minX, minZ, now, "history_primary").toMap());
        List<Map<String, Object>> cells = new ArrayList<>();
        for (BattleMapSourceSnapshot.Cell cell : sorted) {
            cells.add(new ReportDataSchemas.BattleMapObservationCellPayload(
                    cell.chunkX() - minX, cell.chunkZ() - minZ, cell.symbol(), cell.colorRaw()).toMap());
        }
        BattleMapProjection.Result projection = BattleMapProjection.build(value.dimension(), minX, minZ, cells);
        return Optional.of(new Observation(protocolMode(value.sourceId()), value.dimension(),
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

    private static String protocolMode(String sourceId) {
        if (fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationIds.NODEMC_BATTLE_MAP.equals(sourceId)) {
            return NODEMC_PROTOCOL_MODE;
        }
        if (fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationIds.SIMMC_BATTLE_MAP.equals(sourceId)) {
            return SIMMC_PROTOCOL_MODE;
        }
        return sourceId;
    }

    private record Observation(String mode, String dimension, int mapSize, int anchorRow, int anchorColumn,
                               long observedAt, long parsedAt, List<Map<String, Object>> candidates,
                               List<Map<String, Object>> cells, String semanticHash, Set<String> projectedChunkIds) { }
}
