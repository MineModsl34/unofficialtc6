package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

class InfusionCostsTest {
    @Test void sourceOtherEnchantmentPenaltyIsFloatPointThirtyThreeAndTruncatesEveryAspect() {
        AspectList base = new AspectList().add(Aspect.SENSES, 80).add(Aspect.EARTH, 150);
        for (int next = 1; next <= 5; next++) for (int others = 0; others <= 12; others++) {
            AspectList actual = InfusionCosts.enchanted(base, next, others);
            float source = next + others * .33F;
            assertEquals((int)(80 * source), actual.getAmount(Aspect.SENSES));
            assertEquals((int)(150 * source), actual.getAmount(Aspect.EARTH));
        }
        assertEquals(80, base.getAmount(Aspect.SENSES));
        assertEquals(150, base.getAmount(Aspect.EARTH));
    }
    @Test void runicChargeCostsDoubleAndRetainOriginalSignedIntegerOverflowForSafetyGate() {
        assertEquals(40, InfusionCosts.runicVis(0)); assertEquals(60, InfusionCosts.runicVis(1)); assertEquals(100, InfusionCosts.runicVis(2));
        for (int charge = -128; charge <= 127; charge++) {
            assertEquals(20 + (int)(20.0 * Math.pow(2.0, charge)), InfusionCosts.runicVis(charge));
            assertEquals(5 + charge / 2, InfusionCosts.runicInstability(charge));
        }
        assertTrue(InfusionCosts.runicVis(26) > 0);
        assertTrue(InfusionCosts.runicVis(27) < 0);
    }
    @Test void extraWarpChanceCountsExistingEntriesEvenDuplicateIdsAndAlwaysRollsTen() {
        for (int entries = 0; entries <= 13; entries++) {
            int successes = 0;
            for (int roll = 0; roll < 10; roll++) if (InfusionCosts.extraWarp(entries, roll)) successes++;
            assertEquals(Math.min(10, entries), successes);
        }
    }
}
