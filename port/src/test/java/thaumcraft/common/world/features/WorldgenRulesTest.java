package thaumcraft.common.world.features;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorldgenRulesTest {
    @Test
    void normalDensityRetainsClassicAttemptsAndCrystalBudget() {
        assertEquals(51, WorldgenRules.CINNABAR_HEIGHT_BOUND);
        assertEquals(64, WorldgenRules.QUARTZ_HEIGHT_BOUND);
        assertEquals(18, WorldgenRules.scaledAttempts(18, 100));
        assertEquals(20, WorldgenRules.scaledAttempts(20, 100));
        assertEquals(8, WorldgenRules.scaledAttempts(8, 100));
        assertEquals(64, WorldgenRules.scaledAttempts(64, 100));
        assertEquals(9, WorldgenRules.scaledAttempts(18, 50));
        assertEquals(40, WorldgenRules.scaledAttempts(20, 200));
        assertEquals(40, WorldgenRules.scaledAttempts(8, 500));
        assertEquals(320, WorldgenRules.scaledAttempts(64, 500));
    }

    @Test
    void allSupportedDensitiesUseFloatRoundingInsteadOfTruncationOrMinimumOne() {
        for (int density = 1; density <= 500; density++) {
            for (int base : new int[]{18, 20, 8, 64}) {
                assertEquals(Math.round(base * (density / 100.0F)), WorldgenRules.scaledAttempts(base, density),
                    "base " + base + ", density " + density);
            }
        }
        assertEquals(0, WorldgenRules.scaledAttempts(8, 1));
        assertEquals(0, WorldgenRules.scaledAttempts(18, 1));
        assertEquals(1, WorldgenRules.scaledAttempts(64, 1));
        assertEquals(5, WorldgenRules.scaledAttempts(18, 25));
    }
}
