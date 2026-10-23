package thaumcraft.api.research;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;

/** Actual tagged items/blocks and synchronous registry rebinding, NOT an engine reload audit. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ScanOreDictionaryGameTests {
    @GameTest(template = "empty")
    public static void sourceMaterialGroupsMatchAllThirteenCurrentTaggedForms(GameTestHelper h) {
        List<ScanOreDictionary> scanners = sourceScanners();
        List<List<Item>> forms = List.of(
            List.of(Items.IRON_ORE, Items.IRON_INGOT, Items.IRON_BLOCK, ItemsTC.plateIron),
            List.of(ItemsTC.ingotBrass, BlocksTC.metalBlockBrass.asItem(), ItemsTC.plateBrass),
            List.of(ItemsTC.ingotThaumium, BlocksTC.metalBlockThaumium.asItem(), ItemsTC.plateThaumium),
            List.of(ItemsTC.ingotVoid, BlocksTC.metalBlockVoid.asItem(), ItemsTC.plateVoid));
        for (int group = 0; group < forms.size(); group++) {
            for (Item item : forms.get(group)) {
                ItemStack actual = new ItemStack(item, 3);
                for (int candidate = 0; candidate < scanners.size(); candidate++) {
                    h.assertTrue(scanners.get(candidate).checkThing(null, actual) == (candidate == group),
                        "source ingot/block/ore/plate belongs only to its material marker: " + item);
                }
            }
        }
        for (var scanner : scanners) {
            h.assertFalse(scanner.checkThing(null, new ItemStack(ItemsTC.nuggetBrass)), "source material groups never include nuggets");
            h.assertFalse(scanner.checkThing(null, new ItemStack(ItemsTC.thaumiumSword)), "crafted gear is not an ingot/block/plate");
            h.assertFalse(scanner.checkThing(null, ItemStack.EMPTY), "empty stacks never match");
            h.assertFalse(scanner.checkThing(null, null), "null scan target never matches");
        }
        List<String> expected = List.of("f_MATIRON", "f_MATBRASS", "f_MATTHAUMIUM", "f_MATVOID");
        List<String> actual = ScanningManager.things.stream().filter(ScanOreDictionary.class::isInstance)
            .map(scanner -> scanner.getResearchKey(null, null)).toList();
        h.assertTrue(actual.equals(expected), "actual startup installs four handlers once in original relative order");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void directBlockItemsAndDroppedStacksNeverUseGenericBucketOrDoubleSlabFallback(GameTestHelper h) {
        ServerPlayer owner = player(h, "TC_Ore_Direct");
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        var iron = sourceScanners().getFirst();
        h.getLevel().setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), 2);
        h.assertTrue(iron.checkThing(owner, pos), "placed iron storage block uses its direct BlockItem");
        ItemEntity entity = new ItemEntity(h.getLevel(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(ItemsTC.plateIron));
        h.assertTrue(iron.checkThing(owner, entity), "dropped plate uses the actual ItemEntity stack");
        entity.setItem(new ItemStack(ItemsTC.plateVoid));
        h.assertFalse(iron.checkThing(owner, entity), "dropped-stack replacement is immediately observable");

        TagKey<Item> waterBuckets = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:buckets/water"));
        var liquid = ScanOreDictionary.forTags("TC_ORE_DIRECT_WATER", List.of(waterBuckets));
        h.assertTrue(liquid.checkThing(null, new ItemStack(Items.WATER_BUCKET)), "actual current water-bucket tag exists");
        h.getLevel().setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
        h.assertTrue(ScanningManager.getItemFromParms(owner, pos).is(Items.WATER_BUCKET), "generic scanner has its source bucket fallback");
        h.assertFalse(liquid.checkThing(owner, pos), "material scanner never substitutes that bucket");

        var slab = ScanOreDictionary.forTags("TC_ORE_DIRECT_SLAB", List.of(ItemTags.SLABS));
        h.getLevel().setBlock(pos, BlocksTC.slabAncient.defaultBlockState(), 2);
        h.assertTrue(slab.checkThing(owner, pos), "actual half slab has a tagged direct item");
        h.getLevel().setBlock(pos, BlocksTC.slabAncient.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
        h.assertFalse(slab.checkThing(owner, pos), "merged double slab preserves the old missing direct ItemBlock");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void currentRegistryRebindingInvalidatesBothPreviousNegativeAndPositiveResults(GameTestHelper h) {
        var registry = BuiltInRegistries.ITEM;
        Map<TagKey<Item>, List<Holder<Item>>> original = new LinkedHashMap<>();
        registry.getTags().forEach(pair -> original.put(pair.getFirst(), pair.getSecond().stream().toList()));
        TagKey<Item> plates = ScanOreDictionary.tagForLegacyName("plateIron");
        h.assertTrue(original.containsKey(plates), "fixture rebinds an existing tag, never creates a new one");
        ItemStack unchangedStack = new ItemStack(ItemsTC.amber);
        var scanner = new ScanOreDictionary("TC_ORE_REBIND", "plateIron");
        h.assertFalse(scanner.checkThing(null, unchangedStack), "amber initially lacks the plate tag");
        Map<TagKey<Item>, List<Holder<Item>>> changed = new LinkedHashMap<>(original);
        changed.put(plates, List.of(ItemsTC.amber.builtInRegistryHolder()));
        try {
            // Real public registry binding API, scoped to this synchronous test. No server
            // resource-manager/datapack reload is performed or claimed by this fixture.
            registry.bindTags(changed);
            h.assertTrue(scanner.checkThing(null, unchangedStack), "previous negative result does not survive tag rebinding");
            registry.bindTags(original);
            h.assertFalse(scanner.checkThing(null, unchangedStack), "previous positive result does not survive restoring tags");
            h.assertTrue(scanner.checkThing(null, new ItemStack(ItemsTC.plateIron)), "original plate membership restored");
        } finally {
            registry.bindTags(original);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void materialMarkerIsOnceOnlyWithoutKnowledgeAwardsAndSyncsOnlyToOwner(GameTestHelper h) throws Exception {
        ServerPlayer owner = player(h, "TC_Material_Once");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        int xp = owner.totalExperience;
        var scanner = sourceScanners().get(1);
        h.assertTrue(ResearchCategories.getResearch("f_MATBRASS") == null, "source flag is an opaque marker, not a catalog reward");
        List<ServerPlayer> sent = new ArrayList<>();
        try (var scope = new ScannerScope();
             var events = new MarkerProbe(owner, false);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {
                 h.assertTrue(packet instanceof PacketSyncKnowledge, "opaque material marker sends no gain feedback");
                 sent.add(recipient);
             })) {
            ScanningManager.addScannableThing(scanner);
            ScanningManager.scanTheThing(owner, new ItemStack(ItemsTC.plateBrass));
            h.assertTrue(data.isResearchKnown("f_MATBRASS") && data.getResearchStage("f_MATBRASS") == 0,
                "first match records the source unknown marker at stage zero");
            h.assertTrue(data.serializeNBT().getList("knowledge", 10).isEmpty() && owner.totalExperience == xp,
                "material scan itself adds neither raw knowledge nor XP");
            var first = data.serializeNBT().copy();
            ScanningManager.scanTheThing(owner, new ItemStack(ItemsTC.ingotBrass));
            h.assertTrue(data.serializeNBT().equals(first) && events.count == 1, "same material in another form does not progress twice");
            h.assertTrue(PlayerDataSync.isKnowledgeDirty(owner) && sent.isEmpty(), "source dirty queue does not send immediately");
            owner.tickCount = 20;
            PlayerDataSync.flushKnowledgeIfDue(owner);
            h.assertTrue(sent.size() == 1 && sent.getFirst() == owner, "twenty-tick snapshot addresses only the owner");
        } finally { PlayerDataSync.forget(owner); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledMaterialResearchDoesNotRecordMarkerOrQueueSync(GameTestHelper h) throws Exception {
        ServerPlayer owner = player(h, "TC_Material_Cancel");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        var before = data.serializeNBT().copy();
        try (var scope = new ScannerScope(); var events = new MarkerProbe(owner, true);
             var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> { throw new AssertionError("canceled material scan sent a packet"); })) {
            ScanningManager.addScannableThing(sourceScanners().get(1));
            ScanningManager.scanTheThing(owner, new ItemStack(ItemsTC.plateBrass));
            h.assertTrue(events.count == 1, "actual cancellable research event dispatched once");
            h.assertTrue(data.serializeNBT().equals(before) && !PlayerDataSync.isKnowledgeDirty(owner),
                "cancellation preserves raw player data and the dirty queue");
        } finally { PlayerDataSync.forget(owner); }
        h.succeed();
    }

    private static List<ScanOreDictionary> sourceScanners() {
        return List.of(new ScanOreDictionary("f_MATIRON", "oreIron", "ingotIron", "blockIron", "plateIron"),
            new ScanOreDictionary("f_MATBRASS", "ingotBrass", "blockBrass", "plateBrass"),
            new ScanOreDictionary("f_MATTHAUMIUM", "ingotThaumium", "blockThaumium", "plateThaumium"),
            new ScanOreDictionary("f_MATVOID", "ingotVoid", "blockVoid", "plateVoid"));
    }

    private static ServerPlayer player(GameTestHelper h, String name) {
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static final class ScannerScope implements AutoCloseable {
        private final List<IScanThing> original = new ArrayList<>(ScanningManager.things);
        ScannerScope() { ScanningManager.things.clear(); }
        @Override public void close() { ScanningManager.things.clear(); ScanningManager.things.addAll(original); }
    }

    public static final class MarkerProbe implements AutoCloseable {
        private final ServerPlayer owner;
        private final boolean cancel;
        int count;
        MarkerProbe(ServerPlayer owner, boolean cancel) {
            this.owner = owner; this.cancel = cancel; NeoForge.EVENT_BUS.register(this);
        }
        @SubscribeEvent public void research(ResearchEvent.Research event) {
            if (event.getPlayer() == owner && "f_MATBRASS".equals(event.getResearchKey())) {
                count++;
                if (cancel) event.setCanceled(true);
            }
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
