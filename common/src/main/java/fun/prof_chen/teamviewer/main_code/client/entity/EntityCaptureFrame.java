package fun.prof_chen.teamviewer.main_code.client.entity;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/** Reusable structure-of-arrays entity frame. */
public final class EntityCaptureFrame implements EntityCaptureTarget {
    private static final int INITIAL_CAPACITY = 256;

    private UUID submitPlayerId;
    private String dimension;
    private int scannedEntityCount;
    private int size;
    private int previousSize;
    private boolean complete;
    private long outboundEpoch;
    private long filterRevision;
    private Set<String> refreshIds = Set.of();

    private UUID[] ids = new UUID[INITIAL_CAPACITY];
    private String[] types = new String[INITIAL_CAPACITY];
    private String[] names = new String[INITIAL_CAPACITY];
    private double[] x = new double[INITIAL_CAPACITY];
    private double[] y = new double[INITIAL_CAPACITY];
    private double[] z = new double[INITIAL_CAPACITY];
    private double[] vx = new double[INITIAL_CAPACITY];
    private double[] vy = new double[INITIAL_CAPACITY];
    private double[] vz = new double[INITIAL_CAPACITY];
    private float[] widths = new float[INITIAL_CAPACITY];
    private float[] heights = new float[INITIAL_CAPACITY];

    @Override
    public void begin(UUID submitPlayerId, String dimension, int scannedEntityCount) {
        clearRetainedReferences();
        this.submitPlayerId = submitPlayerId;
        this.dimension = dimension == null ? "" : dimension;
        this.scannedEntityCount = Math.max(0, scannedEntityCount);
        this.size = 0;
        this.complete = false;
        this.refreshIds = Set.of();
    }

    @Override
    public void accept(
            UUID id,
            double x, double y, double z,
            double vx, double vy, double vz,
            String entityType,
            String customName,
            float width,
            float height) {
        if (id == null) {
            return;
        }
        ensureCapacity(size + 1);
        int index = size++;
        ids[index] = id;
        types[index] = entityType == null ? "" : entityType;
        names[index] = customName;
        this.x[index] = x;
        this.y[index] = y;
        this.z[index] = z;
        this.vx[index] = vx;
        this.vy[index] = vy;
        this.vz[index] = vz;
        widths[index] = width;
        heights[index] = height;
    }

    @Override
    public void finish(int scannedEntityCount) {
        this.scannedEntityCount = Math.max(0, scannedEntityCount);
        complete = true;
        previousSize = size;
    }

    public void prepareSubmission(long outboundEpoch, long filterRevision, Set<String> refreshIds) {
        this.outboundEpoch = outboundEpoch;
        this.filterRevision = filterRevision;
        this.refreshIds = refreshIds == null || refreshIds.isEmpty() ? Set.of() : Set.copyOf(refreshIds);
    }

    public void recycle() {
        clearRetainedReferences();
        submitPlayerId = null;
        dimension = "";
        scannedEntityCount = 0;
        size = 0;
        previousSize = 0;
        complete = false;
        refreshIds = Set.of();
    }

    private void clearRetainedReferences() {
        if (previousSize > 0) {
            Arrays.fill(ids, 0, previousSize, null);
            Arrays.fill(types, 0, previousSize, null);
            Arrays.fill(names, 0, previousSize, null);
        }
    }

    private void ensureCapacity(int required) {
        if (required <= ids.length) {
            return;
        }
        int capacity = Math.max(required, ids.length + (ids.length >> 1));
        ids = Arrays.copyOf(ids, capacity);
        types = Arrays.copyOf(types, capacity);
        names = Arrays.copyOf(names, capacity);
        x = Arrays.copyOf(x, capacity);
        y = Arrays.copyOf(y, capacity);
        z = Arrays.copyOf(z, capacity);
        vx = Arrays.copyOf(vx, capacity);
        vy = Arrays.copyOf(vy, capacity);
        vz = Arrays.copyOf(vz, capacity);
        widths = Arrays.copyOf(widths, capacity);
        heights = Arrays.copyOf(heights, capacity);
    }

    public UUID submitPlayerId() { return submitPlayerId; }
    public String dimension() { return dimension; }
    public int scannedEntityCount() { return scannedEntityCount; }
    public int size() { return size; }
    public boolean complete() { return complete; }
    public long outboundEpoch() { return outboundEpoch; }
    public long filterRevision() { return filterRevision; }
    public Set<String> refreshIds() { return refreshIds; }
    public UUID id(int index) { return ids[index]; }
    public String type(int index) { return types[index]; }
    public String name(int index) { return names[index]; }
    public double x(int index) { return x[index]; }
    public double y(int index) { return y[index]; }
    public double z(int index) { return z[index]; }
    public double vx(int index) { return vx[index]; }
    public double vy(int index) { return vy[index]; }
    public double vz(int index) { return vz[index]; }
    public float width(int index) { return widths[index]; }
    public float height(int index) { return heights[index]; }
}
