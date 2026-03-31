package fun.prof_chen.teamviewer.main_code.battlemap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;

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

    public BattleMapPositionHistory(int maxSamples) {
        this.maxSamples = Math.max(1, maxSamples);
    }

    public void recordCurrentPlayer(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        String dimension = client.player.getWorld().getRegistryKey().getValue().toString();
        ChunkPos currentChunk = new ChunkPos(client.player.getBlockPos());
        PositionSample last = samples.peekLast();
        if (last != null) {
            if (!Objects.equals(last.dimension(), dimension)) {
                clear();
            } else {
                int deltaChunks = Math.abs(last.chunkX() - currentChunk.x) + Math.abs(last.chunkZ() - currentChunk.z);
                if (deltaChunks > 2) {
                    clear();
                }
            }
        }

        samples.addLast(new PositionSample(now, currentChunk.x, currentChunk.z, dimension));
        trim(now);
    }

    public List<ObservationCandidate> selectCandidates(long snapshotObservedAt, String dimension) {
        if (snapshotObservedAt <= 0 || dimension == null || dimension.isBlank()) {
            return List.of();
        }

        trim(System.currentTimeMillis());
        PositionSample before = null;
        PositionSample after = null;

        for (PositionSample sample : samples) {
            if (!dimension.equals(sample.dimension())) {
                continue;
            }
            if (sample.capturedAtMs() <= snapshotObservedAt) {
                before = sample;
                continue;
            }
            after = sample;
            break;
        }

        if (before == null) {
            return List.of();
        }
        if (snapshotObservedAt - before.capturedAtMs() > MAX_ALIGNMENT_AGE_MS) {
            return List.of();
        }

        List<ObservationCandidate> result = new ArrayList<>();
        result.add(new ObservationCandidate(
                before.chunkX(),
                before.chunkZ(),
                before.capturedAtMs(),
                "history_primary"
        ));

        if (after != null
                && snapshotObservedAt - before.capturedAtMs() <= BOUNDARY_WINDOW_MS
                && after.capturedAtMs() - snapshotObservedAt <= BOUNDARY_WINDOW_MS
                && (after.chunkX() != before.chunkX() || after.chunkZ() != before.chunkZ())) {
            result.add(new ObservationCandidate(
                    after.chunkX(),
                    after.chunkZ(),
                    after.capturedAtMs(),
                    "history_boundary_alternative"
            ));
        }

        return result;
    }

    public void clear() {
        samples.clear();
    }

    private void trim(long now) {
        while (samples.size() > maxSamples) {
            samples.removeFirst();
        }
        while (!samples.isEmpty() && now - samples.peekFirst().capturedAtMs() > MAX_SAMPLE_AGE_MS) {
            samples.removeFirst();
        }
    }

    private record PositionSample(long capturedAtMs, int chunkX, int chunkZ, String dimension) {
    }

    public record ObservationCandidate(
            int baseChunkX,
            int baseChunkZ,
            long positionSampledAt,
            String source) {
    }
}
