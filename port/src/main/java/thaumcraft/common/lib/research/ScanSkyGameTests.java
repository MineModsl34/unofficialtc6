package thaumcraft.common.lib.research;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.api.research.ScanningManager;
import thaumcraft.common.lib.TCTags;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;

/** Actual startup scanner, inventory/notes, cancellable progress and loaded-world sky checks.
 * Completion below is ONLY an explicit attachment fixture; no production manager bypass,
 * fake CELESTIALSCANNING unlock, real-client HUD claim or unresolved arcane recipe is added.
 * Tests run synchronously and restore shared gameTime/dayTime before returning. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ScanSkyGameTests {
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        player.setPos(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
        player.setXRot(-90);
        player.setYRot(-90);
        player.setGameMode(GameType.SURVIVAL);
        settleSky(helper.getLevel(), pos); // Structure preparation may still have queued light work.
        return player;
    }

    private static void completeFixture(GameTestHelper helper, ServerPlayer player) {
        var entry = ResearchCategories.getResearch("CELESTIALSCANNING");
        helper.assertTrue(entry != null && entry.getStages() != null && entry.getStages().length > 0,
            "actual original metadata exists; fixture must not invent a replacement catalog entry");
        var knowledge = ThaumcraftCapabilities.getKnowledge(player);
        knowledge.addResearch("CELESTIALSCANNING");
        knowledge.setResearchStage("CELESTIALSCANNING", entry.getStages().length + 1);
        helper.assertTrue(ThaumcraftCapabilities.knowsResearchStrict(player, "CELESTIALSCANNING"),
            "explicit known-complete attachment fixture, NOT production progression");
    }

    private static ItemStack supplies(ServerPlayer player, int paper) {
        ItemStack tools = new ItemStack(ItemsTC.scribingTools);
        tools.setDamageValue(tools.getMaxDamage()); // Wildcard damage accepts even nominally exhausted tools.
        tools.set(DataComponents.CUSTOM_NAME, Component.literal("Private sky fixture"));
        player.getInventory().items.set(0, tools);
        player.getInventory().items.set(1, new ItemStack(Items.PAPER, paper));
        return tools;
    }

    private static int notes(ServerPlayer player, int metadata) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ItemsTC.celestialNotesVariants[metadata])) count += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(ItemsTC.celestialNotesVariants[metadata])) count += stack.getCount();
        }
        return count;
    }

    private static int paper(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(Items.PAPER)) count += stack.getCount();
        return count;
    }

    private static String prefix(ServerPlayer player) { return LegacySkyScanGeometry.dayPrefix(player.level().getGameTime()); }

    private static void settleSky(ServerLevel level, BlockPos position) {
        var engine = level.getChunkSource().getLightEngine();
        var pending = engine.waitForPendingTasks(position.getX() >> 4, position.getZ() >> 4);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        // ThreadedLevelLightEngine.addTask uses its sorter/task mailboxes; its POST_UPDATE
        // future runs after super.runLightUpdates on that background mailbox, without a
        // main game tick. Repeated scheduling also covers a not-yet-enqueued sorter task.
        // Never call its forbidden public runLightUpdates or advance global world time.
        while (!pending.isDone()) {
            engine.tryScheduleUpdate();
            if (System.nanoTime() - deadline >= 0) throw new AssertionError("Sky fixture lighting did not settle within 10 seconds");
            LockSupport.parkNanos(1_000_000L);
        }
        pending.join(); // Propagate completion failure rather than accepting stale sky light.
    }

    @GameTest(template = "scan_test", skyAccess = true)
    public static void strictCompletionNullPitchAndLegacySkyPositionGateWithoutUnlockingResearch(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "TC_Sky_Gate");
        ScanSky scanner = new ScanSky();
        try (var clock = new Clock(helper.getLevel(), 9L * 24000, 6000)) {
            supplies(owner, 2);
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            helper.assertFalse(scanner.checkThing(owner, null), "unknown CELESTIALSCANNING blocks scanning");
            data.addResearch("CELESTIALSCANNING");
            helper.assertTrue(data.isResearchKnown("CELESTIALSCANNING") && !data.isResearchComplete("CELESTIALSCANNING"),
                "known is not strict complete");
            var before = data.serializeNBT().copy();
            scanner.onSuccess(owner, null);
            helper.assertTrue(data.serializeNBT().equals(before) && paper(owner) == 2 && notes(owner, 0) == 0,
                "direct success call cannot bypass completion gate or consume materials");
            completeFixture(helper, owner);
            helper.assertTrue(scanner.checkThing(owner, null), "actual open sky and sun alignment pass after fixture completion");
            helper.assertFalse(scanner.checkThing(owner, new Object()), "only null scan target is sky");
            owner.setXRot(.01F);
            helper.assertFalse(scanner.checkThing(owner, null), "any positive pitch fails");
            owner.setXRot(-90);
            owner.setYRot(0);
            helper.assertFalse(scanner.checkThing(owner, null), "daytime non-celestial heading fails");
            owner.setYRot(-90);
            BlockPos roof = BlockPos.containing(owner.getX(), owner.getY() + .5D, owner.getZ()).above();
            helper.getLevel().setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
            settleSky(helper.getLevel(), roof);
            helper.assertFalse(scanner.checkThing(owner, null), "opaque block at original sky query blocks it");
            helper.getLevel().setBlockAndUpdate(roof, Blocks.AIR.defaultBlockState());
            owner.setPos(owner.getX(), owner.getY() + .75D, owner.getZ());
            // Deliberate command-only fractional placement: modern blockPosition().above()
            // is the lower block; original getPosition()+up starts one block higher.
            BlockPos lower = owner.blockPosition().above();
            helper.getLevel().setBlockAndUpdate(lower, Blocks.STONE.defaultBlockState());
            settleSky(helper.getLevel(), lower);
            helper.assertTrue(scanner.checkThing(owner, null), "source y+.5 sky query is not replaced by modern feet-floor query");
            helper.getLevel().setBlockAndUpdate(lower, Blocks.AIR.defaultBlockState());
            helper.assertTrue(scanner.getResearchKey(owner, null).isEmpty(), "empty key suppresses generic scan feedback");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "scan_test", skyAccess = true)
    public static void defaultSunScannerConsumesOnePaperRecordsOpaqueDailyKeyOnceAndFlushesOwnerOnly(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Sky_Sun"), other = player(helper, "TC_Sky_Other");
        List<ServerPlayer> recipients = new ArrayList<>();
        List<Object> packets = new ArrayList<>();
        try (var clock = new Clock(helper.getLevel(), 9L * 24000, 6000);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> { recipients.add(recipient); packets.add(packet); })) {
            completeFixture(helper, owner);
            ItemStack tools = supplies(owner, 3), toolBefore = tools.copy();
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            var otherBefore = ThaumcraftCapabilities.getKnowledge(other).serializeNBT().copy();
            var warpBefore = ThaumcraftCapabilities.getWarp(owner).serializeNBT().copy();
            int xp = owner.totalExperience;
            String key = prefix(owner) + "Sun";
            helper.assertTrue(new ItemStack(Items.PAPER).is(TCTags.Items.SCAN_PAPER), "registered finite paper alias bridge includes vanilla paper");
            ScanningManager.scanTheThing(owner, null); // Real startup global ScanSky registration.
            helper.assertTrue(notes(owner, 0) == 1 && paper(owner) == 2 && data.isResearchComplete(key)
                && data.getResearchStage(key) == 0, "one actual registered scanner creates note and exact opaque daily marker");
            helper.assertTrue(ItemStack.isSameItemSameComponents(tools, toolBefore) && tools.getCount() == toolBefore.getCount(),
                "wildcard-damage/custom-NBT tool is never consumed or damaged");
            for (EnumResearchFlag flag : EnumResearchFlag.values()) helper.assertFalse(data.hasResearchFlag(key, flag), "no invented daily-key catalog flags");
            helper.assertTrue(packets.isEmpty() && PlayerDataSync.isKnowledgeDirty(owner), "source schedules knowledge rather than immediate note packet");
            ScanningManager.scanTheThing(owner, null);
            helper.assertTrue(notes(owner, 0) == 1 && paper(owner) == 2, "duplicate daily key awards nothing");
            owner.tickCount = 20;
            PlayerDataSync.flushKnowledgeIfDue(owner);
            helper.assertTrue(packets.size() == 1 && packets.getFirst() instanceof PacketSyncKnowledge && recipients.getFirst() == owner,
                "actual dirty flush only addresses scanning owner");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(other).serializeNBT().equals(otherBefore)
                && ThaumcraftCapabilities.getWarp(owner).serializeNBT().equals(warpBefore) && owner.totalExperience == xp,
                "no foreign knowledge, research warp or catalog XP award");
        } finally { PlayerDataSync.forget(owner); PlayerDataSync.forget(other); }
        helper.succeed();
    }

    @GameTest(template = "scan_test", skyAccess = true)
    public static void canceledProgressSeesAwardFirstStillConsumesPaperCleansOldKeysAndCanRetry(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Sky_Cancel");
        List<Object> packets = new ArrayList<>();
        try (var clock = new Clock(helper.getLevel(), 9L * 24000, 6000);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> packets.add(packet))) {
            completeFixture(helper, owner);
            supplies(owner, 3);
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            String stale = "CEL_8_Sun", key = prefix(owner) + "Sun";
            data.addResearch(stale);
            data.setResearchFlag(stale, EnumResearchFlag.POPUP);
            try (var probe = new Probe(owner, key)) {
                probe.cancel = true;
                ScanningManager.scanTheThing(owner, null);
                helper.assertTrue(probe.calls == 1 && probe.notesAtEvent.equals(List.of(1)) && probe.paperAtEvent.equals(List.of(2))
                    && probe.staleAtEvent.getFirst(), "actual cancellable event occurs after note/paper and before old-key cleanup");
                helper.assertTrue(!data.isResearchKnown(key) && !data.isResearchKnown(stale) && notes(owner, 0) == 1 && paper(owner) == 2,
                    "cancellation rejects marker, NOT already granted note/paper; cleanup still happens");
                helper.assertTrue(data.hasResearchFlag(stale, EnumResearchFlag.POPUP), "source removeResearch leaves orphan flags unchanged");
                ScanningManager.scanTheThing(owner, null);
                helper.assertTrue(probe.calls == 2 && notes(owner, 0) == 2 && paper(owner) == 1 && !data.isResearchKnown(key),
                    "uncaptured daily marker allows another source-accurate retry/award");
            }
            helper.assertTrue(packets.isEmpty() && PlayerDataSync.isKnowledgeDirty(owner), "cleanup queues sync even when progress canceled");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "scan_test", skyAccess = true)
    public static void missingSuppliesCleanOldPrefixesButDuplicateDoesNotCleanOrDirty(GameTestHelper helper) {
        ServerPlayer missing = player(helper, "TC_Sky_Missing"), duplicate = player(helper, "TC_Sky_Duplicate");
        try (var clock = new Clock(helper.getLevel(), 9L * 24000, 6000)) {
            completeFixture(helper, missing);
            var missingData = ThaumcraftCapabilities.getKnowledge(missing);
            missingData.addResearch("CEL_8_Star0");
            missingData.addResearch("CEL_9_Star0");
            missingData.addResearch("!unrelated");
            new ScanSky().onSuccess(missing, null);
            helper.assertTrue(!missingData.isResearchKnown("CEL_8_Star0") && missingData.isResearchKnown("CEL_9_Star0")
                && missingData.isResearchKnown("!unrelated") && !missingData.isResearchKnown("CEL_9_Sun")
                && PlayerDataSync.isKnowledgeDirty(missing), "missing supplies still prune only other-day CEL_ markers and queue sync");
            completeFixture(helper, duplicate);
            supplies(duplicate, 2);
            var duplicateData = ThaumcraftCapabilities.getKnowledge(duplicate);
            duplicateData.addResearch("CEL_8_Star0");
            duplicateData.addResearch("CEL_9_Sun");
            var before = duplicateData.serializeNBT().copy();
            new ScanSky().onSuccess(duplicate, null);
            helper.assertTrue(duplicateData.serializeNBT().equals(before) && paper(duplicate) == 2 && notes(duplicate, 0) == 0
                && !PlayerDataSync.isKnowledgeDirty(duplicate), "duplicate early return neither consumes, cleans nor marks dirty");
        } finally { PlayerDataSync.forget(missing); PlayerDataSync.forget(duplicate); }
        helper.succeed();
    }

    @GameTest(template = "scan_test", skyAccess = true)
    public static void onlyMainThirtySixSupplySlotsCountAndWrongExactToolDoesNotCount(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "TC_Sky_Slots");
        try (var clock = new Clock(helper.getLevel(), 9L * 24000, 6000)) {
            completeFixture(helper, owner);
            ItemStack tools = new ItemStack(ItemsTC.scribingTools);
            owner.getInventory().offhand.set(0, tools);
            owner.getInventory().items.set(1, new ItemStack(Items.PAPER, 2));
            new ScanSky().onSuccess(owner, null);
            helper.assertTrue(paper(owner) == 2 && notes(owner, 0) == 0, "offhand tools are not main-inventory supply");
            owner.getInventory().offhand.set(0, ItemStack.EMPTY);
            owner.getInventory().armor.set(0, tools);
            new ScanSky().onSuccess(owner, null);
            helper.assertTrue(paper(owner) == 2 && notes(owner, 0) == 0, "command-installed armor tools do not count");
            owner.getInventory().armor.set(0, ItemStack.EMPTY);
            owner.getInventory().items.set(0, new ItemStack(Items.WRITABLE_BOOK));
            new ScanSky().onSuccess(owner, null);
            helper.assertTrue(paper(owner) == 2 && notes(owner, 0) == 0, "similar writing item does not match exact TC tool");
            owner.getInventory().items.set(0, tools);
            owner.getInventory().items.set(1, ItemStack.EMPTY);
            owner.getInventory().offhand.set(0, new ItemStack(Items.PAPER, 2));
            new ScanSky().onSuccess(owner, null);
            helper.assertTrue(owner.getInventory().offhand.getFirst().getCount() == 2 && notes(owner, 0) == 0, "offhand paper does not count");
            owner.getInventory().items.set(35, new ItemStack(Items.PAPER, 2));
            new ScanSky().onSuccess(owner, null);
            helper.assertTrue(paper(owner) == 1 && notes(owner, 0) == 1, "last of 36 main slots does count and offhand paper remains untouched");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "scan_test", skyAccess = true)
    public static void allEightMoonNotesUseDayTimePhaseButStableGameTimeDayKey(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "TC_Sky_Moons");
        try (var clock = new Clock(helper.getLevel(), 9L * 24000, 18000)) {
            completeFixture(helper, owner);
            supplies(owner, 9);
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            for (int phase = 0; phase < 8; phase++) {
                helper.getLevel().setDayTime(phase * 24000L + 18000);
                helper.assertTrue(new ScanSky().checkThing(owner, null), "aligned moon is scannable at phase " + phase);
                ScanningManager.scanTheThing(owner, null);
                helper.assertTrue(notes(owner, 5 + phase) == 1 && data.isResearchComplete("CEL_9_Moon" + phase),
                    "actual metadata and gameTime/dayTime split for phase " + phase);
            }
            helper.assertTrue(paper(owner) == 1, "each distinct moon consumes exactly one sheet");
            for (int phase = 0; phase < 8; phase++) helper.assertTrue(data.isResearchKnown("CEL_9_Moon" + phase), "same gameTime day retains other moon markers");
            helper.getLevel().setDayTime(18000);
            ScanningManager.scanTheThing(owner, null);
            helper.assertTrue(paper(owner) == 1 && notes(owner, 5) == 1, "moving dayTime back does not reset an existing gameTime key");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "scan_test", skyAccess = true)
    public static void fourNightStarDirectionsUseOriginalFacingMetadataAndAllowHorizontalPitch(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "TC_Sky_Stars");
        try (var clock = new Clock(helper.getLevel(), 9L * 24000, 18000)) {
            completeFixture(helper, owner);
            supplies(owner, 5);
            owner.setXRot(0); // Source allows pitch == 0; this is off the aligned moon.
            float[] yawBySourceNum = {180, 0, 90, -90};
            for (int num = 0; num < yawBySourceNum.length; num++) {
                owner.setYRot(yawBySourceNum[num]);
                helper.assertTrue(new ScanSky().checkThing(owner, null), "off-celestial sky target still works at night");
                ScanningManager.scanTheThing(owner, null);
                helper.assertTrue(notes(owner, 1 + num) == 1 && ThaumcraftCapabilities.knowsResearch(owner, "CEL_9_Star" + num),
                    "source facing index-minus-two and notes metadata " + num);
            }
            helper.assertTrue(notes(owner, 5) == 0 && paper(owner) == 1, "off-target stars do not also award an aligned moon");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "scan_test", skyAccess = true)
    public static void fullMainInventoryMergesExistingOffhandNoteOtherwiseDropsWithoutDestroyingAward(GameTestHelper helper) {
        ServerPlayer merge = player(helper, "TC_Sky_Merge"), drop = player(helper, "TC_Sky_Drop");
        try (var clock = new Clock(helper.getLevel(), 9L * 24000, 6000)) {
            for (ServerPlayer owner : List.of(merge, drop)) {
                completeFixture(helper, owner);
                for (int slot = 0; slot < 36; slot++) owner.getInventory().items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
                owner.getInventory().items.set(34, new ItemStack(Items.PAPER, 2));
                owner.getInventory().items.set(35, new ItemStack(ItemsTC.scribingTools));
            }
            merge.getInventory().offhand.set(0, new ItemStack(ItemsTC.celestialNotesVariants[0], 3));
            new ScanSky().onSuccess(merge, null);
            helper.assertTrue(merge.getInventory().offhand.getFirst().getCount() == 4 && paper(merge) == 1,
                "source Inventory.add merges compatible existing offhand stack despite full main inventory");
            drop.getInventory().offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));
            var before = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).size();
            new ScanSky().onSuccess(drop, null);
            var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), entity -> entity.getItem().is(ItemsTC.celestialNotesVariants[0]));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).size() == before + 1
                && items.stream().anyMatch(entity -> entity.getItem().getCount() == 1) && notes(drop, 0) == 0,
                "failed insertion actually drops one note into the isolated test world, not silent loss");
            helper.assertTrue(paper(drop) == 1 && ThaumcraftCapabilities.knowsResearch(drop, "CEL_9_Sun"), "dropped reward still precedes actual daily progress");
            for (ItemEntity entity : items) entity.discard();
        } finally { PlayerDataSync.forget(merge); PlayerDataSync.forget(drop); }
        helper.succeed();
    }

    private static final class Clock implements AutoCloseable {
        private final ServerLevel level;
        private final ServerLevelData data;
        private final long gameTime, dayTime;

        Clock(ServerLevel level, long gameTime, long dayTime) {
            this.level = level;
            this.data = (ServerLevelData) level.getLevelData();
            this.gameTime = level.getGameTime();
            this.dayTime = level.getDayTime();
            data.setGameTime(gameTime);
            level.setDayTime(dayTime);
        }

        @Override public void close() { data.setGameTime(gameTime); level.setDayTime(dayTime); }
    }

    public static final class Probe implements AutoCloseable {
        private final ServerPlayer owner;
        private final String key;
        boolean cancel;
        int calls;
        final List<Integer> notesAtEvent = new ArrayList<>(), paperAtEvent = new ArrayList<>();
        final List<Boolean> staleAtEvent = new ArrayList<>();

        Probe(ServerPlayer owner, String key) { this.owner = owner; this.key = key; NeoForge.EVENT_BUS.register(this); }

        @SubscribeEvent public void research(ResearchEvent.Research event) {
            if (event.getPlayer() != owner || !event.getResearchKey().equals(key)) return;
            calls++;
            notesAtEvent.add(notes(owner, 0));
            paperAtEvent.add(paper(owner));
            staleAtEvent.add(ThaumcraftCapabilities.knowsResearch(owner, "CEL_8_Sun"));
            if (cancel) event.setCanceled(true);
        }

        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
