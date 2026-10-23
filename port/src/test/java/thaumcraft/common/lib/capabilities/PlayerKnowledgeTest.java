package thaumcraft.common.lib.capabilities;

import static org.junit.jupiter.api.Assertions.*;
import static thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.*;
import static thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag.*;
import static thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchStatus.*;

import java.util.LinkedHashMap;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;

/** Tests the real storage without a player, network or automatically loaded research catalog. */
class PlayerKnowledgeTest {
    private LinkedHashMap<String, ResearchCategory> previousCatalog;

    @BeforeEach
    void isolateMetadata() {
        previousCatalog = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
    }

    @AfterEach
    void restoreMetadata() {
        ResearchCategories.researchCategories = previousCatalog;
    }

    @Test
    void enumOrderingAndProgressionMatchOriginalNbtAndApi() {
        assertEquals("THEORY", IPlayerKnowledge.EnumKnowledgeType.values()[0].name());
        assertEquals("OBSERVATION", IPlayerKnowledge.EnumKnowledgeType.values()[1].name());
        assertEquals(32, THEORY.getProgression());
        assertEquals(16, OBSERVATION.getProgression());
        assertEquals("T", THEORY.getAbbreviation());
        assertEquals("O", OBSERVATION.getAbbreviation());
        assertTrue(THEORY.hasFields());
        assertTrue(OBSERVATION.hasFields());
        assertArrayEquals(new IPlayerKnowledge.EnumResearchFlag[]{PAGE, RESEARCH, POPUP},
            IPlayerKnowledge.EnumResearchFlag.values());
        assertArrayEquals(new IPlayerKnowledge.EnumResearchStatus[]{UNKNOWN, COMPLETE, IN_PROGRESS},
            IPlayerKnowledge.EnumResearchStatus.values());
    }

    @Test
    void emptyFactoryAndClearDoNotGrantAutoUnlockButNbtLoadDoes() {
        catalogEntry("AUTO", null, true);
        PlayerKnowledge data = new PlayerKnowledge();
        assertTrue(data.getResearchList().isEmpty());
        data.deserializeNBT((CompoundTag) null);
        assertFalse(data.isResearchKnown("AUTO"));
        data.deserializeNBT(new CompoundTag());
        assertTrue(data.isResearchKnown("AUTO"));
        data.clear();
        assertFalse(data.isResearchKnown("AUTO"));
        data.deserializeNBT(null, new CompoundTag());
        assertTrue(data.isResearchKnown("AUTO"));
    }

    @Test
    void stageBoundaryIsBeyondLastStageNotAtLastStage() {
        catalogEntry("STAGED", new ResearchStage[2], false);
        PlayerKnowledge data = new PlayerKnowledge();
        assertEquals(UNKNOWN, data.getResearchStatus("STAGED"));
        assertEquals(-1, data.getResearchStage("STAGED"));
        assertTrue(data.addResearch("STAGED"));
        assertEquals(0, data.getResearchStage("STAGED"));
        assertEquals(IN_PROGRESS, data.getResearchStatus("STAGED"));
        assertTrue(data.setResearchStage("STAGED", 2));
        assertEquals(IN_PROGRESS, data.getResearchStatus("STAGED"));
        assertTrue(data.setResearchStage("STAGED", 3));
        assertEquals(COMPLETE, data.getResearchStatus("STAGED"));
        assertTrue(data.isResearchComplete("STAGED"));
        assertTrue(data.setResearchStage("STAGED", 1));
        assertEquals(IN_PROGRESS, data.getResearchStatus("STAGED"));
        assertFalse(data.setResearchStage("STAGED", 0));
        assertFalse(data.setResearchStage("STAGED", -1));
        assertFalse(data.setResearchStage("UNKNOWN", 1));
        assertFalse(data.setResearchStage(null, 1));
    }

    @Test
    void nullAndEmptyStagesHaveDistinctCompletionSemantics() {
        catalogEntry("NULL", null, false);
        catalogEntry("EMPTY", new ResearchStage[0], false);
        PlayerKnowledge data = new PlayerKnowledge();
        data.addResearch("NULL");
        data.addResearch("EMPTY");
        data.addResearch("MISSING_CATALOG");
        assertEquals(COMPLETE, data.getResearchStatus("NULL"));
        assertEquals(IN_PROGRESS, data.getResearchStatus("EMPTY"));
        assertEquals(COMPLETE, data.getResearchStatus("MISSING_CATALOG"));
        data.setResearchStage("EMPTY", 1);
        assertEquals(COMPLETE, data.getResearchStatus("EMPTY"));
        assertEquals(UNKNOWN, data.getResearchStatus(null));
        assertTrue(data.isResearchKnown(""));
        assertEquals(COMPLETE, data.getResearchStatus(""));
        assertFalse(data.addResearch(""));
    }

    @Test
    void qualifiedQueriesUseBaseStageButStatusLooksUpLiteralKey() {
        catalogEntry("BASE", new ResearchStage[4], false);
        PlayerKnowledge data = new PlayerKnowledge();
        data.addResearch("BASE");
        data.setResearchStage("BASE", 2);
        assertTrue(data.isResearchKnown("BASE@2"));
        assertFalse(data.isResearchKnown("BASE@3"));
        assertEquals(IN_PROGRESS, data.getResearchStatus("BASE"));
        assertEquals(COMPLETE, data.getResearchStatus("BASE@2"));
        assertEquals(-1, data.getResearchStage("BASE@2"));
        assertFalse(data.setResearchStage("BASE@2", 3));
        assertTrue(data.isResearchKnown("BASE@"));
        assertTrue(data.isResearchKnown("BASE@bad"));
        assertTrue(data.isResearchKnown("BASE@@99"));
        assertTrue(data.isResearchKnown("BASE@ 99"));
        assertTrue(data.isResearchKnown("BASE@2147483648"));
        assertTrue(data.isResearchKnown("BASE@-9"));
        assertFalse(data.isResearchKnown("base"));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> data.isResearchKnown("@"));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> data.isResearchKnown("@@"));
    }

    @Test
    void qualifiedLiteralInsertAndRemovalRetainSourceOddities() {
        PlayerKnowledge data = new PlayerKnowledge();
        assertTrue(data.addResearch("QUALIFIED@3"));
        assertTrue(data.getResearchList().contains("QUALIFIED@3"));
        assertFalse(data.isResearchKnown("QUALIFIED@3"));
        assertTrue(data.addResearch("QUALIFIED@3")); // reports true even though HashSet is unchanged
        assertTrue(data.setResearchStage("QUALIFIED@3", 7));
        assertEquals(7, data.getResearchStage("QUALIFIED@3"));
        assertFalse(data.removeResearch("QUALIFIED@3"));
        data.addResearch("BASE");
        data.setResearchStage("BASE", 3);
        assertTrue(data.removeResearch("BASE@2")); // removes the literal key, not BASE
        assertTrue(data.isResearchKnown("BASE"));
    }

    @Test
    void removalLeavesOrphanStagesAndFlagsUntilClearOrReload() {
        PlayerKnowledge data = new PlayerKnowledge();
        data.addResearch("KEY");
        data.setResearchStage("KEY", 4);
        data.setResearchFlag("KEY", PAGE);
        assertTrue(data.removeResearch("KEY"));
        assertFalse(data.removeResearch("KEY"));
        assertEquals(-1, data.getResearchStage("KEY"));
        assertTrue(data.hasResearchFlag("KEY", PAGE));
        PlayerKnowledge saved = new PlayerKnowledge();
        saved.deserializeNBT(data.serializeNBT());
        saved.addResearch("KEY");
        assertEquals(0, saved.getResearchStage("KEY"));
        assertFalse(saved.hasResearchFlag("KEY", PAGE));
        data.addResearch("KEY");
        assertEquals(4, data.getResearchStage("KEY"));
        assertTrue(data.hasResearchFlag("KEY", PAGE));
        data.clear();
        data.addResearch("KEY");
        assertEquals(0, data.getResearchStage("KEY"));
        assertFalse(data.hasResearchFlag("KEY", PAGE));
    }

    @Test
    void researchListIsUnmodifiableLiveView() {
        PlayerKnowledge data = new PlayerKnowledge();
        Set<String> view = data.getResearchList();
        assertThrows(UnsupportedOperationException.class, () -> view.add("NO"));
        data.addResearch("YES");
        assertTrue(view.contains("YES"));
        data.clear();
        assertTrue(view.isEmpty());
    }

    @Test
    void flagsMayPrecedeResearchAndEmptyBucketSurvivesWrongObjectRemoval() {
        PlayerKnowledge data = new PlayerKnowledge();
        assertTrue(data.setResearchFlag("UNKNOWN", POPUP));
        assertFalse(data.setResearchFlag("UNKNOWN", POPUP));
        assertTrue(data.hasResearchFlag("UNKNOWN", POPUP));
        assertTrue(data.serializeNBT().getList("research", Tag.TAG_COMPOUND).isEmpty());
        data.addResearch("UNKNOWN");
        assertTrue(data.clearResearchFlag("UNKNOWN", POPUP));
        assertFalse(data.clearResearchFlag("UNKNOWN", POPUP));
        assertFalse(data.clearResearchFlag("ABSENT", POPUP));
        assertFalse(data.hasResearchFlag("UNKNOWN", POPUP));
        CompoundTag saved = data.serializeNBT();
        CompoundTag child = researchTag(saved, "UNKNOWN");
        assertTrue(child.contains("flags", Tag.TAG_STRING));
        assertEquals("", child.getString("flags"));
        PlayerKnowledge loaded = new PlayerKnowledge();
        loaded.deserializeNBT(saved);
        assertFalse(researchTag(loaded.serializeNBT(), "UNKNOWN").contains("flags"));
    }

    @Test
    void knowledgeIsCategoryScopedAndFlooredInSourceUnits() {
        ResearchCategory category = category("TEST");
        PlayerKnowledge data = new PlayerKnowledge();
        assertTrue(data.addKnowledge(THEORY, category, 31));
        assertEquals(31, data.getKnowledgeRaw(THEORY, category));
        assertEquals(0, data.getKnowledge(THEORY, category));
        assertTrue(data.addKnowledge(THEORY, category, 1));
        assertEquals(1, data.getKnowledge(THEORY, category));
        assertTrue(data.addKnowledge(OBSERVATION, category, 33));
        assertEquals(2, data.getKnowledge(OBSERVATION, category));
        assertEquals(0, data.getKnowledgeRaw(THEORY, null));
        assertTrue(data.addKnowledge(THEORY, null, 64));
        assertEquals(2, data.getKnowledge(THEORY, null));
        assertEquals(64, knowledgeTag(data.serializeNBT(), "T_").getInt("amount"));
        assertEquals(32, knowledgeTag(data.serializeNBT(), "T_TEST").getInt("amount"));
        category.key = "RENAMED";
        assertEquals(0, data.getKnowledgeRaw(THEORY, category));
    }

    @Test
    void negativeTotalsRejectButSignedOverflowIsNotRepaired() {
        ResearchCategory category = category("TEST");
        PlayerKnowledge data = new PlayerKnowledge();
        assertFalse(data.addKnowledge(THEORY, category, -1));
        assertTrue(data.addKnowledge(THEORY, category, 0));
        assertTrue(data.serializeNBT().getList("knowledge", Tag.TAG_COMPOUND).isEmpty());
        assertTrue(data.addKnowledge(THEORY, category, Integer.MAX_VALUE));
        assertFalse(data.addKnowledge(THEORY, category, 1));
        assertEquals(Integer.MAX_VALUE, data.getKnowledgeRaw(THEORY, category));
        data.deserializeNBT(nbt(new ListTag(), knowledgeEntries("T_TEST", Integer.MIN_VALUE)));
        assertEquals(-67108864, data.getKnowledge(THEORY, category));
        assertTrue(data.addKnowledge(THEORY, category, -1));
        assertEquals(Integer.MAX_VALUE, data.getKnowledgeRaw(THEORY, category));
        assertTrue(data.addKnowledge(THEORY, category, -Integer.MAX_VALUE));
        assertEquals(0, data.getKnowledgeRaw(THEORY, category));
    }

    @Test
    void nbtPreservesFirstResearchDuplicateButLastKnowledgeDuplicate() {
        ListTag research = new ListTag();
        research.add(researchEntry("KEY", 2, "PAGE,not_a_flag,page,POPUP"));
        research.add(researchEntry("KEY", 99, "RESEARCH"));
        research.add(researchEntry("", 7, "RESEARCH"));
        research.add(researchEntry("LOW", -5, "RESEARCH"));
        ListTag knowledge = new ListTag();
        knowledge.add(knowledgeEntry("O_TEST", 1));
        knowledge.add(knowledgeEntry("O_TEST", 17));
        knowledge.add(knowledgeEntry("addon_unknown", 42));
        knowledge.add(knowledgeEntry("", 42));
        PlayerKnowledge data = new PlayerKnowledge();
        data.deserializeNBT(nbt(research, knowledge));
        assertEquals(Set.of("KEY", "LOW"), data.getResearchList());
        assertEquals(2, data.getResearchStage("KEY"));
        assertEquals(0, data.getResearchStage("LOW"));
        assertTrue(data.hasResearchFlag("KEY", PAGE));
        assertTrue(data.hasResearchFlag("KEY", POPUP));
        assertFalse(data.hasResearchFlag("KEY", RESEARCH));
        assertTrue(data.hasResearchFlag("LOW", RESEARCH));
        assertEquals(17, data.getKnowledgeRaw(OBSERVATION, category("TEST")));
        CompoundTag saved = data.serializeNBT();
        assertEquals(Set.of("research", "knowledge"), saved.getAllKeys());
        assertEquals(2, saved.getList("knowledge", Tag.TAG_COMPOUND).size());
        assertEquals(42, knowledgeTag(saved, "addon_unknown").getInt("amount"));
        assertFalse(researchTag(saved, "LOW").contains("stage"));
        assertEquals(Set.of("key", "stage", "flags"), researchTag(saved, "KEY").getAllKeys());
        assertEquals(Set.of("key", "amount"), knowledgeTag(saved, "O_TEST").getAllKeys());
    }

    @Test
    void signedLoadedKnowledgeIsFlooredAndOmittedFromSaveWithoutClamping() {
        ListTag knowledge = new ListTag();
        knowledge.add(knowledgeEntry("T_TEST", -1));
        knowledge.add(knowledgeEntry("O_TEST", -17));
        knowledge.add(knowledgeEntry("zero", 0));
        PlayerKnowledge data = new PlayerKnowledge();
        data.deserializeNBT(nbt(new ListTag(), knowledge));
        ResearchCategory category = category("TEST");
        assertEquals(-1, data.getKnowledgeRaw(THEORY, category));
        assertEquals(-1, data.getKnowledge(THEORY, category));
        assertEquals(-17, data.getKnowledgeRaw(OBSERVATION, category));
        assertEquals(-2, data.getKnowledge(OBSERVATION, category));
        assertTrue(data.serializeNBT().getList("knowledge", Tag.TAG_COMPOUND).isEmpty());
        assertFalse(data.addKnowledge(THEORY, category, 0));
        assertTrue(data.addKnowledge(THEORY, category, 1));
        assertEquals(0, data.getKnowledgeRaw(THEORY, category));
    }

    @Test
    void nullLoadIsNoOpAndNonnullLoadReplacesEverything() {
        PlayerKnowledge data = new PlayerKnowledge();
        data.addResearch("KEY");
        data.setResearchStage("KEY", 2);
        data.setResearchFlag("KEY", PAGE);
        data.addKnowledge(THEORY, null, 7);
        CompoundTag before = data.serializeNBT();
        data.deserializeNBT((CompoundTag) null);
        assertEquals(before, data.serializeNBT());
        data.deserializeNBT(new CompoundTag());
        assertTrue(data.getResearchList().isEmpty());
        assertFalse(data.hasResearchFlag("KEY", PAGE));
        assertEquals(-1, data.getResearchStage("KEY"));
        assertEquals(0, data.getKnowledgeRaw(THEORY, null));
    }

    @Test
    void providerBridgeAndRoundTripHaveIndependentCollections() {
        PlayerKnowledge source = new PlayerKnowledge();
        source.addResearch("KEY");
        source.setResearchStage("KEY", 5);
        source.setResearchFlag("KEY", RESEARCH);
        source.addKnowledge(OBSERVATION, null, 32);
        CompoundTag tag = source.serializeNBT(null);
        PlayerKnowledge copy = new PlayerKnowledge();
        copy.deserializeNBT(null, tag);
        assertEquals(source.serializeNBT(), copy.serializeNBT());
        copy.setResearchStage("KEY", 1);
        copy.clearResearchFlag("KEY", RESEARCH);
        copy.addKnowledge(OBSERVATION, null, 16);
        researchTag(tag, "KEY").putInt("stage", 9);
        assertEquals(5, source.getResearchStage("KEY"));
        assertTrue(source.hasResearchFlag("KEY", RESEARCH));
        assertEquals(32, source.getKnowledgeRaw(OBSERVATION, null));
        assertEquals(1, copy.getResearchStage("KEY"));
    }

    private static ResearchCategory category(String key) {
        return new ResearchCategory(key, null, null, null, null);
    }

    private static ResearchEntry catalogEntry(String key, ResearchStage[] stages, boolean autoUnlock) {
        ResearchCategory category = ResearchCategories.researchCategories.computeIfAbsent("TEST", PlayerKnowledgeTest::category);
        ResearchEntry entry = new ResearchEntry();
        entry.setKey(key);
        entry.setCategory(category.key);
        entry.setStages(stages);
        if (autoUnlock) {
            entry.setMeta(new ResearchEntry.EnumResearchMeta[]{ResearchEntry.EnumResearchMeta.AUTOUNLOCK});
        }
        category.research.put(key, entry);
        return entry;
    }

    private static CompoundTag researchEntry(String key, int stage, String flags) {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        tag.putInt("stage", stage);
        tag.putString("flags", flags);
        return tag;
    }

    private static CompoundTag knowledgeEntry(String key, int amount) {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        tag.putInt("amount", amount);
        return tag;
    }

    private static ListTag knowledgeEntries(String key, int amount) {
        ListTag list = new ListTag();
        list.add(knowledgeEntry(key, amount));
        return list;
    }

    private static CompoundTag nbt(ListTag research, ListTag knowledge) {
        CompoundTag tag = new CompoundTag();
        tag.put("research", research);
        tag.put("knowledge", knowledge);
        return tag;
    }

    private static CompoundTag researchTag(CompoundTag root, String key) {
        return findChild(root, "research", key);
    }

    private static CompoundTag knowledgeTag(CompoundTag root, String key) {
        return findChild(root, "knowledge", key);
    }

    private static CompoundTag findChild(CompoundTag root, String listKey, String key) {
        ListTag list = root.getList(listKey, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (tag.getString("key").equals(key)) {
                return tag;
            }
        }
        throw new AssertionError("Missing " + listKey + " child " + key);
    }
}
