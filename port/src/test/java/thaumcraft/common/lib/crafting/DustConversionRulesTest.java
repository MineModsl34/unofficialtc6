package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DustConversionRulesTest {
    @Test void fiftyDecrementsMutateOnlyOnTheFiftyFirstEnd() {
        var timer = new DustConversionRules.Countdown(DustConversionRules.DELAY_DECREMENTS);
        for (int tick = 1; tick <= 50; tick++) assertFalse(timer.readyOnEnd(), "source timer prematurely ready at END" + tick);
        assertTrue(timer.readyOnEnd());
    }
    @Test void zeroPriorityIsNextEndAndNegativeOrUnboundedTimersAreRejected() {
        assertTrue(new DustConversionRules.Countdown(0).readyOnEnd());
        assertThrows(IllegalArgumentException.class, () -> new DustConversionRules.Countdown(-1));
        assertThrows(IllegalArgumentException.class, () -> new DustConversionRules.Countdown(12001));
    }
    @Test void modernSplitOnlyMapsTheOldEmptyAndWaterCauldrons() {
        assertTrue(DustConversionRules.sourceCauldron(true, false));
        assertTrue(DustConversionRules.sourceCauldron(false, true));
        assertFalse(DustConversionRules.sourceCauldron(false, false), "lava/powder/noncauldron are not the original water LEVEL property");
    }
}
