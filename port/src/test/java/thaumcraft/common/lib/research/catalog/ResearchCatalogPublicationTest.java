package thaumcraft.common.lib.research.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEntry.EnumResearchMeta;
import thaumcraft.common.config.ConfigResearch;
import thaumcraft.common.lib.research.catalog.PreparedResearchMetadata.DependencyKind;
import thaumcraft.common.lib.research.catalog.ResearchCatalogPublication.Candidate;
import thaumcraft.common.lib.research.catalog.ResearchCatalogPublication.PreparationException;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken;

/** No runtime hook, real catalog publication at startup, item resolver or player progression. */
class ResearchCatalogPublicationTest {
    private static final Set<String> CATEGORIES = Set.of("BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH", "ADDON", "basics", "UNKNOWN");
    private LinkedHashMap<String, ResearchCategory> previous;

    @BeforeEach
    void isolate() {
        previous = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
    }

    @AfterEach
    void restore() { ResearchCategories.researchCategories = previous; }

    @Test
    void creatingAndPreparingDoesNotAutoPublishOrInitializeGlobalCatalog() {
        var original = ResearchCategories.researchCategories;
        ResearchCatalogPublication publication = new ResearchCatalogPublication();
        Candidate candidate = prepare(parse(entry("ONE", "BASICS", 2, 3)));
        assertSame(original, ResearchCategories.researchCategories);
        assertTrue(original.isEmpty());
        assertNull(publication.published());
        assertTrue(publication.ownedEntries().isEmpty());
        assertEquals(7, candidate.categories().size());
        assertNull(ResearchCategories.getResearch("ONE"));
    }

    @Test
    void detachedBuiltinDefinitionsMatchIndependentConfigResearchMetadata() {
        ConfigResearch.initCategories();
        var global = ResearchCategories.researchCategories;
        var builtins = ResearchCategoryDefinitions.newBuiltinCategories();
        assertEquals(List.of("BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH"), List.copyOf(builtins.keySet()));
        for (String key : builtins.keySet()) {
            ResearchCategory expected = global.get(key);
            ResearchCategory actual = builtins.get(key);
            assertNotSame(expected, actual);
            assertNotSame(expected.formula, actual.formula);
            assertEquals(expected.formula, actual.formula);
            assertArrayEquals(expected.formula.getAspects(), actual.formula.getAspects());
            assertEquals(expected.researchKey, actual.researchKey);
            assertEquals(expected.icon, actual.icon);
            assertEquals(expected.background, actual.background);
            assertEquals(expected.background2, actual.background2);
            assertBounds(actual, 0, 0, 0, 0);
            assertTrue(actual.research.isEmpty());
        }
        var second = ResearchCategoryDefinitions.newBuiltinCategories();
        builtins.get("BASICS").formula.add(Aspect.AIR, 99);
        assertEquals(5, second.get("BASICS").formula.getAmount(Aspect.AIR));
        assertEquals(5, global.get("BASICS").formula.getAmount(Aspect.AIR));
        assertSame(global, ResearchCategories.researchCategories);
    }

    @Test
    void existingCategoryMetadataWinsAndExternalEntriesRetainIdentity() {
        ResearchCategory basics = addCategory("BASICS");
        basics.researchKey = "ADDON_GATE";
        basics.formula = new AspectList().add(Aspect.AIR, 99).add(Aspect.EARTH, -3);
        basics.icon = id("addon:custom.png");
        basics.background = null;
        basics.background2 = id("addon:overlay.png");
        ResearchEntry external = external("EXTERNAL", "BASICS", -8, 11);
        basics.research.put("alias", external);
        basics.minDisplayColumn = -999;
        ResearchCategory addon = addCategory("ADDON");
        ResearchEntry extra = external("OPAQUE@1", "ADDON", 4, 5);
        addon.research.put(extra.getKey(), extra);
        Candidate candidate = prepare(parse(entry("JSON", "BASICS", 2, 3)));
        ResearchCategory detached = candidate.categories().get("BASICS");
        assertNotSame(basics, detached);
        assertNotSame(basics.research, detached.research);
        assertNotSame(basics.formula, detached.formula);
        assertEquals(basics.formula, detached.formula);
        assertEquals("ADDON_GATE", detached.researchKey);
        assertEquals(basics.icon, detached.icon);
        assertNull(detached.background);
        assertEquals(basics.background2, detached.background2);
        assertSame(external, detached.research.get("alias"));
        assertSame(extra, candidate.categories().get("ADDON").research.get(extra.getKey()));
        assertBounds(detached, -8, 0, 2, 11);
        assertEquals(-999, basics.minDisplayColumn);
        assertEquals(1, basics.research.size());
        detached.formula.add(Aspect.AIR, 1);
        assertEquals(99, basics.formula.getAmount(Aspect.AIR));
    }

    @Test
    void previousOwnershipUsesIdentityRatherThanEquals() {
        ResearchCategory category = addCategory("BASICS");
        ResearchEntry old = new EqualEntry();
        configure(old, "OLD", "BASICS", -100, -100);
        ResearchEntry addon = new EqualEntry();
        configure(addon, "ADDON_ENTRY", "BASICS", 8, 9);
        category.research.put("OLD", old);
        category.research.put("ADDON_ENTRY", addon);
        assertEquals(old, addon, "fixture intentionally defeats equality-based ownership");
        Candidate candidate = ResearchCatalogPublication.prepareWhole(parse(entry("JSON", "BASICS", 1, 2)),
            ResearchCategories.researchCategories, Set.of(old));
        assertFalse(candidate.categories().get("BASICS").research.containsKey("OLD"));
        assertSame(addon, candidate.categories().get("BASICS").research.get("ADDON_ENTRY"));
        assertFalse(candidate.ownedEntries().contains(addon));
        assertBounds(candidate.categories().get("BASICS"), 0, 0, 8, 9);
    }

    @Test
    void externalReplacementOfOldOwnedKeyIsNotDeletedOrOverwritten() {
        ResearchCategory category = addCategory("BASICS");
        ResearchEntry old = external("SAME", "BASICS", 1, 2);
        ResearchEntry replacement = external("SAME", "BASICS", 4, 5);
        category.research.put("SAME", replacement);
        PreparationException failure = assertThrows(PreparationException.class, () -> ResearchCatalogPublication.prepareWhole(
            parse(entry("SAME", "BASICS", 6, 7)), ResearchCategories.researchCategories, Set.of(old)));
        assertTrue(failure.path().endsWith(".key"));
        assertSame(replacement, category.research.get("SAME"));
        Candidate withoutCollision = ResearchCatalogPublication.prepareWhole(parse(), ResearchCategories.researchCategories, Set.of(old));
        assertSame(replacement, withoutCollision.categories().get("BASICS").research.get("SAME"));
    }

    @Test
    void jsonKeysCannotShadowApiKeysOrAliasMapKeysAcrossCategories() {
        ResearchCategory addon = addCategory("ADDON");
        ResearchEntry external = external("ENTRY_KEY", "ADDON", 1, 2);
        addon.research.put("MAP_ALIAS", external);
        for (String collision : List.of("ENTRY_KEY", "MAP_ALIAS")) {
            assertThrows(PreparationException.class, () -> prepare(parse(entry(collision, "BASICS", 7, 8))), collision);
        }
        assertSame(external, addon.research.get("MAP_ALIAS"));
    }

    @Test
    void displayCollisionsRejectWholeCandidateButCoordinatesInDifferentCategoriesAreAllowed() {
        ResearchCategory category = addCategory("BASICS");
        ResearchEntry external = external("EXTERNAL", "BASICS", 3, 4);
        category.research.put(external.getKey(), external);
        PreparationException error = assertThrows(PreparationException.class, () -> prepare(parse(
            entry("FIRST", "BASICS", 7, 8), entry("COLLISION", "BASICS", 3, 4))));
        assertTrue(error.path().endsWith(".location"));
        assertEquals(1, category.research.size());
        assertNull(ResearchCategories.getResearch("FIRST"));
        assertEquals(2, prepare(parse(entry("OTHER", "ALCHEMY", 3, 4), entry("OK", "BASICS", 1, 2))).entries().size());
    }

    @Test
    void preparationRechecksManuallyCombinedSpecsInsteadOfTrustingOnlyParser() {
        var one = parse(entry("DUPLICATE", "BASICS", 1, 2));
        var other = parse(entry("DUPLICATE", "ALCHEMY", 3, 4));
        ResearchCatalogSpec combined = new ResearchCatalogSpec(Map.of(), List.of(one.entries().getFirst(), other.entries().getFirst()), List.of());
        assertThrows(PreparationException.class, () -> prepare(combined));
        assertTrue(ResearchCategories.researchCategories.isEmpty());
    }

    @Test
    void missingCandidateCategoryRejectsEvenIfParserWasGivenThatCategoryName() {
        PreparationException error = assertThrows(PreparationException.class, () -> prepare(parse(entry("ONE", "UNKNOWN", 1, 2))));
        assertEquals("test.json", error.source());
        assertTrue(error.path().endsWith(".category"));
        assertTrue(ResearchCategories.researchCategories.isEmpty());
    }

    @Test
    void boundsRebuildFromZeroInsteadOfKeepingRemovedOwnedExtremes() {
        ResearchCategory category = addCategory("BASICS");
        ResearchEntry old = external("OLD", "BASICS", -1000, 1000);
        category.research.put("OLD", old);
        category.minDisplayColumn = -1000;
        category.maxDisplayRow = 1000;
        Candidate candidate = ResearchCatalogPublication.prepareWhole(parse(entry("NEW", "BASICS", 2, -3)),
            ResearchCategories.researchCategories, Set.of(old));
        assertBounds(candidate.categories().get("BASICS"), 0, -3, 2, 0);
        assertBounds(category, -1000, 0, 0, 1000);
        Candidate empty = ResearchCatalogPublication.prepareWhole(parse(), ResearchCategories.researchCategories, Set.of(old));
        assertBounds(empty.categories().get("BASICS"), 0, 0, 0, 0);
    }

    @Test
    void explicitPublishSwapsWholeApiReferenceAndStoresImmutableSpecOwnershipSnapshot() {
        var original = ResearchCategories.researchCategories;
        ResearchCatalogSpec spec = parse(entry("ONE", "BASICS", 1, 2), entry("TWO", "ALCHEMY", 3, 4));
        Candidate candidate = prepare(spec);
        ResearchCatalogPublication publication = new ResearchCatalogPublication();
        var snapshot = publication.publish(candidate);
        assertNotSame(original, ResearchCategories.researchCategories);
        assertTrue(original.isEmpty());
        assertEquals(7, ResearchCategories.researchCategories.size());
        assertSame(candidate.entries().get("ONE"), ResearchCategories.getResearch("ONE"));
        assertSame(candidate.entries().get("TWO"), ResearchCategories.getResearch("TWO"));
        assertSame(snapshot, publication.published());
        assertEquals(spec.sources(), snapshot.spec().sources());
        assertEquals(2, snapshot.ownedEntries().size());
        assertSame(snapshot.ownedEntries(), publication.ownedEntries());
        assertThrows(UnsupportedOperationException.class, () -> candidate.categories().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.ownedEntries().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.spec().sources().clear());
    }

    @Test
    void secondPublicationRemovesOnlyOldOwnedEntriesAndRetainsLaterAddonEntries() {
        ResearchCatalogPublication publication = new ResearchCatalogPublication();
        publication.publish(prepare(parse(entry("REMOVED", "BASICS", -50, -60), entry("REPLACED", "BASICS", 1, 2))));
        ResearchEntry old = ResearchCategories.getResearch("REPLACED");
        ResearchEntry addon = external("ADDON", "BASICS", 8, 9);
        ResearchCategories.researchCategories.get("BASICS").research.put("ADDON", addon);
        Candidate next = ResearchCatalogPublication.prepareWhole(parse(entry("REPLACED", "BASICS", 3, 4)),
            ResearchCategories.researchCategories, publication.ownedEntries());
        publication.publish(next);
        assertNull(ResearchCategories.getResearch("REMOVED"));
        assertNotSame(old, ResearchCategories.getResearch("REPLACED"));
        assertSame(addon, ResearchCategories.getResearch("ADDON"));
        assertFalse(publication.ownedEntries().contains(addon));
        assertEquals(1, publication.ownedEntries().size());
        assertBounds(ResearchCategories.researchCategories.get("BASICS"), 0, 0, 8, 9);
    }

    @Test
    void emptyPublicationRemovesOwnedCatalogWithoutDroppingCategoriesOrExternalEntries() {
        ResearchCatalogPublication publication = new ResearchCatalogPublication();
        publication.publish(prepare(parse(entry("OLD", "BASICS", 1, 2))));
        ResearchEntry addon = external("ADDON", "BASICS", -8, 9);
        ResearchCategories.researchCategories.get("BASICS").research.put("ADDON", addon);
        publication.publish(ResearchCatalogPublication.prepareWhole(parse(), ResearchCategories.researchCategories, publication.ownedEntries()));
        assertEquals(7, ResearchCategories.researchCategories.size());
        assertNull(ResearchCategories.getResearch("OLD"));
        assertSame(addon, ResearchCategories.getResearch("ADDON"));
        assertTrue(publication.ownedEntries().isEmpty());
    }

    @Test
    void failedWholePreparationLeavesPreviousPublicationAndApiUntouched() {
        ResearchCatalogPublication publication = new ResearchCatalogPublication();
        var previousSnapshot = publication.publish(prepare(parse(entry("OWNED", "BASICS", 1, 2))));
        var previousMap = ResearchCategories.researchCategories;
        ResearchEntry addon = external("ADDON", "BASICS", 8, 9);
        previousMap.get("BASICS").research.put("ADDON", addon);
        assertThrows(PreparationException.class, () -> ResearchCatalogPublication.prepareWhole(
            parse(entry("FIRST", "BASICS", 3, 4), entry("ADDON", "ALCHEMY", 5, 6)), previousMap, publication.ownedEntries()));
        assertSame(previousMap, ResearchCategories.researchCategories);
        assertSame(previousSnapshot, publication.published());
        assertNull(ResearchCategories.getResearch("FIRST"));
        assertSame(addon, ResearchCategories.getResearch("ADDON"));
        assertNotNull(ResearchCategories.getResearch("OWNED"));
    }

    @Test
    void publicationRejectsStaleApiReferenceLateEntryAndMetadataChanges() {
        ResearchCatalogPublication publication = new ResearchCatalogPublication();
        ResearchCategory category = addCategory("BASICS");
        var original = ResearchCategories.researchCategories;
        Candidate changedMap = prepare(parse(entry("ONE", "BASICS", 1, 2)));
        ResearchCategories.researchCategories = new LinkedHashMap<>(original);
        assertThrows(IllegalStateException.class, () -> publication.publish(changedMap));
        ResearchCategories.researchCategories = original;
        Candidate lateEntry = prepare(parse(entry("ONE", "BASICS", 1, 2)));
        category.research.put("LATE", external("LATE", "BASICS", 7, 8));
        assertThrows(IllegalStateException.class, () -> publication.publish(lateEntry));
        assertSame(original, ResearchCategories.researchCategories);
        Candidate changedFormula = prepare(parse(entry("ONE", "BASICS", 1, 2)));
        category.formula.add(Aspect.AIR, 1);
        assertThrows(IllegalStateException.class, () -> publication.publish(changedFormula));
        assertNull(publication.published());
    }

    @Test
    void publicationRejectsCandidateIndexMutationBeforeSwap() {
        Candidate candidate = prepare(parse(entry("ONE", "BASICS", 1, 2)));
        candidate.entries().get("ONE").setDisplayColumn(99);
        var original = ResearchCategories.researchCategories;
        assertThrows(IllegalStateException.class, () -> new ResearchCatalogPublication().publish(candidate));
        assertSame(original, ResearchCategories.researchCategories);
        assertTrue(original.isEmpty());
    }

    @Test
    void missingAndExplicitEmptyArraysKeepLegacyNullAndRawPresence() {
        JsonObject object = entry("EMPTY", "BASICS", 0, 0);
        object.add("stages", new JsonArray());
        object.add("parents", new JsonArray());
        object.add("meta", new JsonArray());
        object.add("icons", new JsonArray());
        object.add("addenda", new JsonArray());
        var result = prepare(parse(object)).entries().get("EMPTY");
        assertNull(result.getStages());
        assertNull(result.getParents());
        assertNull(result.getSiblings());
        assertNull(result.getMeta());
        assertNull(result.getIcons());
        assertNull(result.getRewardItem());
        assertNull(result.getRewardKnow());
        assertNull(result.getAddenda());
        assertTrue(result.rawSpec().parents().present());
        assertFalse(result.rawSpec().siblings().present());
        assertFalse(result.hasUnresolved());
    }

    @Test
    void allNonRegistryEntryStageAndAddendumMetadataMapsSourceQuirks() {
        JsonObject object = entry("!Mixed@2", "BASICS", -4, 7);
        object.add("parents", strings("~PARENT@2", "!marker"));
        object.add("siblings", strings("Sibling", "!x"));
        object.add("meta", strings("round", "ROUND", "hidden"));
        stage(object).add("required_research", strings("!Custom@2;NS:Texture.png;ignored", "m_deepdown", "TRAILING;"));
        stage(object).addProperty("warp", -5);
        JsonObject addendum = new JsonObject();
        addendum.addProperty("text", "addendum.text");
        addendum.add("required_research", strings("!Custom;not-split", "~Other@3"));
        JsonArray addenda = new JsonArray(); addenda.add(addendum); object.add("addenda", addenda);
        var result = prepare(parse(object)).entries().get("!Mixed@2");
        assertEquals("!Mixed@2", result.getKey());
        assertEquals("research.!Mixed@2.title", result.getName());
        assertEquals("BASICS", result.getCategory());
        assertEquals(-4, result.getDisplayColumn()); assertEquals(7, result.getDisplayRow());
        assertArrayEquals(new String[]{"~PARENT@2", "!marker"}, result.getParents());
        assertArrayEquals(new String[]{"Sibling", "!x"}, result.getSiblings());
        assertArrayEquals(new EnumResearchMeta[]{EnumResearchMeta.ROUND, EnumResearchMeta.ROUND, EnumResearchMeta.HIDDEN}, result.getMeta());
        var stage = (PreparedResearchMetadata.Stage) result.getStages()[0];
        assertEquals("stage.text", stage.getText());
        assertArrayEquals(new String[]{"!Custom@2", "m_deepdown", "TRAILING"}, stage.getResearch());
        assertArrayEquals(new String[]{"NS:Texture.png", null, null}, stage.getResearchIcon());
        assertEquals(-5, stage.getWarp());
        assertNull(stage.getKnow()); assertNull(stage.getObtain()); assertNull(stage.getCraft()); assertNull(stage.getCraftReference());
        assertEquals("addendum.text", result.getAddenda()[0].getText());
        assertArrayEquals(new String[]{"!Custom;not-split", "~Other@3"}, result.getAddenda()[0].getResearch());
        assertFalse(stage.hasUnresolved());
    }

    @Test
    void knowledgeUsesDetachedCandidateCategoryNotGlobalParseOrOldCategoryReference() {
        ResearchCategory globalBasics = addCategory("BASICS");
        JsonObject object = entry("KNOW", "BASICS", 1, 2);
        object.add("reward_knowledge", strings("THEORY;BASICS;2"));
        stage(object).add("required_knowledge", strings("OBSERVATION;BASICS;+3", "THEORY;BASICS;4"));
        Candidate candidate = prepare(parse(object));
        var result = candidate.entries().get("KNOW");
        ResearchCategory detached = candidate.categories().get("BASICS");
        assertNotSame(globalBasics, detached);
        assertSame(detached, result.getRewardKnow()[0].category);
        assertEquals(EnumKnowledgeType.THEORY, result.getRewardKnow()[0].type);
        assertEquals(2, result.getRewardKnow()[0].amount);
        var stage = result.getStages()[0];
        assertSame(detached, stage.getKnow()[0].category);
        assertSame(detached, stage.getKnow()[1].category);
        assertEquals(EnumKnowledgeType.OBSERVATION, stage.getKnow()[0].type);
        assertEquals(3, stage.getKnow()[0].amount);
        assertEquals(4, stage.getKnow()[1].amount);
        assertSame(globalBasics, ResearchCategories.getResearchCategory("BASICS"));
    }

    @Test
    void requiredKnowledgeMissingFromCandidateFailsRatherThanBeingDropped() {
        JsonObject object = entry("KNOW", "BASICS", 1, 2);
        stage(object).add("required_knowledge", strings("THEORY;ADDON;2"));
        PreparationException error = assertThrows(PreparationException.class, () -> prepare(parse(object)));
        assertTrue(error.path().contains("required_knowledge"));
        assertTrue(ResearchCategories.researchCategories.isEmpty());
    }

    @Test
    void validRecipesUseExactLegacyNormalizationWithoutClaimingRecipeRegistryResolution() {
        JsonObject object = entry("RECIPES", "BASICS", 1, 2);
        stage(object).add("recipes", strings("Thaumcraft:MixedRecipe", "OneUpper", "a:Upper", ":UPPER"));
        Candidate candidate = prepare(parse(object));
        var stage = (PreparedResearchMetadata.Stage) candidate.entries().get("RECIPES").getStages()[0];
        assertArrayEquals(new ResourceLocation[]{id("thaumcraft:mixedrecipe"), id("minecraft:oneupper"), id("minecraft:upper"), id("minecraft:upper")}, stage.getRecipes());
        assertEquals(4, stage.recipeBindings().size());
        assertTrue(stage.recipeBindings().stream().noneMatch(PreparedResearchMetadata.RecipeBinding::blocked));
        assertEquals(4, stage.deferredDependencies().stream().filter(d -> d.kind() == DependencyKind.RECIPE_LOOKUP).count());
        assertTrue(stage.hasUnresolved());
        assertTrue(candidate.hasUnresolved());
    }

    @Test
    void invalidRecipesRetainEveryPositionalDescriptorAndBlockWholeApiArrayWithSpecDiagnostic() {
        JsonObject object = entry("RECIPES", "BASICS", 1, 2);
        stage(object).add("recipes", strings("Thaumcraft:Valid", "minecraft:bad:identifier", "Other"));
        JsonObject addendum = new JsonObject(); addendum.addProperty("text", "addendum");
        addendum.add("recipes", strings("minecraft:bad:identifier"));
        JsonArray addenda = new JsonArray(); addenda.add(addendum); object.add("addenda", addenda);
        ResearchCatalogSpec input = parse(object);
        Candidate candidate = prepare(input);
        var stage = (PreparedResearchMetadata.Stage) candidate.entries().get("RECIPES").getStages()[0];
        assertNull(stage.getRecipes(), "no filtering and no null slots pretending to be a valid API recipe list");
        assertEquals(3, stage.recipeBindings().size());
        assertEquals(id("thaumcraft:valid"), stage.recipeBindings().get(0).resourceLocation());
        assertTrue(stage.recipeBindings().get(1).blocked());
        assertEquals("minecraft:bad:identifier", stage.recipeBindings().get(1).token().raw());
        assertEquals(id("minecraft:other"), stage.recipeBindings().get(2).resourceLocation());
        var extra = (PreparedResearchMetadata.Addendum) candidate.entries().get("RECIPES").getAddenda()[0];
        assertTrue(extra.hasUnresolved()); assertNull(extra.getRecipes()); assertEquals(1, extra.recipeBindings().size());
        assertEquals(2, candidate.spec().diagnostics().stream().filter(d -> d.code().equals("DEFERRED_RECIPE_ID")).count());
        assertEquals(0, input.diagnostics().stream().filter(d -> d.code().equals("DEFERRED_RECIPE_ID")).count());
        assertSame(input.entries().getFirst(), candidate.spec().entries().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> stage.recipeBindings().clear());
        assertThrows(UnsupportedOperationException.class, () -> stage.deferredDependencies().clear());
    }

    @Test
    void allItemOreZeroCountAndMalformedRequirementsRemainNonemptyTokensWithoutCraftHashes() {
        JsonObject object = entry("ITEMS", "BASICS", 1, 2);
        stage(object).add("required_item", strings("minecraft:stick;2;3", "oredict:stick:ignored", "minecraft:stick;0", "minecraft:bad:identifier"));
        stage(object).add("required_craft", strings("oredict:stick", "thaumcraft:missing_item;1;0;{x:'value'}"));
        Candidate candidate = prepare(parse(object));
        var stage = (PreparedResearchMetadata.Stage) candidate.entries().get("ITEMS").getStages()[0];
        assertEquals(4, stage.getObtain().length);
        assertEquals(2, stage.getCraft().length);
        for (Object token : stage.getObtain()) assertInstanceOf(ItemToken.class, token);
        for (Object token : stage.getCraft()) assertInstanceOf(ItemToken.class, token);
        assertSame(stage.rawSpec().requiredItems().values().getFirst(), stage.getObtain()[0]);
        assertEquals(0, ((ItemToken) stage.getObtain()[2]).count());
        assertFalse(((ItemToken) stage.getObtain()[3]).legacyId().modernSyntax());
        assertNull(stage.getCraftReference());
        assertEquals(1, stage.deferredDependencies().stream().filter(d -> d.kind() == DependencyKind.CRAFT_REFERENCE).count());
        assertTrue(stage.hasUnresolved());
        var hashDependency = stage.deferredDependencies().stream().filter(d -> d.kind() == DependencyKind.CRAFT_REFERENCE).findFirst().orElseThrow();
        assertEquals(stage.rawSpec().requiredCraft().values(), hashDependency.token());
        assertEquals(2, ((List<?>) hashDependency.token()).size());
    }

    @Test
    void rewardTokensAreStoredRawAndNotPretendedToBeResolvedItemStacks() {
        JsonObject object = entry("REWARDS", "BASICS", 1, 2);
        object.add("reward_item", strings("minecraft:stick;2;3", "minecraft:bad:identifier;1;0;{x:'v'}"));
        var result = prepare(parse(object)).entries().get("REWARDS");
        assertNull(result.getRewardItem());
        assertEquals(2, result.deferredRewards().size());
        assertEquals(2, result.deferredRewards().getFirst().count());
        assertEquals("{x:'v'}", result.deferredRewards().get(1).rawSnbt());
        assertEquals("{x:\"v\"}", result.deferredRewards().get(1).legacySnbt());
        assertEquals(2, result.deferredDependencies().stream().filter(d -> d.kind() == DependencyKind.REWARD_ITEM).count());
        assertTrue(result.hasUnresolved());
        assertThrows(UnsupportedOperationException.class, () -> result.deferredRewards().clear());
    }

    @Test
    void texturesAndExplicitFocusProtocolMapButItemAndAmbiguousIconsStayDeferred() {
        JsonObject object = entry("ICONS", "BASICS", 1, 2);
        object.add("icons", strings("Thaumcraft:textures/Research/Cat.png", "focus:thaumcraft.SCATTER", "minecraft:stick", "focusstone", "thaumcraft:item;2;3"));
        var result = prepare(parse(object)).entries().get("ICONS");
        assertEquals(id("thaumcraft:textures/research/cat.png"), result.getIcons()[0]);
        assertEquals("focus:thaumcraft.SCATTER", result.getIcons()[1]);
        for (int i = 2; i < 5; i++) assertInstanceOf(ResearchCatalogSpec.IconToken.class, result.getIcons()[i]);
        assertEquals(3, result.deferredDependencies().stream().filter(d -> d.kind() == DependencyKind.ICON).count());
        assertEquals(1, result.deferredDependencies().stream().filter(d -> d.kind() == DependencyKind.FOCUS_ICON).count());
        assertSame(result.rawSpec().icons().values().get(2), result.getIcons()[2]);
    }

    @Test
    void unknownOriginalTypoIsRetainedButNeverActivatedAsKnowledgeRequirement() {
        JsonObject object = entry("TYPO", "BASICS", 1, 2);
        stage(object).add("required_knowledgeauram", strings("OBSERVATION;BASICS;1"));
        Candidate candidate = prepare(parse(object));
        var stage = (PreparedResearchMetadata.Stage) candidate.entries().get("TYPO").getStages()[0];
        assertNull(stage.getKnow());
        assertEquals("[\"OBSERVATION;BASICS;1\"]", stage.rawSpec().unknownFields().get("required_knowledgeauram"));
        assertEquals(1, candidate.spec().diagnostics().stream().filter(d -> d.code().equals("UNKNOWN_FIELD")).count());
        assertFalse(stage.hasUnresolved(), "ignored unknown metadata is not secretly a requirement");
    }

    @Test
    void privateEightSourceFilesMapAllEntriesStagesAddendaAndTokensWithoutInstallingCatalog() throws Exception {
        String configured = System.getProperty("thaumcraft.originalResearchDirectory");
        Path directory = configured == null ? Path.of("..", "reference", "tc6", "src", "assets", "thaumcraft", "research") : Path.of(configured);
        Assumptions.assumeTrue(Files.isDirectory(directory), "Private original fixtures are not packaged");
        Map<String, String> sources = new LinkedHashMap<>();
        for (String name : List.of("basics", "alchemy", "auromancy", "artifice", "infusion", "golemancy", "eldritch", "scans")) {
            sources.put(name + ".json", Files.readString(directory.resolve(name + ".json"), StandardCharsets.UTF_8));
        }
        var original = ResearchCategories.researchCategories;
        Candidate candidate = prepare(ResearchCatalogParser.parse(sources, CATEGORIES));
        assertEquals(148, candidate.entries().size());
        int stages = 0, addenda = 0, requirements = 0, recipes = 0;
        for (var entry : candidate.entries().values()) {
            assertEquals(entry.rawSpec().stages().values().size(), entry.getStages() == null ? 0 : entry.getStages().length);
            if (entry.getStages() != null) for (var rawStage : entry.getStages()) {
                var stage = (PreparedResearchMetadata.Stage) rawStage;
                stages++;
                requirements += stage.getObtain() == null ? 0 : stage.getObtain().length;
                requirements += stage.getCraft() == null ? 0 : stage.getCraft().length;
                recipes += stage.recipeBindings().size();
                assertEquals(stage.rawSpec().requiredItems().values().size(), stage.getObtain() == null ? 0 : stage.getObtain().length);
                assertEquals(stage.rawSpec().requiredCraft().values().size(), stage.getCraft() == null ? 0 : stage.getCraft().length);
                assertNull(stage.getCraftReference());
            }
            if (entry.getAddenda() != null) for (var rawAddendum : entry.getAddenda()) {
                addenda++; recipes += ((PreparedResearchMetadata.Addendum) rawAddendum).recipeBindings().size();
            }
            assertEquals(entry.rawSpec().rewardItems().values(), entry.deferredRewards());
        }
        assertEquals(271, stages); assertEquals(16, addenda); assertEquals(103, requirements); assertEquals(253, recipes);
        assertSame(original, ResearchCategories.researchCategories);
        assertTrue(original.isEmpty());
        assertEquals(sources, candidate.spec().sources());
    }

    private static Candidate prepare(ResearchCatalogSpec spec) {
        return ResearchCatalogPublication.prepareWhole(spec, ResearchCategories.researchCategories, Set.of());
    }

    private static ResearchCatalogSpec parse(JsonObject... entries) {
        JsonArray array = new JsonArray(); for (JsonObject entry : entries) array.add(entry);
        JsonObject root = new JsonObject(); root.add("entries", array);
        return ResearchCatalogParser.parse(Map.of("test.json", root.toString()), CATEGORIES);
    }

    private static JsonObject entry(String key, String category, int column, int row) {
        JsonObject result = new JsonObject();
        result.addProperty("key", key); result.addProperty("name", "research." + key + ".title"); result.addProperty("category", category);
        JsonArray location = new JsonArray(); location.add(column); location.add(row); result.add("location", location);
        JsonObject stage = new JsonObject(); stage.addProperty("text", "stage.text");
        JsonArray stages = new JsonArray(); stages.add(stage); result.add("stages", stages);
        return result;
    }

    private static JsonObject stage(JsonObject entry) { return entry.getAsJsonArray("stages").get(0).getAsJsonObject(); }
    private static JsonArray strings(String... values) {
        JsonArray result = new JsonArray(); for (String value : values) result.add(value); return result;
    }

    private static ResearchCategory addCategory(String key) {
        ResearchCategory category = new ResearchCategory(key, null, new AspectList(), null, null);
        ResearchCategories.researchCategories.put(key, category);
        return category;
    }

    private static ResearchEntry external(String key, String category, int column, int row) {
        ResearchEntry result = new ResearchEntry(); configure(result, key, category, column, row); return result;
    }

    private static void configure(ResearchEntry entry, String key, String category, int column, int row) {
        entry.setKey(key); entry.setCategory(category); entry.setDisplayColumn(column); entry.setDisplayRow(row);
    }

    private static ResourceLocation id(String text) { return ResourceLocation.parse(text); }
    private static void assertBounds(ResearchCategory category, int minColumn, int minRow, int maxColumn, int maxRow) {
        assertEquals(minColumn, category.minDisplayColumn); assertEquals(minRow, category.minDisplayRow);
        assertEquals(maxColumn, category.maxDisplayColumn); assertEquals(maxRow, category.maxDisplayRow);
    }

    private static final class EqualEntry extends ResearchEntry {
        @Override public boolean equals(Object other) { return other instanceof EqualEntry; }
        @Override public int hashCode() { return 1; }
    }
}
