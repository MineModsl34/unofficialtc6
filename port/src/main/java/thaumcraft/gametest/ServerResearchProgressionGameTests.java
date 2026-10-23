package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.container.ContainerResearchTable;
import thaumcraft.common.lib.TCAttachments;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.research.LegacyResearchCrafting;
import thaumcraft.common.lib.research.LegacySkyScanGeometry;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ScanSky;
import thaumcraft.common.lib.research.ServerResearchProgression;
import thaumcraft.common.lib.research.ServerResearchProgression.Status;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCatalogPublication;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/** Actual server evaluator/events/storage, real ResultSlot and physical table conversion.
 * The initial book marker and raw observation balances below are explicitly private fixtures,
 * NOT proof of normal book acquisition, survival acquisition, theory/cards, browser C2S or TCP.
 * Synthetic entries exercise addon safety boundaries; the main path uses real original metadata.
 */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ServerResearchProgressionGameTests {
    @GameTest(template = "scan_test", skyAccess = true)
    public static void actualFirststepsSiblingsRealCraftsPaidCelestialAndActualSkyUse(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Checked_Flow"), other = player(helper, "TC_Checked_Other");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        CompoundTag otherBefore = ThaumcraftCapabilities.getKnowledge(other).serializeNBT().copy();
        List<ServerPlayer> recipients = new ArrayList<>();
        List<PacketSyncKnowledge> snapshots = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        var clock = (ServerLevelData) level.getLevelData();
        long gameTime = level.getGameTime(), dayTime = level.getDayTime();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {
                 helper.assertTrue(packet instanceof PacketSyncKnowledge, "costs/scans queue snapshots, no negative gain feedback");
                 recipients.add(recipient); snapshots.add((PacketSyncKnowledge) packet);
             }); var probe = new Probe(owner)) {
            helper.assertFalse(ServerResearchProgression.tryProgress(owner, "FIRSTSTEPS", -1).applied(), "actual missing book parent blocks start");
            // Actual source key, explicit acquisition fixture only, not a normal thaumonomicon use.
            helper.assertTrue(ResearchManager.progressResearch(owner, "!gotthaumonomicon"), "trusted opaque fixture marker");
            apply(helper, owner, "FIRSTSTEPS", -1);
            helper.assertTrue(knowledge.getResearchStage("FIRSTSTEPS") == 1 && !knowledge.isResearchComplete("FIRSTSTEPS"), "safe start exposes unresolved arcane-workbench stage, never bypasses it");
            helper.assertTrue(ThaumcraftCapabilities.knowsResearchStrict(owner, "KNOWLEDGETYPES", "!gotdream"), "source siblings execute at the successful start, before FIRSTSTEPS completion");
            apply(helper, owner, "THEORYRESEARCH", -1);
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, "THEORYRESEARCH", 1).status() == Status.MISSING_CRAFT, "actual catalog requires real tools/table receipts");

            var menu = owner.inventoryMenu;
            menu.getSlot(1).set(new ItemStack(Items.GLASS_BOTTLE));
            menu.getSlot(2).set(new ItemStack(Items.FEATHER));
            menu.getSlot(3).set(new ItemStack(Items.INK_SAC));
            ItemStack tools = menu.getSlot(0).safeTake(1, Integer.MAX_VALUE, owner);
            helper.assertTrue(tools.is(ItemsTC.scribingTools) && knowledge.isResearchKnown(LegacyResearchCrafting.SCRIBING_TOOLS.marker()), "real registered recipe ResultSlot.take and production crafting listener record tools receipt");
            BlockPos tablePos = helper.absolutePos(new BlockPos(5, 2, 4));
            level.setBlock(tablePos, BlocksTC.tableWood.defaultBlockState(), Block.UPDATE_ALL);
            owner.setItemInHand(InteractionHand.MAIN_HAND, tools);
            var hit = new BlockHitResult(Vec3.atCenterOf(tablePos), Direction.UP, tablePos, false);
            helper.assertTrue(level.getBlockState(tablePos).useItemOn(tools, level, owner, InteractionHand.MAIN_HAND, hit).consumesAction(), "actual wood-table activation");
            helper.assertTrue(knowledge.isResearchKnown(LegacyResearchCrafting.RESEARCH_TABLE.marker()) && owner.getMainHandItem().isEmpty(), "real post-conversion crafting event records the table receipt");
            apply(helper, owner, "THEORYRESEARCH", 1);
            helper.assertTrue(knowledge.getResearchStage("THEORYRESEARCH") == 3 && ThaumcraftCapabilities.knowsResearchStrict(owner, "THEORYRESEARCH"), "source final free stage autocompletes; no theory/cards invented");
            apply(helper, owner, "CELESTIALSCANNING", -1);
            owner.setXRot(-90); owner.setYRot(-90);
            clock.setGameTime(9L * 24000); level.setDayTime(6000);
            settleSky(level, owner.blockPosition());
            helper.assertFalse(new ScanSky().checkThing(owner, null), "known stage1 is not strict celestial completion");
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, "CELESTIALSCANNING", 1).status() == Status.INSUFFICIENT_KNOWLEDGE, "no raw observation acquisition is faked by stage start");
            for (String category : List.of("BASICS", "ARTIFICE", "AUROMANCY")) {
                knowledge.addKnowledge(EnumKnowledgeType.OBSERVATION, ResearchCategories.getResearchCategory(category), 16);
            }
            probe.payments.clear();
            apply(helper, owner, "CELESTIALSCANNING", 1);
            helper.assertTrue(probe.payments.equals(List.of("BASICS:-16", "ARTIFICE:-16", "AUROMANCY:-16")), "original ordered raw negative payment events exactly once per row");
            for (String category : List.of("BASICS", "ARTIFICE", "AUROMANCY")) {
                helper.assertTrue(knowledge.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, ResearchCategories.getResearchCategory(category)) == 0, "each one OBS costs16 raw: " + category);
            }
            helper.assertTrue(knowledge.getResearchStage("CELESTIALSCANNING") == 2 && new ScanSky().checkThing(owner, null), "actual paid completion opens strict celestial gate");
            var table = (TileResearchTable) level.getBlockEntity(tablePos);
            var tableMenu = new ContainerResearchTable(42, owner.getInventory(), table);
            helper.assertTrue(!tableMenu.quickMoveStack(owner, 0).isEmpty(), "real table menu recovers the unchanged tool for scanning");
            owner.getInventory().items.set(1, new ItemStack(Items.PAPER, 2));
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.thaumometer));
            ItemsTC.thaumometer.use(level, owner, InteractionHand.MAIN_HAND);
            String daily = LegacySkyScanGeometry.dayPrefix(level.getGameTime()) + "Sun";
            helper.assertTrue(knowledge.isResearchKnown(daily) && owner.getInventory().items.stream().anyMatch(stack -> stack.is(ItemsTC.celestialNotesVariants[0])), "actual thaumometer use reaches installed sky scanner after real paid progression");
            helper.assertTrue(snapshots.isEmpty() && PlayerDataSync.isKnowledgeDirty(owner), "only dirty queue before periodic sync");
            owner.tickCount = 19; PlayerDataSync.flushKnowledgeIfDue(owner);
            helper.assertTrue(snapshots.isEmpty(), "no snapshot before20");
            var beforeFlush = knowledge.serializeNBT().copy();
            owner.tickCount = 20; PlayerDataSync.flushKnowledgeIfDue(owner);
            helper.assertTrue(recipients.equals(List.of(owner)) && snapshots.getFirst().data().equals(beforeFlush), "normal twenty-tick owner-only exact snapshot BEFORE source server POPUP clearing");
            helper.assertFalse(knowledge.hasResearchFlag("CELESTIALSCANNING", EnumResearchFlag.POPUP), "source server POPUP clears only after snapshot construction");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(other).serializeNBT().equals(otherBefore), "bystander never pays/progresses");
            var finalData = knowledge.serializeNBT().copy();
            helper.assertFalse(ServerResearchProgression.tryProgress(owner, "CELESTIALSCANNING", 1).applied(), "stale repeat cannot pay again");
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, "CELESTIALSCANNING", 2).status() == Status.COMPLETE && knowledge.serializeNBT().equals(finalData), "complete repeat unchanged");
        } finally {
            clock.setGameTime(gameTime); level.setDayTime(dayTime);
            owner.inventoryMenu.clearCraftingContent(); PlayerDataSync.forget(owner); PlayerDataSync.forget(other);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactCatalogKeysStagesAndParentsRejectBeforeEvents(GameTestHelper helper) {
        var owner = player(helper, "TC_Checked_Keys");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        var before = data.serializeNBT().copy();
        try (var probe = new Probe(owner)) {
            for (String key : new String[]{"", "!gotthaumonomicon", "[#]123", "FIRSTSTEPS@1", "FIRSTSTEPS&&ORE", "FIRSTSTEPS||ORE", "missing"}) {
                helper.assertTrue(ServerResearchProgression.tryProgress(owner, key, -1).status() == Status.INVALID_KEY, "no opaque/qualified/expression grant: " + key);
            }
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, "FIRSTSTEPS", 0).status() == Status.STALE_STAGE, "unknown -1 is not known0");
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, "FIRSTSTEPS", -1).status() == Status.MISSING_PARENTS, "required parent exact source semantics");
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, "CELESTIALSCANNING", -1).status() == Status.MISSING_PARENTS, "no bypass of theory parent");
            helper.assertTrue(probe.research.isEmpty() && probe.payments.isEmpty() && data.serializeNBT().equals(before) && !PlayerDataSync.isKnowledgeDirty(owner), "all precheck failures have no OUR events/data/sync");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void duplicateCostsAggregateAffordabilityButKeepOrderedEventsAndPayOnce(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Checked_Dup");
        var category = ResearchCategories.getResearchCategory("BASICS");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        try (var fixture = new Fixture(costEntry("TC_CHECK_DUP", 1, 1)); var probe = new Probe(owner);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {})) {
            data.addResearch(fixture.key()); data.setResearchStage(fixture.key(), 1);
            data.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 31);
            CompoundTag before = data.serializeNBT().copy();
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, fixture.key(), 1).status() == Status.INSUFFICIENT_KNOWLEDGE, "duplicate cost needs32, not two independent16 checks");
            helper.assertTrue(data.serializeNBT().equals(before) && probe.research.isEmpty(), "insufficient aggregate rejected before Research event");
            data.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 1);
            apply(helper, owner, fixture.key(), 1);
            helper.assertTrue(probe.payments.equals(List.of("BASICS:-16", "BASICS:-16")) && probe.research.equals(List.of(fixture.key())), "ordered original rows, not one aggregated32 event or double event posting");
            helper.assertTrue(data.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category) == 0 && data.isResearchComplete(fixture.key()), "one aggregate32 debit and completion");
            before = data.serializeNBT().copy();
            helper.assertFalse(ServerResearchProgression.tryProgress(owner, fixture.key(), 1).applied(), "stale repeated payment rejected");
            helper.assertTrue(data.serializeNBT().equals(before) && probe.payments.size() == 2, "no duplicate payment");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rootAndSecondPaymentCancellationLeaveAllOurCostsAndStageUntouched(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Checked_Cancel");
        var category = ResearchCategories.getResearchCategory("BASICS");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        try (var fixture = new Fixture(costEntry("TC_CHECK_CANCEL", 1, 1)); var probe = new Probe(owner);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> helper.fail("cancelled payment sends no packet"))) {
            data.addResearch(fixture.key()); data.setResearchStage(fixture.key(), 1);
            data.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 32);
            var before = data.serializeNBT().copy();
            probe.cancelRoot = true;
            helper.assertFalse(ServerResearchProgression.tryProgress(owner, fixture.key(), 1).applied(), "requested Research cancellation");
            helper.assertTrue(probe.payments.isEmpty() && data.serializeNBT().equals(before), "no Knowledge event or costs after cancelled Research");
            probe.cancelRoot = false; probe.cancelPaymentAt = 2;
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, fixture.key(), 1).status() == Status.CANCELLED_KNOWLEDGE, "second original row cancellation");
            helper.assertTrue(probe.payments.equals(List.of("BASICS:-16", "BASICS:-16")) && data.serializeNBT().equals(before), "first accepted row also not deducted until all events accept");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "no OUR dirty/XP/stage on cancellation");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void listenerOwnedDataChangesRemainButPreventOurPaymentAndReentrantRequests(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Checked_Listener");
        var category = ResearchCategories.getResearchCategory("BASICS");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        try (var fixture = new Fixture(costEntry("TC_CHECK_LISTENER", 1)); var probe = new Probe(owner)) {
            data.addResearch(fixture.key()); data.setResearchStage(fixture.key(), 1);
            data.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 16);
            List<Status> nested = new ArrayList<>();
            probe.paymentAction = event -> {
                nested.add(ServerResearchProgression.tryProgress(owner, fixture.key(), 1).status());
                ThaumcraftCapabilities.getWarp(owner).set(EnumWarpType.NORMAL, 7);
                data.addResearch("!listener-owned");
                owner.getInventory().items.set(5, new ItemStack(Items.DIAMOND, 2));
            };
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, fixture.key(), 1).status() == Status.CHANGED_DURING_EVENTS, "full owner mutation invalidates attempt");
            helper.assertTrue(nested.equals(List.of(Status.REENTRANT)) && data.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category) == 16 && data.getResearchStage(fixture.key()) == 1, "no nested transaction or OUR debit/progress");
            helper.assertTrue(data.isResearchKnown("!listener-owned") && ThaumcraftCapabilities.getWarp(owner).get(EnumWarpType.NORMAL) == 7 && owner.getInventory().items.get(5).getCount() == 2, "listener-owned state retained, never rolled back");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "service adds no dirty after changed listener baseline");
            probe.paymentAction = null;
            probe.researchAction = event -> data.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 1);
            helper.assertFalse(ServerResearchProgression.tryProgress(owner, fixture.key(), 1).applied(), "requested Research listener mutation also rejects before OUR payment");
            helper.assertTrue(data.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category) == 17 && data.getResearchStage(fixture.key()) == 1 && probe.payments.size() == 1,
                "root listener award retained, no new payment events/debit/progress");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void identicalAttachmentReplacementAndDescriptorMutationPreventDetachedDebits(GameTestHelper helper) {
        var owner = player(helper, "TC_Checked_Replace");
        var category = ResearchCategories.getResearchCategory("BASICS");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        try (var fixture = new Fixture(costEntry("TC_CHECK_REPLACE", 1)); var probe = new Probe(owner)) {
            data.addResearch(fixture.key()); data.setResearchStage(fixture.key(), 1); data.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 16);
            var before = data.serializeNBT().copy();
            probe.paymentAction = event -> {
                PlayerKnowledge replacement = new PlayerKnowledge();
                replacement.addResearch(fixture.key()); replacement.setResearchStage(fixture.key(), 1);
                replacement.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 16);
                helper.assertTrue(replacement.serializeNBT().equals(before), "deliberately identical replacement state");
                owner.setData(TCAttachments.KNOWLEDGE, replacement);
            };
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, fixture.key(), 1).status() == Status.CHANGED_DURING_EVENTS, "attachment identity revalidated even with equal NBT");
            helper.assertTrue(data.serializeNBT().equals(before) && ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().equals(before), "neither detached nor replacement data was debited");
            probe.paymentAction = event -> fixture.entry().getStages()[0].getKnow()[0].amount = 2;
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, fixture.key(), 1).status() == Status.CHANGED_DURING_EVENTS, "mutable descriptor change revalidated after Knowledge event");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().equals(before), "no debit under old descriptor; listener descriptor remains changed");
            helper.assertTrue(fixture.entry().getStages()[0].getKnow()[0].amount == 2, "no descriptor rollback");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingItemsCraftReceiptsMalformedDescriptorsAndOverflowRejectBeforeOurMutation(GameTestHelper helper) {
        var owner = player(helper, "TC_Checked_Deferred");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        var expected = List.of(Status.MISSING_ITEM, Status.MISSING_CRAFT, Status.MISSING_CRAFT,
            Status.MISSING_CRAFT, Status.UNSUPPORTED, Status.UNSUPPORTED, Status.UNSUPPORTED);
        int index = 0;
        for (String stageJson : List.of(
            "\"required_item\":[\"minecraft:paper\"]",
            "\"required_craft\":[\"thaumcraft:arcane_workbench\"]",
            "\"required_craft\":[\"thaumcraft:scribing_tools;1;1\"]",
            "\"required_craft\":[\"thaumcraft:scribing_tools;1;0;{x:1}\"]",
            "\"required_craft\":[\"thaumcraft:scribing_tools;0;0\"]",
            "\"required_item\":[\"addon:unregistered_dependency\"]",
            "\"required_knowledge\":[\"OBSERVATION;BASICS;2147483647\"]")) {
            String key = "TC_CHECK_DEFER_" + UUID.randomUUID();
            var spec = ResearchCatalogParser.parse(Map.of("test:private", "{\"entries\":[{\"key\":\"" + key + "\",\"name\":\"test\",\"category\":\"BASICS\",\"location\":[999,999],\"stages\":[{\"text\":\"test\"," + stageJson + "}]}]}"), ResearchCategories.researchCategories.keySet());
            var entry = ResearchCatalogPublication.prepareWhole(spec, ResearchCategories.researchCategories, Set.of()).entries().get(key);
            try (var fixture = new Fixture(entry); var probe = new Probe(owner)) {
                data.addResearch(key); data.setResearchStage(key, 1);
                var before = data.serializeNBT().copy();
                helper.assertTrue(ServerResearchProgression.tryProgress(owner, key, 1).status() == expected.get(index++), "missing or malformed active requirement rejected: " + stageJson);
                helper.assertTrue(data.serializeNBT().equals(before) && probe.research.isEmpty() && probe.payments.isEmpty(), "no OUR changes/events for unsupported descriptor");
            }
        }
        ResearchEntry reward = costEntry("TC_CHECK_REWARD", 1);
        reward.setRewardItem(new ItemStack[]{ItemStack.EMPTY});
        try (var fixture = new Fixture(reward); var probe = new Probe(owner)) {
            data.addResearch(fixture.key()); data.setResearchStage(fixture.key(), 1);
            var before = data.serializeNBT().copy();
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, fixture.key(), 1).status() == Status.UNSUPPORTED, "malformed native reward cannot be silently dropped or invented");
            helper.assertTrue(data.serializeNBT().equals(before) && probe.research.isEmpty(), "reward rejection before payment/event");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void requiredResearchIsStrictAndKnownZeroResumeIsNotUnknownStart(GameTestHelper helper) {
        var owner = player(helper, "TC_Checked_Strict");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        ResearchEntry parent = costEntry("TC_CHECK_REQUIRED", 1);
        ResearchEntry child = costEntry("TC_CHECK_STRICT");
        child.getStages()[0].setResearch(new String[]{parent.getKey()});
        try (var parentFixture = new Fixture(parent); var childFixture = new Fixture(child); var probe = new Probe(owner)) {
            data.addResearch(child.getKey());
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, child.getKey(), -1).status() == Status.STALE_STAGE, "known0 is not -1");
            apply(helper, owner, child.getKey(), 0);
            helper.assertTrue(data.getResearchStage(child.getKey()) == 1, "known0 resumes to stage1 without paying future stage");
            data.addResearch(parent.getKey()); data.setResearchStage(parent.getKey(), 1);
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, child.getKey(), 1).status() == Status.MISSING_RESEARCH, "known incomplete requirement is insufficient");
            data.setResearchStage(parent.getKey(), 2); // Explicit synthetic requirement fixture only.
            apply(helper, owner, child.getKey(), 1);
            helper.assertTrue(data.isResearchComplete(child.getKey()), "strict completed requirement permits no-cost transition");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledSiblingAfterPaidParentDoesNotRefundCommittedParent(GameTestHelper helper) {
        var owner = player(helper, "TC_Checked_Sibling");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        var category = ResearchCategories.getResearchCategory("BASICS");
        ResearchEntry parent = costEntry("TC_CHECK_PAID_PARENT", 1);
        ResearchEntry sibling = costEntry("TC_CHECK_CANCEL_SIBLING");
        parent.setSiblings(new String[]{sibling.getKey()});
        try (var parentFixture = new Fixture(parent); var siblingFixture = new Fixture(sibling); var probe = new Probe(owner)) {
            data.addResearch(parent.getKey()); data.setResearchStage(parent.getKey(), 1);
            data.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 16);
            probe.cancelKey = sibling.getKey();
            probe.researchAction = event -> {
                if (event.getResearchKey().equals(sibling.getKey())) helper.assertTrue(data.isResearchComplete(parent.getKey()) && data.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category) == 0, "source sibling listener runs AFTER paid parent commit");
            };
            apply(helper, owner, parent.getKey(), 1);
            helper.assertTrue(data.isResearchComplete(parent.getKey()) && !data.isResearchKnown(sibling.getKey()), "canceled sibling never rolls back parent or invents sibling progress");
            helper.assertTrue(probe.payments.equals(List.of("BASICS:-16")) && probe.research.equals(List.of(parent.getKey(), sibling.getKey())), "requested event, original payment, then actual sibling event");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void preexistingGenericStageReplacementCannotEraseImmutableOriginalRequirements(GameTestHelper helper) {
        var owner = player(helper, "TC_Checked_Raw");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        var original = ResearchCategories.getResearch("CELESTIALSCANNING");
        ResearchStage[] stages = original.getStages();
        ResearchStage saved = stages[0];
        try (var probe = new Probe(owner)) {
            // Explicit parent/storage fixture only for an adversarial metadata test; main path
            // above obtains THEORYRESEARCH and CELESTIALSCANNING through actual operations.
            data.addResearch("THEORYRESEARCH"); data.setResearchStage("THEORYRESEARCH", 3);
            data.addResearch("CELESTIALSCANNING"); data.setResearchStage("CELESTIALSCANNING", 1);
            for (String category : List.of("BASICS", "ARTIFICE", "AUROMANCY")) {
                data.addKnowledge(EnumKnowledgeType.OBSERVATION, ResearchCategories.getResearchCategory(category), 16);
            }
            var before = data.serializeNBT().copy();
            ResearchStage replacement = new ResearchStage(); replacement.setText(saved.getText());
            stages[0] = replacement; // SAME LENGTH, replaced BEFORE transaction/event snapshot.
            helper.assertTrue(ServerResearchProgression.tryProgress(owner, "CELESTIALSCANNING", 1).status() == Status.UNSUPPORTED, "Entry rawSpec authoritative even for a generic replacement Stage");
            helper.assertTrue(data.serializeNBT().equals(before) && probe.research.isEmpty() && probe.payments.isEmpty(), "no free completion, zero payment or events under erased typed costs");
        } finally { stages[0] = saved; PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownRequestedOrphanFlagListenerMutationIsRetainedButInvalidatesStart(GameTestHelper helper) {
        var owner = player(helper, "TC_Checked_Flag");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        try (var fixture = new Fixture(costEntry("TC_CHECK_ORPHAN_FLAG", 1)); var probe = new Probe(owner)) {
            var before = data.serializeNBT().copy();
            probe.researchAction = event -> data.setResearchFlag(fixture.key(), EnumResearchFlag.POPUP);
            helper.assertFalse(ServerResearchProgression.tryProgress(owner, fixture.key(), -1).applied(), "unknown requested flag mutation cannot hide behind unchanged serialized NBT");
            helper.assertTrue(data.serializeNBT().equals(before) && data.getResearchStage(fixture.key()) == -1 && data.hasResearchFlag(fixture.key(), EnumResearchFlag.POPUP),
                "listener-owned orphan flag retained; no OUR start, source NBT omits orphan flag");
            helper.assertTrue(probe.payments.isEmpty() && !PlayerDataSync.isKnowledgeDirty(owner), "no OUR payment/dirty");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void addonCategoryEqualityCannotMergeDistinctCanonicalKnowledgePools(GameTestHelper helper) {
        var owner = player(helper, "TC_Checked_EqualPools");
        var categories = ResearchCategoryDefinitions.newBuiltinCategories();
        var first = new EqualCategory("TC_EQUAL_A");
        var second = new EqualCategory("TC_EQUAL_B");
        categories.put(first.key, first); categories.put(second.key, second);
        helper.assertTrue(first.equals(second) && first != second, "intentional addon value-equality override, distinct canonical identities");
        try (var scope = ResearchCatalogTestScope.install(categories); var probe = new Probe(owner)) {
            var entry = costEntry("TC_CHECK_EQUAL_POOLS");
            entry.getStages()[0].setKnow(new ResearchStage.Knowledge[]{
                new ResearchStage.Knowledge(EnumKnowledgeType.OBSERVATION, first, 1),
                new ResearchStage.Knowledge(EnumKnowledgeType.OBSERVATION, second, 1)});
            try (var fixture = new Fixture(entry)) {
                var data = ThaumcraftCapabilities.getKnowledge(owner);
                data.addResearch(entry.getKey()); data.setResearchStage(entry.getKey(), 1);
                data.addKnowledge(EnumKnowledgeType.OBSERVATION, first, 16);
                data.addKnowledge(EnumKnowledgeType.OBSERVATION, second, 19);
                apply(helper, owner, entry.getKey(), 1);
                helper.assertTrue(data.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, first) == 0
                    && data.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, second) == 3, "different registered keys remain independent16-raw payments");
                helper.assertTrue(probe.payments.equals(List.of("TC_EQUAL_A:-16", "TC_EQUAL_B:-16")), "events retain their distinct canonical category arguments");
            }
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    private static final class EqualCategory extends ResearchCategory {
        EqualCategory(String key) { super(key, null, null, null, null, null); }
        @Override public boolean equals(Object other) { return other instanceof EqualCategory; }
        @Override public int hashCode() { return 1; }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        player.setPos(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
        return player;
    }
    private static void apply(GameTestHelper helper, ServerPlayer owner, String key, int stage) {
        var result = ServerResearchProgression.tryProgress(owner, key, stage);
        helper.assertTrue(result.applied(), key + " stage" + stage + " accepted: " + result);
    }
    private static ResearchEntry costEntry(String key, int... amounts) {
        ResearchEntry entry = new ResearchEntry(); entry.setKey(key); entry.setName("private.test"); entry.setCategory("BASICS");
        ResearchStage stage = new ResearchStage(); stage.setText("private.test");
        if (amounts.length > 0) {
            ResearchStage.Knowledge[] costs = new ResearchStage.Knowledge[amounts.length];
            for (int i = 0; i < amounts.length; i++) {
                costs[i] = new ResearchStage.Knowledge(EnumKnowledgeType.OBSERVATION,
                    ResearchCategories.getResearchCategory("BASICS"), amounts[i]);
            }
            stage.setKnow(costs);
        }
        entry.setStages(new ResearchStage[]{stage}); return entry;
    }
    private static void settleSky(ServerLevel level, BlockPos pos) {
        var engine = level.getChunkSource().getLightEngine();
        var pending = engine.waitForPendingTasks(pos.getX() >> 4, pos.getZ() >> 4);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!pending.isDone()) {
            engine.tryScheduleUpdate();
            if (System.nanoTime() - deadline >= 0) throw new AssertionError("Sky fixture light barrier timed out");
            LockSupport.parkNanos(1_000_000L);
        }
        pending.join(); // Real background POST_UPDATE barrier; never public runLightUpdates.
    }
    private static final class Fixture implements AutoCloseable {
        private final ResearchEntry entry, previous;
        private final Map<String, ResearchEntry> map;
        Fixture(ResearchEntry entry) {
            this.entry = entry; map = ResearchCategories.getResearchCategory(entry.getCategory()).research;
            previous = map.put(entry.getKey(), entry);
        }
        String key() { return entry.getKey(); }
        ResearchEntry entry() { return entry; }
        @Override public void close() { if (previous == null) map.remove(entry.getKey()); else map.put(entry.getKey(), previous); }
    }
    public static final class Probe implements AutoCloseable {
        private final ServerPlayer owner;
        final List<String> research = new ArrayList<>(), payments = new ArrayList<>();
        boolean cancelRoot;
        int cancelPaymentAt = -1;
        String cancelKey;
        Consumer<ResearchEvent.Research> researchAction;
        Consumer<ResearchEvent.Knowledge> paymentAction;
        Probe(ServerPlayer owner) { this.owner = owner; NeoForge.EVENT_BUS.register(this); }
        @SubscribeEvent public void research(ResearchEvent.Research event) {
            if (event.getPlayer() != owner) return;
            research.add(event.getResearchKey());
            if (researchAction != null) researchAction.accept(event);
            if (cancelRoot || event.getResearchKey().equals(cancelKey)) event.setCanceled(true);
        }
        @SubscribeEvent public void knowledge(ResearchEvent.Knowledge event) {
            if (event.getPlayer() != owner || event.getAmount() >= 0) return;
            payments.add((event.getCategory() == null ? "" : event.getCategory().key) + ":" + event.getAmount());
            if (paymentAction != null) paymentAction.accept(event);
            if (payments.size() == cancelPaymentAt) event.setCanceled(true);
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
