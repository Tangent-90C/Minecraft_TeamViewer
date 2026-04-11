package fun.prof_chen.teamviewer.main_code.battlemap;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record BattleMapObservationResult(
        String mode,
        String dimension,
        int mapSize,
        int anchorRow,
        int anchorCol,
        long snapshotObservedAt,
        long parsedAt,
        List<Map<String, Object>> candidates,
        List<Map<String, Object>> cells,
        String semanticHash,
        Set<String> projectedChunkIds) {
}
