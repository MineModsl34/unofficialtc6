package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.research.ResearchItemResolver;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ServerResearchProgression;
import thaumcraft.common.lib.research.ServerResearchProgression.Status;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCatalogPublication;

/** Real server transactions on explicit synthetic addon/catalog fixtures, not survival/TCP.
 * Inventory, knowledge, warp, rewards, event callbacks and owner packet routes are production. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ResearchPhaseFourGameTests {
    @GameTest(template = "empty")
    public static void duplicateObtainsAreAggregatedCancelledAtomicallyThenPaidWithRealRewardsAndWarp(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Phase4_Atomic");
        var other = player(helper, "TC_Phase4_Other");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        var candidate = prepared("TC_PHASE4_ATOMIC", "\"reward_item\":[\"minecraft:diamond;2;0\"],\"reward_knowledge\":[\"THEORY;BASICS;1\"],",
            "\"required_item\":[\"minecraft:paper;3;0\",\"minecraft:paper;2;0\"],\"required_knowledge\":[\"OBSERVATION;BASICS;1\"],\"warp\":3");
        var entry = candidate.entries().get("TC_PHASE4_ATOMIC");
        var otherBefore = ThaumcraftCapabilities.getKnowledge(other).serializeNBT().copy();
        var recipients = new ArrayList<ServerPlayer>();
        try (var fixture = ResearchCatalogTestScope.install(new java.util.LinkedHashMap<>(candidate.categories())); var probe = new ServerResearchProgressionGameTests.Probe(owner);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> recipients.add(recipient))) {
            var basics = ResearchCategories.getResearchCategory("BASICS");
            knowledge.addResearch(entry.getKey()); knowledge.setResearchStage(entry.getKey(), 1);
            knowledge.addKnowledge(EnumKnowledgeType.OBSERVATION, basics, 16);
            owner.getInventory().items.set(0, new ItemStack(Items.PAPER, 2));
            owner.getInventory().items.set(1, new ItemStack(Items.PAPER, 2));
            var before = knowledge.serializeNBT().copy();
            var shortObtain = ServerResearchProgression.tryProgress(owner, entry.getKey(), 1);
            helper.assertTrue(shortObtain.status() == Status.MISSING_ITEM,
                "two rows need five papers, not two independent checks against four: " + shortObtain);
            helper.assertTrue(knowledge.serializeNBT().equals(before) && probe.research.isEmpty(), "short obtain fails before events/payment");
            owner.getInventory().items.get(1).grow(1);
            probe.cancelPaymentAt = 1;
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, entry.getKey(), 1).status() == Status.CANCELLED_KNOWLEDGE,
                "cancelled final knowledge check blocks ALL our costs");
            helper.assertTrue(count(owner, Items.PAPER) == 5 && count(owner, Items.DIAMOND) == 0
                && knowledge.serializeNBT().equals(before) && ThaumcraftCapabilities.getWarp(owner).getCounter() == 0,
                "cancelled check consumes no paper, knowledge, stage, reward or warp");
            probe.cancelPaymentAt = -1;
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, entry.getKey(), 1).applied(), "accepted complete transaction");
            helper.assertTrue(count(owner, Items.PAPER) == 0 && count(owner, Items.DIAMOND) == 2 && knowledge.isResearchComplete(entry.getKey()),
                "five papers paid once and actual resolved reward enters inventory");
            helper.assertTrue(knowledge.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, basics) == 0
                && knowledge.getKnowledgeRaw(EnumKnowledgeType.THEORY, basics) == 32, "source16 debit and32 reward, not whole-points raw confusion");
            var warp = ThaumcraftCapabilities.getWarp(owner);
            helper.assertTrue(warp.get(EnumWarpType.PERMANENT) == 3 && warp.get(EnumWarpType.NORMAL) == 3
                && warp.getCounter() == 6, "source single-stage warp3 added twice then split3+3");
            var complete = knowledge.serializeNBT().copy();
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, entry.getKey(), 1).status() == Status.STALE_STAGE
                && knowledge.serializeNBT().equals(complete) && count(owner, Items.DIAMOND) == 2, "replay cannot repeat cost/reward");
            helper.assertTrue(!recipients.isEmpty() && recipients.stream().allMatch(recipient -> recipient == owner)
                && ThaumcraftCapabilities.getKnowledge(other).serializeNBT().equals(otherBefore), "warp/gain snapshots are owner only");
        } finally { PlayerDataSync.forget(owner); PlayerDataSync.forget(other); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void listenerInventoryMutationIsRetainedButInvalidatesAllPlannedObtains(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Phase4_Listener");
        var candidate = prepared("TC_PHASE4_LISTENER", "", "\"required_item\":[\"minecraft:paper;2;0\"],\"required_knowledge\":[\"OBSERVATION;BASICS;1\"]");
        var entry = candidate.entries().get("TC_PHASE4_LISTENER");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        try (var fixture = ResearchCatalogTestScope.install(new java.util.LinkedHashMap<>(candidate.categories())); var probe = new ServerResearchProgressionGameTests.Probe(owner);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> helper.fail("no packet for changed transaction"))) {
            var basics = ResearchCategories.getResearchCategory("BASICS");
            knowledge.addResearch(entry.getKey()); knowledge.setResearchStage(entry.getKey(), 1);
            knowledge.addKnowledge(EnumKnowledgeType.OBSERVATION, basics, 16);
            owner.getInventory().items.set(0, new ItemStack(Items.PAPER, 3));
            probe.paymentAction = event -> owner.getInventory().items.get(0).shrink(1);
            var changed = ServerResearchProgression.tryProgress(owner, entry.getKey(), 1);
            helper.assertTrue(changed.status() == Status.CHANGED_DURING_EVENTS,
                "even still-affordable changed inventory invalidates captured payment: " + changed);
            helper.assertTrue(count(owner, Items.PAPER) == 2 && knowledge.getResearchStage(entry.getKey()) == 1
                && knowledge.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, basics) == 16 && !PlayerDataSync.isKnowledgeDirty(owner),
                "listener's one-paper mutation retained, no our two-paper debit/knowledge/stage/dirty");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullInventoryDropsOnlyUnacceptedRewardRemainderAndNoRepeat(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Phase4_Remainder");
        var entry = nativeEntry("TC_PHASE4_REMAINDER");
        entry.setRewardItem(new ItemStack[]{new ItemStack(Items.DIAMOND, 3)});
        for (int i = 0; i < owner.getInventory().items.size(); i++) owner.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 64));
        owner.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 63));
        var beforeEntities = Set.copyOf(helper.getLevel().getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(3)));
        try (var fixture = new Fixture(entry); var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {})) {
            var data = ThaumcraftCapabilities.getKnowledge(owner); data.addResearch(entry.getKey()); data.setResearchStage(entry.getKey(), 1);
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, entry.getKey(), 1).applied(), "valid native reward completes");
            var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(3)).stream()
                .filter(entity -> !beforeEntities.contains(entity) && entity.getItem().is(Items.DIAMOND)).toList();
            helper.assertTrue(count(owner, Items.DIAMOND) == 64 && dropped.stream().mapToInt(entity -> entity.getItem().getCount()).sum() == 2,
                "one diamond accepted, ONLY two dropped: no source partial-insertion duplicate");
            helper.assertFalse(ServerResearchProgression.tryProgress(owner, entry.getKey(), 1).applied(), "repeat rejected");
            helper.assertTrue(count(owner, Items.DIAMOND) == 64 && dropped.stream().mapToInt(entity -> entity.getItem().getCount()).sum() == 2, "no repeat reward");
            dropped.forEach(ItemEntity::discard);
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceRewardKnowledgeCancellationDoesNotUndoCompletedItemsOrStage(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Phase4_RewardEvent");
        var entry = nativeEntry("TC_PHASE4_REWARD_EVENT");
        entry.setRewardItem(new ItemStack[]{new ItemStack(Items.DIAMOND)});
        var basics = ResearchCategories.getResearchCategory("BASICS");
        entry.setRewardKnow(new ResearchStage.Knowledge[]{new ResearchStage.Knowledge(EnumKnowledgeType.THEORY, basics, 2)});
        var listener = new RewardCancellation(owner);
        NeoForge.EVENT_BUS.register(listener);
        try (var fixture = new Fixture(entry); var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {})) {
            var data = ThaumcraftCapabilities.getKnowledge(owner); data.addResearch(entry.getKey()); data.setResearchStage(entry.getKey(), 1);
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, entry.getKey(), 1).applied(), "source completion precedes positive reward event");
            helper.assertTrue(listener.raw == 64 && data.isResearchComplete(entry.getKey()) && count(owner, Items.DIAMOND) == 1
                && data.getKnowledgeRaw(EnumKnowledgeType.THEORY, basics) == 0, "cancel only knowledge reward, not committed stage/items");
        } finally { NeoForge.EVENT_BUS.unregister(listener); PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualNestedWarpUnlockConsumesGlobalNoFlagsBeforeParentCompletion(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Phase4_WarpOrder");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        // Explicit strict FIRSTSTEPS fixture isolates nested side-effect order, not survival.
        data.addResearch("FIRSTSTEPS"); data.setResearchStage("FIRSTSTEPS", ResearchCategories.getResearch("FIRSTSTEPS").getStages().length + 1);
        var entry = nativeEntry("TC_PHASE4_WARP_ORDER"); entry.getStages()[0].setWarp(1);
        boolean previousNoFlags = ResearchManager.noFlags;
        try (var fixture = new Fixture(entry); var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {})) {
            data.addResearch(entry.getKey()); data.setResearchStage(entry.getKey(), 1);
            ResearchManager.noFlags = true;
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, entry.getKey(), 1).applied(), "checked warp completes with real nested WARP");
            helper.assertTrue(data.isResearchComplete("WARP") && !data.hasResearchFlag("WARP", EnumResearchFlag.RESEARCH)
                && data.hasResearchFlag(entry.getKey(), EnumResearchFlag.RESEARCH) && !ResearchManager.noFlags,
                "source warp unlock consumes noFlags BEFORE original parent completion flags");
        } finally { ResearchManager.noFlags = previousNoFlags; PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    private static ResearchEntry nativeEntry(String key) {
        var entry = new ResearchEntry(); entry.setKey(key); entry.setName("private.phase4"); entry.setCategory("BASICS");
        var stage = new ResearchStage(); stage.setText("private.phase4"); stage.setKnow(new ResearchStage.Knowledge[0]);
        entry.setStages(new ResearchStage[]{stage}); return entry;
    }
    private static ResearchCatalogPublication.Candidate prepared(String key, String extra, String stage) {
        String json = "{\"entries\":[{\"key\":\"" + key + "\",\"name\":\"private.phase4\",\"category\":\"BASICS\",\"location\":[998,998],"
            + extra + "\"stages\":[{\"text\":\"private.phase4\"," + stage + "}]}]}";
        var spec = ResearchCatalogParser.parse(Map.of("test:phase4", json), ResearchCategories.researchCategories.keySet());
        // Install the whole detached candidate: its Knowledge rows intentionally refer to its
        // own canonical categories. Transplanting just one entry into the live catalog would
        // manufacture a noncanonical-category rejection before testing any item payment.
        return ResearchCatalogPublication.prepareWhole(spec,
            thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions.newBuiltinCategories(), Set.of());
    }
    private static int count(ServerPlayer owner, net.minecraft.world.item.Item item) {
        return owner.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static ServerPlayer player(GameTestHelper helper, String name) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5))); return player;
    }
    private record Fixture(ResearchEntry entry, ResearchEntry old) implements AutoCloseable {
        Fixture(ResearchEntry entry) { this(entry, ResearchCategories.getResearchCategory(entry.getCategory()).research.put(entry.getKey(), entry)); }
        @Override public void close() {
            var map = ResearchCategories.getResearchCategory(entry.getCategory()).research;
            if (old == null) map.remove(entry.getKey()); else map.put(entry.getKey(), old);
        }
    }
    public static final class RewardCancellation {
        private final ServerPlayer owner; int raw;
        RewardCancellation(ServerPlayer owner) { this.owner = owner; }
        @SubscribeEvent public void knowledge(ResearchEvent.Knowledge event) {
            if (event.getPlayer() == owner && event.getAmount() > 0) { raw = event.getAmount(); event.setCanceled(true); }
        }
    }
}
