package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class LegacyEnchantmentScanNamesTest {
    @Test
    void allThirtyNamesAndOriginalOrderAreExplicitRatherThanDerivedFromModernDescriptions() {
        assertEquals(List.of(
            "protection=protect.all", "fire_protection=protect.fire", "feather_falling=protect.fall",
            "blast_protection=protect.explosion", "projectile_protection=protect.projectile",
            "respiration=oxygen", "aqua_affinity=waterWorker", "thorns=thorns",
            "depth_strider=waterWalker", "frost_walker=frostWalker", "binding_curse=binding_curse",
            "sharpness=damage.all", "smite=damage.undead", "bane_of_arthropods=damage.arthropods",
            "knockback=knockback", "fire_aspect=fire", "looting=lootBonus", "sweeping=sweeping",
            "efficiency=digging", "silk_touch=untouching", "unbreaking=durability", "fortune=lootBonusDigger",
            "power=arrowDamage", "punch=arrowKnockback", "flame=arrowFire", "infinity=arrowInfinite",
            "luck_of_the_sea=lootBonusFishing", "lure=fishingSpeed", "mending=mending",
            "vanishing_curse=vanishing_curse"
        ), LegacyEnchantmentScanNames.VANILLA.stream()
            .map(d -> d.originalLocation().getPath() + "=" + d.legacyName().substring("enchantment.".length())).toList());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 16, 17, 18, 19, 20, 21, 22,
            32, 33, 34, 35, 48, 49, 50, 51, 61, 62, 70, 71),
            LegacyEnchantmentScanNames.VANILLA.stream().map(LegacyEnchantmentScanNames.Descriptor::originalId).toList());
    }

    @Test
    void onlySweepingRegistryIdentityChangesAndAllTargetsAreUnique() {
        assertEquals(30, LegacyEnchantmentScanNames.VANILLA.stream().map(d -> d.target().location()).distinct().count());
        for (var descriptor : LegacyEnchantmentScanNames.VANILLA) {
            assertEquals("minecraft", descriptor.originalLocation().getNamespace());
            assertTrue(descriptor.target().isFor(Registries.ENCHANTMENT));
            String modern = descriptor.originalLocation().getPath().equals("sweeping") ? "sweeping_edge" : descriptor.originalLocation().getPath();
            assertEquals(ResourceLocation.withDefaultNamespace(modern), descriptor.target().location());
            assertEquals("!" + descriptor.legacyName(), descriptor.researchKey());
            assertEquals(descriptor.researchKey(), descriptor.scanner().getResearchKey(null, null));
        }
    }

    @Test
    void descriptorsCannotBeMutatedAndFactoriesDoNotRetainHandlerIdentity() {
        assertThrows(UnsupportedOperationException.class, () -> LegacyEnchantmentScanNames.VANILLA.clear());
        var descriptor = LegacyEnchantmentScanNames.VANILLA.getFirst();
        var first = descriptor.scanner();
        var second = descriptor.scanner();
        assertNotSame(first, second);
        assertSame(descriptor.target(), first.target());
        assertEquals(descriptor.legacyName(), first.legacyName());
        assertFalse(first.checkThing(null, null));
    }

    @Test
    void explicitAddonNameIsOpaqueAndNoRegistryLookupOrModernNameGuessingOccurs() {
        var key = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse("addon:not_registered"));
        var scanner = new ScanEnchantment(key, "addon.Mixed Case@legacy");
        assertSame(key, scanner.target());
        assertEquals("!addon.Mixed Case@legacy", scanner.getResearchKey(null, null));
        assertEquals("!", new ScanEnchantment(key, "").getResearchKey(null, null));
        assertThrows(NullPointerException.class, () -> new ScanEnchantment(null, "name"));
        assertThrows(NullPointerException.class, () -> new ScanEnchantment(key, null));
    }
}
