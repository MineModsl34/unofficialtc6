package thaumcraft.client.jei;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.crafting.InfusionCosts;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;

class InfusionDisplayStagesTest {
    @Test void sourceEightEnchantmentsExposeEveryPaidUpgradeAndNoLevelAboveTheirMaximum() {
        List<EnumInfusionEnchantment> source = List.of(EnumInfusionEnchantment.BURROWING, EnumInfusionEnchantment.COLLECTOR,
            EnumInfusionEnchantment.DESTRUCTIVE, EnumInfusionEnchantment.REFINING, EnumInfusionEnchantment.SOUNDING,
            EnumInfusionEnchantment.ARCING, EnumInfusionEnchantment.ESSENCE, EnumInfusionEnchantment.LAMPLIGHT);
        int total = 0;
        for (var enchantment : source) {
            var levels = InfusionDisplayStages.enchantmentLevels(enchantment.maxLevel);
            assertEquals(enchantment.maxLevel, levels.size());
            assertEquals(1, levels.getFirst());
            assertEquals(enchantment.maxLevel, levels.getLast());
            assertEquals(levels.size(), levels.stream().distinct().count());
            assertThrows(UnsupportedOperationException.class, () -> levels.add(0));
            total += levels.size();
        }
        assertEquals(21, total);
    }

    @Test void runicDisplayKeepsAllPositiveSourceCostsIncludingThoseAboveTheBaseRecipeCodecLimit() {
        var charges = InfusionDisplayStages.runicCharges();
        assertEquals(27, charges.size());
        assertEquals(0, charges.getFirst());
        assertEquals(26, charges.getLast());
        for (int index = 0; index < charges.size(); index++) {
            assertEquals(index, charges.get(index));
            assertTrue(InfusionCosts.runicVis(charges.get(index)) > 0);
        }
        assertTrue(InfusionCosts.runicVis(charges.getLast()) > 32767);
        assertTrue(InfusionCosts.runicVis(charges.getLast() + 1) <= 0);
        assertThrows(UnsupportedOperationException.class, () -> charges.add(27));
    }

    @Test void invalidEnchantmentMaximumCannotCreateAnUnboundedOrEmptyProjection() {
        assertThrows(IllegalArgumentException.class, () -> InfusionDisplayStages.enchantmentLevels(0));
        assertThrows(IllegalArgumentException.class, () -> InfusionDisplayStages.enchantmentLevels(-1));
        assertThrows(IllegalArgumentException.class, () -> InfusionDisplayStages.enchantmentLevels(Short.MAX_VALUE + 1));
    }
}
