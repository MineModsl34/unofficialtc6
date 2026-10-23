package thaumcraft.common.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;

/** Synthetic isolated catalog only: no resource copying, parser, runtime hook or player progression. */
class ConfigResearchTest {
    private LinkedHashMap<String, ResearchCategory> previousCatalog;

    @BeforeEach
    void isolateCatalog() {
        previousCatalog = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
    }

    @AfterEach
    void restoreCatalog() {
        ResearchCategories.researchCategories = previousCatalog;
    }

    @Test
    void classMetadataDoesNotInitializeTheGlobalCatalog() {
        assertEquals(8, ConfigResearch.BUILTIN_RESEARCH_LOCATIONS.size());
        assertEquals(7, ConfigResearch.TCCategories.length);
        assertTrue(ResearchCategories.researchCategories.isEmpty());
    }

    @Test
    void categoryAndResourceArrayOrdersRetainTheirSourceDifference() {
        assertArrayEquals(new String[]{"BASICS", "ALCHEMY", "AUROMANCY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH"},
            ConfigResearch.TCCategories);
        ConfigResearch.initCategories();
        assertEquals(List.of("BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH"),
            List.copyOf(ResearchCategories.researchCategories.keySet()));
    }

    @Test
    void allSourceFormulaWeightsAndInsertionOrdersAreExact() {
        ConfigResearch.initCategories();
        assertFormula("BASICS", new Aspect[]{Aspect.PLANT, Aspect.ORDER, Aspect.ENTROPY, Aspect.AIR, Aspect.FIRE, Aspect.EARTH, Aspect.WATER},
            new int[]{5, 5, 5, 5, 5, 3, 5});
        assertFormula("AUROMANCY", new Aspect[]{Aspect.AURA, Aspect.MAGIC, Aspect.FLUX, Aspect.CRYSTAL, Aspect.COLD, Aspect.AIR},
            new int[]{20, 20, 15, 5, 5, 5});
        assertFormula("ALCHEMY", new Aspect[]{Aspect.ALCHEMY, Aspect.FLUX, Aspect.MAGIC, Aspect.LIFE, Aspect.AVERSION, Aspect.DESIRE, Aspect.WATER},
            new int[]{30, 10, 10, 5, 5, 5, 5});
        assertFormula("ARTIFICE", new Aspect[]{Aspect.MECHANISM, Aspect.CRAFT, Aspect.METAL, Aspect.TOOL, Aspect.ENERGY, Aspect.LIGHT,
            Aspect.FLIGHT, Aspect.TRAP, Aspect.FIRE}, new int[]{10, 10, 10, 10, 10, 5, 5, 5, 5});
        assertFormula("INFUSION", new Aspect[]{Aspect.MAGIC, Aspect.PROTECT, Aspect.TOOL, Aspect.FLUX, Aspect.CRAFT, Aspect.SOUL, Aspect.EARTH},
            new int[]{30, 10, 10, 5, 5, 5, 3});
        assertFormula("GOLEMANCY", new Aspect[]{Aspect.MAN, Aspect.MOTION, Aspect.MIND, Aspect.MECHANISM, Aspect.EXCHANGE, Aspect.SENSES,
            Aspect.BEAST, Aspect.ORDER}, new int[]{20, 10, 10, 10, 5, 5, 5, 5});
        assertFormula("ELDRITCH", new Aspect[]{Aspect.ELDRITCH, Aspect.DARKNESS, Aspect.MAGIC, Aspect.MIND, Aspect.VOID, Aspect.DEATH,
            Aspect.UNDEAD, Aspect.ENTROPY}, new int[]{20, 10, 5, 5, 5, 5, 5, 5});
    }

    @Test
    void unlockKeysResourcesEmptyEntriesAndDefaultBoundsMatchSource() {
        ConfigResearch.initCategories();
        assertCategory("BASICS", null, "textures/items/thaumonomicon_cheat.png", 1);
        assertCategory("AUROMANCY", "UNLOCKAUROMANCY", "textures/research/cat_auromancy.png", 2);
        assertCategory("ALCHEMY", "UNLOCKALCHEMY", "textures/research/cat_alchemy.png", 3);
        assertCategory("ARTIFICE", "UNLOCKARTIFICE", "textures/research/cat_artifice.png", 4);
        assertCategory("INFUSION", "UNLOCKINFUSION", "textures/research/cat_infusion.png", 7);
        assertCategory("GOLEMANCY", "UNLOCKGOLEMANCY", "textures/research/cat_golemancy.png", 5);
        assertCategory("ELDRITCH", "UNLOCKELDRITCH", "textures/research/cat_eldritch.png", 6);
        assertNull(ResearchCategories.getResearch("FIRSTSTEPS"));
        assertNull(ResearchCategories.getResearch("UNLOCKAUROMANCY"));
    }

    @Test
    void defaultLocationListAndExplicitRegistrationBridgeLoadNoEntries() {
        List<ResourceLocation> expected = List.of(tc("research/basics"), tc("research/alchemy"), tc("research/auromancy"),
            tc("research/artifice"), tc("research/infusion"), tc("research/golemancy"), tc("research/eldritch"), tc("research/scans"));
        assertEquals(expected, ConfigResearch.BUILTIN_RESEARCH_LOCATIONS);
        assertThrows(UnsupportedOperationException.class, () -> ConfigResearch.BUILTIN_RESEARCH_LOCATIONS.add(tc("research/extra")));
        List<ResourceLocation> registered = new ArrayList<>();
        ConfigResearch.registerResourceLocations(registered::add);
        assertEquals(expected, registered);
        assertTrue(ResearchCategories.researchCategories.isEmpty());
        assertTrue(registered.stream().allMatch(location -> !location.getPath().endsWith(".json")));
    }

    @Test
    void reinitializationDoesNotReplaceAddonCategoriesFormulasEntriesOrBounds() {
        AspectList addonFormula = new AspectList().add(Aspect.AIR, 99);
        ResearchCategory addon = ResearchCategories.registerCategory("ADDON", "ADDON_GATE", null, null, null);
        ResearchCategory existingBasics = ResearchCategories.registerCategory("BASICS", "CUSTOM_BASIC_GATE", addonFormula, null, null);
        ResearchEntry entry = new ResearchEntry();
        entry.setKey("ADDON_ENTRY");
        existingBasics.research.put(entry.getKey(), entry);
        existingBasics.minDisplayColumn = -9;
        ConfigResearch.initCategories();
        var first = new LinkedHashMap<>(ResearchCategories.researchCategories);
        ConfigResearch.initCategories();
        assertEquals(List.of("ADDON", "BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH"),
            List.copyOf(ResearchCategories.researchCategories.keySet()));
        for (String key : first.keySet()) assertSame(first.get(key), ResearchCategories.getResearchCategory(key));
        assertSame(addon, ResearchCategories.getResearchCategory("ADDON"));
        assertSame(existingBasics, ResearchCategories.getResearchCategory("BASICS"));
        assertSame(addonFormula, existingBasics.formula);
        assertSame(entry, existingBasics.research.get("ADDON_ENTRY"));
        assertEquals("CUSTOM_BASIC_GATE", existingBasics.researchKey);
        assertEquals(-9, existingBasics.minDisplayColumn);
    }

    private static void assertFormula(String key, Aspect[] aspects, int[] weights) {
        AspectList formula = ResearchCategories.getResearchCategory(key).formula;
        assertArrayEquals(aspects, formula.getAspects(), key + " source order");
        assertEquals(weights.length, formula.size(), key + " no extra terms");
        int total = 0;
        for (int index = 0; index < aspects.length; index++) {
            assertEquals(weights[index], formula.getAmount(aspects[index]), key + "/" + aspects[index].getTag());
            total += weights[index];
        }
        assertEquals(total, formula.visSize(), key + " total weight");
    }

    private static void assertCategory(String key, String unlock, String icon, int background) {
        ResearchCategory category = ResearchCategories.getResearchCategory(key);
        assertEquals(key, category.key);
        assertEquals(unlock, category.researchKey);
        assertEquals(tc(icon), category.icon);
        assertEquals(tc("textures/gui/gui_research_back_" + background + ".jpg"), category.background);
        assertEquals(tc("textures/gui/gui_research_back_over.png"), category.background2);
        assertEquals(0, category.minDisplayColumn);
        assertEquals(0, category.maxDisplayColumn);
        assertEquals(0, category.minDisplayRow);
        assertEquals(0, category.maxDisplayRow);
        assertTrue(category.research.isEmpty(), "category bootstrap must not pretend the JSON catalog is loaded");
    }

    private static ResourceLocation tc(String path) {
        return ResourceLocation.fromNamespaceAndPath("thaumcraft", path);
    }
}
