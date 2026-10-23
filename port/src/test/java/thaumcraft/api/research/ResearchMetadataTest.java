package thaumcraft.api.research;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge;

class ResearchMetadataTest {
    private LinkedHashMap<String, ResearchCategory> previousCategories;
    private Locale previousLocale;

    @BeforeEach
    void isolateGlobalApiRegistry() {
        previousCategories = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.ROOT);
    }

    @AfterEach
    void restoreGlobalApiRegistry() {
        ResearchCategories.researchCategories = previousCategories;
        Locale.setDefault(previousLocale);
    }

    @Test
    void categoryRegistrationRetainsOrderOpaqueKeysAndOriginalMetadataReferences() {
        AspectList formula = new AspectList().add(Aspect.AIR, 10);
        ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/test/icon.png");
        ResourceLocation background = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/test/background.png");
        ResourceLocation background2 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/test/background2.png");
        ResearchCategory first = ResearchCategories.registerCategory("BASICS", "!gotthaumonomicon", formula, icon, background);
        ResearchCategory second = ResearchCategories.registerCategory("ALCHEMY", "KEY@2", null, icon, background, background2);
        ResearchCategory lower = ResearchCategories.registerCategory("basics", null, null, null, null);

        assertEquals(List.of("BASICS", "ALCHEMY", "basics"), List.copyOf(ResearchCategories.researchCategories.keySet()));
        assertSame(first, ResearchCategories.getResearchCategory("BASICS"));
        assertSame(lower, ResearchCategories.getResearchCategory("basics"));
        assertSame(formula, first.formula);
        assertSame(icon, first.icon);
        assertSame(background, first.background);
        assertNull(first.background2);
        assertSame(background2, second.background2);
        assertEquals("!gotthaumonomicon", first.researchKey);
        assertEquals("KEY@2", second.researchKey);
    }

    @Test
    void duplicateCategoryRegistrationReturnsNullWithoutReplacingEitherOverload() {
        ResearchCategory first = category("BASICS");
        assertNull(ResearchCategories.registerCategory("BASICS", "OTHER", null, null, null));
        assertNull(ResearchCategories.registerCategory("BASICS", "OTHER", null, null, null, null));
        assertSame(first, ResearchCategories.getResearchCategory("BASICS"));
        assertEquals(1, ResearchCategories.researchCategories.size());
    }

    @Test
    void researchLookupUsesEntryKeyNotMapIndexAndFirstRegisteredCategory() {
        ResearchCategory first = category("FIRST");
        ResearchCategory second = category("SECOND");
        ResearchEntry earlier = entry("FIRST", "!MARKER@2", 0, 0);
        ResearchEntry later = entry("SECOND", "!MARKER@2", 0, 0);
        first.research.put("different-map-index", earlier);
        second.research.put(later.getKey(), later);

        assertSame(earlier, ResearchCategories.getResearch("!MARKER@2"));
        assertNull(ResearchCategories.getResearch("different-map-index"));
        assertNull(ResearchCategories.getResearch("!marker@2"));
        assertNull(ResearchCategories.getResearch(null));
        ResearchCategories.researchCategories.remove("FIRST");
        assertSame(later, ResearchCategories.getResearch("!MARKER@2"));
    }

    @Test
    void insertionRejectsMissingCategoryDuplicateKeyAndOccupiedCoordinateWithoutMutatingBounds() {
        ResearchCategory basics = category("BASICS");
        ResearchEntry original = entry("BASICS", "FIRSTSTEPS", 2, 3);
        assertTrue(ResearchCategories.registerResearch(original));
        assertFalse(ResearchCategories.registerResearch(entry("MISSING", "KEY", 1, 1)));
        assertFalse(ResearchCategories.registerResearch(entry("BASICS", "FIRSTSTEPS", -100, -100)));
        assertFalse(ResearchCategories.registerResearch(entry("BASICS", "OTHER", 2, 3)));
        assertSame(original, basics.research.get("FIRSTSTEPS"));
        assertEquals(1, basics.research.size());
        assertEquals(0, basics.minDisplayColumn);
        assertEquals(0, basics.minDisplayRow);
        assertEquals(2, basics.maxDisplayColumn);
        assertEquals(3, basics.maxDisplayRow);
    }

    @Test
    void insertionBoundsAlwaysIncludeInitialZeroAndKeysCanRepeatInDifferentCategories() {
        ResearchCategory positive = category("POSITIVE");
        ResearchCategory negative = category("NEGATIVE");
        ResearchEntry a = entry("POSITIVE", "SAME", 4, 5);
        ResearchEntry b = entry("NEGATIVE", "SAME", -4, -5);
        assertTrue(ResearchCategories.registerResearch(a));
        assertTrue(ResearchCategories.registerResearch(b));
        assertSame(a, ResearchCategories.getResearch("SAME"));
        assertEquals(0, positive.minDisplayColumn);
        assertEquals(0, positive.minDisplayRow);
        assertEquals(4, positive.maxDisplayColumn);
        assertEquals(5, positive.maxDisplayRow);
        assertEquals(-4, negative.minDisplayColumn);
        assertEquals(-5, negative.minDisplayRow);
        assertEquals(0, negative.maxDisplayColumn);
        assertEquals(0, negative.maxDisplayRow);
    }

    @Test
    void emptyModelHasNullableMetadataAndDoesNotInventStagesOrAutounlock() {
        ResearchEntry entry = new ResearchEntry();
        assertNull(entry.getKey());
        assertNull(entry.getCategory());
        assertNull(entry.getName());
        assertNull(entry.getParents());
        assertNull(entry.getParentsClean());
        assertNull(entry.getParentsStripped());
        assertNull(entry.getSiblings());
        assertNull(entry.getStages());
        assertNull(entry.getMeta());
        assertNull(entry.getRewardItem());
        assertNull(entry.getRewardKnow());
        assertNull(entry.getAddenda());
        assertFalse(entry.hasMeta(ResearchEntry.EnumResearchMeta.AUTOUNLOCK));
        assertEquals(0, entry.getDisplayColumn());
        assertEquals(0, entry.getDisplayRow());
        assertTrue(ResearchCategories.researchCategories.isEmpty());
    }

    @Test
    void nullEmptyAndNonemptyStageArraysRemainDistinctAndDirectlyMutable() {
        ResearchEntry entry = new ResearchEntry();
        ResearchStage[] empty = new ResearchStage[0];
        entry.setStages(empty);
        assertSame(empty, entry.getStages());
        assertEquals(0, entry.getStages().length);
        ResearchStage[] stages = {new ResearchStage(), new ResearchStage()};
        entry.setStages(stages);
        assertSame(stages, entry.getStages());
        stages[0].setWarp(7);
        assertEquals(7, entry.getStages()[0].getWarp());
        entry.setStages(null);
        assertNull(entry.getStages());
    }

    @Test
    void parentViewsStripOnlyOneTildeAndFirstQualifierWithoutChangingOriginalArray() {
        ResearchEntry entry = new ResearchEntry();
        String[] parents = {"~FIRSTSTEPS@2", "~~HIDDEN@3@4", "!gotcrystals", null, "~"};
        entry.setParents(parents);
        assertSame(parents, entry.getParents());
        assertArrayEquals(new String[]{"FIRSTSTEPS@2", "~HIDDEN@3@4", "!gotcrystals", "null", ""}, entry.getParentsStripped());
        assertArrayEquals(new String[]{"FIRSTSTEPS", "~HIDDEN", "!gotcrystals", "null", ""}, entry.getParentsClean());
        assertArrayEquals(new String[]{"~FIRSTSTEPS@2", "~~HIDDEN@3@4", "!gotcrystals", null, "~"}, parents);
        assertNotSame(entry.getParentsStripped(), entry.getParentsStripped());
    }

    @Test
    void metaOrderAndNullableMembershipMatchOriginalApi() {
        assertArrayEquals(new ResearchEntry.EnumResearchMeta[]{ResearchEntry.EnumResearchMeta.ROUND,
            ResearchEntry.EnumResearchMeta.SPIKY, ResearchEntry.EnumResearchMeta.REVERSE,
            ResearchEntry.EnumResearchMeta.HIDDEN, ResearchEntry.EnumResearchMeta.AUTOUNLOCK,
            ResearchEntry.EnumResearchMeta.HEX}, ResearchEntry.EnumResearchMeta.values());
        ResearchEntry entry = new ResearchEntry();
        assertFalse(entry.hasMeta(null));
        ResearchEntry.EnumResearchMeta[] meta = {ResearchEntry.EnumResearchMeta.AUTOUNLOCK, null};
        entry.setMeta(meta);
        assertSame(meta, entry.getMeta());
        assertTrue(entry.hasMeta(ResearchEntry.EnumResearchMeta.AUTOUNLOCK));
        assertTrue(entry.hasMeta(null));
        meta[0] = ResearchEntry.EnumResearchMeta.HEX;
        assertFalse(entry.hasMeta(ResearchEntry.EnumResearchMeta.AUTOUNLOCK));
    }

    @Test
    void rewardsSiblingsIconsAndAddendaPreserveOriginalReferences() {
        ResearchEntry entry = new ResearchEntry();
        Object[] icons = {"focus", "icon"};
        String[] siblings = {"!gotdream"};
        ItemStack[] rewards = new ItemStack[0];
        ResearchStage.Knowledge[] knowledge = {new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY, null, 3)};
        ResearchAddendum[] addenda = {new ResearchAddendum()};
        entry.setIcons(icons);
        entry.setSiblings(siblings);
        entry.setRewardItem(rewards);
        entry.setRewardKnow(knowledge);
        entry.setAddenda(addenda);
        assertSame(icons, entry.getIcons());
        assertSame(siblings, entry.getSiblings());
        assertSame(rewards, entry.getRewardItem());
        assertSame(knowledge, entry.getRewardKnow());
        assertSame(addenda, entry.getAddenda());
    }

    @Test
    void stageAndAddendumRetainAllMutableRequirementsWithoutValidationOrNormalization() {
        ResearchStage stage = new ResearchStage();
        ResearchAddendum addendum = new ResearchAddendum();
        assertNull(stage.getRecipes());
        assertNull(stage.getObtain());
        assertNull(stage.getCraft());
        assertNull(stage.getCraftReference());
        assertNull(stage.getKnow());
        assertNull(stage.getResearch());
        assertNull(stage.getResearchIcon());
        assertEquals(0, stage.getWarp());
        assertNull(addendum.getText());
        assertNull(addendum.getRecipes());
        assertNull(addendum.getResearch());
        ResourceLocation[] recipes = {ResourceLocation.fromNamespaceAndPath("thaumcraft", "example")};
        Object[] obtain = {"oreMarker"};
        Object[] craft = {"craftMarker"};
        int[] references = {Integer.MIN_VALUE, 12};
        ResearchStage.Knowledge[] knowledge = {new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, null, -2)};
        String[] research = {"FIRSTSTEPS@2", "!gotdream"};
        String[] icons = {"rawIcon"};
        stage.setRecipes(recipes);
        stage.setObtain(obtain);
        stage.setCraft(craft);
        stage.setCraftReference(references);
        stage.setKnow(knowledge);
        stage.setResearch(research);
        stage.setResearchIcon(icons);
        stage.setWarp(-7);
        addendum.setRecipes(recipes);
        addendum.setResearch(research);
        assertSame(recipes, stage.getRecipes());
        assertSame(obtain, stage.getObtain());
        assertSame(craft, stage.getCraft());
        assertSame(references, stage.getCraftReference());
        assertSame(knowledge, stage.getKnow());
        assertSame(research, stage.getResearch());
        assertSame(icons, stage.getResearchIcon());
        assertEquals(-7, stage.getWarp());
        assertSame(recipes, addendum.getRecipes());
        assertSame(research, addendum.getResearch());
    }

    @Test
    void localizedMetadataIsDeferredCommonTranslatableComponent() {
        ResearchEntry entry = new ResearchEntry();
        ResearchStage stage = new ResearchStage();
        ResearchAddendum addendum = new ResearchAddendum();
        entry.setName("research.FIRSTSTEPS.title");
        stage.setText("research.FIRSTSTEPS.stage.1");
        addendum.setText("research.FIRSTSTEPS.addendum");
        assertEquals("research.FIRSTSTEPS.title", translationKey(entry.getLocalizedName()));
        assertEquals("research.FIRSTSTEPS.stage.1", translationKey(stage.getTextLocalized()));
        assertEquals("research.FIRSTSTEPS.addendum", translationKey(addendum.getTextLocalized()));
        assertEquals("tc.research_category.BASICS", translationKey(ResearchCategories.getCategoryName("BASICS")));
    }

    @Test
    void formulaUsesOnlyWeightedAspectsSquareModifierSqrtAndCeiling() {
        ResearchCategory category = category("BASICS");
        assertEquals(0, category.applyFormula(null));
        category.formula = new AspectList().add(Aspect.AIR, 20).add(Aspect.FIRE, 5);
        AspectList input = new AspectList().add(Aspect.AIR, 10).add(Aspect.FIRE, 2).add(Aspect.WATER, 1000);
        assertEquals(5, category.applyFormula(input)); // ceil(sqrt(10*2 + 2*0.5))
        assertEquals(7, category.applyFormula(input, 1.5));
        assertEquals(10, category.applyFormula(input, 2.0));
        assertEquals(10, category.applyFormula(input, -2.0));
        assertEquals(0, category.applyFormula(input, 0.0));
        category.formula.add(Aspect.AIR, 10);
        assertEquals(6, category.applyFormula(input)); // API formula alias is still mutable
    }

    @Test
    void formulaDoesNotSqrtNegativeTotalsOrClampThem() {
        ResearchCategory category = category("BASICS");
        category.formula = new AspectList().add(Aspect.AIR, -7);
        assertEquals(-2, category.applyFormula(new AspectList().add(Aspect.AIR, 3))); // ceil(-2.1)
        assertEquals(0, category.applyFormula(new AspectList().add(Aspect.FIRE, 4)));
        category.formula = new AspectList();
        assertEquals(0, category.applyFormula(null));
    }

    @Test
    void knowledgeParserUppercasesTypeAndCategoryAndAcceptsPositiveIntegerOnly() {
        ResearchCategory basics = category("BASICS");
        ResearchStage.Knowledge knowledge = ResearchStage.Knowledge.parse("observation;basics;2");
        assertNotNull(knowledge);
        assertSame(IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, knowledge.type);
        assertSame(basics, knowledge.category);
        assertEquals(2, knowledge.amount);
        assertNull(ResearchStage.Knowledge.parse("THEORY;BASICS;0"));
        assertNull(ResearchStage.Knowledge.parse("THEORY;BASICS;-1"));
        assertNull(ResearchStage.Knowledge.parse("THEORY;BASICS;bad"));
        assertNull(ResearchStage.Knowledge.parse("THEORY;BASICS;2147483648"));
        assertNull(ResearchStage.Knowledge.parse("THEORY;MISSING;1"));
        assertNull(ResearchStage.Knowledge.parse("THEORY;BASICS; 1"));
        assertEquals(1, ResearchStage.Knowledge.parse("THEORY;BASICS;+1").amount);
    }

    @Test
    void knowledgeParserRetainsJavaSplitAndInvalidEnumFailureBehavior() {
        category("BASICS");
        assertNull(ResearchStage.Knowledge.parse("THEORY;1"));
        assertNull(ResearchStage.Knowledge.parse("OBSERVATION;1"));
        assertNull(ResearchStage.Knowledge.parse("THEORY"));
        assertNull(ResearchStage.Knowledge.parse("THEORY;BASICS;1;ignored"));
        assertEquals(1, ResearchStage.Knowledge.parse("THEORY;BASICS;1;").amount);
        assertThrows(IllegalArgumentException.class, () -> ResearchStage.Knowledge.parse("INVALID;BASICS;1"));
        assertThrows(IllegalArgumentException.class, () -> ResearchStage.Knowledge.parse(" theory;BASICS;1"));
        assertThrows(NullPointerException.class, () -> ResearchStage.Knowledge.parse(null));
    }

    @Test
    void knowledgeParserUsesOriginalDefaultLocaleNotUnannouncedRootLocaleFix() {
        category("BASICS");
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        assertThrows(IllegalArgumentException.class, () -> ResearchStage.Knowledge.parse("observation;BASICS;1"));
        assertEquals(1, ResearchStage.Knowledge.parse("OBSERVATION;BASICS;1").amount);
    }

    private static ResearchCategory category(String key) {
        return ResearchCategories.registerCategory(key, null, null, null, null);
    }

    private static ResearchEntry entry(String category, String key, int column, int row) {
        ResearchEntry entry = new ResearchEntry();
        entry.setCategory(category);
        entry.setKey(key);
        entry.setDisplayColumn(column);
        entry.setDisplayRow(row);
        return entry;
    }

    private static String translationKey(Component component) {
        return ((TranslatableContents)component.getContents()).getKey();
    }
}
