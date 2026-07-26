package fun.prof_chen.teamviewer.main_code.client.entity;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import fun.prof_chen.teamviewer.main_code.network.protocol.EntityPatchView;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityReportPipelineTest {
    @Test
    void coalescesPendingFramesToLatestState() throws Exception {
        BlockingNetwork network = new BlockingNetwork();
        EntityReportPipeline pipeline = new EntityReportPipeline(network);
        UUID player = UUID.randomUUID();
        UUID entity = UUID.randomUUID();

        pipeline.submit(frame(pipeline, player, entity, 1.0));
        assertTrue(network.firstSendEntered.await(2, TimeUnit.SECONDS));
        pipeline.submit(frame(pipeline, player, entity, 2.0));
        pipeline.submit(frame(pipeline, player, entity, 3.0));
        network.releaseFirstSend.countDown();

        for (int attempt = 0; attempt < 100 && network.sentX.size() < 2; attempt++) {
            Thread.sleep(10L);
        }
        assertEquals(List.of(1.0, 3.0), network.sentX,
                "the intermediate frame must be replaced without losing the final state");
        pipeline.close();
    }

    private static EntityCaptureFrame frame(
            EntityReportPipeline pipeline, UUID player, UUID entity, double x) {
        EntityCaptureFrame frame = pipeline.acquire();
        if (frame == null) throw new IllegalStateException("frame pool exhausted");
        frame.begin(player, "minecraft:overworld", 1);
        frame.accept(entity, x, 2, 3, 0, 0, 0,
                "minecraft:zombie", null, 0.6f, 1.95f);
        frame.finish(1);
        frame.prepareSubmission(1L, 0L, Set.of());
        return frame;
    }

    private static final class BlockingNetwork extends NetworkManager {
        private final CountDownLatch firstSendEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirstSend = new CountDownLatch(1);
        private final List<Double> sentX = new java.util.concurrent.CopyOnWriteArrayList<>();

        private BlockingNetwork() {
            super(new HashMap<UUID, RemotePlayerInfo>(), runtime(), (uri, options, listener) -> null);
        }

        @Override
        public boolean sendTypedEntitiesPatchIfCurrent(
                long expectedEpoch, UUID submitPlayerId, EntityPatchView patch) {
            sentX.add(patch.x(0));
            if (sentX.size() == 1) {
                firstSendEntered.countDown();
                try {
                    releaseFirstSend.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }

        @Override
        public void sendTypedEntityKeepaliveIfNeeded(
                long expectedEpoch, UUID submitPlayerId, Collection<UUID> entityIds) {
        }

        private static RuntimeGateway runtime() {
            return new RuntimeGateway() {
                public String getCurrentDimensionId() { return "minecraft:overworld"; }
                public UUID getLocalPlayerId() { return null; }
                public String getClientProgramVersion() { return "test"; }
                public String getClientProtocolVersion() { return "0.6.2"; }
                public String getClientMinCompatibleProtocolVersion() { return "0.6.1"; }
                public String getServerProtocolFallbackVersion() { return "0.0.0"; }
                public String getProgramVersionUnknown() { return "unknown"; }
                public Path getLogsDirectory() { return Path.of("build", "test-logs"); }
            };
        }
    }
}
