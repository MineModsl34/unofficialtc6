package thaumcraft.common.blocks.misc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceFluidBlockRulesTest {
    @Test void finiteFillsPreserveEight125MillibucketQuantaAndCapacity() {
        for (int existing = 0; existing <= 8; existing++) for (int offered = 0; offered <= 2000; offered++) {
            int filled = SourceFluidBlockRules.finiteFill(existing, offered);
            assertEquals(Math.min(8 - existing, offered / 125) * 125, filled);
            assertTrue(filled <= offered);
            assertTrue(existing * 125 + filled <= 1000);
        }
        assertEquals(0, SourceFluidBlockRules.finiteFill(2, -100));
    }
    @Test void sourceWorldWrappersOnlyDrainWholeCellsAndClassicSources() {
        for (int amount = 1; amount <= 8; amount++) {
            assertEquals(0, SourceFluidBlockRules.finiteDrain(amount, amount * 125 - 1));
            assertEquals(amount * 125, SourceFluidBlockRules.finiteDrain(amount, amount * 125));
            assertEquals(amount * 125, SourceFluidBlockRules.finiteDrain(amount, 2000));
        }
        assertEquals(0, SourceFluidBlockRules.classicDrain(false, 2000));
        assertEquals(0, SourceFluidBlockRules.classicDrain(true, 999));
        assertEquals(1000, SourceFluidBlockRules.classicDrain(true, 1000));
    }
    @Test void preserveUnusualForgeTankDisplaySeparateFromActualFiniteDrain() {
        assertEquals(1000, SourceFluidBlockRules.sourceTankDisplay(8, 8));
        assertEquals(222, SourceFluidBlockRules.sourceTankDisplay(1, 8));
        assertEquals(125, SourceFluidBlockRules.finiteDrain(1, 1000));
        assertEquals(1000, SourceFluidBlockRules.sourceTankDisplay(4, 4));
        assertEquals(800, SourceFluidBlockRules.sourceTankDisplay(3, 4));
        assertThrows(IllegalArgumentException.class, () -> SourceFluidBlockRules.finiteFill(9, 2000));
    }
}
