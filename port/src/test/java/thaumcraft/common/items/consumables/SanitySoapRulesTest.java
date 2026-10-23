package thaumcraft.common.items.consumables;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SanitySoapRulesTest {
    @Test void completionHasStrictSourceThreshold() {
        for (int remaining = 100; remaining >= 5; remaining--) assertFalse(SanitySoapRules.completes(remaining));
        for (int remaining = 4; remaining >= 0; remaining--) assertTrue(SanitySoapRules.completes(remaining));
        assertEquals(100, SanitySoapRules.USE_DURATION);
    }
    @Test void wardAndCurrentPurifyingBlockEachAddOne() {
        assertEquals(1, SanitySoapRules.normalReduction(false, false));
        assertEquals(2, SanitySoapRules.normalReduction(true, false));
        assertEquals(2, SanitySoapRules.normalReduction(false, true));
        assertEquals(3, SanitySoapRules.normalReduction(true, true));
    }
}
