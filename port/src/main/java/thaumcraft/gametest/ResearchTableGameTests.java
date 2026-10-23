package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.common.blocks.basic.BlockResearchTable;
import thaumcraft.common.container.ContainerResearchTable;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.research.LegacyResearchCrafting;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import thaumcraft.common.tiles.crafting.ResearchTableRegistries;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/** Real conversion/events, registered BE persistence/capabilities and vanilla menu clicks.
 * This verifies the functional physical prefix, NOT real client rendering/GUI acceptance,
 * theory/card behavior, source-world migration or unresolved complex research progression. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ResearchTableGameTests {
    private static ServerPlayer player(GameTestHelper helper, String name) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        player.setPos(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static ItemStack tool(int count) {
        ItemStack tool = new ItemStack(ItemsTC.scribingTools, count);
        tool.setDamageValue(37);
        tool.set(DataComponents.CUSTOM_NAME, Component.literal("Private research-table fixture"));
        CompoundTag data = new CompoundTag();
        data.putString("fixture", "deep-copy");
        tool.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return tool;
    }

    private static BlockHitResult hit(BlockPos pos, Direction face) { return new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false); }

    private static void activate(GameTestHelper helper, BlockPos pos, ServerPlayer player, InteractionHand hand, Direction face) {
        BlockState state = helper.getLevel().getBlockState(pos);
        helper.assertTrue(state.useItemOn(player.getItemInHand(hand), helper.getLevel(), player, hand, hit(pos, face)).consumesAction(),
            "source table activation consumes interaction without item fallback");
    }

    private static TileResearchTable placed(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().setBlock(pos, BlocksTC.researchTable.defaultBlockState(), Block.UPDATE_ALL);
        var entity = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(entity instanceof TileResearchTable, "registered research table creates its real BE");
        return (TileResearchTable) entity;
    }

    @GameTest(template = "scan_test")
    public static void conversionCopiesEntireChosenHandAnyFaceSameFacingAndFiresRealCraftAfterMutation(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Table_Convert");
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> {});
             var probe = new CraftProbe(helper, owner)) {
            float[] yaws = {0, 90, 180, -90, 45, -45.001F};
            Direction[] facings = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST, Direction.WEST, Direction.EAST};
            for (int index = 0; index < 6; index++) {
                BlockPos pos = helper.absolutePos(new BlockPos(2 + index % 3 * 3, 2, 2 + index / 3 * 3));
                helper.getLevel().setBlock(pos, BlocksTC.tableWood.defaultBlockState(), Block.UPDATE_ALL);
                owner.setYRot(yaws[index]);
                owner.setShiftKeyDown(index == 1);
                owner.setGameMode(index >= 2 ? GameType.CREATIVE : GameType.SURVIVAL);
                InteractionHand hand = index % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack held = tool(3), before = held.copy();
                owner.setItemInHand(hand, held);
                probe.hand = hand;
                probe.pos = pos;
                activate(helper, pos, owner, hand, Direction.values()[index]);
                TileResearchTable table = (TileResearchTable) helper.getLevel().getBlockEntity(pos);
                helper.assertTrue(helper.getLevel().getBlockState(pos).is(BlocksTC.researchTable)
                    && helper.getLevel().getBlockState(pos).getValue(BlockResearchTable.FACING) == facings[index], "same source facing, no opposite/sneak or face restriction");
                helper.assertTrue(owner.getItemInHand(hand).isEmpty() && table.getItem(0).getCount() == 3
                    && ItemStack.isSameItemSameComponents(table.getItem(0), before) && table.getItem(1).isEmpty(),
                    "full command-sized held stack retains damage/components; whole hand cleared even creative; no paper cost");
                held.setDamageValue(90);
                held.setCount(7);
                helper.assertTrue(table.getItem(0).getCount() == 3 && table.getItem(0).getDamageValue() == 37, "conversion breaks alias to previous hand stack");
            }
            helper.assertTrue(probe.calls == 6, "exactly one actual ItemCraftedEvent per successful physical conversion, all six clicked faces");
            helper.assertTrue(ThaumcraftCapabilities.knowsResearchStrict(owner, LegacyResearchCrafting.RESEARCH_TABLE.marker()),
                "real crafting listener records source finite opaque table reference, not THEORYRESEARCH unlock");
            helper.assertFalse(ThaumcraftCapabilities.knowsResearch(owner, "THEORYRESEARCH"), "conversion has no research prerequisite or complex unlock bypass");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "scan_test")
    public static void stoneWrongToolAndEmptyHandConsumeInteractionWithoutConversionOrCraft(GameTestHelper helper) {
        var owner = player(helper, "TC_Table_Nonconvert");
        try (var probe = new CraftProbe(helper, owner)) {
            BlockPos stone = helper.absolutePos(new BlockPos(2, 2, 3)), wood = helper.absolutePos(new BlockPos(5, 2, 3));
            helper.getLevel().setBlock(stone, BlocksTC.tableStone.defaultBlockState(), Block.UPDATE_ALL);
            helper.getLevel().setBlock(wood, BlocksTC.tableWood.defaultBlockState(), Block.UPDATE_ALL);
            owner.setItemInHand(InteractionHand.OFF_HAND, tool(1));
            activate(helper, stone, owner, InteractionHand.OFF_HAND, Direction.UP);
            helper.assertTrue(helper.getLevel().getBlockState(stone).is(BlocksTC.tableStone) && !owner.getOffhandItem().isEmpty(), "stone table never converts");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
            activate(helper, wood, owner, InteractionHand.MAIN_HAND, Direction.UP);
            helper.assertTrue(helper.getLevel().getBlockState(wood).is(BlocksTC.tableWood) && owner.getMainHandItem().is(Items.WRITABLE_BOOK), "similar writing item is not IScribeTools");
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(helper.getLevel().getBlockState(wood).useWithoutItem(helper.getLevel(), owner, hit(wood, Direction.UP)).consumesAction(), "source empty-hand activation also consumes");
            helper.assertTrue(probe.calls == 0 && helper.getLevel().getBlockState(wood).is(BlocksTC.tableWood), "no spurious craft events or block replacement");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "scan_test")
    public static void canceledCraftingResearchDoesNotRollBackPhysicalConversionOrReturnHeldTool(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Table_Cancel");
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.getLevel().setBlock(pos, BlocksTC.tableWood.defaultBlockState(), Block.UPDATE_ALL);
        owner.setItemInHand(InteractionHand.MAIN_HAND, tool(1));
        var before = ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().copy();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> {});
             var probe = new CancelCraftingResearch(owner)) {
            activate(helper, pos, owner, InteractionHand.MAIN_HAND, Direction.DOWN);
            helper.assertTrue(probe.calls == 1, "actual real ItemCraftedEvent reaches cancellable progress listener");
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(BlocksTC.researchTable)
                && owner.getMainHandItem().isEmpty() && ((TileResearchTable) helper.getLevel().getBlockEntity(pos)).getItem(0).is(ItemsTC.scribingTools),
                "event cancellation never rolls back already performed block/tool conversion");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().equals(before) && !PlayerDataSync.isKnowledgeDirty(owner),
                "canceled finite crafting progress changes no player knowledge or dirty status");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "scan_test")
    public static void registeredBeSaveStaticLoadUpdateTagAndInputCopiesPreserveBothSlotsAndSourceLimit(GameTestHelper helper) {
        var table = placed(helper, new BlockPos(3, 2, 3));
        ItemStack input = tool(3), inputBefore = input.copy();
        table.setItem(0, input);
        table.setItem(1, new ItemStack(Items.PAPER, 80));
        helper.assertTrue(table.getContainerSize() == 2 && table.getMaxStackSize() == 64 && table.getItem(1).getCount() == 64, "source two slots and inventory cap64, not modern default99");
        input.setDamageValue(90);
        input.setCount(8);
        helper.assertTrue(table.getItem(0).getCount() == 3 && ItemStack.isSameItemSameComponents(table.getItem(0), inputBefore), "setter-copy is an explicit modern alias boundary");
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = table.saveWithFullMetadata(registries);
        saved.putString("CustomName", "Private stored source name");
        var loaded = BlockEntity.loadStatic(table.getBlockPos(), table.getBlockState(), saved, registries);
        helper.assertTrue(loaded instanceof TileResearchTable && loaded.getType() == ResearchTableRegistries.RESEARCH_TABLE, "real registered BE loader, not hand-written fixture deserializer");
        var copy = (TileResearchTable) loaded;
        helper.assertTrue(copy.getItem(0).getCount() == 3 && copy.getItem(1).getCount() == 64
            && ItemStack.isSameItemSameComponents(copy.getItem(0), inputBefore), "exact persisted item/damage/custom components survive");
        helper.assertTrue(copy.saveWithFullMetadata(registries).equals(saved), "full generated storage NBT round trip is exact");
        table.getItem(0).setDamageValue(77);
        helper.assertTrue(copy.getItem(0).getDamageValue() == 37, "loaded stack detached from original live inventory");
        CompoundTag tag = table.getUpdateTag(registries);
        CompoundTag visible = table.saveWithoutMetadata(registries);
        visible.remove("note");
        visible.putBoolean("renderNote", false);
        helper.assertTrue(tag.equals(visible) && !tag.contains("note") && !tag.getBoolean("renderNote")
            && table.getUpdatePacket() != null && table.getUpdatePacket().getTag().equals(tag),
            "ordinary initial/update BE transport keeps both slots and revision, with only public note presence");
        var before = copy.saveWithoutMetadata(registries);
        copy.clearContent();
        helper.assertTrue(copy.saveWithoutMetadata(registries).equals(before), "source clear() no-op retained, not silently fixed");
        helper.assertTrue(copy.removeItem(1, 2).getCount() == 2 && copy.getItem(1).getCount() == 62, "actual removal/split still works");
        helper.succeed();
    }

    @GameTest(template = "scan_test")
    public static void actualMenuManualSlotsRejectRawPaperDamageAndRetainOriginalQuickMoveBugAndOrder(GameTestHelper helper) {
        var owner = player(helper, "TC_Table_Menu");
        var table = placed(helper, new BlockPos(3, 2, 3));
        ContainerResearchTable menu = (ContainerResearchTable) table.createMenu(7, owner.getInventory(), owner);
        helper.assertTrue(menu.getType() == ResearchTableRegistries.RESEARCH_TABLE_MENU && menu.slots.size() == 38, "registered menu exposes 2+36 real slots");
        List<Integer> hotbarOrder = new ArrayList<>();
        for (int index = 29; index < 38; index++) hotbarOrder.add(menu.slots.get(index).getContainerSlot());
        helper.assertTrue(hotbarOrder.equals(List.of(0, 3, 6, 1, 4, 7, 2, 5, 8))
            && menu.slots.get(2).getContainerSlot() == 9 && menu.slots.get(28).getContainerSlot() == 35, "original column-major hotbar and player registration order");
        ItemStack rawDamagedPaper = new ItemStack(Items.PAPER, 2);
        rawDamagedPaper.set(DataComponents.DAMAGE, 1);
        helper.assertTrue(LegacyScanIdentity.rawDamage(rawDamagedPaper) == 1 && rawDamagedPaper.getDamageValue() == 0,
            "command-only raw DAMAGE fixture proves modern nondurable getter clamps metadata");
        helper.assertFalse(table.canPlaceItem(1, rawDamagedPaper) || menu.slots.get(1).mayPlace(rawDamagedPaper), "both actual insertion gates reject raw paper metadata1");
        menu.setCarried(rawDamagedPaper);
        menu.clicked(1, 0, ClickType.PICKUP, owner);
        helper.assertTrue(table.getItem(1).isEmpty() && menu.getCarried().getCount() == 2, "actual vanilla click does not insert invalid paper");
        ItemStack paper = new ItemStack(Items.PAPER, 10);
        paper.set(DataComponents.CUSTOM_NAME, Component.literal("NBT is ignored for source paper match"));
        menu.setCarried(paper);
        menu.clicked(1, 0, ClickType.PICKUP, owner);
        helper.assertTrue(table.getItem(1).getCount() == 10 && menu.getCarried().isEmpty(), "actual valid paper click works regardless extra components");
        menu.setCarried(tool(1));
        menu.clicked(0, 0, ClickType.PICKUP, owner);
        helper.assertTrue(table.getItem(0).is(ItemsTC.scribingTools) && menu.getCarried().isEmpty(), "actual tool click works without theory actions");
        helper.assertTrue(!menu.quickMoveStack(owner, 0).isEmpty() && table.getItem(0).isEmpty()
            && owner.getInventory().items.get(8).is(ItemsTC.scribingTools), "table-to-player quick move uses reverse source order (hotbar8 first)");
        table.setItem(1, ItemStack.EMPTY);
        owner.getInventory().items.set(9, new ItemStack(Items.PAPER, 3));
        helper.assertTrue(menu.quickMoveStack(owner, 2).isEmpty() && table.getItem(1).isEmpty() && owner.getInventory().items.get(9).getCount() == 3,
            "source player-index-as-table-index bug intentionally rejects player-to-table shift-click");
        for (int button = 0; button <= 10; button++) helper.assertFalse(menu.clickMenuButton(owner, button), "no placeholder theory action/cost bypass");
        PlayerDataSync.forget(owner);
        helper.succeed();
    }

    @GameTest(template = "scan_test")
    public static void exactEightBlockMenuDistanceFacingChangesKeepContentThenRealBreakDropsBlockAndInventoryOnce(GameTestHelper helper) {
        var owner = player(helper, "TC_Table_Remove");
        var table = placed(helper, new BlockPos(3, 2, 3));
        BlockPos pos = table.getBlockPos();
        table.setItem(0, tool(1));
        table.setItem(1, new ItemStack(Items.PAPER, 7));
        var oldTool = table.getItem(0).copy();
        ContainerResearchTable menu = (ContainerResearchTable) table.createMenu(8, owner.getInventory(), owner);
        owner.setPos(pos.getX() + .5D + 8.0D, pos.getY() + .5D, pos.getZ() + .5D);
        helper.assertTrue(menu.stillValid(owner), "source center distance squared64 is inclusive");
        owner.setPos(owner.getX() + .001D, owner.getY(), owner.getZ());
        helper.assertFalse(menu.stillValid(owner), "beyond exact source eight blocks is rejected, not modern reach-based four");
        helper.getLevel().setBlock(pos, table.getBlockState().rotate(Rotation.CLOCKWISE_90), Block.UPDATE_ALL);
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) == table && table.getItem(1).getCount() == 7,
            "same-block facing edits retain original BE identity and inventory");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(), "rotation spills no contents");
        helper.getLevel().destroyBlock(pos, true, owner);
        helper.assertFalse(menu.stillValid(owner), "removed/replaced BE invalidates the existing menu");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds());
        helper.assertTrue(count(drops, BlocksTC.researchTable.asItem()) == 1 && count(drops, ItemsTC.scribingTools) == 1 && count(drops, Items.PAPER) == 7,
            "actual world break yields research-table item plus both stored slots exactly once");
        helper.assertTrue(drops.stream().anyMatch(entity -> ItemStack.isSameItemSameComponents(entity.getItem(), oldTool)), "dropped tool preserves custom components and wear");
        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).size() == drops.size(), "repeat removal cannot duplicate inventory drops");
        for (ItemEntity entity : drops) entity.discard();
        PlayerDataSync.forget(owner);
        helper.succeed();
    }

    @GameTest(template = "scan_test")
    public static void allSixSidedCapabilitiesFilterSourceToolPaperAndAllowExtractionButNullHasNoHandler(GameTestHelper helper) {
        var table = placed(helper, new BlockPos(3, 2, 3));
        for (Direction face : Direction.values()) {
            var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, table.getBlockPos(), face);
            helper.assertTrue(handler != null && handler.getSlots() == 2 && handler.getSlotLimit(1) == 64, "actual registered sided source capability " + face);
            helper.assertTrue(handler.insertItem(0, new ItemStack(Items.PAPER), false).getCount() == 1, "paper cannot enter tool slot");
            ItemStack malformed = new ItemStack(Items.PAPER);
            malformed.set(DataComponents.DAMAGE, 1);
            helper.assertTrue(handler.insertItem(1, malformed, false).getCount() == 1, "raw positive paper DAMAGE rejected by automation as well");
            helper.assertTrue(handler.insertItem(1, new ItemStack(Items.PAPER, 5), false).isEmpty() && table.getItem(1).getCount() == 5,
                "valid paper insertion is available from every face");
            helper.assertTrue(handler.extractItem(1, 2, false).getCount() == 2 && table.getItem(1).getCount() == 3, "source every-face extraction works");
            table.setItem(1, ItemStack.EMPTY);
        }
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, table.getBlockPos(), null) == null, "source unsided getCapability delegates instead of exposing its sided inventory");
        helper.succeed();
    }

    @GameTest(template = "scan_test")
    public static void placementSourceFloatFacingAndPhysicalShapeHaveNoSolidSupportsOrTheoryTicker(GameTestHelper helper) {
        var owner = player(helper, "TC_Table_Physics");
        var block = (BlockResearchTable) BlocksTC.researchTable;
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        float[] yaws = {0, 90, 180, -90, 44.999F, 45, -45, -45.001F};
        Direction[] expected = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST, Direction.SOUTH, Direction.EAST};
        for (int index = 0; index < yaws.length; index++) {
            owner.setYRot(yaws[index]);
            owner.setShiftKeyDown(index % 2 == 0);
            BlockPlaceContext context = new BlockPlaceContext(owner, InteractionHand.MAIN_HAND, new ItemStack(block), hit(pos, Direction.UP));
            BlockState state = block.getStateForPlacement(context);
            helper.assertTrue(state.getValue(BlockResearchTable.FACING) == expected[index], "source float facing unchanged by sneak/place side");
            helper.assertTrue(state.isCollisionShapeFullBlock(helper.getLevel(), pos) && state.getOcclusionShape(helper.getLevel(), pos).isEmpty(), "source full-cube collision but nonopaque rendering");
            helper.assertTrue(state.ignitedByLava(), "source Material.WOOD burning flag permits lava ignition");
            for (Direction face : Direction.values()) helper.assertFalse(state.isFaceSturdy(helper.getLevel(), pos, face), "research-table source has no solid support face");
            helper.assertTrue(block.getTicker(helper.getLevel(), state, ResearchTableRegistries.RESEARCH_TABLE) == null, "physical slice has no invented theory ticking/awards");
        }
        helper.assertTrue(block.getExplosionResistance() == 12.0F, "source20 resistance converted by existing0.6 mapping");
        PlayerDataSync.forget(owner);
        helper.succeed();
    }

    private static int count(List<ItemEntity> drops, Item item) {
        return drops.stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    public static final class CraftProbe implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer owner;
        InteractionHand hand;
        BlockPos pos;
        int calls;

        CraftProbe(GameTestHelper helper, ServerPlayer owner) { this.helper = helper; this.owner = owner; NeoForge.EVENT_BUS.register(this); }

        @SubscribeEvent public void crafted(PlayerEvent.ItemCraftedEvent event) {
            if (event.getEntity() != owner) return;
            calls++;
            helper.assertTrue(event.getCrafting().is(BlocksTC.researchTable.asItem()) && event.getCrafting().getCount() == 1
                && event.getInventory().getContainerSize() == 1 && event.getInventory().isEmpty(), "source event output and EMPTY one-slot fake crafting matrix");
            helper.assertTrue(pos != null && owner.getItemInHand(hand).isEmpty() && helper.getLevel().getBlockEntity(pos) instanceof TileResearchTable
                && ((TileResearchTable) helper.getLevel().getBlockEntity(pos)).getItem(0).getCount() == 3,
                "real event fired AFTER block/BE/tool copy/whole-hand clear");
        }

        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }

    public static final class CancelCraftingResearch implements AutoCloseable {
        private final ServerPlayer owner;
        int calls;

        CancelCraftingResearch(ServerPlayer owner) { this.owner = owner; NeoForge.EVENT_BUS.register(this); }

        @SubscribeEvent public void research(ResearchEvent.Research event) {
            if (event.getPlayer() == owner && event.getResearchKey().equals(LegacyResearchCrafting.RESEARCH_TABLE.marker())) {
                calls++;
                event.setCanceled(true);
            }
        }

        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
