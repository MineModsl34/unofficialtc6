package thaumcraft.gametest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchStatus;
import thaumcraft.api.research.ResearchAddendum;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;

/** A nonempty external catalog is never part of storage fixtures and is restored by identity. */
class ResearchCatalogTestScopeTest {
    private LinkedHashMap<String, ResearchCategory> previous;
    private LinkedHashMap<String, ResearchCategory> original;

    @BeforeEach
    void prepareNonemptyExternalCatalog() {
        previous = ResearchCategories.researchCategories;
        original = new LinkedHashMap<>();
        ResearchCategory basics = category("BASICS");
        ResearchCategory addon = category("TC_PORT_AUDIT");
        basics.formula = new AspectList().add(Aspect.AIR, 7).add(Aspect.EARTH, -3);
        basics.minDisplayColumn = -77; basics.maxDisplayRow = 88;
        ResearchEntry entry = entry("COLLIDING", "BASICS", null, false);
        entry.setName("external.name"); entry.setParents(new String[]{"~Parent@2"});
        entry.setSiblings(new String[]{"Sibling"}); entry.setIcons(new Object[]{"focus:EXTERNAL"});
        entry.setRewardKnow(new ResearchStage.Knowledge[]{new ResearchStage.Knowledge(EnumKnowledgeType.THEORY, basics, 2)});
        ResearchAddendum addendum = new ResearchAddendum(); addendum.setText("original.addendum");
        addendum.setResearch(new String[]{"!original"}); entry.setAddenda(new ResearchAddendum[]{addendum});
        basics.research.put("MAP_ALIAS", entry);
        ResearchStage stage = new ResearchStage(); stage.setText("original.stage"); stage.setWarp(7);
        stage.setResearch(new String[]{"OriginalRequirement"}); stage.setResearchIcon(new String[]{null});
        stage.setCraftReference(new int[]{Integer.MIN_VALUE, 7});
        addon.research.put("EXT_STAGE", entry("EXT_STAGE", "TC_PORT_AUDIT", new ResearchStage[]{stage}, false));
        addon.research.put("PRODUCTION_AUTO", entry("PRODUCTION_AUTO", "TC_PORT_AUDIT", null, true));
        original.put("BASICS", basics); original.put("TC_PORT_AUDIT", addon);
        ResearchCategories.researchCategories = original;
    }

    @AfterEach
    void restoreActualTestEnvironment() { ResearchCategories.researchCategories = previous; }

    @Test
    void richNonemptyOriginalMapCategoryEntryArraysFormulaAndMetadataSurviveFreshScope() {
        var snapshot = ResearchCatalogTestScope.snapshot();
        ResearchCategory category = original.get("BASICS");
        ResearchEntry entry = category.research.get("MAP_ALIAS");
        var entryMap = category.research; var formula = category.formula; var parents = entry.getParents();
        var stages = original.get("TC_PORT_AUDIT").research.get("EXT_STAGE").getStages();
        assertEquals(2, snapshot.categoryCount()); assertEquals(3, snapshot.entryCount());
        var synthetic = synthetic();
        try (var scope = ResearchCatalogTestScope.install(synthetic, snapshot)) {
            assertSame(synthetic, ResearchCategories.researchCategories);
            assertTrue(scope.detached()); assertTrue(snapshot.contentsUnchanged()); assertFalse(snapshot.restored());
            assertNull(ResearchCategories.getResearch("PRODUCTION_AUTO"));
            synthetic.get("BASICS").research.get("COLLIDING").getStages()[0].setText("mutated fixture only");
        }
        assertTrue(snapshot.restored());
        assertSame(original, ResearchCategories.researchCategories); assertSame(category, original.get("BASICS"));
        assertSame(entryMap, category.research); assertSame(entry, category.research.get("MAP_ALIAS"));
        assertSame(parents, entry.getParents()); assertSame(formula, category.formula);
        assertSame(stages, original.get("TC_PORT_AUDIT").research.get("EXT_STAGE").getStages());
        assertEquals("original.stage", stages[0].getText()); assertEquals(7, stages[0].getWarp());
        assertEquals("external.name", entry.getName()); assertEquals(-77, category.minDisplayColumn); assertEquals(88, category.maxDisplayRow);
        assertArrayEquals(new String[]{"~Parent@2"}, parents); assertEquals(-3, formula.getAmount(Aspect.EARTH));
    }

    @Test
    void collidingProductionStagesAndAutoUnlockNeverChangeSyntheticStorageOutcomes() {
        var snapshot = ResearchCatalogTestScope.snapshot();
        try (var ignored = ResearchCatalogTestScope.install(synthetic(), snapshot)) {
            PlayerKnowledge data = new PlayerKnowledge();
            assertTrue(data.getResearchList().isEmpty(), "fresh factory has no implicit catalog unlocks");
            data.addResearch("COLLIDING");
            assertEquals(EnumResearchStatus.IN_PROGRESS, data.getResearchStatus("COLLIDING"), "uses fresh fixture stages, not production's null stages");
            data.deserializeNBT(new net.minecraft.nbt.CompoundTag());
            assertTrue(data.isResearchKnown("SYNTHETIC_AUTO"));
            assertFalse(data.isResearchKnown("PRODUCTION_AUTO"), "real auto-unlock entries never enter replay context");
        }
        assertTrue(snapshot.restored());
        PlayerKnowledge actual = new PlayerKnowledge(); actual.addResearch("COLLIDING");
        assertEquals(EnumResearchStatus.COMPLETE, actual.getResearchStatus("COLLIDING"), "original null-stage metadata restored");
    }

    @Test
    void finallyRestoresNonemptyOriginalAfterNativeFixtureException() {
        var snapshot = ResearchCatalogTestScope.snapshot();
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
            try (var ignored = ResearchCatalogTestScope.install(synthetic(), snapshot)) {
                PlayerKnowledge data = new PlayerKnowledge();
                data.deserializeNBT(net.minecraft.nbt.TagParser.parseTag("{research:[{key:'@'}]}"));
            }
        });
        assertTrue(snapshot.restored()); assertSame(original, ResearchCategories.researchCategories);
    }

    @Test
    void nestedFreshScopesRestoreInnerOuterAndProductionReferencesInLifoOrder() {
        var originalState = ResearchCatalogTestScope.snapshot();
        var outer = synthetic();
        try (var ignored = ResearchCatalogTestScope.install(outer, originalState)) {
            var outerState = ResearchCatalogTestScope.snapshot();
            var inner = synthetic();
            try (var nested = ResearchCatalogTestScope.install(inner, outerState)) {
                assertSame(inner, ResearchCategories.researchCategories); assertTrue(nested.detached());
            }
            assertSame(outer, ResearchCategories.researchCategories); assertTrue(outerState.restored());
        }
        assertSame(original, ResearchCategories.researchCategories); assertTrue(originalState.restored());
    }

    @Test
    void sharedCategoryEntryMapArrayStageAndFormulaAreRejectedBeforeGlobalSwap() {
        var snapshot = ResearchCatalogTestScope.snapshot();
        ResearchCategory old = original.get("BASICS");
        ResearchEntry oldEntry = old.research.get("MAP_ALIAS");
        var sameCategory = synthetic(); sameCategory.put("BASICS", old); rejectedAlias(sameCategory, snapshot);
        var sameEntry = synthetic(); sameEntry.get("BASICS").research.put("BORROWED", oldEntry); rejectedAlias(sameEntry, snapshot);
        var sameMap = synthetic(); sameMap.get("BASICS").research = old.research; rejectedAlias(sameMap, snapshot);
        var sameArray = synthetic(); sameArray.get("BASICS").research.get("COLLIDING").setParents(oldEntry.getParents()); rejectedAlias(sameArray, snapshot);
        var sameStage = synthetic(); sameStage.get("BASICS").research.get("COLLIDING").setStages(
            new ResearchStage[]{original.get("TC_PORT_AUDIT").research.get("EXT_STAGE").getStages()[0]}); rejectedAlias(sameStage, snapshot);
        var sameFormula = synthetic(); sameFormula.get("BASICS").formula = old.formula; rejectedAlias(sameFormula, snapshot);
        rejectedAlias(original, snapshot);
    }

    @Test
    void snapshotDetectsMutablePublicStageArrayAndEntryMapChangesWithoutRegistrySerialization() {
        var state = ResearchCatalogTestScope.snapshot();
        ResearchEntry entry = original.get("TC_PORT_AUDIT").research.get("EXT_STAGE");
        entry.getStages()[0].setWarp(8);
        assertFalse(state.contentsUnchanged()); entry.getStages()[0].setWarp(7); assertTrue(state.contentsUnchanged());
        entry.getStages()[0].getCraftReference()[0] = 0;
        assertFalse(state.contentsUnchanged()); entry.getStages()[0].getCraftReference()[0] = Integer.MIN_VALUE; assertTrue(state.contentsUnchanged());
        var stages = entry.getStages(); entry.setStages(stages.clone());
        assertFalse(state.contentsUnchanged(), "equal array content is not the same original array identity"); entry.setStages(stages);
        assertTrue(state.contentsUnchanged());
        original.get("BASICS").research.put("newAlias", original.get("BASICS").research.get("MAP_ALIAS"));
        assertFalse(state.contentsUnchanged()); original.get("BASICS").research.remove("newAlias"); assertTrue(state.contentsUnchanged());
    }

    @Test
    void closeRestoresReferenceButReportsUnexpectedMutationInsteadOfPretendingItWasPreserved() {
        var state = ResearchCatalogTestScope.snapshot();
        var scope = ResearchCatalogTestScope.install(synthetic(), state);
        ResearchEntry originalEntry = original.get("BASICS").research.get("MAP_ALIAS");
        originalEntry.setName("unexpected external mutation");
        assertThrows(IllegalStateException.class, scope::close);
        assertSame(original, ResearchCategories.researchCategories);
        assertFalse(state.restored());
        originalEntry.setName("external.name"); assertTrue(state.restored());
        scope.close(); // idempotent: never swaps a catalog again after a closed scope.
    }

    @Test
    void staleSnapshotOrUnexpectedScopeReplacementFailsExplicitlyWithOriginalReferenceRestored() {
        var state = ResearchCatalogTestScope.snapshot();
        original.get("BASICS").maxDisplayRow = 99;
        assertThrows(IllegalStateException.class, () -> ResearchCatalogTestScope.install(synthetic(), state));
        assertSame(original, ResearchCategories.researchCategories); original.get("BASICS").maxDisplayRow = 88;
        try (var scope = ResearchCatalogTestScope.install(synthetic(), state)) {
            ResearchCategories.researchCategories = new LinkedHashMap<>();
            assertThrows(IllegalStateException.class, scope::close);
        }
        assertTrue(state.restored());
    }

    private void rejectedAlias(LinkedHashMap<String, ResearchCategory> map, ResearchCatalogTestScope.Snapshot snapshot) {
        assertThrows(IllegalStateException.class, () -> ResearchCatalogTestScope.install(map, snapshot));
        assertSame(original, ResearchCategories.researchCategories); assertTrue(snapshot.restored());
    }

    private static LinkedHashMap<String, ResearchCategory> synthetic() {
        var map = new LinkedHashMap<String, ResearchCategory>();
        ResearchCategory category = category("BASICS");
        ResearchStage stage = new ResearchStage(); stage.setText("fresh synthetic stage");
        category.research.put("COLLIDING", entry("COLLIDING", "BASICS", new ResearchStage[]{stage}, false));
        category.research.put("SYNTHETIC_AUTO", entry("SYNTHETIC_AUTO", "BASICS", null, true));
        map.put("BASICS", category); return map;
    }

    private static ResearchCategory category(String key) {
        ResearchCategory category = new ResearchCategory(key, "gate." + key, null, null, null);
        category.research = new LinkedHashMap<>(); return category;
    }

    private static ResearchEntry entry(String key, String category, ResearchStage[] stages, boolean auto) {
        ResearchEntry result = new ResearchEntry(); result.setKey(key); result.setCategory(category); result.setStages(stages);
        result.setMeta(auto ? new ResearchEntry.EnumResearchMeta[]{ResearchEntry.EnumResearchMeta.AUTOUNLOCK} : null); return result;
    }
}
