package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.DyeItemRenameFix;
import net.minecraft.util.datafix.fixes.EntityTheRenameningFix;
import net.minecraft.util.datafix.fixes.ItemStackTheFlatteningFix;
import org.junit.jupiter.api.Test;

class LegacyVanillaScanIdentitiesTest {
    @Test
    void everyDeclaredCorrespondenceAgreesWithNativeFlatteningAndLaterItemRenames() {
        Map<String, String> subsequentOnly = Map.of(
            "minecraft:melon_block", "minecraft:melon", "minecraft:melon", "minecraft:melon_slice",
            "minecraft:speckled_melon", "minecraft:glistering_melon_slice",
            "minecraft:chorus_fruit_popped", "minecraft:popped_chorus_fruit",
            "minecraft:sign", "minecraft:oak_sign", "minecraft:grass_path", "minecraft:dirt_path");
        for (var entry : LegacyVanillaScanIdentities.knownItems().entrySet()) {
            var identity = entry.getValue();
            String flattened = ItemStackTheFlatteningFix.updateItem(identity.itemId().toString(), identity.metadata());
            if (flattened == null) flattened = subsequentOnly.get(identity.itemId().toString());
            assertNotNull(flattened, entry.toString());
            flattened = EntityTheRenameningFix.RENAMED_ITEMS.getOrDefault(flattened, flattened);
            flattened = DyeItemRenameFix.RENAMED_IDS.getOrDefault(flattened, flattened);
            flattened = switch (flattened) {
                case "minecraft:stone_slab" -> "minecraft:smooth_stone_slab";
                case "minecraft:grass" -> "minecraft:short_grass";
                default -> flattened;
            };
            assertEquals(entry.getKey().toString(), flattened, identity.toString());
            assertTrue(identity.sourceProven());
            assertEquals(identity, LegacyScanIdentity.identify(entry.getKey(), 0));
        }
        assertTrue(LegacyVanillaScanIdentities.knownItems().size() > 280);
    }

    @Test
    void bannerDamageIsReversedWhileOtherColoredFamiliesKeepBlockMetadata() {
        assertKey("white_banner", "banner15");
        assertKey("black_banner", "banner0");
        assertKey("red_banner", "banner1");
        assertKey("white_wool", "wool0");
        assertKey("red_wool", "wool14");
        assertKey("light_gray_terracotta", "stained_hardened_clay8");
        assertKey("red_bed", "bed14");
    }

    @Test
    void dyeIngredientIdentityIsNotTheNewDyeItemAndAmbiguousInverseStaysUnproven() {
        assertKey("ink_sac", "dye0");
        assertKey("lapis_lazuli", "dye4");
        assertKey("bone_meal", "dye15");
        assertKey("red_dye", "dye1");
        for (String modern : new String[] {"black_dye", "white_dye", "blue_dye", "brown_dye", "dead_bush", "netherite_ingot", "stone_slab", "piglin_head", "cherry_planks"}) {
            assertFalse(LegacyScanIdentity.identify(ResourceLocation.withDefaultNamespace(modern), 0).sourceProven(), modern);
        }
    }

    @Test
    void laterRenamesAndItemVersusBlockNameCollisionsRetainOriginalItemIds() {
        assertKey("melon", "melon_block0");
        assertKey("melon_slice", "melon0");
        assertKey("snow_block", "snow0");
        assertKey("snow", "snow_layer0");
        assertKey("nether_bricks", "nether_brick0");
        assertKey("nether_brick", "netherbrick0");
        assertKey("tropical_fish", "fish2");
        assertKey("smooth_stone_slab", "stone_slab0");
        assertKey("oak_sign", "sign0");
        assertKey("dirt_path", "grass_path0");
        assertKey("music_disc_13", "record_130");
    }

    @Test
    void vanillaCorrespondencesAreImmutableAndNamespaceSensitive() {
        assertThrows(UnsupportedOperationException.class, () -> LegacyVanillaScanIdentities.knownItems().clear());
        assertFalse(LegacyScanIdentity.identify(ResourceLocation.parse("addon:red_wool"), 0).sourceProven());
        var fallback = LegacyScanIdentity.identify(ResourceLocation.parse("addon:iron_sword"), 23);
        assertEquals(23, fallback.metadata());
        assertEquals("!addon:iron_sword23", LegacyScanIdentity.researchKey(fallback, false));
    }

    private static void assertKey(String modern, String legacyKey) {
        assertEquals("!minecraft:" + legacyKey,
            LegacyScanIdentity.researchKey(LegacyScanIdentity.identify(ResourceLocation.withDefaultNamespace(modern), 0), false));
    }
}
