package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchAddendum;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;

/** Actual server API/events with detached synthetic metadata, NOT book/C2S/client acceptance. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ResearchStageEffectsGameTests {
    private ResearchStageEffectsGameTests() {}

    @GameTest(template = "empty")
    public static void completedSiblingConsumesNoFlagsWhileParentIsStillUnfinished(GameTestHelper helper) {
        var owner = player(helper, "TC_Stage_Flags");
        var categories = ResearchCategoryDefinitions.newBuiltinCategories();
        var parent = entry("TC_STAGE_PARENT", 2);
        var sibling = entry("TC_STAGE_CHILD", 1);
        parent.setSiblings(new String[]{sibling.getKey(), "!TC_STAGE_OPAQUE"});
        categories.get("BASICS").research.put(parent.getKey(), parent);
        categories.get("BASICS").research.put(sibling.getKey(), sibling);
        boolean previousNoFlags = ResearchManager.noFlags;
        try (var scope = ResearchCatalogTestScope.install(categories); var events = new EventTrace(owner)) {
            ResearchManager.noFlags = true;
            int xp = owner.totalExperience;
            helper.assertTrue(ResearchManager.progressResearch(owner, parent.getKey()), "parent start succeeds");
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            helper.assertTrue(data.getResearchStage(parent.getKey()) == 1 && !data.isResearchComplete(parent.getKey()), "parent remains on exposed first stage");
            helper.assertTrue(data.getResearchStage(sibling.getKey()) == 2 && data.isResearchComplete(sibling.getKey()), "sibling completes already on parent start");
            helper.assertTrue(data.hasResearchFlag(sibling.getKey(), EnumResearchFlag.POPUP), "child completion popup survives noFlags");
            helper.assertFalse(data.hasResearchFlag(sibling.getKey(), EnumResearchFlag.RESEARCH), "child, not unfinished parent, consumes source suppression");
            helper.assertFalse(ResearchManager.noFlags, "first synced catalog completion consumes noFlags");
            helper.assertFalse(data.hasResearchFlag(parent.getKey(), EnumResearchFlag.POPUP), "ordinary unfinished parent has no completion popup");
            helper.assertTrue(data.isResearchComplete("!TC_STAGE_OPAQUE") && data.getResearchStage("!TC_STAGE_OPAQUE") == 0, "opaque sibling records once at zero");
            helper.assertTrue(events.keys.equals(List.of(parent.getKey(), sibling.getKey(), "!TC_STAGE_OPAQUE")), "source event order is parent then siblings");
            helper.assertTrue(owner.totalExperience == xp + 10, "five XP for each catalog transition, none for opaque sibling");
            helper.assertTrue(PlayerDataSync.isKnowledgeDirty(owner), "source synced transitions queue owner snapshot");
        } finally { ResearchManager.noFlags = previousNoFlags; PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void noSyncStillTraversesSiblingsAndNotifiesAddendaWithoutXpFlagsOrDirty(GameTestHelper helper) {
        var owner = player(helper, "TC_Stage_NoSync");
        var categories = ResearchCategoryDefinitions.newBuiltinCategories();
        var parent = entry("TC_STAGE_NOSYNC_PARENT", 2);
        var sibling = entry("TC_STAGE_NOSYNC_CHILD", 1);
        var addendumOwner = entry("TC_STAGE_ADDENDUM_OWNER", 0);
        var addendum = new ResearchAddendum();
        addendum.setText("test");
        addendum.setResearch(new String[]{sibling.getKey()});
        addendumOwner.setAddenda(new ResearchAddendum[]{addendum});
        parent.setSiblings(new String[]{sibling.getKey(), "!TC_STAGE_NOSYNC_OPAQUE"});
        for (var entry : List.of(parent, sibling, addendumOwner)) categories.get("BASICS").research.put(entry.getKey(), entry);
        boolean previousNoFlags = ResearchManager.noFlags;
        try (var scope = ResearchCatalogTestScope.install(categories); var events = new EventTrace(owner)) {
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            data.addResearch(addendumOwner.getKey()); // Explicit test fixture for an existing addendum recipient.
            ResearchManager.noFlags = true;
            int xp = owner.totalExperience;
            helper.assertTrue(ResearchManager.progressResearch(owner, parent.getKey(), false), "unsynced parent start still applies");
            helper.assertTrue(data.getResearchStage(parent.getKey()) == 1 && data.isResearchComplete(sibling.getKey()), "unsynced traversal still changes stages");
            helper.assertTrue(data.isResearchComplete("!TC_STAGE_NOSYNC_OPAQUE"), "unsynced opaque sibling persists in attachment");
            helper.assertTrue(data.hasResearchFlag(addendumOwner.getKey(), EnumResearchFlag.PAGE), "source addendum PAGE applies even without sync");
            for (String key : List.of(parent.getKey(), sibling.getKey(), "!TC_STAGE_NOSYNC_OPAQUE")) {
                helper.assertFalse(data.hasResearchFlag(key, EnumResearchFlag.POPUP), "no unsynced POPUP: " + key);
                helper.assertFalse(data.hasResearchFlag(key, EnumResearchFlag.RESEARCH), "no unsynced RESEARCH: " + key);
            }
            helper.assertTrue(ResearchManager.noFlags, "unsynced completion leaves global suppression latched");
            helper.assertTrue(owner.totalExperience == xp, "no unsynced XP");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "no invented dirty flush for unsynced progression/addenda");
            helper.assertTrue(events.keys.equals(List.of(parent.getKey(), sibling.getKey(), "!TC_STAGE_NOSYNC_OPAQUE")), "events still retain source traversal order");
        } finally { ResearchManager.noFlags = previousNoFlags; PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledLaterSiblingPreservesParentAndEarlierSiblingCommits(GameTestHelper helper) {
        var owner = player(helper, "TC_Stage_CancelChild");
        var categories = ResearchCategoryDefinitions.newBuiltinCategories();
        var parent = entry("TC_STAGE_CANCEL_PARENT", 2);
        var earlier = entry("TC_STAGE_CANCEL_EARLIER", 1);
        var cancelled = entry("TC_STAGE_CANCEL_LATER", 1);
        parent.setSiblings(new String[]{earlier.getKey(), cancelled.getKey()});
        for (var entry : List.of(parent, earlier, cancelled)) categories.get("BASICS").research.put(entry.getKey(), entry);
        boolean previousNoFlags = ResearchManager.noFlags;
        try (var scope = ResearchCatalogTestScope.install(categories); var events = new EventTrace(owner)) {
            ResearchManager.noFlags = false;
            events.cancelKey = cancelled.getKey();
            int xp = owner.totalExperience;
            helper.assertTrue(ResearchManager.progressResearch(owner, parent.getKey()), "sibling cancellation does not fail successful parent");
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            helper.assertTrue(data.getResearchStage(parent.getKey()) == 1 && data.isResearchComplete(earlier.getKey()), "parent and earlier child remain committed");
            helper.assertFalse(data.isResearchKnown(cancelled.getKey()), "cancelled child has no our marker or stage");
            helper.assertTrue(events.keys.equals(List.of(parent.getKey(), earlier.getKey(), cancelled.getKey())), "cancelled child event runs after earlier commits");
            helper.assertTrue(owner.totalExperience == xp + 10, "only successful catalog transitions award XP");
            helper.assertTrue(PlayerDataSync.isKnowledgeDirty(owner), "earlier successful transitions remain dirty");
        } finally { ResearchManager.noFlags = previousNoFlags; PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finiteProgressingSiblingCyclePreservesSourceStagesEventsAndXp(GameTestHelper helper) {
        var owner = player(helper, "TC_Stage_FiniteCycle");
        var categories = ResearchCategoryDefinitions.newBuiltinCategories();
        var first = entry("TC_STAGE_CYCLE_A", 3);
        var second = entry("TC_STAGE_CYCLE_B", 1);
        first.setSiblings(new String[]{second.getKey()});
        second.setSiblings(new String[]{first.getKey()});
        for (var entry : List.of(first, second)) categories.get("BASICS").research.put(entry.getKey(), entry);
        boolean previousNoFlags = ResearchManager.noFlags;
        try (var scope = ResearchCatalogTestScope.install(categories); var events = new EventTrace(owner)) {
            ResearchManager.noFlags = false;
            int xp = owner.totalExperience;
            helper.assertTrue(ResearchManager.progressResearch(owner, first.getKey()), "an increasing-stage cycle is source-finite, not an automatic refusal");
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            helper.assertTrue(data.getResearchStage(first.getKey()) == 4 && data.getResearchStage(second.getKey()) == 2, "recursive commits complete both nodes in source order");
            helper.assertTrue(events.keys.equals(List.of(first.getKey(), second.getKey(), first.getKey(), first.getKey())), "source commits parent before reentering it at a new stored stage");
            helper.assertTrue(owner.totalExperience == xp + 20, "four successful catalog transitions retain their five XP each");
            helper.assertTrue(data.hasResearchFlag(first.getKey(), EnumResearchFlag.POPUP)
                && data.hasResearchFlag(second.getKey(), EnumResearchFlag.POPUP), "only final transitions supply completion effects");
        } finally { ResearchManager.noFlags = previousNoFlags; PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nonProgressingCompletionAndMalformedSiblingEffectsRejectBeforeOurRootWrites(GameTestHelper helper) {
        var owner = player(helper, "TC_Stage_Preflight");
        var categories = ResearchCategoryDefinitions.newBuiltinCategories();
        var parent = entry("TC_STAGE_PREFLIGHT_PARENT", 2);
        var sibling = entry("TC_STAGE_PREFLIGHT_REWARD", 1);
        sibling.setRewardKnow(new ResearchStage.Knowledge[]{new ResearchStage.Knowledge(
            EnumKnowledgeType.OBSERVATION, categories.get("BASICS"), 0)});
        parent.setSiblings(new String[]{sibling.getKey()});
        for (var entry : List.of(parent, sibling)) categories.get("BASICS").research.put(entry.getKey(), entry);
        try (var scope = ResearchCatalogTestScope.install(categories); var events = new EventTrace(owner)) {
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            var before = data.serializeNBT().copy();
            int xp = owner.totalExperience;
            helper.assertFalse(ResearchManager.completeResearch(owner, "TC_STAGE_QUALIFIED@1"), "old qualified literal completion loop is rejected by pure bounded preflight");
            helper.assertFalse(ResearchManager.progressResearch(owner, parent.getKey()), "malformed zero-amount sibling reward blocks root before mutation, not after a partial parent start");
            helper.assertTrue(events.keys.isEmpty() && data.serializeNBT().equals(before), "pure graph preflight posts no events or test-only unlocks");
            helper.assertTrue(owner.totalExperience == xp && !PlayerDataSync.isKnowledgeDirty(owner), "rejected graph has no our XP or dirty state");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    private static ResearchEntry entry(String key, int count) {
        var entry = new ResearchEntry();
        entry.setKey(key); entry.setCategory("BASICS"); entry.setName("research.ORE.title");
        if (count > 0) {
            ResearchStage[] stages = new ResearchStage[count];
            for (int i = 0; i < count; i++) stages[i] = new ResearchStage();
            entry.setStages(stages);
        }
        return entry;
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    /** Synchronous owner-filtered real event subscriber, removed before any world tick. */
    public static final class EventTrace implements AutoCloseable {
        private final ServerPlayer owner;
        final List<String> keys = new ArrayList<>();
        String cancelKey;
        EventTrace(ServerPlayer owner) { this.owner = owner; NeoForge.EVENT_BUS.register(this); }
        @SubscribeEvent
        public void research(ResearchEvent.Research event) {
            if (event.getPlayer() != owner) return;
            keys.add(event.getResearchKey());
            if (event.getResearchKey().equals(cancelKey)) event.setCanceled(true);
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
