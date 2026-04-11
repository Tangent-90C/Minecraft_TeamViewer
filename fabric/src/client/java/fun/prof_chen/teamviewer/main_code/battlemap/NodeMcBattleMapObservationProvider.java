package fun.prof_chen.teamviewer.main_code.battlemap;

import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.model.ReportDataSchemas;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class NodeMcBattleMapObservationProvider implements BattleMapObservationProvider {
    private final ScoreboardBattleMapParser parser = new ScoreboardBattleMapParser();
    private final BattleMapPositionHistory positionHistory = new BattleMapPositionHistory(120);

    @Override
    public BattleMapMode mode() {
        return BattleMapMode.NODEMC;
    }

    @Override
    public void tick(MinecraftClient client) {
        positionHistory.recordCurrentPlayer(client);
    }

    @Override
    public Optional<BattleMapObservationResult> collect(MinecraftClient client, Config config) {
        Optional<ScoreboardBattleMapParser.ParsedBattleMapSnapshot> parsed = parser.parse(
                client,
                config != null && config.isBattleMapDebugEnabled()
        );
        if (parsed.isEmpty() || parsed.get().cells().isEmpty()) {
            return Optional.empty();
        }

        long snapshotObservedAt = BattleMapSidebarSnapshotTracker.lastSidebarObservedAt();
        if (snapshotObservedAt <= 0L) {
            snapshotObservedAt = System.currentTimeMillis();
        }
        List<BattleMapPositionHistory.ObservationCandidate> candidates = positionHistory.selectCandidates(
                snapshotObservedAt,
                parsed.get().dimension()
        );
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        List<Map<String, Object>> candidatePayloads = new ArrayList<>();
        for (BattleMapPositionHistory.ObservationCandidate candidate : candidates) {
            candidatePayloads.add(new ReportDataSchemas.BattleMapObservationCandidatePayload(
                    candidate.baseChunkX(),
                    candidate.baseChunkZ(),
                    candidate.positionSampledAt(),
                    candidate.source()
            ).toMap());
        }

        List<Map<String, Object>> cellPayloads = new ArrayList<>();
        for (ScoreboardBattleMapParser.RelativeBattleChunkCell cell : parsed.get().cells()) {
            cellPayloads.add(new ReportDataSchemas.BattleMapObservationCellPayload(
                    cell.relChunkX(),
                    cell.relChunkZ(),
                    cell.symbol(),
                    cell.colorRaw()
            ).toMap());
        }

        BattleMapProjectionUtil.Projection projection = BattleMapProjectionUtil.buildProjection(
                parsed.get().dimension(),
                candidates.getFirst().baseChunkX(),
                candidates.getFirst().baseChunkZ(),
                cellPayloads
        );
        long parsedAt = System.currentTimeMillis();
        return Optional.of(new BattleMapObservationResult(
                mode().id(),
                parsed.get().dimension(),
                parsed.get().size(),
                parsed.get().anchorRow(),
                parsed.get().anchorColumn(),
                snapshotObservedAt,
                parsedAt,
                candidatePayloads,
                cellPayloads,
                projection.semanticHash(),
                projection.chunkIds()
        ));
    }

    @Override
    public void reset() {
        positionHistory.clear();
        BattleMapSidebarSnapshotTracker.reset();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
