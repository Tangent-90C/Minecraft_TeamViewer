package fun.prof_chen.teamviewer.main_code.battlemap;

import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleMapPositionHistoryTest {
    @Test
    void returnsNoCandidateWithoutSamples() {
        BattleMapPositionHistory history = new BattleMapPositionHistory(10);
        assertEquals(List.of(), history.select(System.currentTimeMillis(), "minecraft:overworld"));
    }

    @Test
    void rejectsLatestSampleWhenObservationIsTooFarInTheFuture() {
        BattleMapPositionHistory history = new BattleMapPositionHistory(10);
        long now = System.currentTimeMillis();
        history.record(world(15.9, 15.9), now);
        assertEquals(List.of(), history.select(now + 7_000L, "minecraft:overworld"));
    }

    @Test
    void preservesBoundaryCandidatesAroundObservedPacketTime() {
        BattleMapPositionHistory history = new BattleMapPositionHistory(10);
        long now = System.currentTimeMillis();
        history.record(world(15.9, 15.9), now - 100);
        history.record(world(16.1, 15.9), now + 100);
        List<BattleMapPositionHistory.Candidate> candidates = history.select(now, "minecraft:overworld");
        assertEquals(2, candidates.size());
        assertEquals(0, candidates.get(0).baseChunkX());
        assertEquals(1, candidates.get(1).baseChunkX());
        assertEquals("history_boundary_alternative", candidates.get(1).source());
    }

    private static ClientWorldSnapshot world(double x, double z) {
        Position3D position = new Position3D(x, 64, z);
        return new ClientWorldSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000001"), "local", true,
                "minecraft:overworld", -64, position, position, new Position3D(0, 0, 1),
                new Position3D(0, 1, 0), List.of(), List.of());
    }
}
