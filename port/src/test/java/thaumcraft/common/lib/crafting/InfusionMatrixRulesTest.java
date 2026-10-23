package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static thaumcraft.common.lib.crafting.InfusionMatrixRules.*;
import org.junit.jupiter.api.Test;

class InfusionMatrixRulesTest {
    @Test void successfulComponentLookupBeginsThenConsumesOnlyOnTheSixthMatchingCycle() {
        int remaining = 0;
        for (int cycle = 1; cycle <= 6; cycle++) {
            var step = component(remaining); remaining = step.remaining();
            assertEquals(cycle == 1, step.begin()); assertEquals(cycle == 6, step.consume());
        }
        assertEquals(0, remaining); assertTrue(component(remaining).begin());
    }
    @Test void stabilityUsesTheOriginalIntegerHalfCapAndInclusiveBoundaries() {
        assertEquals(Stability.STABLE, stability(12, 25));
        assertEquals(Stability.VERY_STABLE, stability(12.01F, 25));
        assertEquals(Stability.STABLE, stability(0, 25));
        assertEquals(Stability.UNSTABLE, stability(-.01F, 25));
        assertEquals(Stability.UNSTABLE, stability(-24.99F, 25));
        assertEquals(Stability.VERY_UNSTABLE, stability(-25, 25));
    }
    @Test void lossIsCalculatedBeforeReplenishmentAndThenClamped() {
        assertEquals(2F, lossPerCycle(10, 25, 25));
        assertEquals(10F / 6F, lossPerCycle(10, 0, 25));
        assertEquals(10F / 7F, lossPerCycle(10, -1, 25));
        assertEquals(10F / 8F, lossPerCycle(10, -25, 25));
        assertEquals(12.5F, craftStability(13, 25, 10, .5F, .5F));
        assertEquals(-100F, craftStability(-99, 25, 100, -5, 1));
        assertEquals(25F, craftStability(24, 25, 0, 5, 1));
    }
    @Test void idleRecoveryHasItsSourceMinimumEvenForAsymmetricLayouts() {
        assertEquals(.1F, idleStability(0, 25, -10));
        assertEquals(3F, idleStability(0, 25, 3));
        assertEquals(25F, idleStability(24.99F, 25, .1F));
    }
    @Test void mishapThresholdIsInclusiveAndInvalidInputIsUnconditional() {
        assertTrue(mishap(false, 25, 1499));
        assertFalse(mishap(true, 0, 0));
        assertTrue(mishap(true, -10, 10));
        assertFalse(mishap(true, -10, 11));
    }
    @Test void allTwentyFourSourceRollsRetainTheirDifferentConsequences() {
        Mishap[] expected = {Mishap.EJECT, Mishap.EJECT, Mishap.EJECT, Mishap.EJECT,
            Mishap.WARP, Mishap.WARP, Mishap.WARP, Mishap.ZAP_ONE, Mishap.ZAP_ONE, Mishap.ZAP_ONE,
            Mishap.ZAP_ALL, Mishap.ZAP_ALL, Mishap.EJECT_GOO, Mishap.EJECT_GOO, Mishap.EJECT_FLUX,
            Mishap.EJECT_FLUX, Mishap.EJECT_GOO_3, Mishap.DESTROY_FLUX, Mishap.HARM_ONE, Mishap.HARM_ONE,
            Mishap.EJECT_EXPLOSION, Mishap.EJECT_EXPLOSION, Mishap.HARM_ALL, Mishap.EXPLOSION};
        for (int roll = 0; roll < 24; roll++) assertEquals(expected[roll], mishap(roll), "source roll" + roll);
        assertThrows(IllegalArgumentException.class, () -> mishap(-1));
        assertThrows(IllegalArgumentException.class, () -> mishap(24));
    }
    @Test void diminishingReturnsCountPairsNotIndividualCandles() {
        assertEquals(.1F, diminishing(.1F, 0));
        assertEquals(.075F, diminishing(.1F, 1), .0000001F);
        assertEquals(.05625F, diminishing(.1F, 2), .0000001F);
    }
    @Test void completeFoundationAndAllFourUpgradesApplyInSourceOrder() {
        var speed = new Upgrade[]{Upgrade.SPEED, Upgrade.SPEED, Upgrade.SPEED, Upgrade.SPEED};
        var ancient = modifiers(Foundation.ANCIENT, speed, new Pedestal[]{Pedestal.ANCIENT, Pedestal.ELDRITCH}, .2F);
        assertEquals(5, ancient.cycleTime()); assertEquals(2, ancient.countDelay());
        assertEquals(.9325F, ancient.cost(), .000001F); assertEquals(.1F, ancient.replenish(), .000001F);
        var eldritch = modifiers(Foundation.ELDRITCH, speed, new Pedestal[0], 0);
        assertEquals(3, eldritch.cycleTime()); assertEquals(1, eldritch.countDelay());
        assertEquals(1.09F, eldritch.cost(), .000001F); assertEquals(.2F, eldritch.replenish());
        var cost = modifiers(Foundation.ARCANE_OR_MIXED, new Upgrade[]{Upgrade.COST, Upgrade.COST, Upgrade.COST, Upgrade.COST}, new Pedestal[0], 0);
        assertEquals(14, cost.cycleTime()); assertEquals(7, cost.countDelay()); assertEquals(.92F, cost.cost(), .000001F);
    }
    @Test void costsAndReplacementWearTruncateRatherThanRound() {
        assertEquals(0, cost(1, .49F)); assertEquals(2, cost(5, .5F)); assertEquals(3, cost(3, 1.09F));
        assertEquals(66, damage(1, 3, 200)); assertEquals(0, damage(0, 100, 200));
    }
    @Test void legacyPositionKeysPreserveSignAndOldHashSetOrdering() {
        assertEquals(1L << 26, sourcePosition(0, 1, 0));
        assertEquals(1L, sourcePosition(0, 0, 1));
        assertEquals(1L << 38, sourcePosition(1, 0, 0));
        int[][] values = {{-30000000, -64, 30000000}, {30000000, 320, -30000000}, {-1, -1, -1}, {0, 0, 0}, {1, 2047, 1}, {-1, -2048, -1}};
        for (int[] position : values) {
            long key = sourcePosition(position[0], position[1], position[2]);
            assertEquals(position[0], sourceX(key)); assertEquals(position[1], sourceY(key)); assertEquals(position[2], sourceZ(key));
        }
    }
}
