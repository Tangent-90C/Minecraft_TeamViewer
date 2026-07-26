package fun.prof_chen.teamviewer.main_code.client.entity;

import fun.prof_chen.teamviewer.main_code.network.protocol.EntityPatchView;

import java.util.Arrays;
import java.util.UUID;

/** Reusable typed patch buffer; values reference the current capture frame until encoding completes. */
final class EntityPatchBuffer implements EntityPatchView {
    private int upsertCount;
    private int deleteCount;
    private UUID[] upsertIds = new UUID[256];
    private int[] sourceIndexes = new int[256];
    private int[] masks = new int[256];
    private UUID[] deleteIds = new UUID[64];
    private EntityCaptureFrame source;

    void reset(EntityCaptureFrame source) {
        this.source = source;
        upsertCount = 0;
        deleteCount = 0;
    }

    void addUpsert(int sourceIndex, int mask) {
        ensureUpsertCapacity(upsertCount + 1);
        upsertIds[upsertCount] = source.id(sourceIndex);
        sourceIndexes[upsertCount] = sourceIndex;
        masks[upsertCount] = mask;
        upsertCount++;
    }

    void addDelete(UUID id) {
        if (id == null) {
            return;
        }
        ensureDeleteCapacity(deleteCount + 1);
        deleteIds[deleteCount++] = id;
    }

    boolean isEmpty() {
        return upsertCount == 0 && deleteCount == 0;
    }

    void clearReferences() {
        Arrays.fill(upsertIds, 0, upsertCount, null);
        Arrays.fill(deleteIds, 0, deleteCount, null);
        source = null;
        upsertCount = 0;
        deleteCount = 0;
    }

    private void ensureUpsertCapacity(int required) {
        if (required <= upsertIds.length) return;
        int capacity = Math.max(required, upsertIds.length + (upsertIds.length >> 1));
        upsertIds = Arrays.copyOf(upsertIds, capacity);
        sourceIndexes = Arrays.copyOf(sourceIndexes, capacity);
        masks = Arrays.copyOf(masks, capacity);
    }

    private void ensureDeleteCapacity(int required) {
        if (required <= deleteIds.length) return;
        deleteIds = Arrays.copyOf(deleteIds, Math.max(required, deleteIds.length + (deleteIds.length >> 1)));
    }

    private int sourceIndex(int index) { return sourceIndexes[index]; }
    int sourceIndexAt(int index) { return sourceIndexes[index]; }
    @Override public int upsertCount() { return upsertCount; }
    @Override public UUID upsertId(int index) { return upsertIds[index]; }
    @Override public int fieldMask(int index) { return masks[index]; }
    @Override public double x(int index) { return source.x(sourceIndex(index)); }
    @Override public double y(int index) { return source.y(sourceIndex(index)); }
    @Override public double z(int index) { return source.z(sourceIndex(index)); }
    @Override public double vx(int index) { return source.vx(sourceIndex(index)); }
    @Override public double vy(int index) { return source.vy(sourceIndex(index)); }
    @Override public double vz(int index) { return source.vz(sourceIndex(index)); }
    @Override public String dimension(int index) { return source.dimension(); }
    @Override public String entityType(int index) { return source.type(sourceIndex(index)); }
    @Override public String entityName(int index) { return source.name(sourceIndex(index)); }
    @Override public float width(int index) { return source.width(sourceIndex(index)); }
    @Override public float height(int index) { return source.height(sourceIndex(index)); }
    @Override public int deleteCount() { return deleteCount; }
    @Override public UUID deleteId(int index) { return deleteIds[index]; }
}
