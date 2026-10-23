package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.InternalMethodHandler;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.misc.PacketKnowledgeGain;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCatalogPublication;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;

/** Production attachments/events/owner routing, not a real socket/HUD/progression-completeness claim. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class ResearchManagerGameTests {
    @GameTest(template = "empty")
    public static void wholeKnowledgeGainPacketsAddressOnlyOwnerAndDirtyWaitsTwentyTicks(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_RS_Gain");
        var other = player(helper, "TC_RS_Other");
        var category = ResearchCategories.getResearchCategory("BASICS");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        data.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 15);
        List<Delivery> deliveries = new ArrayList<>();
        try (var capture = capture(deliveries); var probe = new Probe(owner)) {
            helper.assertTrue(ResearchManager.addKnowledge(owner, EnumKnowledgeType.OBSERVATION, category, 1), "raw award succeeds");
            helper.assertTrue(data.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category) == 16, "raw units not whole units");
            helper.assertTrue(deliveries.size() == 1 && deliveries.getFirst().payload() instanceof PacketKnowledgeGain, "15->16 sends one gain");
            helper.assertTrue(ResearchManager.addKnowledge(owner, EnumKnowledgeType.OBSERVATION, category, 32), "two more whole units");
            helper.assertTrue(deliveries.size() == 3, "one packet per crossed whole unit");
            helper.assertTrue(deliveries.stream().allMatch(d -> d.owner() == owner), "no broadcast/bystander recipient");
            helper.assertTrue(deliveries.stream().map(Delivery::payload).map(PacketKnowledgeGain.class::cast)
                .allMatch(p -> p.knowledgeType() == EnumKnowledgeType.OBSERVATION && p.categoryKey().equals("BASICS")), "source feedback fields");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(other).getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category) == 0,
                "bystander attachment unchanged");
            helper.assertTrue(probe.knowledgeCalls == 2 && probe.lastKnowledge.getAmount() == 32, "actual immutable events retain raw request");
            helper.assertTrue(PlayerDataSync.isKnowledgeDirty(owner), "raw award queues owner dirty sync");
            owner.tickCount = 19;
            PlayerDataSync.flushKnowledgeIfDue(owner);
            helper.assertTrue(deliveries.size() == 3, "no early snapshot");
            owner.tickCount = 20;
            PlayerDataSync.flushKnowledgeIfDue(owner);
            helper.assertTrue(deliveries.size() == 4 && deliveries.getLast().payload() instanceof PacketSyncKnowledge,
                "actual periodic knowledge snapshot follows gain feedback");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "queue consumed once");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledKnowledgeAndResearchEventsPreventOurMutationPacketsAndDirty(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_RS_Cancel");
        var category = ResearchCategories.getResearchCategory("BASICS");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        var before = data.serializeNBT().copy();
        List<Delivery> deliveries = new ArrayList<>();
        try (var capture = capture(deliveries); var probe = new Probe(owner)) {
            probe.cancelKnowledge = true;
            probe.cancelResearch = true;
            helper.assertFalse(ResearchManager.addKnowledge(owner, EnumKnowledgeType.OBSERVATION, category, 16), "Knowledge cancellation returned");
            helper.assertFalse(ResearchManager.progressResearch(owner, "!TC_CANCELLED"), "Research cancellation returned");
            helper.assertTrue(probe.knowledgeCalls == 1 && probe.researchCalls == 1, "real event bus dispatched both");
            helper.assertTrue(data.serializeNBT().equals(before), "no research/knowledge mutation after cancellation");
            helper.assertTrue(deliveries.isEmpty(), "no gain/snapshot");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "cancellation does not queue sync");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceRefusedUnderflowStillReturnsTrueAndDirtyWhileApiZeroSkipsEvent(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_RS_Refused");
        var category = ResearchCategories.getResearchCategory("BASICS");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        data.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 3);
        List<Delivery> deliveries = new ArrayList<>();
        try (var capture = capture(deliveries); var probe = new Probe(owner)) {
            helper.assertTrue(ResearchManager.addKnowledge(owner, EnumKnowledgeType.OBSERVATION, category, -4), "source ignores storage boolean");
            helper.assertTrue(data.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category) == 3, "model refused underflow");
            helper.assertTrue(PlayerDataSync.isKnowledgeDirty(owner), "source still queues dirty");
            helper.assertTrue(deliveries.isEmpty(), "negative request sends no gain");
            PlayerDataSync.forget(owner);
            helper.assertFalse(new InternalMethodHandler().addKnowledge(owner, EnumKnowledgeType.OBSERVATION, category, 0), "API filters zero");
            helper.assertTrue(probe.knowledgeCalls == 1, "filtered zero posts no event");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "filtered zero queues no sync");
            helper.assertTrue(ResearchManager.addKnowledge(owner, EnumKnowledgeType.OBSERVATION, category, 0), "direct manager retains source zero behavior");
            helper.assertTrue(probe.knowledgeCalls == 2 && PlayerDataSync.isKnowledgeDirty(owner), "direct zero event and dirty preserved");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidGainTransportDescriptorFailsBeforeOurRawAwardAfterEvent(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_RS_WireBound");
        var category = new ResearchCategory("A".repeat(PacketKnowledgeGain.MAX_CATEGORY_CHARACTERS + 1),
            null, null, null, null, null);
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        var before = data.serializeNBT().copy();
        List<Delivery> deliveries = new ArrayList<>();
        try (var capture = capture(deliveries); var probe = new Probe(owner)) {
            boolean rejected = false;
            try { ResearchManager.addKnowledge(owner, EnumKnowledgeType.OBSERVATION, category, 16); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "oversized transport key is not silently truncated");
            helper.assertTrue(probe.knowledgeCalls == 1, "source pre-mutation cancellable event still dispatched");
            helper.assertTrue(data.serializeNBT().equals(before), "positive wire preflight prevents partial raw award");
            helper.assertTrue(deliveries.isEmpty(), "no partial gain packet");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "no partial dirty");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownOpaqueMarkersAreOnceOnlyStageZeroWithoutXpFlagsWarpOrImmediatePackets(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_RS_Markers");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        var warp = ThaumcraftCapabilities.getWarp(owner).serializeNBT().copy();
        int xp = owner.totalExperience;
        List<Delivery> deliveries = new ArrayList<>();
        try (var capture = capture(deliveries); var probe = new Probe(owner)) {
            for (String key : new String[]{"!TC_aspect", "!tc_test:item7", "f_TC_test", "[#]-123456"}) {
                helper.assertTrue(new InternalMethodHandler().progressResearch(owner, key), "new opaque marker " + key);
                helper.assertFalse(ResearchManager.progressResearch(owner, key), "ordinary duplicate false " + key);
                helper.assertTrue(data.isResearchComplete(key) && data.getResearchStage(key) == 0, "unknown marker complete at zero");
                for (var flag : EnumResearchFlag.values()) helper.assertFalse(data.hasResearchFlag(key, flag), "no unknown marker flags");
            }
            helper.assertTrue(probe.researchCalls == 4, "duplicate posts no second event");
            helper.assertTrue(owner.totalExperience == xp, "unknown keys grant no XP");
            helper.assertTrue(ThaumcraftCapabilities.getWarp(owner).serializeNBT().equals(warp), "no warp award");
            helper.assertTrue(deliveries.isEmpty() && PlayerDataSync.isKnowledgeDirty(owner), "dirty-only marker route");
            PlayerDataSync.forget(owner);
            helper.assertTrue(ResearchManager.progressResearch(owner, "f_TC_nosync", false), "sync=false still records marker");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "sync=false queues nothing");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualOrePlantsPearlSimpleCatalogEntriesCompleteAtTwoWithFiveXpEach(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_RS_Simple");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        boolean oldNoFlags = ResearchManager.noFlags;
        ResearchManager.noFlags = false;
        int xp = owner.totalExperience;
        List<Delivery> deliveries = new ArrayList<>();
        try (var capture = capture(deliveries)) {
            for (String key : new String[]{"ORE", "PLANTS", "PRIMPEARL"}) {
                helper.assertTrue(ResearchCategories.getResearch(key) != null, "actual generated catalog entry present " + key);
                helper.assertTrue(ResearchManager.unsupportedReason(ResearchCategories.getResearch(key)) == null, "cosmetic deferred tokens not gameplay blockers");
                helper.assertTrue(ResearchManager.progressResearch(owner, key), "actual simple catalog succeeds");
                helper.assertTrue(data.isResearchComplete(key) && data.getResearchStage(key) == 2, "source skip directly to complete stage2");
                helper.assertTrue(data.hasResearchFlag(key, EnumResearchFlag.POPUP) && data.hasResearchFlag(key, EnumResearchFlag.RESEARCH), "source completion flags");
                helper.assertFalse(ResearchManager.progressResearch(owner, key), "completed duplicate false");
            }
            helper.assertTrue(owner.totalExperience == xp + 15, "five source XP per successful catalog advance");
            helper.assertTrue(deliveries.isEmpty(), "no gain/reward packets for simple completion before dirty flush");
        } finally { ResearchManager.noFlags = oldNoFlags; PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void noFlagsSuppressesResearchFlagButPopupStartOverridesSourceSuppression(GameTestHelper helper) {
        var owner = player(helper, "TC_RS_Flags");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        boolean oldNoFlags = ResearchManager.noFlags;
        try {
            ResearchManager.noFlags = true;
            helper.assertTrue(ResearchManager.progressResearch(owner, "ORE"), "simple completion");
            helper.assertTrue(data.hasResearchFlag("ORE", EnumResearchFlag.POPUP), "POPUP always set");
            helper.assertFalse(data.hasResearchFlag("ORE", EnumResearchFlag.RESEARCH), "global noFlags source suppression");
            helper.assertFalse(ResearchManager.noFlags, "known synced completion consumes noFlags");
            ResearchManager.noFlags = true;
            helper.assertTrue(ResearchManager.startResearchWithPopup(owner, "PLANTS"), "explicit popup start");
            helper.assertTrue(data.hasResearchFlag("PLANTS", EnumResearchFlag.RESEARCH), "popup wrapper overrides suppression");
            helper.assertFalse(ResearchManager.noFlags, "source flag consumed");
        } finally { ResearchManager.noFlags = oldNoFlags; PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualFluxCanStartButActiveRequirementsAndRawRewardsRemainGuarded(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_RS_Deferred");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        var originalCatalog = ResearchCategories.researchCategories;
        int xp = owner.totalExperience;
        try (var probe = new Probe(owner)) {
            helper.assertTrue(ResearchCategories.getResearch("FLUX") != null, "actual multi-stage source FLUX present");
            helper.assertTrue(ResearchManager.startResearchWithPopup(owner, "FLUX"), "future stage requirements do not block a safe start");
            helper.assertTrue(data.getResearchStage("FLUX") == 1 && !data.isResearchComplete("FLUX"), "start exposes first stage, not completion");
            helper.assertTrue(owner.totalExperience == xp + 5 && probe.researchCalls == 1, "safe start has its source event and five XP");
            helper.assertTrue(data.hasResearchFlag("FLUX", EnumResearchFlag.POPUP)
                && data.hasResearchFlag("FLUX", EnumResearchFlag.RESEARCH), "explicit popup wrapper flags even unfinished research");
            PlayerDataSync.forget(owner);
            var before = data.serializeNBT().copy();
            helper.assertFalse(ResearchManager.progressResearch(owner, "FLUX"), "trusted API cannot bypass active stage requirements");
            // The eight built-ins have no reward_item field. Exercise the real addon/datapack
            // descriptor path instead of claiming an original reward fixture exists.
            ResearchCategories.researchCategories = ResearchCategoryDefinitions.newBuiltinCategories();
            String raw = "{\"entries\":[{\"key\":\"TC_DEFERRED_REWARD\",\"name\":\"test\",\"category\":\"BASICS\","
                + "\"location\":[1,1],\"reward_item\":[\"addon:missing_reward;2;0\"],\"stages\":[{\"text\":\"test\"}]}]}";
            var spec = ResearchCatalogParser.parse(Map.of("test:research", raw), ResearchCategories.researchCategories.keySet());
            var rewardEntry = ResearchCatalogPublication.prepareWhole(spec, ResearchCategories.researchCategories, Set.of())
                .entries().get("TC_DEFERRED_REWARD");
            ResearchCategories.researchCategories.get("BASICS").research.put(rewardEntry.getKey(), rewardEntry);
            helper.assertTrue(rewardEntry.getRewardItem() == null, "typed getter cannot represent deferred reward");
            helper.assertFalse(ResearchManager.progressResearch(owner, rewardEntry.getKey()), "raw reward token cannot be granted accidentally");
            helper.assertTrue(probe.researchCalls == 1, "active requirement and executed reward reject before additional events");
            helper.assertTrue(data.serializeNBT().equals(before) && owner.totalExperience == xp + 5, "refused transitions add no partial progress or XP");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "no dirty for deferred entries");
        } finally { ResearchCategories.researchCategories = originalCatalog; PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eventMutationOfDescriptorIsRevalidatedBeforeOurProgress(GameTestHelper helper) throws Exception {
        var original = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = ResearchCategoryDefinitions.newBuiltinCategories();
        var entry = simple("TC_REVALIDATE");
        ResearchCategories.researchCategories.get("BASICS").research.put(entry.getKey(), entry);
        var owner = player(helper, "TC_RS_Revalidate");
        try (var probe = new Probe(owner)) {
            probe.researchAction = event -> entry.getStages()[0].setObtain(new Object[]{"deferred"});
            helper.assertFalse(ResearchManager.progressResearch(owner, entry.getKey()), "after-event requirement injection blocks advance");
            helper.assertTrue(probe.researchCalls == 1, "event was actually dispatched");
            helper.assertFalse(ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown(entry.getKey()), "our marker not written after listener mutation");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "no partial dirty");
        } finally { ResearchCategories.researchCategories = original; PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void strippedQualifiedParentRequisiteIsCheckedBeforeResearchEvent(GameTestHelper helper) throws Exception {
        var original = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = ResearchCategoryDefinitions.newBuiltinCategories();
        var entry = simple("TC_CHILD");
        entry.setParents(new String[]{"~TC_PARENT@1"});
        ResearchCategories.researchCategories.get("BASICS").research.put(entry.getKey(), entry);
        var owner = player(helper, "TC_RS_Parents");
        try (var probe = new Probe(owner)) {
            helper.assertFalse(ResearchManager.doesPlayerHaveRequisites(owner, entry.getKey()), "qualified parent absent");
            helper.assertFalse(ResearchManager.progressResearch(owner, entry.getKey()), "parent blocks progress");
            helper.assertTrue(probe.researchCalls == 0, "parent gate precedes event");
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            data.addResearch("TC_PARENT");
            data.setResearchStage("TC_PARENT", 1);
            helper.assertTrue(new InternalMethodHandler().doesPlayerHaveRequisites(owner, entry.getKey()), "single '~' stripped, @1 query retained");
            helper.assertTrue(ResearchManager.progressResearch(owner, entry.getKey()), "qualified parent enables simple child");
        } finally { ResearchCategories.researchCategories = original; PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void addendaPageIsMetadataOnlyKnownCompletionEffectEvenWithoutSync(GameTestHelper helper) {
        var original = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = ResearchCategoryDefinitions.newBuiltinCategories();
        var target = simple("TC_ADDENDUM_OWNER");
        target.setStages(null);
        var addendum = new ResearchAddendum();
        addendum.setText("test");
        addendum.setResearch(new String[]{"TC_TRIGGER"});
        target.setAddenda(new ResearchAddendum[]{addendum});
        ResearchCategories.researchCategories.get("BASICS").research.put(target.getKey(), target);
        var unknownOwner = player(helper, "TC_RS_AddUnknown");
        var knownOwner = player(helper, "TC_RS_AddKnown");
        try {
            var unknownData = ThaumcraftCapabilities.getKnowledge(unknownOwner);
            unknownData.addResearch(target.getKey());
            helper.assertTrue(ResearchManager.progressResearch(unknownOwner, "TC_TRIGGER", false), "unknown trigger recorded");
            helper.assertFalse(unknownData.hasResearchFlag(target.getKey(), EnumResearchFlag.PAGE), "unknown branch never scans addenda");
            var trigger = simple("TC_TRIGGER");
            ResearchCategories.researchCategories.get("BASICS").research.put(trigger.getKey(), trigger);
            var knownData = ThaumcraftCapabilities.getKnowledge(knownOwner);
            knownData.addResearch(target.getKey());
            helper.assertTrue(ResearchManager.progressResearch(knownOwner, trigger.getKey(), false), "known simple trigger completion");
            helper.assertTrue(knownData.hasResearchFlag(target.getKey(), EnumResearchFlag.PAGE), "source addendum PAGE even sync=false");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(knownOwner), "metadata notice does not invent sync=false dirty");
            helper.assertFalse(knownData.hasResearchFlag(trigger.getKey(), EnumResearchFlag.POPUP), "sync=false popup absent");
        } finally {
            ResearchCategories.researchCategories = original;
            PlayerDataSync.forget(unknownOwner); PlayerDataSync.forget(knownOwner);
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        // Socketless test fixture, never inserted into a user world/player list or logged in.
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static ResearchEntry simple(String key) {
        var entry = new ResearchEntry();
        entry.setKey(key); entry.setCategory("BASICS"); entry.setName("research.ORE.title");
        entry.setStages(new ResearchStage[]{new ResearchStage()});
        return entry;
    }

    private static AutoCloseable capture(List<Delivery> deliveries) {
        return PlayerDataSync.captureGameTestPackets((owner, payload) -> deliveries.add(new Delivery(owner, payload)));
    }

    private record Delivery(ServerPlayer owner, CustomPacketPayload payload) {}

    /** Test-only temporary subscriber; always removed before the synchronous test returns. */
    public static final class Probe implements AutoCloseable {
        private final ServerPlayer owner;
        int knowledgeCalls;
        int researchCalls;
        boolean cancelKnowledge;
        boolean cancelResearch;
        ResearchEvent.Knowledge lastKnowledge;
        Consumer<ResearchEvent.Research> researchAction = event -> {};

        Probe(ServerPlayer owner) { this.owner = owner; NeoForge.EVENT_BUS.register(this); }

        @SubscribeEvent
        public void knowledge(ResearchEvent.Knowledge event) {
            if (event.getPlayer() != owner) return;
            knowledgeCalls++; lastKnowledge = event;
            if (cancelKnowledge) event.setCanceled(true);
        }

        @SubscribeEvent
        public void research(ResearchEvent.Research event) {
            if (event.getPlayer() != owner) return;
            researchCalls++; researchAction.accept(event);
            if (cancelResearch) event.setCanceled(true);
        }

        @Override
        public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
