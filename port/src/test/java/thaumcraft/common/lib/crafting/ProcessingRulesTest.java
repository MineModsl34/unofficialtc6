package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProcessingRulesTest {
    @Test void infernalSourceBellowsCurveAndSpeedyVis() {
        assertArrayEquals(new int[]{140,120,102,86,72},java.util.stream.IntStream.rangeClosed(0,4).map(b -> ProcessingRules.infernalCookTime(false,b)).toArray());
        assertArrayEquals(new int[]{80,60,42,26,12},java.util.stream.IntStream.rangeClosed(0,4).map(b -> ProcessingRules.infernalCookTime(true,b)).toArray());
        assertEquals(12,ProcessingRules.infernalCookTime(true,20));
    }
    @Test void bonusInclusiveVersusVentStrictAndSequentialTrials() {
        assertTrue(ProcessingRules.bonus(.33F,.33F)); assertFalse(ProcessingRules.bonus(Math.nextUp(.33F),.33F));
        assertFalse(ProcessingRules.vented(3,() -> .333));
        AtomicInteger calls = new AtomicInteger();
        assertTrue(ProcessingRules.vented(3,() -> calls.incrementAndGet() == 2 ? .2 : .9)); assertEquals(2,calls.get());
    }
    @Test void vanillaFurnaceBellowsSourceBoundsAreNotOtherFurnaceTypes() {
        assertEquals(0,ProcessingRules.vanillaBellowsProgress(0)); assertEquals(2,ProcessingRules.vanillaBellowsProgress(1));
        assertEquals(199,ProcessingRules.vanillaBellowsProgress(198)); assertEquals(199,ProcessingRules.vanillaBellowsProgress(199));
        assertEquals(200,ProcessingRules.vanillaBellowsProgress(200));
        assertEquals(79,ProcessingRules.vanillaBellowsProgress(79,80));
        assertEquals(79,ProcessingRules.vanillaBellowsProgress(78,80));
    }
    @Test void sourceXpAboveOneDoesNotMultiplyOutputAndFractionsRoundStochastically() {
        assertEquals(0,ProcessingRules.experience(8,0,() -> 0)); assertEquals(8,ProcessingRules.experience(8,2,() -> 0));
        assertEquals(1,ProcessingRules.experience(3,.5F,() -> .5)); assertEquals(2,ProcessingRules.experience(3,.5F,() -> .49));
    }
}
