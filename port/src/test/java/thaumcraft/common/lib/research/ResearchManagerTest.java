package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.internal.DummyInternalMethodHandler;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchAddendum;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.research.catalog.PreparedResearchMetadata;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCatalogPublication;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;

/** Pure policy/data semantics, not a substitute for production event/packet/server acceptance. */
class ResearchManagerTest {
    private LinkedHashMap<String, ResearchCategory> previous;
    private boolean previousNoFlags;

    @BeforeEach
    void isolate() {
        previous = ResearchCategories.researchCategories;
        previousNoFlags = ResearchManager.noFlags;
        ResearchCategories.researchCategories = ResearchCategoryDefinitions.newBuiltinCategories();
        ResearchManager.noFlags = false;
    }

    @AfterEach
    void restore() {
        ResearchCategories.researchCategories = previous;
        ResearchManager.noFlags = previousNoFlags;
    }

    @Test
    void unknownOpaqueMarkersStayStageZeroWithoutFlagsOrCatalogNormalization() {
        for (String key : new String[]{"!ignis", "!minecraft:stone0", "f_SPIDER", "[#]-123456", "MiXeD marker"}) {
            var data = new PlayerKnowledge();
            assertNull(ResearchManager.unsupportedReason(null));
            assertFalse(ResearchManager.advanceSimpleResearch(data, key, null, true));
            assertTrue(data.isResearchComplete(key));
            assertEquals(0, data.getResearchStage(key));
            assertEquals(java.util.Set.of(key), data.getResearchList());
            assertFalse(data.hasResearchFlag(key, EnumResearchFlag.POPUP));
            assertFalse(data.hasResearchFlag(key, EnumResearchFlag.RESEARCH));
            assertFalse(data.hasResearchFlag(key, EnumResearchFlag.PAGE));
        }
    }

    @Test
    void qualifiedUnknownTargetRetainsSingleCallStorageQuirkWithoutCompletionLoop() {
        var data = new PlayerKnowledge();
        assertFalse(ResearchManager.advanceSimpleResearch(data, "ABSENT@1", null, true));
        assertTrue(data.getResearchList().contains("ABSENT@1"));
        assertFalse(data.getResearchList().contains("ABSENT"));
        assertFalse(data.isResearchKnown("ABSENT@1"));
        assertEquals(0, data.getResearchStage("ABSENT@1"));
    }

    @Test
    void knownSimpleStageStartsAndCompletesAtTwoAndExistingStageOneCompletes() {
        ResearchEntry entry = entry("SIMPLE", new ResearchStage[]{new ResearchStage()});
        ResearchCategories.researchCategories.get("BASICS").research.put(entry.getKey(), entry);
        assertNull(ResearchManager.unsupportedReason(entry));
        var fresh = new PlayerKnowledge();
        assertTrue(ResearchManager.advanceSimpleResearch(fresh, entry.getKey(), entry, true));
        assertEquals(2, fresh.getResearchStage(entry.getKey()));
        assertTrue(fresh.isResearchComplete(entry.getKey()));
        assertTrue(fresh.hasResearchFlag(entry.getKey(), EnumResearchFlag.POPUP));
        assertTrue(fresh.hasResearchFlag(entry.getKey(), EnumResearchFlag.RESEARCH));
        var existing = new PlayerKnowledge();
        existing.addResearch(entry.getKey());
        existing.setResearchStage(entry.getKey(), 1);
        assertTrue(ResearchManager.advanceSimpleResearch(existing, entry.getKey(), entry, false));
        assertEquals(2, existing.getResearchStage(entry.getKey()));
        assertFalse(existing.hasResearchFlag(entry.getKey(), EnumResearchFlag.POPUP));
    }

    @Test
    void nullStagesCompleteAtZeroAndNoFlagsOnlyConsumesOnKnownSyncedCompletion() {
        ResearchEntry entry = entry("NULL_STAGES", null);
        assertNull(ResearchManager.unsupportedReason(entry));
        var data = new PlayerKnowledge();
        ResearchManager.noFlags = true;
        ResearchManager.advanceSimpleResearch(data, "!aer", null, true);
        assertTrue(ResearchManager.noFlags);
        ResearchManager.advanceSimpleResearch(data, entry.getKey(), entry, false);
        assertTrue(ResearchManager.noFlags);
        assertEquals(0, data.getResearchStage(entry.getKey()));
        ResearchManager.advanceSimpleResearch(data, entry.getKey(), entry, true);
        assertFalse(ResearchManager.noFlags);
        assertTrue(data.hasResearchFlag(entry.getKey(), EnumResearchFlag.POPUP));
        assertFalse(data.hasResearchFlag(entry.getKey(), EnumResearchFlag.RESEARCH));
    }

    @Test
    void qualifiedKnownBaseWithoutExactStageDoesNotInventCompletionPopup() {
        ResearchEntry entry = entry("BASE@2", new ResearchStage[]{new ResearchStage()});
        var data = new PlayerKnowledge();
        data.addResearch("BASE");
        data.setResearchStage("BASE", 2);
        assertTrue(data.isResearchKnown(entry.getKey()));
        assertFalse(ResearchManager.advanceSimpleResearch(data, entry.getKey(), entry, true));
        assertEquals(-1, data.getResearchStage(entry.getKey()));
        assertFalse(data.hasResearchFlag(entry.getKey(), EnumResearchFlag.POPUP));
    }

    @Test
    void malformedArraysFailClosedButAValidMultistageStartIsSafe() {
        assertNotNull(ResearchManager.unsupportedReason(entry("EMPTY", new ResearchStage[0])));
        assertNull(ResearchManager.unsupportedReason(entry("MULTI", new ResearchStage[]{new ResearchStage(), new ResearchStage()})));
        assertNotNull(ResearchManager.unsupportedReason(entry("NULL_ELEMENT", new ResearchStage[]{null})));
    }

    @Test
    void evenEmptyNonNullArraysExposeStageOneAndRequireCheckedAdvance() {
        for (int kind = 0; kind < 4; kind++) {
            var stage = new ResearchStage();
            switch (kind) {
                case 0 -> stage.setObtain(new Object[0]);
                case 1 -> stage.setCraft(new Object[0]);
                case 2 -> stage.setKnow(new ResearchStage.Knowledge[0]);
                case 3 -> stage.setResearch(new String[0]);
                default -> throw new AssertionError();
            }
            var entry = put(entry("REQ", new ResearchStage[]{stage}));
            var data = new PlayerKnowledge();
            assertNull(ResearchManager.unsupportedReason(entry));
            assertNull(ResearchManager.transitionReason(data, "REQ", true, false, false));
            assertFalse(ResearchManager.advanceSimpleResearch(data, "REQ", entry, true));
            assertEquals(1, data.getResearchStage("REQ"));
            assertNotNull(ResearchManager.transitionReason(data, "REQ", true, false, false));
            assertNull(ResearchManager.transitionReason(data, "REQ", true, true, false));
        }
    }

    @Test
    void executedWarpIsSupportedButMalformedKnowledgeRewardsStillFailClosed() {
        var stage = new ResearchStage();
        stage.setWarp(1);
        assertNull(ResearchManager.unsupportedReason(entry("WARP", new ResearchStage[]{stage})));
        var sibling = entry("SIBLING", null);
        sibling.setSiblings(new String[]{"OTHER"});
        put(sibling);
        assertNull(ResearchManager.unsupportedReason(sibling));
        assertNull(ResearchManager.transitionReason(new PlayerKnowledge(), "SIBLING", true, false, false));
        var reward = entry("REWARD", null);
        reward.setRewardKnow(new ResearchStage.Knowledge[]{new ResearchStage.Knowledge(EnumKnowledgeType.THEORY, null, 1)});
        assertNotNull(ResearchManager.unsupportedReason(reward));
    }

    @Test
    void rawRewardsRemainPresentEvenThoughTypedRewardItemGetterIsNull() {
        var entry = prepared("\"reward_item\":[\"addon:missing_reward;2;0\"],", "{\"text\":\"stage\"}");
        assertNull(entry.getRewardItem());
        assertEquals(1, entry.rawSpec().rewardItems().values().size());
        assertEquals("addon:missing_reward", entry.rawSpec().rewardItems().values().getFirst().name());
        // Actual registry resolution/refusal belongs to ResearchItemResolverGameTests:
        // plain JUnit has no NeoForge LoadingModList or live ItemStack registries.
    }

    @Test
    void rawRequirementsCannotBeBypassedByClearingMutableApiArrays() {
        var entry = prepared("", "{\"text\":\"stage\",\"required_item\":[\"addon:missing;1;0\"]}");
        entry.getStages()[0].setObtain(null);
        put(entry);
        var data = new PlayerKnowledge();
        assertNull(ResearchManager.unsupportedReason(entry));
        // The raw requirement still exposes a first stage rather than a free completion.
        assertFalse(ResearchManager.advanceSimpleResearch(data, "TEST", entry, true));
        assertEquals(1, data.getResearchStage("TEST"));
        assertTrue(ResearchManager.transitionReason(data, "TEST", true, false, false).contains("Active stage requirements"));
        ResearchCategories.getResearchCategory("BASICS").research.remove("TEST");
        var multi = prepared("", "{\"text\":\"one\"},{\"text\":\"two\"}");
        multi.setStages(null);
        assertTrue(ResearchManager.unsupportedReason(multi).contains("Raw stage structure"));
        assertThrows(IllegalArgumentException.class, () -> LegacyResearchStageTransition.calculate(multi, 0));
    }

    @Test
    void cosmeticItemIconsAndRecipeLookupsDoNotDisableVerifiedSimpleResearch() {
        var entry = prepared("\"icons\":[\"addon:missing_icon\"],", "{\"text\":\"stage\",\"recipes\":[\"addon:missing_recipe\"]}");
        assertTrue(entry.hasUnresolved());
        assertNull(ResearchManager.unsupportedReason(entry));
    }

    @Test
    void futureRequirementsWarpAndRewardsDoNotBlockStartButExecutedEffectsDo() {
        var future = new ResearchStage();
        future.setObtain(new Object[]{"unresolved"});
        future.setWarp(3);
        var result = put(entry("FUTURE", new ResearchStage[]{future, new ResearchStage()}));
        result.setRewardKnow(new ResearchStage.Knowledge[]{new ResearchStage.Knowledge(EnumKnowledgeType.THEORY, null, 1)});
        var data = new PlayerKnowledge();
        assertNull(ResearchManager.transitionReason(data, "FUTURE", true, false, false));
        assertFalse(ResearchManager.advanceSimpleResearch(data, "FUTURE", result, true));
        assertEquals(1, data.getResearchStage("FUTURE"));
        // Checked costs permit actual warp. Invalid reward categories still reject BEFORE commit.
        assertTrue(ResearchManager.transitionReason(data, "FUTURE", true, true, false).contains("knowledge reward category"));
        future.setWarp(0);
        assertTrue(ResearchManager.transitionReason(data, "FUTURE", true, true, false).contains("knowledge reward category"));
        assertNull(ResearchManager.transitionReason(data, "FUTURE", false, true, false));
    }

    @Test
    void startAndLastPageSkippingAreNotAFullEmptyStageSkippingLoop() {
        var first = new ResearchStage();
        first.setResearch(new String[0]);
        var middle = new ResearchStage();
        var result = put(entry("THREE", new ResearchStage[]{first, middle, new ResearchStage()}));
        var data = new PlayerKnowledge();
        assertFalse(ResearchManager.advanceSimpleResearch(data, "THREE", result, false));
        assertEquals(1, data.getResearchStage("THREE"));
        assertNull(ResearchManager.transitionReason(data, "THREE", false, true, false));
        assertFalse(ResearchManager.advanceSimpleResearch(data, "THREE", result, false));
        assertEquals(2, data.getResearchStage("THREE"));
        assertTrue(ResearchManager.advanceSimpleResearch(data, "THREE", result, false));
        assertEquals(4, data.getResearchStage("THREE"));
    }

    @Test
    void nullLastPageIsSkippedButAnEmptyNonNullRequirementArrayIsNot() {
        var last = new ResearchStage();
        last.setKnow(new ResearchStage.Knowledge[0]);
        var result = put(entry("TWO", new ResearchStage[]{new ResearchStage(), last}));
        var data = new PlayerKnowledge();
        ResearchManager.advanceSimpleResearch(data, "TWO", result, false);
        assertEquals(1, data.getResearchStage("TWO"));
        assertFalse(ResearchManager.advanceSimpleResearch(data, "TWO", result, false));
        assertEquals(2, data.getResearchStage("TWO"));
        assertNotNull(ResearchManager.transitionReason(data, "TWO", false, false, false));
        assertNull(ResearchManager.transitionReason(data, "TWO", false, true, false));
        assertTrue(ResearchManager.advanceSimpleResearch(data, "TWO", result, false));
        assertEquals(3, data.getResearchStage("TWO"));
    }

    @Test
    void completePreflightRejectsFutureCostWithoutBlockingSafeSingleStart() {
        var active = new ResearchStage();
        active.setCraft(new Object[]{"deferred"});
        put(entry("COST", new ResearchStage[]{active, new ResearchStage()}));
        var data = new PlayerKnowledge();
        assertNull(ResearchManager.transitionReason(data, "COST", true, false, false));
        assertTrue(ResearchManager.transitionReason(data, "COST", true, false, true).contains("requirements"));
        assertTrue(data.getResearchList().isEmpty());
    }

    @Test
    void entireSiblingCompletionIsPreflightedAndParentCheckedAuthorityIsNotForwarded() {
        var parent = put(entry("PARENT", new ResearchStage[]{new ResearchStage(), new ResearchStage(), new ResearchStage()}));
        parent.setSiblings(new String[]{"SAFE", "CHILD"});
        put(entry("SAFE", null));
        var active = new ResearchStage();
        active.setResearch(new String[]{"MISSING"});
        put(entry("CHILD", new ResearchStage[]{active, new ResearchStage()}));
        var data = new PlayerKnowledge();
        CompoundTag before = data.serializeNBT().copy();
        assertTrue(ResearchManager.transitionReason(data, "PARENT", true, false, false).contains("requirements"));
        assertTrue(ResearchManager.transitionReason(data, "PARENT", true, true, false).contains("requirements"));
        assertEquals(before, data.serializeNBT());
        assertTrue(data.getResearchList().isEmpty());
    }

    @Test
    void completedAndStageIncreasingSiblingBackEdgesAreFinite() {
        var a = put(entry("A", new ResearchStage[]{new ResearchStage()}));
        var b = put(entry("B", new ResearchStage[]{new ResearchStage()}));
        a.setSiblings(new String[]{"B"});
        b.setSiblings(new String[]{"A"});
        var data = new PlayerKnowledge();
        assertNull(ResearchManager.transitionReason(data, "A", true, false, false));
        a.setStages(new ResearchStage[]{new ResearchStage(), new ResearchStage(), new ResearchStage()});
        assertNull(ResearchManager.transitionReason(data, "A", true, false, false));
        b.setStages(new ResearchStage[]{new ResearchStage(), new ResearchStage()});
        assertNull(ResearchManager.transitionReason(data, "A", true, false, false));
        assertNull(ResearchManager.transitionReason(data, "A", true, false, true));
        assertTrue(data.getResearchList().isEmpty(), "Projection must not write the real storage");
    }

    @Test
    void qualifiedSingleMarkerRemainsNativeButCompletionAndSiblingLoopsAreBounded() {
        var data = new PlayerKnowledge();
        assertNull(ResearchManager.transitionReason(data, "ABSENT@1", true, false, false));
        assertTrue(ResearchManager.transitionReason(data, "ABSENT@1", true, false, true).contains("Non-progressing"));
        var parent = put(entry("PARENT", null));
        parent.setSiblings(new String[]{"ABSENT@1"});
        assertTrue(ResearchManager.transitionReason(data, "PARENT", true, false, false).contains("Non-progressing"));
        assertTrue(data.getResearchList().isEmpty());
    }

    @Test
    void invalidPersistedStageSanitizesToZeroAndExcessiveSiblingDepthFailsClosed() {
        put(entry("NEGATIVE", new ResearchStage[]{new ResearchStage()}));
        var raw = new CompoundTag();
        var list = new ListTag();
        var row = new CompoundTag();
        row.putString("key", "NEGATIVE");
        row.putInt("stage", -2);
        list.add(row);
        raw.put("research", list);
        var data = new PlayerKnowledge();
        data.deserializeNBT(raw);
        // Both TC6 and the modern storage only load positive stages. Invalid persisted
        // values become known/unstarted zero, not an attainable negative-stage loop.
        assertEquals(0, data.getResearchStage("NEGATIVE"));
        assertNull(ResearchManager.transitionReason(data, "NEGATIVE", true, false, true));
        for (int i = 0; i < 130; i++) {
            var entry = put(entry("DEPTH" + i, null));
            if (i < 129) entry.setSiblings(new String[]{"DEPTH" + (i + 1)});
        }
        var automatic = put(entry("AUTO", null));
        automatic.setMeta(new ResearchEntry.EnumResearchMeta[]{ResearchEntry.EnumResearchMeta.AUTOUNLOCK});
        var empty = new PlayerKnowledge();
        assertTrue(ResearchManager.transitionReason(empty, "DEPTH0", true, false, false).contains("recursive"));
        assertTrue(empty.getResearchList().isEmpty());
        assertEquals(-1, empty.getResearchStage("AUTO"));
    }

    @Test
    void immutableRawParentSiblingAndStageShapeCannotBeErasedBeforeCall() {
        var result = prepared("\"parents\":[\"PARENT\"],\"siblings\":[\"SIBLING\"],", "{\"text\":\"one\"},{\"text\":\"two\"}");
        put(result);
        result.setParents(null);
        assertTrue(ResearchManager.transitionReason(new PlayerKnowledge(), "TEST", true, false, false).contains("Raw parent"));
        result.setParents(new String[]{"PARENT"});
        result.setSiblings(new String[0]);
        assertTrue(ResearchManager.transitionReason(new PlayerKnowledge(), "TEST", true, false, false).contains("Raw sibling"));
        result.setSiblings(new String[]{"SIBLING"});
        result.setStages(new ResearchStage[]{new ResearchStage()});
        assertTrue(ResearchManager.transitionReason(new PlayerKnowledge(), "TEST", true, false, false).contains("Raw stage structure"));
    }

    @Test
    void descriptorSnapshotDetectsReplacementAndInPlaceMutableSemanticChanges() {
        var stage = new ResearchStage();
        var category = ResearchCategories.getResearchCategory("BASICS");
        var cost = new ResearchStage.Knowledge(EnumKnowledgeType.OBSERVATION, category, 16);
        stage.setKnow(new ResearchStage.Knowledge[]{cost});
        stage.setResearch(new String[]{"A"});
        stage.setCraftReference(new int[]{123});
        var result = put(entry("SNAPSHOT", new ResearchStage[]{stage}));
        var addendum = new ResearchAddendum();
        addendum.setResearch(new String[]{"OTHER"});
        result.setAddenda(new ResearchAddendum[]{addendum});
        assertTrue(ResearchManager.captureDescriptorSnapshot().unchanged());
        var snapshot = ResearchManager.captureDescriptorSnapshot();
        cost.amount++;
        assertFalse(snapshot.unchanged());
        cost.amount--;
        snapshot = ResearchManager.captureDescriptorSnapshot();
        stage.getResearch()[0] = "B";
        assertFalse(snapshot.unchanged());
        snapshot = ResearchManager.captureDescriptorSnapshot();
        stage.getCraftReference()[0]++;
        assertFalse(snapshot.unchanged());
        snapshot = ResearchManager.captureDescriptorSnapshot();
        addendum.getResearch()[0] = "DIFFERENT";
        assertFalse(snapshot.unchanged());
        snapshot = ResearchManager.captureDescriptorSnapshot();
        result.getStages()[0] = new ResearchStage();
        assertFalse(snapshot.unchanged());
        snapshot = ResearchManager.captureDescriptorSnapshot();
        category.research = new LinkedHashMap<>(category.research);
        assertFalse(snapshot.unchanged());
        snapshot = ResearchManager.captureDescriptorSnapshot();
        ResearchCategories.researchCategories = new LinkedHashMap<>(ResearchCategories.researchCategories);
        assertFalse(snapshot.unchanged());
    }

    @Test
    void researchEventsRetainImmutableArgumentsAndNeoCancellation() {
        var category = ResearchCategories.getResearchCategory("BASICS");
        var knowledge = new ResearchEvent.Knowledge(null, EnumKnowledgeType.OBSERVATION, category, -17);
        assertNull(knowledge.getPlayer());
        assertSame(category, knowledge.getCategory());
        assertSame(EnumKnowledgeType.OBSERVATION, knowledge.getType());
        assertEquals(-17, knowledge.getAmount());
        assertFalse(knowledge.isCanceled());
        knowledge.setCanceled(true);
        assertTrue(knowledge.isCanceled());
        var research = new ResearchEvent.Research(null, "!MiXeD@2");
        assertEquals("!MiXeD@2", research.getResearchKey());
        research.setCanceled(true);
        assertTrue(research.isCanceled());
    }

    @Test
    void dummyApiDoesNotInventAwardsOrProgressionBeforeProductionDelegation() {
        var dummy = new DummyInternalMethodHandler();
        assertFalse(dummy.addKnowledge(null, null, null, 16));
        assertFalse(dummy.progressResearch(null, "ANY"));
        assertFalse(dummy.doesPlayerHaveRequisites(null, "ANY"));
    }

    private static ResearchEntry entry(String key, ResearchStage[] stages) {
        var result = new ResearchEntry();
        result.setKey(key);
        result.setName("research." + key + ".title");
        result.setCategory("BASICS");
        result.setStages(stages);
        return result;
    }

    private static <T extends ResearchEntry> T put(T entry) {
        ResearchCategories.researchCategories.get("BASICS").research.put(entry.getKey(), entry);
        return entry;
    }

    private static PreparedResearchMetadata.Entry prepared(String extraFields, String stages) {
        String raw = "{\"entries\":[{\"key\":\"TEST\",\"name\":\"test\",\"category\":\"BASICS\",\"location\":[3,4],"
            + extraFields + "\"stages\":[" + stages + "]}]}";
        var spec = ResearchCatalogParser.parse(Map.of("test:research", raw), ResearchCategories.researchCategories.keySet());
        return ResearchCatalogPublication.prepareWhole(spec, ResearchCategories.researchCategories, java.util.Set.of()).entries().get("TEST");
    }
}
