package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.crafting.WorkbenchDustConversion;
import thaumcraft.common.lib.research.ResearchItemResolver;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;

/** Actual dust item use-first, source early craft receipt and real level-END delayed swap.
 * Normal survival acquisition, network and original bamf/client-particle parity remain separate QA.
 */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class WorkbenchDustGameTests {
    private static ServerPlayer owner(GameTestHelper helper, String name, int stage) {
        ServerPlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        owner.setGameMode(GameType.SURVIVAL); owner.setPos(helper.absolutePos(new BlockPos(10, 4, 10)).getX(), helper.absolutePos(new BlockPos(10, 4, 10)).getY(), helper.absolutePos(new BlockPos(10, 4, 10)).getZ());
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner); knowledge.addResearch("FIRSTSTEPS"); knowledge.setResearchStage("FIRSTSTEPS", stage);
        return owner;
    }
    private static BlockPos target(GameTestHelper helper, int x) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, 3, 3));
        helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.getLevel().setBlock(pos, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL); return pos;
    }
    private static InteractionResult use(ServerPlayer owner, BlockPos pos) {
        return owner.getItemInHand(InteractionHand.OFF_HAND).onItemUseFirst(new UseOnContext(owner, InteractionHand.OFF_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false)));
    }
    private static String marker(GameTestHelper helper) {
        Integer hash = ResearchItemResolver.craftReference(new ItemStack(BlocksTC.arcaneWorkbench));
        helper.assertTrue(hash != null, "actual source workbench translation-key crafting reference resolved"); return "[#]" + hash;
    }

    @GameTest(template = "scan_test", timeoutTicks = 80)
    public static void actualSourceGatePaidDustEarlyReceiptAndEnd51RealBlockEntityConversion(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, "TC_Workbench_Dust", 0); BlockPos pos = target(helper, 3);
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemsTC.salisMundus, 2));
        helper.assertTrue(use(owner, pos) == InteractionResult.PASS && owner.getItemInHand(InteractionHand.OFF_HAND).getCount() == 2,
            "raw known0 cannot convert before FIRSTSTEPS@1");
        ThaumcraftCapabilities.getKnowledge(owner).setResearchStage("FIRSTSTEPS", 1);
        try (CraftProbe probe = new CraftProbe(helper, owner, pos, 1, event -> {})) {
            helper.assertTrue(use(owner, pos) == InteractionResult.SUCCESS && probe.calls == 1, "real ItemMagicDust dispatch fires source callback exactly once");
        }
        String receipt = marker(helper);
        helper.assertTrue(ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown(receipt), "source receipt exists immediately BEFORE delayed block mutation");
        long start = helper.getTick();
        helper.runAtTickTime(start + 50, () -> helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.CRAFTING_TABLE), "50 END decrements do not execute yet"));
        helper.runAtTickTime(start + 51, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(BlocksTC.arcaneWorkbench) && helper.getLevel().getBlockEntity(pos) instanceof TileArcaneWorkbench,
                "END51 places registered source block and genuine persistent/menu-capable block entity");
            helper.assertTrue(owner.getItemInHand(InteractionHand.OFF_HAND).getCount() == 1 && ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown(receipt),
                "exactly one paid dust, no fake delayed regrant"); helper.succeed();
        });
    }

    @GameTest(template = "scan_test", timeoutTicks = 80)
    public static void canceledAndStaleDelayedSwapsKeepSourceAlreadyPaidDustAndEarlyCraftReceipt(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, "TC_Workbench_Cancel", 1); BlockPos canceled = target(helper, 3), stale = target(helper, 5);
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemsTC.salisMundus, 2));
        helper.assertTrue(use(owner, canceled) == InteractionResult.SUCCESS && use(owner, stale) == InteractionResult.SUCCESS, "two genuine pending paid conversions");
        helper.getLevel().setBlock(stale, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        String receipt = marker(helper);
        var probe = new PlaceProbe(helper, owner, canceled, event -> event.setCanceled(true));
        long start = helper.getTick();
        helper.runAtTickTime(start + 51, () -> {
            try {
                helper.assertTrue(probe.calls == 1 && helper.getLevel().getBlockState(canceled).is(Blocks.CRAFTING_TABLE)
                    && helper.getLevel().getBlockState(stale).is(Blocks.STONE), "real fixed-UP old-state place cancel and stale-state guard retain target blocks");
                helper.assertTrue(owner.getItemInHand(InteractionHand.OFF_HAND).isEmpty() && ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown(receipt),
                    "documented original quirk: cancel/stale failure neither refunds dust NOR undoes immediate source receipt");
                helper.succeed();
            } finally { probe.close(); }
        });
    }

    @GameTest(template = "scan_test", timeoutTicks = 80)
    public static void creativePreservesDustButPlaceListenerMutationCannotBeOverwritten(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, "TC_Workbench_Rewrite", 1); owner.setGameMode(GameType.CREATIVE); BlockPos pos = target(helper, 3);
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemsTC.salisMundus, 3));
        try (CraftProbe probe = new CraftProbe(helper, owner, pos, 3, event -> {})) {
            helper.assertTrue(use(owner, pos) == InteractionResult.SUCCESS && probe.calls == 1, "creative still fires immediate real craft event but does not consume dust");
        }
        var probe = new PlaceProbe(helper, owner, pos, event -> helper.getLevel().setBlock(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL));
        long start = helper.getTick();
        helper.runAtTickTime(start + 50, () -> helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.CRAFTING_TABLE), "creative retains normal source countdown"));
        helper.runAtTickTime(start + 51, () -> {
            try {
                helper.assertTrue(probe.calls == 1 && helper.getLevel().getBlockState(pos).is(Blocks.DIAMOND_BLOCK)
                    && owner.getItemInHand(InteractionHand.OFF_HAND).getCount() == 3,
                    "modern post-listener guard retains external diamond mutation instead of blindly overwriting it; creative dust unchanged");
                helper.succeed();
            } finally { probe.close(); }
        });
    }

    public static final class CraftProbe implements AutoCloseable {
        final GameTestHelper helper; final ServerPlayer owner; final BlockPos pos; final int dust; final Consumer<PlayerEvent.ItemCraftedEvent> listener; int calls;
        CraftProbe(GameTestHelper helper, ServerPlayer owner, BlockPos pos, int dust, Consumer<PlayerEvent.ItemCraftedEvent> listener) {
            this.helper = helper; this.owner = owner; this.pos = pos; this.dust = dust; this.listener = listener; NeoForge.EVENT_BUS.register(this);
        }
        @SubscribeEvent public void crafted(PlayerEvent.ItemCraftedEvent event) {
            if (event.getEntity() != owner || !event.getCrafting().is(BlocksTC.arcaneWorkbench.asItem())) return;
            calls++; helper.assertTrue(event.getInventory().getContainerSize() == 1 && event.getInventory().isEmpty() && event.getCrafting().getCount() == 1
                && helper.getLevel().getBlockState(pos).is(Blocks.CRAFTING_TABLE) && owner.getItemInHand(InteractionHand.OFF_HAND).getCount() == dust,
                "source immediate one-slot fake matrix event AFTER dust payment BEFORE capture/timer/block mutation"); listener.accept(event);
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
    public static final class PlaceProbe implements AutoCloseable {
        final GameTestHelper helper; final ServerPlayer owner; final BlockPos pos; final Consumer<BlockEvent.EntityPlaceEvent> listener; int calls;
        PlaceProbe(GameTestHelper helper, ServerPlayer owner, BlockPos pos, Consumer<BlockEvent.EntityPlaceEvent> listener) {
            this.helper = helper; this.owner = owner; this.pos = pos; this.listener = listener; NeoForge.EVENT_BUS.register(this);
        }
        @SubscribeEvent public void placed(BlockEvent.EntityPlaceEvent event) {
            if (event.getEntity() != owner || !event.getPos().equals(pos)) return;
            calls++; helper.assertTrue(event.getPlacedBlock().is(Blocks.CRAFTING_TABLE) && event.getPlacedAgainst().is(Blocks.STONE), "source place check on OLD target against below (fixed UP despite clicked WEST)");
            listener.accept(event); close();
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
