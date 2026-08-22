package fun.prof_chen.teamviewer.integration.journeymap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Native renderer policy owned by the JourneyMap integration companion. */
public final class JourneyMapGroupPolicyBridge {
    private static final int MAX_DISTANCE = 10_000;
    private static final Map<String, Policy> POLICIES = new ConcurrentHashMap<>();
    private static volatile boolean supported;

    private JourneyMapGroupPolicyBridge() { }

    public static boolean isSupported() { return supported; }

    static void setSupported(boolean value) {
        supported = value;
        if (!value) POLICIES.clear();
    }

    public static void setPolicy(
            String groupId, boolean renderWorld, boolean rotatingBeam, boolean staticBeam, int maxDistance) {
        if (!supported || groupId == null || groupId.isBlank()) return;
        POLICIES.put(groupId, new Policy(renderWorld, rotatingBeam, staticBeam,
                Math.max(0, Math.min(MAX_DISTANCE, maxDistance))));
    }

    public static void clearPolicies() { POLICIES.clear(); }

    public static boolean anyWorldRenderingEnabled() {
        return supported && POLICIES.values().stream().anyMatch(Policy::renderWorld);
    }

    public static Boolean worldRendering(String groupId) {
        Policy policy = policy(groupId);
        return policy == null ? null : policy.renderWorld();
    }

    public static boolean rotatingBeam(String groupId, boolean fallback) {
        Policy policy = policy(groupId);
        return policy == null ? fallback : policy.rotatingBeam();
    }

    public static boolean staticBeam(String groupId, boolean fallback) {
        Policy policy = policy(groupId);
        return policy == null ? fallback : policy.staticBeam();
    }

    public static int maxDistance(String groupId, int fallback) {
        Policy policy = policy(groupId);
        return policy == null ? fallback : policy.maxDistance();
    }

    private static Policy policy(String groupId) {
        return supported && groupId != null ? POLICIES.get(groupId) : null;
    }

    private record Policy(boolean renderWorld, boolean rotatingBeam, boolean staticBeam, int maxDistance) { }
}
