package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;

class ResearchTexturePathsTest {
    @Test
    void mapsOnlyTheSevenBuiltinJpegsToTheirGeneratedPngs() {
        for (int index = 1; index <= 7; index++) {
            var source = ResourceLocation.parse("thaumcraft:textures/gui/gui_research_back_" + index + ".jpg");
            assertEquals(ResourceLocation.parse("thaumcraft:textures/gui/gui_research_back_" + index + ".png"),
                ResearchTexturePaths.forRendering(source));
            assertEquals("textures/gui/gui_research_back_" + index + ".jpg", source.getPath());
        }
    }

    @Test
    void leavesNullPngOverlayAddonAndNonBuiltinPathsUnchanged() {
        assertNull(ResearchTexturePaths.forRendering(null));
        for (String path : List.of("thaumcraft:textures/gui/gui_research_back_over.png",
            "thaumcraft:textures/gui/gui_research_back_0.jpg", "thaumcraft:textures/gui/gui_research_back_8.jpg",
            "addon:textures/gui/gui_research_back_1.jpg", "thaumcraft:textures/other.jpg",
            "thaumcraft:textures/gui/gui_research_back_1.png")) {
            var source = ResourceLocation.parse(path);
            assertSame(source, ResearchTexturePaths.forRendering(source));
        }
    }

    @Test
    void neverMutatesThePublicSourceCategoryDefinitions() {
        var categories = ResearchCategoryDefinitions.newBuiltinCategories();
        // Source registration order is not numeric texture order: INFUSION uses background7.
        var sourceNumbers = Map.of("BASICS", 1, "AUROMANCY", 2, "ALCHEMY", 3, "ARTIFICE", 4,
            "INFUSION", 7, "GOLEMANCY", 5, "ELDRITCH", 6);
        for (var category : categories.values()) {
            int index = sourceNumbers.get(category.key);
            var original = category.background;
            assertEquals("textures/gui/gui_research_back_" + index + ".jpg", original.getPath());
            assertEquals("textures/gui/gui_research_back_" + index + ".png",
                ResearchTexturePaths.forRendering(original).getPath());
            assertSame(original, category.background);
        }
        assertEquals(7, categories.size());
    }
}
