package fun.prof_chen.teamviewer.main_code.client.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameClientBridgeTest {
    @Test
    void preservesFinitePositiveDistanceWithinHardLimit() {
        assertEquals(16.0D, GameClientBridge.normalizeMarkTargetDistance(16.0D));
        assertEquals(128.5D, GameClientBridge.normalizeMarkTargetDistance(128.5D));
        assertEquals(512.0D, GameClientBridge.normalizeMarkTargetDistance(512.0D));
    }

    @Test
    void clampsDistanceAboveHardLimit() {
        assertEquals(512.0D, GameClientBridge.normalizeMarkTargetDistance(513.0D));
        assertEquals(512.0D, GameClientBridge.normalizeMarkTargetDistance(Double.MAX_VALUE));
    }

    @Test
    void fallsBackToHardLimitForInvalidDistance() {
        assertEquals(512.0D, GameClientBridge.normalizeMarkTargetDistance(0.0D));
        assertEquals(512.0D, GameClientBridge.normalizeMarkTargetDistance(-1.0D));
        assertEquals(512.0D, GameClientBridge.normalizeMarkTargetDistance(Double.NaN));
        assertEquals(512.0D, GameClientBridge.normalizeMarkTargetDistance(Double.POSITIVE_INFINITY));
        assertEquals(512.0D, GameClientBridge.normalizeMarkTargetDistance(Double.NEGATIVE_INFINITY));
    }
}
