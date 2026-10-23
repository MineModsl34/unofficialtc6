package thaumcraft.common.blocks.misc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeathFluidRulesTest {
    @Test void exactFourQuantaMetadataAndCollisionVectors() {
        for (int metadata = 0; metadata < 4; metadata++) {
            assertEquals(4 - metadata, DeathFluidRules.quanta(metadata));
            assertEquals(metadata, DeathFluidRules.metadata(4 - metadata));
            assertEquals(1 - (4 - metadata) / 8F, DeathFluidRules.slowdown(metadata));
            assertEquals(5 - metadata, DeathFluidRules.dissolveDamage(metadata));
            assertEquals((4 - metadata) / 5F, DeathFluidRules.renderHeight(metadata));
        }
        assertEquals(7, DeathFluidRules.TICK_RATE);
    }
    @Test void sourceClassicDecayAndVerticalFeedAreNotVanillaEightQuanta() {
        assertEquals(3, DeathFluidRules.expectedQuanta(true, -100));
        for (int amount = 1; amount <= 4; amount++) assertEquals(amount - 1, DeathFluidRules.expectedQuanta(false, amount));
        assertEquals(-101, DeathFluidRules.expectedQuanta(false, -100));
        for (int amount = 1; amount <= 4; amount++) {
            assertEquals(5 - amount, DeathFluidRules.outwardMetadata(amount, false));
            assertEquals(1, DeathFluidRules.outwardMetadata(amount, true));
        }
    }
    @Test void rejectImpossibleFluidAmountsRatherThanSilentlyProducingWaterMetadata() {
        for (int invalid : new int[]{-1, 4, 8, 15}) assertThrows(IllegalArgumentException.class, () -> DeathFluidRules.quanta(invalid));
        for (int invalid : new int[]{-1, 0, 5, 8}) assertThrows(IllegalArgumentException.class, () -> DeathFluidRules.metadata(invalid));
    }
    @Test void sourceDownwardCurrentExcludesAttachmentExceptionsAndOnlySourceIceMaterial() {
        for (boolean fluid : new boolean[]{false, true}) for (boolean ice : new boolean[]{false, true})
            for (boolean exception : new boolean[]{false, true}) for (boolean stairs : new boolean[]{false, true}) for (boolean face : new boolean[]{false, true})
                assertEquals(!fluid && !ice && !exception && !stairs && face, DeathFluidRules.downwardCurrent(fluid, ice, exception, stairs, face));
        assertFalse(DeathFluidRules.downwardCurrent(false, false, true, false, true)); // glass/piston
        assertTrue(DeathFluidRules.downwardCurrent(false, false, false, false, true)); // packed ice is not Material.ICE
    }
}
