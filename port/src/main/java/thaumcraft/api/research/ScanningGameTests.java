package thaumcraft.api.research;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import thaumcraft.common.lib.research.ScanGeneric;

/**
 * Same package solely to test the actual package-private dispatch/inventory seams.
 * Synchronous fixtures restore the registered scanner identities before returning; no registry
 * probes, production player grants, real-client networking or catalog substitutions are involved.
 */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class ScanningGameTests {
    @GameTest(template = "empty")
    public static void compoundAspectAndGenericAwardsOnce(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Scan_Once");
        ServerPlayer other = player(helper, "TC_Scan_Other");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        ItemStack crystal = ThaumcraftApiHelper.makeCrystal(Aspect.MAGIC);
        var aspects = AspectHelper.getObjectAspects(crystal);
        helper.assertTrue(aspects != null && aspects.getAmount(Aspect.MAGIC) > 0, "actual contained compound aspect");
        for (Aspect component : Aspect.MAGIC.getComponents()) {
            helper.assertFalse(knowledge.isResearchKnown("!" + component.getTag()), "components remain unknown before scan");
        }
        List<ServerPlayer> recipients = new ArrayList<>();
        try (var scope = new ScannerScope();
             var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> recipients.add(recipient))) {
            ScanningManager.addScannableThing(new ScanAspect("!" + Aspect.MAGIC.getTag(), Aspect.MAGIC));
            ScanningManager.addScannableThing(new ScanGeneric());
            helper.assertTrue(ScanningManager.isThingStillScannable(owner, crystal), "fresh aspect and generic key");
            ScanningManager.scanTheThing(owner, crystal);
            for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
                int aspectAward = List.of("AUROMANCY", "BASICS", "ALCHEMY").contains(category.key) ? 1 : 0;
                helper.assertTrue(knowledge.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, category)
                    == aspectAward + category.applyFormula(aspects), "source raw aspect+formula award: " + category.key);
            }
            CompoundTag first = knowledge.serializeNBT().copy();
            int initialRecipients = recipients.size();
            ScanningManager.scanTheThing(owner, crystal);
            helper.assertTrue(knowledge.serializeNBT().equals(first), "repeat scan changes no marker or raw knowledge");
            helper.assertTrue(recipients.size() == initialRecipients, "repeat scan emits no gain packet");
            helper.assertFalse(ScanningManager.isThingStillScannable(owner, crystal), "known markers stop highlighting");
            helper.assertTrue(PlayerDataSync.isKnowledgeDirty(owner), "real progression queues source dirty sync");
            owner.tickCount = 20;
            PlayerDataSync.flushKnowledgeIfDue(owner);
            helper.assertTrue(!recipients.isEmpty() && recipients.stream().allMatch(value -> value == owner), "all gain/snapshot sends address owner only");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(other).getResearchList().isEmpty(), "bystander untouched");
        } finally { PlayerDataSync.forget(owner); PlayerDataSync.forget(other); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledResearchSkipsAllAspectAwards(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Scan_Cancel");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        CompoundTag before = knowledge.serializeNBT().copy();
        try (var scope = new ScannerScope(); var events = new CancelProbe(owner, true);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> { throw new AssertionError("canceled scan sent a packet"); })) {
            ScanningManager.addScannableThing(new ScanAspect("!" + Aspect.MAGIC.getTag(), Aspect.MAGIC));
            ScanningManager.scanTheThing(owner, ThaumcraftApiHelper.makeCrystal(Aspect.MAGIC));
            helper.assertTrue(events.research == 1 && events.knowledge == 0, "research cancellation occurs before onSuccess");
            helper.assertTrue(knowledge.serializeNBT().equals(before), "canceled marker and knowledge remain unchanged");
            helper.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "no dirty queue for canceled progress");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledKnowledgeKeepsSuccessfulScanMarker(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Scan_KnowCancel");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        try (var scope = new ScannerScope(); var events = new CancelProbe(owner, false);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> { throw new AssertionError("canceled knowledge sent gain"); })) {
            ScanningManager.addScannableThing(new ScanAspect("!" + Aspect.MAGIC.getTag(), Aspect.MAGIC));
            ScanningManager.scanTheThing(owner, ThaumcraftApiHelper.makeCrystal(Aspect.MAGIC));
            helper.assertTrue(events.research == 1 && events.knowledge == 3, "three independently cancellable source awards");
            helper.assertTrue(knowledge.isResearchKnown("!" + Aspect.MAGIC.getTag()), "successful scan marker remains");
            for (String key : List.of("AUROMANCY", "BASICS", "ALCHEMY")) {
                helper.assertTrue(knowledge.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, ResearchCategories.getResearchCategory(key)) == 0,
                    "canceled raw award: " + key);
            }
            helper.assertTrue(PlayerDataSync.isKnowledgeDirty(owner), "successful research still queues sync");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockConstructorRegistersItemsBeforeBlock(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "TC_Scan_BlockCtor");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, BlocksTC.oreCinnabar.defaultBlockState(), 2);
        try (var scope = new ScannerScope()) {
            ScanBlock block = new ScanBlock("TC_SCANNER_BLOCK", BlocksTC.oreAmber, BlocksTC.oreCinnabar);
            helper.assertTrue(ScanningManager.things.size() == 2 && ScanningManager.things.get(0) instanceof ScanItem
                && ScanningManager.things.get(1) instanceof ScanItem, "constructor appends both item scans first");
            ScanningManager.addScannableThing(block);
            helper.assertTrue(ScanningManager.things.get(2) == block, "caller appends block after constructor side effects");
            helper.assertTrue(block.checkThing(owner, pos), "actual cinnabar block matches identity");
            helper.assertFalse(block.checkThing(owner, new ItemStack(BlocksTC.oreCinnabar)), "block handler does not pretend to scan ItemStack");
            helper.assertTrue(ScanningManager.things.get(1).checkThing(owner, new ItemStack(BlocksTC.oreCinnabar)), "constructor-created item handler matches");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualChestContentsUseUpCapability(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Scan_Chest");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
        chest.setItem(0, new ItemStack(ItemsTC.amber));
        chest.setItem(26, new ItemStack(ItemsTC.amber));
        var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        helper.assertTrue(handler != null && handler.getStackInSlot(26).is(ItemsTC.amber), "actual NeoForge UP handler sees last chest slot");
        AtomicInteger itemChecks = new AtomicInteger();
        AtomicInteger successes = new AtomicInteger();
        try (var scope = new ScannerScope(); var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> {})) {
            ScanningManager.addScannableThing(new IScanThing() {
                @Override public boolean checkThing(Player player, Object object) {
                    if (!(object instanceof ItemStack stack) || !stack.is(ItemsTC.amber)) return false;
                    itemChecks.incrementAndGet(); return true;
                }
                @Override public String getResearchKey(Player player, Object object) { return "TC_SCANNER_UP_CONTENTS"; }
                @Override public void onSuccess(Player player, Object object) { successes.incrementAndGet(); }
            });
            ScanningManager.scanTheThing(owner, pos);
            helper.assertTrue(itemChecks.get() == 2 && successes.get() == 1, "both nonempty slots recursively checked; duplicate marker awarded once");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown("TC_SCANNER_UP_CONTENTS"), "real attachment received container scan marker");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryLimitCountsNonemptyAndWarnsAtExactly100(GameTestHelper helper) {
        var sparse = new CountingInventory(205);
        for (int slot = 0; slot < 205; slot += 2) sparse.setStackInSlot(slot, new ItemStack(ItemsTC.amber));
        AtomicInteger scans = new AtomicInteger();
        AtomicInteger warnings = new AtomicInteger();
        ScanningManager.scanInventory(sparse, stack -> scans.incrementAndGet(), warnings::incrementAndGet);
        helper.assertTrue(scans.get() == 100 && warnings.get() == 1 && sparse.lastRead == 198, "empty slots do not consume 100-stack limit; later contents untouched");
        var exact = new CountingInventory(100);
        for (int slot = 0; slot < 100; slot++) exact.setStackInSlot(slot, new ItemStack(ItemsTC.amber));
        scans.set(0); warnings.set(0);
        ScanningManager.scanInventory(exact, stack -> scans.incrementAndGet(), warnings::incrementAndGet);
        helper.assertTrue(scans.get() == 100 && warnings.get() == 1 && exact.lastRead == 99, "source warns even if the hundredth stack is the final slot");
        var empties = new CountingInventory(200);
        scans.set(0); warnings.set(0);
        ScanningManager.scanInventory(empties, stack -> scans.incrementAndGet(), warnings::incrementAndGet);
        helper.assertTrue(scans.get() == 0 && warnings.get() == 0 && empties.lastRead == 199, "empty inventories are not capped at 100 slots");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void directBlockAspectDoesNotUsePickOrBucketFallback(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "TC_Scan_BlockItem");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
        helper.assertTrue(ScanningManager.getItemFromParms(owner, pos).is(Items.WATER_BUCKET), "generic water block gets bucket fallback");
        helper.assertFalse(new ScanAspect("TC_WATER", Aspect.WATER).checkThing(owner, pos), "direct water BlockItem is empty, no bucket aspect");
        helper.getLevel().setBlock(pos, Blocks.LAVA.defaultBlockState(), 2);
        helper.assertTrue(ScanningManager.getItemFromParms(owner, pos).is(Items.LAVA_BUCKET), "generic lava gets bucket fallback");
        helper.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 2);
        helper.assertTrue(ScanningManager.getItemFromParms(owner, pos).is(Items.CHEST), "waterlogged solid remains its own BlockItem");
        helper.assertTrue(LegacyScanIdentity.directBlockStack(BlocksTC.slabAncient.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)).isEmpty(),
            "old double slabs had no direct ItemBlock");
        helper.assertTrue(LegacyScanIdentity.directBlockStack(BlocksTC.slabAncient.defaultBlockState()).is(BlocksTC.slabAncient.asItem()), "half slab has direct item");
        for (var slab : List.of(BlocksTC.slabArcaneStone, BlocksTC.slabArcaneBrick, BlocksTC.slabAncient,
            BlocksTC.slabEldritch, BlocksTC.slabGreatwood, BlocksTC.slabSilverwood)) {
            helper.getLevel().setBlock(pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
            helper.assertTrue(ScanningManager.getItemFromParms(owner, pos).isEmpty(), "actual double TC slab has no item or pick fallback: " + slab);
            helper.assertTrue(new ScanGeneric().getResearchKey(owner, pos) == null, "double TC slab has no generic marker: " + slab);
            helper.assertFalse(new ScanGeneric().checkThing(owner, pos), "double TC slab has no generic aspects: " + slab);
            helper.getLevel().setBlock(pos, slab.defaultBlockState(), 2);
            helper.assertTrue(ScanningManager.getItemFromParms(owner, pos).is(slab.asItem()), "half TC slab still has its original item: " + slab);
        }
        ItemStack input = new ItemStack(ItemsTC.amber);
        helper.assertTrue(ScanningManager.getItemFromParms(owner, input) == input, "source returns input stack reference");
        ItemEntity dropped = new ItemEntity(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(), input);
        helper.assertTrue(ScanningManager.getItemFromParms(owner, dropped) == dropped.getItem(), "source returns dropped stack reference");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyItemWildcardAndNbtAsymmetry(GameTestHelper helper) {
        ItemStack pearl = new ItemStack(ItemsTC.primordialPearl);
        pearl.setDamageValue(7);
        helper.assertTrue(new ScanItem("PRIMPEARL", ScanItem.wildcard(new ItemStack(ItemsTC.primordialPearl))).checkThing(null, pearl), "unclamped source wildcard matches pearl charge seven");
        helper.assertFalse(new ScanItem("EXACT", new ItemStack(ItemsTC.primordialPearl)).checkThing(null, pearl), "default metadata is not wildcard");
        helper.assertTrue(ScanItem.matchesForCrafting(new ItemStack(ItemsTC.ingotVoid), ScanItem.wildcard(new ItemStack(ItemsTC.ingotThaumium))), "flattened ingots share old wildcard item identity");
        helper.assertFalse(ScanItem.matchesForCrafting(new ItemStack(ItemsTC.ingotVoid), new ItemStack(ItemsTC.ingotThaumium)), "metadata variants differ without wildcard");
        ItemStack recipe = new ItemStack(ItemsTC.amber);
        CompoundTag required = new CompoundTag(); required.putInt("scan_test", 3);
        recipe.set(DataComponents.CUSTOM_DATA, CustomData.of(required));
        ItemStack bare = new ItemStack(ItemsTC.amber, 17);
        helper.assertTrue(ScanItem.matchesForCrafting(bare, recipe), "source actual-no-NBT quirk ignores recipe NBT and count");
        CompoundTag extra = required.copy(); extra.putString("extra", "allowed");
        bare.set(DataComponents.CUSTOM_DATA, CustomData.of(extra));
        helper.assertTrue(ScanItem.matchesForCrafting(bare, recipe), "source top-level NBT subset accepts extra actual keys");
        extra.putByte("scan_test", (byte) 3); bare.set(DataComponents.CUSTOM_DATA, CustomData.of(extra));
        helper.assertFalse(ScanItem.matchesForCrafting(bare, recipe), "SNBT type suffix matters: byte is not int");
        ItemStack magic = ThaumcraftApiHelper.makeCrystal(Aspect.MAGIC);
        ItemStack same = magic.copy(); same.set(TCDataComponents.ASPECTS, magic.get(TCDataComponents.ASPECTS).copy());
        helper.assertTrue(ScanItem.matchesForCrafting(magic, same), "migrated AspectList values compare as old NBT, not Java identity");
        helper.assertFalse(ScanItem.matchesForCrafting(ThaumcraftApiHelper.makeCrystal(Aspect.FIRE), magic), "different contained aspects remain distinct");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entityNbtChecksAndGenericForgeName(GameTestHelper helper) {
        Zombie zombie = new Zombie(EntityType.ZOMBIE, helper.getLevel());
        zombie.setSilent(true);
        helper.assertTrue(new ScanEntity("SILENT", Zombie.class, false, new ThaumcraftApi.EntityTagsNBT("Silent", (byte) 1)).checkThing(null, zombie), "saved byte NBT matches source typed value");
        helper.assertFalse(new ScanEntity("NOT_SILENT", Zombie.class, false, new ThaumcraftApi.EntityTagsNBT("Silent", (byte) 0)).checkThing(null, zombie), "mismatched typed NBT rejected");
        helper.assertTrue(new ScanGeneric().getResearchKey(null, zombie).equals("!Zombie"), "generic entity uses old Forge EntityEntry name");
        helper.assertTrue(new ScanGeneric().getResearchKey(null, player(helper, "TC_Scan_PlayerName")).equals("!null"), "Player has no old EntityEntry");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static final class ScannerScope implements AutoCloseable {
        private final List<IScanThing> original = new ArrayList<>(ScanningManager.things);
        ScannerScope() { ScanningManager.things.clear(); }
        @Override public void close() { ScanningManager.things.clear(); ScanningManager.things.addAll(original); }
    }

    private static final class CountingInventory extends ItemStackHandler {
        int lastRead = -1;
        CountingInventory(int slots) { super(slots); }
        @Override public ItemStack getStackInSlot(int slot) { lastRead = slot; return super.getStackInSlot(slot); }
    }

    public static final class CancelProbe implements AutoCloseable {
        private final ServerPlayer owner;
        private final boolean cancelResearch;
        int research;
        int knowledge;
        CancelProbe(ServerPlayer owner, boolean cancelResearch) {
            this.owner = owner; this.cancelResearch = cancelResearch; NeoForge.EVENT_BUS.register(this);
        }
        @SubscribeEvent public void research(ResearchEvent.Research event) {
            if (event.getPlayer() != owner) return;
            research++; if (cancelResearch) event.setCanceled(true);
        }
        @SubscribeEvent public void knowledge(ResearchEvent.Knowledge event) {
            if (event.getPlayer() != owner) return;
            knowledge++; if (!cancelResearch) event.setCanceled(true);
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
