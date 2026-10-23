package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEntry.EnumResearchMeta;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.research.ResearchBrowserAvailability.Reason;

/** Bootstrap-free source availability rules, not GUI rendering/C2S or gameplay acceptance. */
class ResearchBrowserAvailabilityTest {
    private LinkedHashMap<String, ResearchCategory> previous;
    private PlayerKnowledge knowledge;

    @BeforeEach
    void isolate() {
        previous = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
        ResearchCategories.researchCategories.put("TEST", new ResearchCategory("TEST", null, null, null, null));
        knowledge = new PlayerKnowledge();
    }

    @AfterEach
    void restore() { ResearchCategories.researchCategories = previous; }

    @Test
    void ordinaryUnknownNodeCanBeVisibleWhileItsStrictParentLocksTheAction() {
        entry("PARENT", 2);
        entry("CHILD", 1).setParents(new String[]{"PARENT"});
        var locked = query("CHILD");
        assertFalse(locked.known()); assertTrue(locked.visible()); assertTrue(locked.categoryAccessible());
        assertFalse(locked.parentsSatisfied()); assertFalse(locked.mayRequestProgress());
        assertEquals(Reason.MISSING_PARENTS, locked.reason());
        knowledge.addResearch("PARENT"); knowledge.setResearchStage("PARENT", 2);
        assertFalse(query("CHILD").parentsSatisfied());
        knowledge.setResearchStage("PARENT", 3);
        var start = query("CHILD");
        assertTrue(start.parentsSatisfied()); assertTrue(start.mayRequestProgress());
        assertEquals(Reason.START_UNKNOWN, start.reason());
    }

    @Test
    void hiddenUnknownNullParentsAreInvisibleButAnEmptyArrayCanUnlock() {
        hidden(entry("NULL_PARENTS", 1));
        hidden(entry("EMPTY_PARENTS", 1)).setParents(new String[0]);
        var absent = query("NULL_PARENTS");
        assertTrue(absent.parentsSatisfied()); assertFalse(absent.visible());
        assertFalse(absent.mayRequestProgress()); assertEquals(Reason.NOT_VISIBLE, absent.reason());
        var empty = query("EMPTY_PARENTS");
        assertTrue(empty.visible()); assertTrue(empty.parentsSatisfied()); assertTrue(empty.mayRequestProgress());
    }

    @Test
    void knownHiddenZeroResumesExplicitlyAndKnownCompletionNeverRequestsAnotherTransition() {
        hidden(entry("KNOWN", 1));
        knowledge.addResearch("KNOWN");
        var before = knowledge.serializeNBT().copy();
        var zero = query("KNOWN");
        assertTrue(zero.known()); assertTrue(zero.visible()); assertTrue(zero.mayRequestProgress());
        assertEquals(Reason.RESUME_KNOWN_ZERO, zero.reason());
        assertEquals(before, knowledge.serializeNBT()); // Not a hidden grant or auto-resume.
        knowledge.setResearchStage("KNOWN", 1);
        assertEquals(Reason.ADVANCE_KNOWN, query("KNOWN").reason());
        knowledge.setResearchStage("KNOWN", 2);
        assertEquals(Reason.COMPLETE, query("KNOWN").reason());
        assertFalse(query("KNOWN").mayRequestProgress());
        entry("NO_STAGES", 0); knowledge.addResearch("NO_STAGES");
        assertTrue(query("NO_STAGES").visible());
        assertEquals(Reason.COMPLETE, query("NO_STAGES").reason());
    }

    @Test
    void knownNodesBypassHiddenVisibilityRecursionButNotServerParentRequisites() {
        hidden(entry("INVISIBLE_PARENT", 1));
        hidden(entry("KNOWN_CHILD", 1)).setParents(new String[]{"INVISIBLE_PARENT"});
        knowledge.addResearch("KNOWN_CHILD");
        var result = query("KNOWN_CHILD");
        assertTrue(result.known()); assertTrue(result.visible());
        assertFalse(result.parentsSatisfied()); assertFalse(result.mayRequestProgress());
        assertEquals(Reason.MISSING_PARENTS, result.reason());
    }

    @Test
    void lockedCategoryBlocksOnlyUnknownStartsAndKnownSearchEntriesRemainAvailable() {
        entry("CATEGORY_GATE", 1);
        ResearchCategories.getResearchCategory("TEST").researchKey = "CATEGORY_GATE";
        entry("UNKNOWN", 1); entry("KNOWN", 2);
        var unknown = query("UNKNOWN");
        assertTrue(unknown.visible()); assertFalse(unknown.categoryAccessible());
        assertFalse(unknown.mayRequestProgress()); assertEquals(Reason.CATEGORY_LOCKED, unknown.reason());
        knowledge.addResearch("KNOWN"); knowledge.setResearchStage("KNOWN", 1);
        var searched = query("KNOWN");
        assertTrue(searched.visible()); assertFalse(searched.categoryAccessible());
        assertTrue(searched.mayRequestProgress()); assertEquals(Reason.ADVANCE_KNOWN, searched.reason());
        knowledge.addResearch("CATEGORY_GATE"); knowledge.setResearchStage("CATEGORY_GATE", 1);
        assertFalse(query("UNKNOWN").categoryAccessible());
        knowledge.setResearchStage("CATEGORY_GATE", 2);
        assertTrue(query("UNKNOWN").categoryAccessible()); assertTrue(query("UNKNOWN").mayRequestProgress());
    }

    @Test
    void rawTildeAndQualifiedParentsSkipMissingLiteralVisibilityLookupsButUseStrictSourceQueries() {
        hidden(entry("P", 2));
        entry("RAW", 1).setParents(new String[]{"P"});
        entry("TILDE", 1).setParents(new String[]{"~P"});
        entry("QUALIFIED", 1).setParents(new String[]{"P@1"});
        assertFalse(query("RAW").visible());
        assertTrue(query("TILDE").visible()); assertFalse(query("TILDE").parentsSatisfied());
        assertTrue(query("QUALIFIED").visible()); assertFalse(query("QUALIFIED").parentsSatisfied());
        knowledge.addResearch("P"); knowledge.setResearchStage("P", 1);
        assertTrue(query("QUALIFIED").parentsSatisfied()); assertTrue(query("QUALIFIED").mayRequestProgress());
        assertFalse(query("TILDE").parentsSatisfied());
        knowledge.setResearchStage("P", 3);
        assertTrue(query("TILDE").mayRequestProgress());
    }

    @Test
    void aMissingParentEntryIsSkippedForVisibilityButItsOpaqueMarkerStillMustBeKnown() {
        entry("CHILD", 1).setParents(new String[]{"!opaque_parent"});
        var before = query("CHILD");
        assertTrue(before.visible()); assertFalse(before.parentsSatisfied());
        knowledge.addResearch("!opaque_parent");
        assertTrue(query("CHILD").mayRequestProgress());
    }

    @Test
    void parentAndCategoryExpressionsReuseOriginalRegexOrFallthroughAndEarlyReturn() {
        hidden(entry("HIDDEN", 1)).setParents(new String[]{"ALPHA||BETA", "MISSING"});
        assertFalse(query("HIDDEN").parentsSatisfied());
        knowledge.addResearch("MISSING");
        assertTrue(query("HIDDEN").mayRequestProgress()); // Failed OR falls through.
        knowledge.removeResearch("MISSING"); knowledge.addResearch("A");
        assertTrue(query("HIDDEN").parentsSatisfied()); // Character 'A' skips MISSING.
        assertTrue(ThaumcraftCapabilities.knowsResearchStrictData(knowledge, "ALPHA||BETA", "MISSING"));
        ResearchCategories.getResearchCategory("TEST").researchKey = "NOPE||XYZ";
        assertTrue(query("HIDDEN").categoryAccessible()); // Failed category OR falls through too.
        ResearchCategories.getResearchCategory("TEST").researchKey = "ABSENT&&NEVER";
        assertFalse(query("HIDDEN").categoryAccessible());
    }

    @Test
    void qualifiedCategoryGateUsesKnownStageRatherThanCompletion() {
        entry("GATE", 2); entry("CHILD", 1);
        ResearchCategories.getResearchCategory("TEST").researchKey = "GATE@1";
        knowledge.addResearch("GATE"); knowledge.setResearchStage("GATE", 1);
        assertFalse(knowledge.isResearchComplete("GATE"));
        assertTrue(query("CHILD").categoryAccessible()); assertTrue(query("CHILD").mayRequestProgress());
    }

    @Test
    void visibilityCyclesFailClosedButKnownNodesBreakTheRecursion() {
        entry("A", 1).setParents(new String[]{"B"});
        entry("B", 1).setParents(new String[]{"A"});
        var before = knowledge.serializeNBT().copy();
        var cycle = query("A");
        assertFalse(cycle.visible()); assertFalse(cycle.mayRequestProgress());
        assertEquals(Reason.VISIBILITY_CYCLE, cycle.reason()); assertEquals(before, knowledge.serializeNBT());
        knowledge.addResearch("A");
        var known = query("A");
        assertTrue(known.visible()); assertEquals(Reason.MISSING_PARENTS, known.reason());
        knowledge.addResearch("B"); knowledge.setResearchStage("B", 2);
        assertTrue(query("A").mayRequestProgress());
    }

    @Test
    void visibilityDepthAndWideMissingParentLookupsAreBounded() {
        for (int i = 0; i < ResearchBrowserAvailability.MAX_VISIBILITY_DEPTH; i++) {
            var node = entry("DEPTH_" + i, 1);
            if (i + 1 < ResearchBrowserAvailability.MAX_VISIBILITY_DEPTH) node.setParents(new String[]{"DEPTH_" + (i + 1)});
        }
        assertTrue(query("DEPTH_0").visible());
        entry("TOO_DEEP", 1).setParents(new String[]{"DEPTH_0"});
        var deep = query("TOO_DEEP");
        assertEquals(Reason.DEPTH_LIMIT, deep.reason()); assertFalse(deep.mayRequestProgress());
        var wide = entry("WIDE", 1);
        String[] parents = new String[ResearchBrowserAvailability.MAX_VISIBILITY_VISITS];
        for (int i = 0; i < parents.length; i++) parents[i] = "MISSING_" + i;
        wide.setParents(parents);
        assertEquals(Reason.VISIT_LIMIT, query("WIDE").reason());
        assertFalse(query("WIDE").mayRequestProgress());
    }

    @Test
    void metadataChangesAreReflectedWithoutStickyInvisibleOrCategoryCaches() {
        var parent = hidden(entry("PARENT", 1));
        entry("CHILD", 1).setParents(new String[]{"PARENT"});
        var first = query("CHILD");
        assertFalse(first.visible());
        parent.setMeta(null);
        var changed = query("CHILD");
        assertTrue(changed.visible()); assertFalse(changed.mayRequestProgress());
        knowledge.addResearch("PARENT"); knowledge.setResearchStage("PARENT", 2);
        assertTrue(query("CHILD").mayRequestProgress());
        ResearchCategories.getResearchCategory("TEST").researchKey = "new_gate";
        assertFalse(query("CHILD").categoryAccessible());
        assertFalse(first.visible()); // A returned decision is immutable, not a live view.
    }

    @Test
    void malformedDescriptorsAndUnknownEntriesRefuseWithoutChangingDataOrNativeAdapters() {
        var staged = entry("BAD_PARENT", 1); staged.setParents(new String[]{"@"});
        CompoundTag before = knowledge.serializeNBT().copy();
        assertEquals(Reason.MALFORMED, query("BAD_PARENT").reason());
        assertFalse(query("BAD_PARENT").mayRequestProgress());
        assertThrows(ArrayIndexOutOfBoundsException.class,
            () -> ThaumcraftCapabilities.knowsResearchStrictData(knowledge, "@"));
        assertEquals(Reason.MALFORMED, query(null).reason());
        assertEquals(Reason.UNKNOWN_ENTRY, query("missing").reason());
        assertEquals(before, knowledge.serializeNBT());
        knowledge.addResearch("!not_in_catalog");
        var marker = query("!not_in_catalog");
        assertTrue(marker.known()); assertFalse(marker.visible()); assertFalse(marker.mayRequestProgress());
        assertEquals(Reason.UNKNOWN_ENTRY, marker.reason());
        var missingCategory = entry("BAD_CATEGORY", 1); missingCategory.setCategory("MISSING");
        assertEquals(Reason.MISSING_CATEGORY, query("BAD_CATEGORY").reason());
        CompoundTag afterMarker = knowledge.serializeNBT().copy();
        ResearchCategories.getResearchCategory("TEST").research = null;
        assertEquals(Reason.MALFORMED, query("BAD_CATEGORY").reason());
        assertEquals(afterMarker, knowledge.serializeNBT());
        assertThrows(NullPointerException.class, () -> ResearchBrowserAvailability.evaluate(null, "BAD_CATEGORY"));
    }

    @Test
    void nullParentElementsRetainSourceLiteralNullCoercionRatherThanBeingFiltered() {
        entry("CHILD", 1).setParents(new String[]{null});
        assertTrue(query("CHILD").visible()); assertFalse(query("CHILD").parentsSatisfied());
        knowledge.addResearch("null");
        assertTrue(query("CHILD").mayRequestProgress());
    }

    private ResearchBrowserAvailability.Decision query(String key) {
        return ResearchBrowserAvailability.evaluate(knowledge, key);
    }

    private ResearchEntry entry(String key, int stageCount) {
        var entry = new ResearchEntry();
        entry.setKey(key); entry.setName("test"); entry.setCategory("TEST");
        if (stageCount > 0) {
            ResearchStage[] stages = new ResearchStage[stageCount];
            for (int i = 0; i < stages.length; i++) stages[i] = new ResearchStage();
            entry.setStages(stages);
        }
        ResearchCategories.getResearchCategory("TEST").research.put(key, entry);
        return entry;
    }

    private static ResearchEntry hidden(ResearchEntry entry) {
        entry.setMeta(new EnumResearchMeta[]{EnumResearchMeta.HIDDEN});
        return entry;
    }
}
