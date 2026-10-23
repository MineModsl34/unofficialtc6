package thaumcraft.common.items.curios;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import thaumcraft.api.items.IScribeTools;
import thaumcraft.common.items.tools.ItemScribingTools;

/** Descriptor/component structure only; no item-registry bootstrap or translated client rendering. */
class ItemCelestialNotesTest {
    @Test
    void allThirteenMetadataValuesKeepOriginalVariantOrder() {
        List<String> expected = List.of("sun", "stars_1", "stars_2", "stars_3", "stars_4",
            "moon_1", "moon_2", "moon_3", "moon_4", "moon_5", "moon_6", "moon_7", "moon_8");
        assertEquals(13, ItemCelestialNotes.VARIANT_COUNT);
        assertEquals(expected, ItemCelestialNotes.VARIANTS);
        for (int metadata = 0; metadata < expected.size(); metadata++) {
            assertEquals(expected.get(metadata), ItemCelestialNotes.variantName(metadata));
        }
        assertThrows(UnsupportedOperationException.class, () -> ItemCelestialNotes.VARIANTS.clear());
    }

    @Test
    void textureSuffixesKeepOriginalOverlayFilenamesInsteadOfVariantUnderscores() {
        List<String> expected = List.of("sun", "stars1", "stars2", "stars3", "stars4",
            "moon1", "moon2", "moon3", "moon4", "moon5", "moon6", "moon7", "moon8");
        for (int metadata = 0; metadata < expected.size(); metadata++) {
            assertEquals(expected.get(metadata), ItemCelestialNotes.textureSuffix(metadata));
        }
    }

    @Test
    void eachVariantUsesSharedOldNameAndExactAquamarineTooltipKeyWithoutClientTranslation() {
        assertEquals("item.celestial_notes", ItemCelestialNotes.DESCRIPTION_ID);
        for (int metadata = 0; metadata < ItemCelestialNotes.VARIANT_COUNT; metadata++) {
            var tooltip = ItemCelestialNotes.variantTooltip(metadata);
            var contents = assertInstanceOf(TranslatableContents.class, tooltip.getContents());
            assertEquals("item.celestial_notes." + ItemCelestialNotes.variantName(metadata) + ".text", contents.getKey());
            assertEquals(0, contents.getArgs().length);
            assertEquals(ChatFormatting.AQUA.getColor().intValue(), tooltip.getStyle().getColor().getValue());
        }
    }

    @Test
    void invalidMetadataNeverSilentlySelectsSunOrClampsToLastVariant() {
        for (int metadata : new int[] {-1, 13, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> ItemCelestialNotes.variantName(metadata));
            assertThrows(IllegalArgumentException.class, () -> ItemCelestialNotes.textureSuffix(metadata));
            assertThrows(IllegalArgumentException.class, () -> ItemCelestialNotes.variantTooltip(metadata));
        }
    }

    @Test
    void writingToolKeepsSourceMarkerAndHundredPointDurabilityContract() {
        assertEquals(100, ItemScribingTools.MAX_DAMAGE);
        assertTrue(IScribeTools.class.isAssignableFrom(ItemScribingTools.class));
        assertEquals(0, IScribeTools.class.getDeclaredMethods().length, "source interface is only a marker");
    }
}
