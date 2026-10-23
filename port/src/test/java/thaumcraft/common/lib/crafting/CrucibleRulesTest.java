package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CrucibleRulesTest {
    @Test void originalHeatingCoolingAndEmptyTankEdges() {
        assertEquals(151, CrucibleRules.heat((short) 150, true, true));
        assertEquals(200, CrucibleRules.heat((short) 200, true, true));
        assertEquals(149, CrucibleRules.heat((short) 150, true, false));
        assertEquals(149, CrucibleRules.heat((short) 150, false, true));
        assertEquals(0, CrucibleRules.heat((short) 0, false, false));
        assertTrue(CrucibleRules.syncHeat((short) 150, (short) 151, true, true));
        assertFalse(CrucibleRules.syncHeat((short) 151, (short) 150, true, false));
        assertTrue(CrucibleRules.syncHeat((short) 150, (short) 149, true, false));
        assertFalse(CrucibleRules.syncHeat((short) 150, (short) 149, false, false));
    }
    @Test void sourceMutatingLoopConsumesOnlyHalfTheUniformStack() {
        assertEquals(0, CrucibleRules.sourceBatchRemainder(1));
        assertEquals(1, CrucibleRules.sourceBatchRemainder(2));
        assertEquals(1, CrucibleRules.sourceBatchRemainder(3));
        assertEquals(32, CrucibleRules.sourceBatchRemainder(64));
        for (int count = 1; count <= 64; count++) assertEquals(count / 2, CrucibleRules.sourceBatchRemainder(count));
    }
    @Test void sourceHeightRetainsFloatOperationOrderAndUnusualOverflow() {
        assertEquals(.3F, CrucibleRules.fluidHeight(0, 0));
        assertEquals(.8F, CrucibleRules.fluidHeight(1000, 0));
        assertEquals(.9999F, CrucibleRules.fluidHeight(1000, 500));
        assertEquals(1.001F, CrucibleRules.fluidHeight(1000, 501));
        assertEquals(.9F, CrucibleRules.fluidHeight(1000, 250), .000001F);
    }
    @Test void comparatorAndFluxUseOriginalUnclampedArithmetic() {
        assertEquals(0, CrucibleRules.comparator(0));
        assertEquals(1, CrucibleRules.comparator(1));
        assertEquals(8, CrucibleRules.comparator(250));
        assertEquals(15, CrucibleRules.comparator(500));
        assertEquals(29, CrucibleRules.comparator(1000));
        assertEquals(1F, CrucibleRules.decayFlux(true));
        assertEquals(.25F, CrucibleRules.decayFlux(false));
    }
}
