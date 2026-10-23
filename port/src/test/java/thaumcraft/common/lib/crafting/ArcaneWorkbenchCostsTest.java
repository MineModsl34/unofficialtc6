package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArcaneWorkbenchCostsTest {
    @Test void shapedAndShapelessCostsHaveTheSameBoundedPrimalVector() {
        assertDoesNotThrow(() -> ArcaneWorkbenchCosts.validateRecipeCosts("", "FIRSTSTEPS@2", 20, List.of(1, 1, 1, 1, 1, 1)));
        assertDoesNotThrow(() -> ArcaneWorkbenchCosts.validateRecipeCosts("", "UNLOCKINFUSION", 5, List.of(0, 0, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> ArcaneWorkbenchCosts.validateRecipeCosts("", "", -1, List.of(0, 0, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> ArcaneWorkbenchCosts.validateRecipeCosts("", "", 0, List.of(0, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> ArcaneWorkbenchCosts.validateRecipeCosts("", "", 0, List.of(0, 0, 0, 0, 0, 65)));
        assertThrows(IllegalArgumentException.class, () -> ArcaneWorkbenchCosts.validateRecipeCosts("", "", 0, Arrays.asList(0, 0, 0, 0, 0, null)));
        assertThrows(IllegalArgumentException.class, () -> ArcaneWorkbenchCosts.validateRecipeCosts("x".repeat(257), "", 0, List.of(0, 0, 0, 0, 0, 0)));
    }
    @Test void combinesSourceIntegerGearAndMaximumExhaustionBeforeDivision() {
        for (int gear : new int[]{0, 5, 29, 40, 100, 150}) for (int first = -1; first < 6; first++) for (int second = -1; second < 6; second++) {
            int original = gear;
            if (first >= 0 || second >= 0) original -= (Math.max(Math.max(0, first), Math.max(0, second)) + 1) * 10;
            assertEquals(Float.floatToIntBits(original / 100F), Float.floatToIntBits(ArcaneWorkbenchCosts.discount(gear, first, second)));
        }
    }
    @Test void sourceVisUsesTruncationNotRoundingOrAnInventedMinimum() {
        assertEquals(20, ArcaneWorkbenchCosts.visCost(20, 0));
        assertEquals(19, ArcaneWorkbenchCosts.visCost(20, .05F));
        assertEquals(22, ArcaneWorkbenchCosts.visCost(20, -.1F));
        assertEquals(0, ArcaneWorkbenchCosts.visCost(20, 1F));
        assertEquals(-10, ArcaneWorkbenchCosts.visCost(20, 1.5F));
    }
}
