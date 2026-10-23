package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class LegacyPotionScanNamesTest {
    @Test
    void allTwentySevenSourceNamesAreExactAndOrderedByOldPotionRegistry() {
        List<String> ids = List.of("speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health", "instant_damage",
            "jump_boost", "nausea", "regeneration", "resistance", "fire_resistance", "water_breathing", "invisibility",
            "blindness", "night_vision", "hunger", "weakness", "poison", "wither", "health_boost", "absorption", "saturation",
            "glowing", "levitation", "luck", "unluck");
        List<String> names = List.of("moveSpeed", "moveSlowdown", "digSpeed", "digSlowDown", "damageBoost", "heal", "harm",
            "jump", "confusion", "regeneration", "resistance", "fireResistance", "waterBreathing", "invisibility",
            "blindness", "nightVision", "hunger", "weakness", "poison", "wither", "healthBoost", "absorption", "saturation",
            "glowing", "levitation", "luck", "unluck");
        assertEquals(27, LegacyPotionScanNames.vanillaNamesById().size());
        assertEquals(ids.stream().map(ResourceLocation::withDefaultNamespace).toList(),
            List.copyOf(LegacyPotionScanNames.vanillaNamesById().keySet()));
        for (int index = 0; index < ids.size(); index++) {
            var name = LegacyPotionScanNames.resolve(ResourceLocation.withDefaultNamespace(ids.get(index)), "WRONG_MODERN_NAME");
            assertEquals("!effect." + names.get(index), name.researchKey());
            assertTrue(name.sourceProven());
        }
    }

    @Test
    void modernExtraAndAddonFallbackNamesRemainOpaqueAndExplicitlyUnproven() {
        for (var id : List.of(ResourceLocation.parse("minecraft:slow_falling"), ResourceLocation.parse("addon:speed"))) {
            var name = LegacyPotionScanNames.resolve(id, "effect.AddOn_MiXeD");
            assertEquals("!effect.AddOn_MiXeD", name.researchKey());
            assertFalse(name.sourceProven());
        }
    }

    @Test
    void mappingIsImmutableAndMissingFallbackFailsInsteadOfInventingAName() {
        assertThrows(UnsupportedOperationException.class, () -> LegacyPotionScanNames.vanillaNamesById().clear());
        assertThrows(NullPointerException.class, () -> LegacyPotionScanNames.resolve(ResourceLocation.parse("addon:missing"), null));
        assertEquals("!effect.moveSpeed", LegacyPotionScanNames.resolve(ResourceLocation.parse("minecraft:speed"), null).researchKey());
    }
}
