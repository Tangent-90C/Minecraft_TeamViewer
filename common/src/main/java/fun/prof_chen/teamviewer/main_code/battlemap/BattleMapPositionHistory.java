package fun.prof_chen.teamviewer.main_code.battlemap;

import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public final class BattleMapPositionHistory {
    private static final long MAX_SAMPLE_AGE_MS = 6_000L;
    private static final long MAX_ALIGNMENT_AGE_MS = 1_500L;
    private static final long BOUNDARY_WINDOW_MS = 250L;
    private final int maxSamples;
    private final Deque<PositionSample> samples = new ArrayDeque<>();

    public BattleMapPositionHistory(int maxSamples) { this.maxSamples = Math.max(1, maxSamples); }

    public void record(ClientWorldSnapshot world, long now) {
        if (world == null || !world.available()) return;
        int chunkX = Math.floorDiv((int) Math.floor(world.localPlayerPosition().x()), 16);
        int chunkZ = Math.floorDiv((int) Math.floor(world.localPlayerPosition().z()), 16);
        PositionSample last = samples.peekLast();
        if (last != null && (!Objects.equals(last.dimension(), world.dimension())
                || Math.abs(last.chunkX() - chunkX) + Math.abs(last.chunkZ() - chunkZ) > 2)) clear();
        samples.addLast(new PositionSample(now, chunkX, chunkZ, world.dimension()));
        trim(now);
    }

    public List<Candidate> select(long observedAt, String dimension) {
        trim(System.currentTimeMillis());
        PositionSample before = null, after = null, latest = null;
        for (PositionSample sample : samples) {
            if (!Objects.equals(dimension, sample.dimension())) continue;
            latest = sample;
            if (sample.capturedAt() <= observedAt) before = sample; else { after = sample; break; }
        }
        if (before == null) before = latest;
        if (before == null) return List.of();
        if (observedAt - before.capturedAt() > MAX_ALIGNMENT_AGE_MS) {
            if (Math.abs(latest.capturedAt() - observedAt) > MAX_SAMPLE_AGE_MS) return List.of();
            return List.of(candidate(latest, "history_primary"));
        }
        List<Candidate> result = new ArrayList<>();
        result.add(candidate(before, "history_primary"));
        if (after != null && observedAt - before.capturedAt() <= BOUNDARY_WINDOW_MS
                && after.capturedAt() - observedAt <= BOUNDARY_WINDOW_MS
                && (after.chunkX() != before.chunkX() || after.chunkZ() != before.chunkZ())) {
            result.add(candidate(after, "history_boundary_alternative"));
        }
        return result;
    }

    public void clear() { samples.clear(); }
    private void trim(long now) {
        while (samples.size() > maxSamples) samples.removeFirst();
        while (!samples.isEmpty() && now - samples.peekFirst().capturedAt() > MAX_SAMPLE_AGE_MS) samples.removeFirst();
    }
    private static Candidate candidate(PositionSample sample, String source) {
        return new Candidate(sample.chunkX(), sample.chunkZ(), sample.capturedAt(), source);
    }
    private record PositionSample(long capturedAt, int chunkX, int chunkZ, String dimension) { }
    public record Candidate(int baseChunkX, int baseChunkZ, long sampledAt, String source) { }
}
