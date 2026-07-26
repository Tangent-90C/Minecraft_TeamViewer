package fun.prof_chen.teamviewer.main_code.client.entity;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.network.protocol.EntityPatchView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Single-consumer entity diff pipeline. Main-thread capture owns a frame until submit; the worker owns it
 * until direct protobuf encoding finishes. Pending work is a latest-value mailbox, never an unbounded queue.
 */
public final class EntityReportPipeline implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityReportPipeline.class);
    private static final long FORCE_FULL_REFRESH_MS = 25_000L;
    private static final int FRAME_POOL_SIZE = 3;

    private final NetworkManager network;
    private final Object mailboxLock = new Object();
    private final ArrayDeque<EntityCaptureFrame> availableFrames = new ArrayDeque<>(FRAME_POOL_SIZE);
    private final Map<UUID, MutableEntityState> sentState = new HashMap<>();
    private final EntityPatchBuffer patch = new EntityPatchBuffer();
    private MutableEntityState[] frameStates = new MutableEntityState[256];
    private boolean[] newFrameStates = new boolean[256];
    private final Thread worker;

    private EntityCaptureFrame pending;
    private volatile boolean running = true;
    private long stateEpoch = Long.MIN_VALUE;
    private long stateFilterRevision = Long.MIN_VALUE;
    private long lastPatchSentAtMs;
    private int generation;

    public EntityReportPipeline(NetworkManager network) {
        this.network = Objects.requireNonNull(network, "network");
        for (int index = 0; index < FRAME_POOL_SIZE; index++) {
            availableFrames.addLast(new EntityCaptureFrame());
        }
        worker = new Thread(this::runWorker, "teamviewer-entity-report");
        worker.setDaemon(true);
        worker.start();
    }

    public EntityCaptureFrame acquire() {
        synchronized (mailboxLock) {
            return availableFrames.pollFirst();
        }
    }

    public void submit(EntityCaptureFrame frame) {
        if (frame == null) return;
        if (!running || !frame.complete() || frame.submitPlayerId() == null) {
            release(frame);
            return;
        }
        synchronized (mailboxLock) {
            EntityCaptureFrame replaced = pending;
            pending = frame;
            if (replaced != null) {
                releaseLocked(replaced);
            }
            mailboxLock.notifyAll();
        }
    }

    /** Drop queued work. Sent-state is reset lazily whenever the network epoch changes. */
    public void discardPending() {
        synchronized (mailboxLock) {
            if (pending != null) {
                releaseLocked(pending);
                pending = null;
            }
        }
    }

    private void runWorker() {
        while (running) {
            EntityCaptureFrame frame;
            synchronized (mailboxLock) {
                while (running && pending == null) {
                    try {
                        mailboxLock.wait();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        running = false;
                        break;
                    }
                }
                frame = pending;
                pending = null;
            }
            if (frame == null) continue;
            try {
                process(frame);
            } catch (Throwable error) {
                LOGGER.warn("Entity report worker failed: {}", error.toString());
            } finally {
                patch.clearReferences();
                clearFrameStates(frame.size());
                release(frame);
            }
        }
    }

    private void process(EntityCaptureFrame frame) {
        if (frame.outboundEpoch() != stateEpoch) {
            sentState.clear();
            stateEpoch = frame.outboundEpoch();
            stateFilterRevision = frame.filterRevision();
            lastPatchSentAtMs = 0L;
        }
        boolean filterChanged = frame.filterRevision() != stateFilterRevision;
        boolean forceAll = filterChanged
                || System.currentTimeMillis() - lastPatchSentAtMs >= FORCE_FULL_REFRESH_MS;
        Set<String> refreshIds = frame.refreshIds();
        int currentGeneration = ++generation;
        if (currentGeneration == 0) {
            generation = currentGeneration = 1;
            for (MutableEntityState state : sentState.values()) state.seenGeneration = 0;
        }

        patch.reset(frame);
        ensureFrameStateCapacity(frame.size());
        for (int index = 0; index < frame.size(); index++) {
            UUID id = frame.id(index);
            MutableEntityState previous = sentState.get(id);
            boolean isNew = previous == null;
            if (isNew) previous = new MutableEntityState();
            previous.seenGeneration = currentGeneration;
            frameStates[index] = previous;
            newFrameStates[index] = isNew;
            boolean refresh = !refreshIds.isEmpty() && refreshIds.contains(id.toString());
            int mask = isNew || forceAll || refresh
                    ? EntityPatchView.ALL : changedFields(previous, frame, index);
            if (mask != 0) patch.addUpsert(index, mask);
        }

        for (Map.Entry<UUID, MutableEntityState> entry : sentState.entrySet()) {
            if (entry.getValue().seenGeneration != currentGeneration) {
                patch.addDelete(entry.getKey());
            }
        }
        if (!refreshIds.isEmpty()) {
            Set<String> knownDeletes = new HashSet<>();
            for (int index = 0; index < patch.deleteCount(); index++) {
                knownDeletes.add(patch.deleteId(index).toString());
            }
            for (String requestedId : refreshIds) {
                if (requestedId == null || knownDeletes.contains(requestedId)) continue;
                try {
                    UUID id = UUID.fromString(requestedId);
                    if (!contains(frame, id)) patch.addDelete(id);
                } catch (IllegalArgumentException ignored) {
                    // Entity IDs produced by Minecraft are UUIDs; ignore malformed server requests.
                }
            }
        }

        boolean sent = patch.isEmpty()
                || network.sendTypedEntitiesPatchIfCurrent(
                        frame.outboundEpoch(), frame.submitPlayerId(), patch);
        if (!sent) return;
        if (!patch.isEmpty()) lastPatchSentAtMs = System.currentTimeMillis();
        stateFilterRevision = frame.filterRevision();
        commit(frame, currentGeneration);
        long now = System.currentTimeMillis();
        for (int index = 0; index < patch.upsertCount(); index++) {
            frameStates[patch.sourceIndexAt(index)].lastLivenessAtMs = now;
        }
        ArrayList<UUID> keepalive = null;
        long keepaliveInterval = network.getEntityKeepaliveIntervalMs();
        for (Map.Entry<UUID, MutableEntityState> entry : sentState.entrySet()) {
            if (now - entry.getValue().lastLivenessAtMs < keepaliveInterval) continue;
            if (keepalive == null) keepalive = new ArrayList<>();
            keepalive.add(entry.getKey());
            entry.getValue().lastLivenessAtMs = now;
        }
        if (keepalive != null) {
            network.sendTypedEntityKeepaliveIfNeeded(
                    frame.outboundEpoch(), frame.submitPlayerId(), keepalive);
        }
    }

    private static boolean contains(EntityCaptureFrame frame, UUID id) {
        for (int index = 0; index < frame.size(); index++) {
            if (id.equals(frame.id(index))) return true;
        }
        return false;
    }

    private void commit(EntityCaptureFrame frame, int currentGeneration) {
        for (int index = 0; index < frame.size(); index++) {
            MutableEntityState state = frameStates[index];
            state.set(frame, index, currentGeneration);
            if (newFrameStates[index]) sentState.put(frame.id(index), state);
        }
        Iterator<Map.Entry<UUID, MutableEntityState>> iterator = sentState.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().seenGeneration != currentGeneration) iterator.remove();
        }
    }

    private void ensureFrameStateCapacity(int required) {
        if (required <= frameStates.length) return;
        int capacity = Math.max(required, frameStates.length + (frameStates.length >> 1));
        frameStates = Arrays.copyOf(frameStates, capacity);
        newFrameStates = Arrays.copyOf(newFrameStates, capacity);
    }

    private void clearFrameStates(int size) {
        Arrays.fill(frameStates, 0, Math.min(size, frameStates.length), null);
        Arrays.fill(newFrameStates, 0, Math.min(size, newFrameStates.length), false);
    }

    private static int changedFields(MutableEntityState previous, EntityCaptureFrame frame, int index) {
        int mask = 0;
        if (Double.compare(previous.x, frame.x(index)) != 0) mask |= EntityPatchView.X;
        if (Double.compare(previous.y, frame.y(index)) != 0) mask |= EntityPatchView.Y;
        if (Double.compare(previous.z, frame.z(index)) != 0) mask |= EntityPatchView.Z;
        if (Double.compare(previous.vx, frame.vx(index)) != 0) mask |= EntityPatchView.VX;
        if (Double.compare(previous.vy, frame.vy(index)) != 0) mask |= EntityPatchView.VY;
        if (Double.compare(previous.vz, frame.vz(index)) != 0) mask |= EntityPatchView.VZ;
        if (!Objects.equals(previous.dimension, frame.dimension())) mask |= EntityPatchView.DIMENSION;
        if (!Objects.equals(previous.type, frame.type(index))) mask |= EntityPatchView.TYPE;
        if (!Objects.equals(previous.name, frame.name(index))) mask |= EntityPatchView.NAME;
        if (Float.compare(previous.width, frame.width(index)) != 0) mask |= EntityPatchView.WIDTH;
        if (Float.compare(previous.height, frame.height(index)) != 0) mask |= EntityPatchView.HEIGHT;
        return mask;
    }

    private void release(EntityCaptureFrame frame) {
        synchronized (mailboxLock) {
            releaseLocked(frame);
        }
    }

    private void releaseLocked(EntityCaptureFrame frame) {
        frame.recycle();
        availableFrames.addLast(frame);
    }

    @Override
    public void close() {
        running = false;
        synchronized (mailboxLock) {
            if (pending != null) {
                releaseLocked(pending);
                pending = null;
            }
            mailboxLock.notifyAll();
        }
        worker.interrupt();
    }

    private static final class MutableEntityState {
        private double x, y, z, vx, vy, vz;
        private String dimension, type, name;
        private float width, height;
        private int seenGeneration;
        private long lastLivenessAtMs;

        private void set(EntityCaptureFrame frame, int index, int generation) {
            x = frame.x(index);
            y = frame.y(index);
            z = frame.z(index);
            vx = frame.vx(index);
            vy = frame.vy(index);
            vz = frame.vz(index);
            dimension = frame.dimension();
            type = frame.type(index);
            name = frame.name(index);
            width = frame.width(index);
            height = frame.height(index);
            seenGeneration = generation;
        }
    }
}
