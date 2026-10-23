package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.items.curios.ItemThaumonomicon;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.misc.PacketKnowledgeGain;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;
import thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit;

/** Real registered items/playerTouch/wakeup/use and real event routing, with unspawned FakePlayer
 * owners and a scoped GameTest-only personal-packet capture. NOT normal survival, dust conversion,
 * full book GUI, actual TCP, cheat sheets or item/NBT migration acceptance.
 */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class BookAcquisitionGameTests {
    @GameTest(template = "empty")
    public static void actualNormalBookPickupRecordsRawKnownZeroOnceOnlyForOwner(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Book_Pickup"), other = player(helper, "TC_Book_Other");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        data.addResearch("!TC_BOOK_KEEP"); data.setResearchStage("!TC_BOOK_KEEP", 3);
        data.setResearchFlag("!TC_BOOK_KEEP", EnumResearchFlag.PAGE);
        data.addKnowledge(EnumKnowledgeType.OBSERVATION,
            thaumcraft.api.research.ResearchCategories.getResearchCategory("BASICS"), 7);
        var warp = ThaumcraftCapabilities.getWarp(owner);
        warp.set(EnumWarpType.PERMANENT, 11); warp.set(EnumWarpType.NORMAL, 7);
        warp.set(EnumWarpType.TEMPORARY, 3); warp.setCounter(29);
        CompoundTag before = data.serializeNBT().copy(), warpBefore = warp.serializeNBT().copy();
        CompoundTag otherBefore = ThaumcraftCapabilities.getKnowledge(other).serializeNBT().copy();
        CompoundTag otherWarp = ThaumcraftCapabilities.getWarp(other).serializeNBT().copy();
        int xp = owner.totalExperience, xpLevel = owner.experienceLevel;
        int xpProgress = Float.floatToIntBits(owner.experienceProgress);
        try (var trace = new Trace(helper, owner); var probe = new Probe(owner)) {
            helper.assertTrue(ItemsTC.thaumonomicon instanceof ItemThaumonomicon, "actual registered normal book class");
            ItemStack normal = new ItemStack(ItemsTC.thaumonomicon);
            helper.assertTrue(normal.getMaxStackSize() == 1 && normal.getRarity() == Rarity.UNCOMMON, "source normal properties");
            ItemEntity first = drop(helper, owner, normal);
            first.playerTouch(owner);
            helper.assertTrue(data.isResearchKnown("!gotthaumonomicon") && data.getResearchStage("!gotthaumonomicon") == 0,
                "production PRE records raw known0, not ResearchManager progress");
            helper.assertTrue(!first.isAlive() && owner.getInventory().items.stream().anyMatch(s -> s.is(ItemsTC.thaumonomicon)),
                "actual vanilla pickup adds the registered item");
            helper.assertTrue(trace.snapshots.size() == 1 && hasKey(trace.snapshots.getFirst(), "!gotthaumonomicon"), "immediate owner snapshot contains marker");
            CompoundTag expected = before.copy();
            var marker = new CompoundTag(); marker.putString("key", "!gotthaumonomicon");
            expected.getList("research", net.minecraft.nbt.Tag.TAG_COMPOUND).add(marker);
            helper.assertTrue(ResearchCatalogNetworkAudit.normalizedKnowledge(trace.snapshots.getFirst()).equals(
                    ResearchCatalogNetworkAudit.normalizedKnowledge(expected))
                && ResearchCatalogNetworkAudit.normalizedKnowledge(data.serializeNBT()).equals(
                    ResearchCatalogNetworkAudit.normalizedKnowledge(expected)),
                "full raw research/stages/flags/knowledge preserved; new known0 row omits stage and flags");
            drop(helper, owner, normal.copy()).playerTouch(owner);
            helper.assertTrue(trace.snapshots.size() == 1, "second book pickup does not resync or regrant");
            helper.assertTrue(probe.research.isEmpty() && owner.totalExperience == xp && owner.experienceLevel == xpLevel
                && Float.floatToIntBits(owner.experienceProgress) == xpProgress && warp.serializeNBT().equals(warpBefore)
                && !PlayerDataSync.isKnowledgeDirty(owner),
                "bootstrap has no ResearchEvent, XP or dirty latch");
            for (var flag : EnumResearchFlag.values()) helper.assertFalse(data.hasResearchFlag("!gotthaumonomicon", flag), "no bootstrap flags: " + flag);
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(other).serializeNBT().equals(otherBefore)
                && ThaumcraftCapabilities.getWarp(other).serializeNBT().equals(otherWarp), "bystander full knowledge/warp storage unchanged");
        } finally { cleanup(owner, other); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualPreRetainsFullInventoryAndTargetOwnerQuirks(GameTestHelper helper) throws Exception {
        ServerPlayer full = player(helper, "TC_Book_Full"), locked = player(helper, "TC_Book_Target");
        fillInventory(full);
        ItemEntity fullBook = drop(helper, full, new ItemStack(ItemsTC.thaumonomicon));
        try (var fullTrace = new Trace(helper, full)) {
            fullBook.playerTouch(full);
            helper.assertTrue(fullBook.isAlive() && fullBook.getItem().getCount() == 1, "full inventory rejects actual insertion");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(full).getResearchStage("!gotthaumonomicon") == 0
                && fullTrace.snapshots.size() == 1, "source PRE already recorded marker despite failed insertion");
        } finally { fullBook.discard(); cleanup(full); }
        ItemEntity lockedBook = drop(helper, locked, new ItemStack(ItemsTC.thaumonomicon));
        try (var trace = new Trace(helper, locked)) {
            lockedBook.setTarget(UUID.randomUUID());
            lockedBook.playerTouch(locked);
            helper.assertTrue(lockedBook.isAlive() && locked.getInventory().items.stream().noneMatch(s -> s.is(ItemsTC.thaumonomicon)), "other target prevents inventory pickup");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(locked).getResearchStage("!gotthaumonomicon") == 0
                && trace.snapshots.size() == 1, "source PRE precedes target ownership check");
        } finally { lockedBook.discard(); cleanup(locked); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualPickupDelayAndPriorDenialBlockButLaterDenialCannotRollbackMarker(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Book_Gates");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        ItemEntity book = drop(helper, owner, new ItemStack(ItemsTC.thaumonomicon));
        try (var trace = new Trace(helper, owner); var probe = new Probe(owner)) {
            book.setPickUpDelay(5);
            book.playerTouch(owner);
            helper.assertTrue(book.isAlive() && !data.isResearchKnown("!gotthaumonomicon") && trace.snapshots.isEmpty(), "old pre-event pickupDelay guard preserved");
            book.setNoPickUpDelay();
            probe.denyBefore = true;
            book.playerTouch(owner);
            helper.assertTrue(book.isAlive() && !data.isResearchKnown("!gotthaumonomicon") && trace.snapshots.isEmpty(), "earlier denial prevents our bootstrap");
            probe.denyBefore = false; probe.denyAfter = true;
            book.playerTouch(owner);
            helper.assertTrue(book.isAlive() && data.getResearchStage("!gotthaumonomicon") == 0 && trace.snapshots.size() == 1,
                "later source event denial does not roll back earlier raw marker/sync");
            helper.assertTrue(owner.getInventory().items.stream().noneMatch(s -> s.is(ItemsTC.thaumonomicon)), "later denied entity was never acquired");
        } finally { book.discard(); cleanup(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualNoSleepCrystalPickupSyncsBothRawMarkersBeforeJournalDeliveryOnce(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Book_NoSleep");
        boolean previousNoSleep = ModConfig.CONFIG_MISC.noSleep;
        ModConfig.CONFIG_MISC.noSleep = true;
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        int xp = owner.totalExperience;
        try (var trace = new Trace(helper, owner); var probe = new Probe(owner)) {
            drop(helper, owner, new ItemStack(ItemsTC.crystalEssence)).playerTouch(owner);
            helper.assertTrue(data.getResearchStage("!gotcrystals") == 0 && data.getResearchStage("!gotdream") == 0, "source direct known0 crystal/dream markers");
            helper.assertTrue(trace.snapshots.size() == 2 && hasKey(trace.snapshots.get(0), "!gotcrystals")
                && !hasKey(trace.snapshots.get(0), "!gotdream") && hasKey(trace.snapshots.get(1), "!gotdream"), "source immediate crystal-then-dream packet order");
            helper.assertTrue(trace.journalsAtSend.equals(List.of(0L, 0L)), "both immediate source snapshots precede journal insertion");
            assertJournal(helper, owner, journals(owner).getFirst());
            drop(helper, owner, new ItemStack(ItemsTC.crystalEssence)).playerTouch(owner);
            helper.assertTrue(trace.snapshots.size() == 2 && journals(owner).size() == 1, "no second journal/resync on repeat crystals");
            helper.assertTrue(probe.research.isEmpty() && owner.totalExperience == xp && !PlayerDataSync.isKnowledgeDirty(owner), "raw bootstrap does not emit progression rewards");
        } finally { ModConfig.CONFIG_MISC.noSleep = previousNoSleep; cleanup(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualWakeEventNeedsCrystalButNotDawnOrSleepDuration(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Book_Wake"), other = player(helper, "TC_Book_NoCrystal");
        boolean previousNoSleep = ModConfig.CONFIG_MISC.noSleep;
        ModConfig.CONFIG_MISC.noSleep = false;
        long dayTime = helper.getLevel().getDayTime();
        try (var trace = new Trace(helper, owner); var probe = new Probe(owner)) {
            drop(helper, owner, new ItemStack(ItemsTC.crystalEssence)).playerTouch(owner);
            helper.assertTrue(trace.snapshots.size() == 1 && journals(owner).isEmpty(), "normal crystal pickup alone gives no dream journal");
            // Real Player.stopSleepInBed posts NeoForge wakeup even without a dawn/sleep-duration
            // requirement. Do not substitute a direct BookAcquisitionEvents call or grant fixture.
            owner.stopSleepInBed(true, false);
            helper.assertTrue(trace.snapshots.size() == 2 && ThaumcraftCapabilities.getKnowledge(owner).getResearchStage("!gotdream") == 0,
                "actual wake route records source dream regardless wake flags");
            assertJournal(helper, owner, journals(owner).getFirst());
            owner.stopSleepInBed(false, true);
            helper.assertTrue(trace.snapshots.size() == 2 && journals(owner).size() == 1, "repeat wake cannot duplicate journal");
            other.stopSleepInBed(true, false);
            helper.assertFalse(ThaumcraftCapabilities.getKnowledge(other).isResearchKnown("!gotdream"), "unrelated crystal-free owner remains untouched");
            helper.assertTrue(journals(other).isEmpty() && helper.getLevel().getDayTime() == dayTime && probe.research.isEmpty(), "no invented sunrise/time/progression mutation");
        } finally { ModConfig.CONFIG_MISC.noSleep = previousNoSleep; cleanup(owner, other); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullInventoryDreamDropsSourceJournalAtHalfEyeHeight(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Book_Drop");
        fillInventory(owner);
        boolean previousNoSleep = ModConfig.CONFIG_MISC.noSleep;
        ModConfig.CONFIG_MISC.noSleep = true;
        List<ItemEntity> spawned = new ArrayList<>();
        try (var trace = new Trace(helper, owner)) {
            ItemEntity crystal = drop(helper, owner, new ItemStack(ItemsTC.crystalEssence));
            spawned.add(crystal);
            crystal.playerTouch(owner);
            var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(2),
                item -> item.getItem().is(Items.WRITTEN_BOOK));
            spawned.addAll(dropped);
            helper.assertTrue(crystal.isAlive() && journals(owner).isEmpty() && dropped.size() == 1, "source failed journal insertion spawns one real vanilla entity");
            ItemEntity journal = dropped.getFirst();
            helper.assertTrue(journal.getX() == owner.getX() && journal.getZ() == owner.getZ()
                && Math.abs(journal.getY() - (owner.getY() + owner.getEyeHeight() / 2.0F)) < 0.00001,
                "InventoryUtils.dropItemAtEntity source position, not player throwing API");
            helper.assertFalse(journal.hasPickUpDelay(), "source dream drop has vanilla zero pickup delay");
            assertJournal(helper, owner, journal.getItem());
            helper.assertTrue(trace.snapshots.size() == 2 && !PlayerDataSync.isKnowledgeDirty(owner), "source raw markers sync even with failed crystal/journal insertion");
        } finally {
            ModConfig.CONFIG_MISC.noSleep = previousNoSleep;
            spawned.forEach(ItemEntity::discard); cleanup(owner);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualBookUseSyncsWithoutInventingAcquisitionOrProgress(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Book_Use");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        CompoundTag before = data.serializeNBT().copy();
        int xp = owner.totalExperience;
        try (var trace = new Trace(helper, owner); var probe = new Probe(owner)) {
            ItemStack book = new ItemStack(ItemsTC.thaumonomicon);
            owner.setItemInHand(InteractionHand.OFF_HAND, book);
            var result = ItemsTC.thaumonomicon.use(helper.getLevel(), owner, InteractionHand.OFF_HAND);
            helper.assertTrue(result.getResult().consumesAction() && result.getObject() == book, "actual common Item.use returns held normal book");
            helper.assertTrue(trace.snapshots.size() == 1 && trace.snapshots.getFirst().equals(before) && data.serializeNBT().equals(before), "normal use explicitly syncs owner without acquisition marker");
            helper.assertTrue(!data.isResearchKnown("!gotthaumonomicon") && probe.research.isEmpty()
                && owner.totalExperience == xp && !PlayerDataSync.isKnowledgeDirty(owner), "book in hand/use is not an invented acquisition or unlock API");
        } finally { cleanup(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualBookUseRepairsKnownZeroParentsButPreservesKnownAndCancelledMalformedSiblings(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Book_Repair");
        var categories = ResearchCategoryDefinitions.newBuiltinCategories();
        ResearchEntry parent = entry("TC_BOOK_PARENT"), free = entry("TC_BOOK_FREE"), pending = entry("TC_BOOK_KNOWN_ZERO"),
            cancelled = entry("TC_BOOK_CANCELLED"), unsupported = entry("TC_BOOK_UNSUPPORTED");
        free.setRewardKnow(new ResearchStage.Knowledge[]{new ResearchStage.Knowledge(EnumKnowledgeType.OBSERVATION, categories.get("BASICS"), 1)});
        unsupported.setRewardKnow(new ResearchStage.Knowledge[]{new ResearchStage.Knowledge(EnumKnowledgeType.OBSERVATION, categories.get("BASICS"), 0)});
        parent.setSiblings(new String[]{free.getKey(), pending.getKey(), cancelled.getKey(), unsupported.getKey()});
        for (var entry : List.of(parent, free, pending, cancelled, unsupported)) categories.get("BASICS").research.put(entry.getKey(), entry);
        boolean previousNoFlags = ResearchManager.noFlags;
        try (var scope = ResearchCatalogTestScope.install(categories); var trace = new Trace(helper, owner, true); var probe = new Probe(owner)) {
            ResearchManager.noFlags = false;
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            data.addResearch(parent.getKey()); data.addResearch(pending.getKey()); // Explicit synthetic repair preconditions only.
            probe.cancelResearchKey = cancelled.getKey();
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.thaumonomicon));
            int xp = owner.totalExperience;
            ItemsTC.thaumonomicon.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
            helper.assertTrue(data.getResearchStage(parent.getKey()) == 0 && data.isResearchComplete(free.getKey()), "normal source repairs sibling of known0 parent without advancing parent");
            helper.assertTrue(data.getResearchStage(pending.getKey()) == 0 && !data.isResearchComplete(pending.getKey()), "known0 sibling is known, so source repair deliberately skips it");
            helper.assertFalse(data.isResearchKnown(cancelled.getKey()), "real canceled sibling event remains canceled");
            helper.assertFalse(data.isResearchKnown(unsupported.getKey()), "malformed zero-amount reward remains fail-closed before its event");
            helper.assertTrue(probe.research.equals(List.of(free.getKey(), cancelled.getKey())) && owner.totalExperience == xp + 5, "only genuine supported child completion earns source XP/event");
            helper.assertTrue(trace.snapshots.size() == 1 && hasKey(trace.snapshots.getFirst(), free.getKey())
                && PlayerDataSync.isKnowledgeDirty(owner), "final immediate owner sync does not consume queued Manager dirty update");
            helper.assertTrue(trace.gains.size() == 1 && trace.gains.getFirst().knowledgeType() == EnumKnowledgeType.OBSERVATION
                && trace.gains.getFirst().categoryKey().equals("BASICS")
                && data.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, categories.get("BASICS")) == 16,
                "supported sibling emits its real owner-only whole-point feedback and raw16 reward");
            helper.assertFalse(data.isResearchKnown("!gotthaumonomicon"), "repair use still does not invent pickup marker");
        } finally { ResearchManager.noFlags = previousNoFlags; cleanup(owner); }
        helper.succeed();
    }

    private static ResearchEntry entry(String key) {
        ResearchEntry entry = new ResearchEntry();
        entry.setKey(key); entry.setCategory("BASICS"); entry.setName("research.ORE.title");
        entry.setStages(new ResearchStage[]{new ResearchStage()});
        return entry;
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        owner.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)));
        return owner;
    }

    private static ItemEntity drop(GameTestHelper helper, ServerPlayer owner, ItemStack stack) {
        ItemEntity item = new ItemEntity(helper.getLevel(), owner.getX(), owner.getY(), owner.getZ(), stack);
        item.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    private static void fillInventory(ServerPlayer owner) {
        for (int i = 0; i < owner.getInventory().items.size(); i++) owner.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
    }

    private static List<ItemStack> journals(ServerPlayer owner) {
        return owner.getInventory().items.stream().filter(stack -> stack.is(Items.WRITTEN_BOOK)).toList();
    }

    private static void assertJournal(GameTestHelper helper, ServerPlayer owner, ItemStack stack) {
        var content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        helper.assertTrue(stack.is(Items.WRITTEN_BOOK) && content != null && content.generation() == 3
            && content.author().equals(owner.getName().getString()) && content.pages().size() == 3 && !content.resolved(),
            "source typed written journal author/generation/pages");
        helper.assertTrue(content.tryCraftCopy() == null, "source generation3 journal cannot be copied through written-book crafting");
        helper.assertTrue(content.title().raw().equals(Language.getInstance().getOrDefault("book.start.title")), "journal title actually localized using common server language");
        for (int page = 1; page <= 3; page++) {
            String raw = Language.getInstance().getOrDefault("book.start." + page);
            helper.assertFalse(raw.equals("book.start." + page), "actual TC server translation loaded");
            // Converted lang pages contain a JSON string, including physical newline chars.
            // Vanilla/Gson old page resolution decodes that string rather than displaying quotes.
            String expected = com.google.gson.JsonParser.parseString(raw).getAsString();
            helper.assertTrue(content.pages().get(page - 1).raw().getString().equals(expected), "source translated page decoded exactly: " + page);
        }
    }

    private static boolean hasKey(CompoundTag tag, String key) {
        for (var item : tag.getList("research", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            if (((CompoundTag) item).getString("key").equals(key)) return true;
        }
        return false;
    }

    private static void cleanup(ServerPlayer... players) { for (var player : players) PlayerDataSync.forget(player); }

    private static final class Trace implements AutoCloseable {
        final List<CompoundTag> snapshots = new ArrayList<>();
        final List<Long> journalsAtSend = new ArrayList<>();
        final List<PacketKnowledgeGain> gains = new ArrayList<>();
        private final AutoCloseable capture;
        Trace(GameTestHelper helper, ServerPlayer owner) {
            this(helper, owner, false);
        }
        Trace(GameTestHelper helper, ServerPlayer owner, boolean allowCompletionGain) {
            capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {
                if (allowCompletionGain && packet instanceof PacketKnowledgeGain gain) {
                    helper.assertTrue(recipient == owner, "completion feedback is private to the actual owner");
                    gains.add(gain);
                    return;
                }
                helper.assertTrue(recipient == owner && packet instanceof PacketSyncKnowledge, "only actual owner knowledge route");
                snapshots.add(((PacketSyncKnowledge) packet).data());
                journalsAtSend.add((long) journals(owner).size());
            });
        }
        @Override public void close() throws Exception { capture.close(); }
    }

    /** Real synchronous owner-filtered subscribers, restored before any world tick. */
    public static final class Probe implements AutoCloseable {
        private final ServerPlayer owner;
        final List<String> research = new ArrayList<>();
        boolean denyBefore, denyAfter;
        String cancelResearchKey;
        Probe(ServerPlayer owner) { this.owner = owner; NeoForge.EVENT_BUS.register(this); }
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void before(ItemEntityPickupEvent.Pre event) {
            if (event.getPlayer() == owner && denyBefore) event.setCanPickup(TriState.FALSE);
        }
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void after(ItemEntityPickupEvent.Pre event) {
            if (event.getPlayer() == owner && denyAfter) event.setCanPickup(TriState.FALSE);
        }
        @SubscribeEvent
        public void research(ResearchEvent.Research event) {
            if (event.getPlayer() != owner) return;
            research.add(event.getResearchKey());
            if (event.getResearchKey().equals(cancelResearchKey)) event.setCanceled(true);
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
