package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.EntitySpecialItem;
import thaumcraft.common.entities.SpecialItemRegistries;
import thaumcraft.common.items.resources.ItemMagicDust;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.crafting.BookshelfDustConversion;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;

/** Actual ItemStack use-first/events/level-END timer/special drop/touch prefix.
 * !gotdream is an EXPLICIT fixture here; this is not the separate normal acquisition-path test.
 * Direct entity.playerTouch invokes the real collision/pickup implementation, not an invented
 * pickup event or research grant. No natural player walk, real TCP/visual/FX acceptance claim. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class DustBookGameTests {
    private DustBookGameTests() {}

    private static ServerPlayer owner(GameTestHelper h, String name, boolean dream) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        BlockPos remote = h.absolutePos(new BlockPos(10, 4, 10));
        player.setPos(remote.getX() + .5D, remote.getY(), remote.getZ() + .5D);
        if (dream) ThaumcraftCapabilities.getKnowledge(player).addResearch("!gotdream");
        return player;
    }
    private static BlockPos shelf(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(3, 3, 3));
        h.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        h.getLevel().setBlock(pos, Blocks.BOOKSHELF.defaultBlockState(), Block.UPDATE_ALL);
        return pos;
    }
    private static UseOnContext context(ServerPlayer owner, BlockPos pos, InteractionHand hand, Direction face) {
        return new UseOnContext(owner, hand, new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }
    private static InteractionResult use(ServerPlayer owner, BlockPos pos, InteractionHand hand, Direction face) {
        return owner.getItemInHand(hand).onItemUseFirst(context(owner, pos, hand, face));
    }
    private static List<ItemEntity> drops(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5D));
    }
    private static List<ItemEntity> books(GameTestHelper h, BlockPos pos) {
        return drops(h, pos).stream().filter(entity -> entity.getItem().is(ItemsTC.thaumonomicon)).toList();
    }
    private static void assertNoBook(GameTestHelper h, BlockPos pos) {
        h.assertTrue(books(h, pos).isEmpty(), "No book exists before a successful delayed swap");
    }
    private static void at(GameTestHelper h, long start, int delay, Runnable task) { h.runAtTickTime(start + delay, task); }

    @GameTest(template = "scan_test", timeoutTicks = 90)
    public static void actualUseConsumesBeforeImmediateCraftAndConvertsOnEnd51ThenRealTouchGrantsOnlyPicker(GameTestHelper h) {
        ServerPlayer a = owner(h, "TC_Dust_A", true), b = owner(h, "TC_Dust_B", false);
        BlockPos pos = shelf(h);
        a.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemsTC.salisMundus, 2));
        try (CraftProbe probe = new CraftProbe(h, a, pos, InteractionHand.OFF_HAND, 1, event -> {})) {
            h.assertTrue(use(a, pos, InteractionHand.OFF_HAND, Direction.WEST) == InteractionResult.SUCCESS, "Actual use-first server trigger returns success");
            h.assertTrue(probe.calls == 1 && a.getItemInHand(InteractionHand.OFF_HAND).getCount() == 1, "One dust consumed and one immediate craft event");
        }
        long start = h.getTick();
        at(h, start, 50, () -> {
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.BOOKSHELF), "Source fifty END decrements do not execute the runnable yet");
            assertNoBook(h, pos);
            h.assertFalse(ThaumcraftCapabilities.getKnowledge(a).isResearchKnown("!gotthaumonomicon"), "Immediate crafting never grants pickup marker");
        });
        at(h, start, 51, () -> {
            h.assertTrue(h.getLevel().getBlockState(pos).isAir() && books(h, pos).size() == 1, "Actual level END event51 removes shelf and spawns exactly one book");
            EntitySpecialItem book = (EntitySpecialItem) books(h, pos).getFirst();
            h.assertTrue(book.getType() == SpecialItemRegistries.TYPE && book.getDeltaMovement().equals(Vec3.ZERO)
                && book.getY() == pos.getY() + .1D, "Source registered special drop initial position/zero velocity");
            h.assertTrue(drops(h, pos).size() == 1 && a.getInventory().countItem(ItemsTC.thaumonomicon) == 0
                && b.getInventory().countItem(ItemsTC.thaumonomicon) == 0, "No shelf loot or immediate inventory insertion");
            h.assertFalse(ThaumcraftCapabilities.getKnowledge(a).isResearchKnown("!gotthaumonomicon"), "No knowledge grant until actual collision callback");
            List<ServerPlayer> recipients = new ArrayList<>();
            try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {
                h.assertTrue(packet instanceof PacketSyncKnowledge, "Book pickup sends only immediate owner knowledge snapshot");
                recipients.add(recipient);
            })) { book.playerTouch(b); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
            h.assertTrue(book.isRemoved() && b.getInventory().countItem(ItemsTC.thaumonomicon) == 1
                && ThaumcraftCapabilities.getKnowledge(b).getResearchStage("!gotthaumonomicon") == 0
                && !ThaumcraftCapabilities.getKnowledge(a).isResearchKnown("!gotthaumonomicon")
                && recipients.equals(List.of(b)), "Actual touch grants raw known0 marker and immediate snapshot to picker B, not converter A");
            h.succeed();
        });
    }

    @GameTest(template = "scan_test")
    public static void noDreamSneakAndEditDenyWithoutConsumptionAndUnknownWorkbenchGateRemainsClosed(GameTestHelper h) {
        ServerPlayer a = owner(h, "TC_Dust_Gates", false);
        BlockPos pos = shelf(h);
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.salisMundus, 3));
        h.assertTrue(use(a, pos, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.PASS, "Only known !gotdream opens bookshelf dust gate");
        ThaumcraftCapabilities.getKnowledge(a).addResearch("!gotdream");
        a.setShiftKeyDown(true);
        h.assertTrue(use(a, pos, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.PASS, "Sneak declines before any trigger");
        a.setShiftKeyDown(false);
        a.setGameMode(GameType.ADVENTURE);
        UseOnContext context = context(a, pos, InteractionHand.MAIN_HAND, Direction.UP);
        h.assertTrue(((ItemMagicDust) ItemsTC.salisMundus).onItemUseFirst(a.getMainHandItem(), context) == InteractionResult.FAIL,
            "Actual item implementation retains source edit failure");
        h.assertTrue(use(a, pos, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.PASS,
            "Modern ItemStack adventure permission gate intercepts before extension (documented engine behavior)");
        a.setGameMode(GameType.SURVIVAL);
        h.getLevel().setBlock(pos, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
        var knowledge = ThaumcraftCapabilities.getKnowledge(a);
        knowledge.addResearch("FIRSTSTEPS"); // Raw known0 is BELOW the concrete workbench @1 gate.
        h.assertTrue(use(a, pos, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.PASS
            && h.getLevel().getBlockState(pos).is(Blocks.CRAFTING_TABLE) && a.getMainHandItem().getCount() == 3,
            "Raw known0 cannot bypass source FIRSTSTEPS@1 workbench gate; real positive conversion is covered separately");
        h.succeed();
    }

    @GameTest(template = "scan_test", timeoutTicks = 80)
    public static void creativeStillDelaysAndCraftsWithoutConsumingDust(GameTestHelper h) {
        ServerPlayer a = owner(h, "TC_Dust_Creative", true);
        BlockPos pos = shelf(h); a.setGameMode(GameType.CREATIVE);
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.salisMundus, 4));
        try (CraftProbe probe = new CraftProbe(h, a, pos, InteractionHand.MAIN_HAND, 4, event -> {})) {
            h.assertTrue(use(a, pos, InteractionHand.MAIN_HAND, Direction.DOWN) == InteractionResult.SUCCESS && probe.calls == 1, "Creative source fires immediate craft too");
        }
        long start = h.getTick();
        at(h, start, 50, () -> { h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.BOOKSHELF), "Creative does not bypass delay50"); assertNoBook(h, pos); });
        at(h, start, 51, () -> { h.assertTrue(books(h, pos).size() == 1 && a.getMainHandItem().getCount() == 4, "Creative delayed book exists and dust unchanged"); h.succeed(); });
    }

    @GameTest(template = "scan_test", timeoutTicks = 80)
    public static void delayedChangedStateCancelsWithoutDustRefundOrShelfDrops(GameTestHelper h) {
        ServerPlayer a = owner(h, "TC_Dust_Changed", true); BlockPos pos = shelf(h);
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.salisMundus));
        h.assertTrue(use(a, pos, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.SUCCESS, "Initial source trigger accepted");
        long start = h.getTick();
        at(h, start, 20, () -> h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL));
        at(h, start, 51, () -> {
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE) && a.getMainHandItem().isEmpty(), "Exact captured state identity rejects changed block; dust remains consumed");
            assertNoBook(h, pos); h.assertTrue(drops(h, pos).isEmpty(), "Canceled source does not drop shelf contents"); h.succeed();
        });
    }

    @GameTest(template = "scan_test", timeoutTicks = 80)
    public static void actualDelayedPlaceCancellationUsesOldStateAndUpAgainstBelowRegardlessOffhandSide(GameTestHelper h) {
        ServerPlayer a = owner(h, "TC_Dust_PlaceCancel", true); BlockPos pos = shelf(h);
        a.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemsTC.salisMundus));
        PlaceProbe probe = new PlaceProbe(h, a, pos, event -> event.setCanceled(true));
        try { h.assertTrue(use(a, pos, InteractionHand.OFF_HAND, Direction.EAST) == InteractionResult.SUCCESS, "Use schedules before delayed place event"); }
        catch (RuntimeException | Error failure) { probe.close(); throw failure; }
        long start = h.getTick();
        at(h, start, 51, () -> { try {
            h.assertTrue(probe.calls == 1 && h.getLevel().getBlockState(pos).is(Blocks.BOOKSHELF)
                && a.getItemInHand(InteractionHand.OFF_HAND).isEmpty(), "Actual old-state place event cancels delayed swap without refund");
            assertNoBook(h, pos); h.succeed();
        } finally { probe.close(); } });
    }

    @GameTest(template = "scan_test", timeoutTicks = 80)
    public static void placeListenerMutationIsPreservedByModernPostEventRevalidation(GameTestHelper h) {
        ServerPlayer a = owner(h, "TC_Dust_PlaceChange", true); BlockPos pos = shelf(h);
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.salisMundus));
        PlaceProbe probe = new PlaceProbe(h, a, pos, event -> h.getLevel().setBlock(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL));
        try { h.assertTrue(use(a, pos, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.SUCCESS, "Initial attempt queued"); }
        catch (RuntimeException | Error failure) { probe.close(); throw failure; }
        long start = h.getTick();
        at(h, start, 51, () -> { try {
            h.assertTrue(probe.calls == 1 && h.getLevel().getBlockState(pos).is(Blocks.DIAMOND_BLOCK), "Modern safety boundary retains listener-owned changed state");
            assertNoBook(h, pos); h.succeed();
        } finally { probe.close(); } });
    }

    @GameTest(template = "scan_test", timeoutTicks = 80)
    public static void immediateCraftListenerStateIsCapturedAfterEventAndUnsupportedResultRewriteFailsClosed(GameTestHelper h) {
        ServerPlayer a = owner(h, "TC_Dust_CraftState", true); BlockPos pos = shelf(h);
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.salisMundus));
        try (CraftProbe probe = new CraftProbe(h, a, pos, InteractionHand.MAIN_HAND, 0,
            event -> h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL))) {
            h.assertTrue(use(a, pos, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.SUCCESS && probe.calls == 1,
                "Source immediate event precedes captured source state");
        }
        ServerPlayer rewritten = owner(h, "TC_Dust_Rewritten", true);
        BlockPos other = h.absolutePos(new BlockPos(8, 3, 3));
        h.getLevel().setBlock(other, Blocks.BOOKSHELF.defaultBlockState(), Block.UPDATE_ALL);
        rewritten.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.salisMundus));
        try (CraftProbe probe = new CraftProbe(h, rewritten, other, InteractionHand.MAIN_HAND, 0,
            event -> event.getCrafting().setCount(2))) {
            h.assertTrue(use(rewritten, other, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.SUCCESS && probe.calls == 1,
                "Listener result rewrite and immediate crafting effects remain, dust was consumed");
        }
        long start = h.getTick();
        at(h, start, 51, () -> {
            h.assertTrue(h.getLevel().getBlockState(pos).isAir() && books(h, pos).size() == 1,
                "Source captures post-crafting stone, does not recalculate bookshelf/dream trigger at delayed execute");
            h.assertTrue(h.getLevel().getBlockState(other).is(Blocks.BOOKSHELF) && rewritten.getMainHandItem().isEmpty(),
                "Explicit modern unsupported-output boundary retains shelf and consumed dust instead of inventing full swapper support");
            assertNoBook(h, other); h.succeed();
        });
    }

    @GameTest(template = "scan_test", timeoutTicks = 80)
    public static void liveItemTagBridgeMatchesAfterVanillaAndRestoresBindingsBeforeAnyYield(GameTestHelper h) {
        ServerPlayer a = owner(h, "TC_Dust_Tag", true); BlockPos pos = shelf(h);
        var registry = BuiltInRegistries.ITEM;
        Map<TagKey<Item>, List<Holder<Item>>> original = new LinkedHashMap<>();
        registry.getTags().forEach(pair -> original.put(pair.getFirst(), pair.getSecond().stream().toList()));
        h.assertTrue(original.containsKey(Tags.Items.BOOKSHELVES), "Uses an existing official c:bookshelves tag");
        Map<TagKey<Item>, List<Holder<Item>>> changed = new LinkedHashMap<>(original);
        changed.put(Tags.Items.BOOKSHELVES, List.of(Items.STONE.builtInRegistryHolder()));
        try {
            registry.bindTags(changed);
            h.assertTrue(BookshelfDustConversion.canConvert(h.getLevel(), a, pos), "Vanilla bookshelf trigger still wins when its item tag membership is absent");
            h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.salisMundus));
            h.assertTrue(BookshelfDustConversion.canConvert(h.getLevel(), a, pos)
                && use(a, pos, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.SUCCESS, "Actual BlockItem membership enables fallback trigger");
        } finally { registry.bindTags(original); }
        h.assertFalse(BookshelfDustConversion.canConvert(h.getLevel(), a, pos), "No stale tag cache after synchronous exact restoration");
        long start = h.getTick();
        at(h, start, 51, () -> { h.assertTrue(books(h, pos).size() == 1, "Already accepted original trigger is not re-matched against later tag membership"); h.succeed(); });
    }

    @GameTest(template = "scan_test", timeoutTicks = 80)
    public static void reentrantCraftSchedulesTwoConsumedAttemptsButSingleSwapAndRemovedOwnerRejects(GameTestHelper h) {
        ServerPlayer a = owner(h, "TC_Dust_Reentrant", true); BlockPos pos = shelf(h);
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.salisMundus, 2));
        int before = BookshelfDustConversion.pendingCount(h.getLevel());
        try (CraftProbe probe = new CraftProbe(h, a, pos, InteractionHand.MAIN_HAND, -1, event -> {
            if (event.getCrafting().getCount() == 1 && a.getMainHandItem().getCount() == 1)
                h.assertTrue(use(a, pos, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.SUCCESS, "One guarded reentrant immediate craft use schedules its own source delay");
        })) {
            h.assertTrue(use(a, pos, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.SUCCESS
                && probe.calls == 2 && a.getMainHandItem().isEmpty() && BookshelfDustConversion.pendingCount(h.getLevel()) == before + 2,
                "Two real consumed attempts/reserved queue entries, no immediate book");
        }
        ServerPlayer removed = owner(h, "TC_Dust_Removed", true);
        BlockPos other = h.absolutePos(new BlockPos(8, 3, 3));
        h.getLevel().setBlock(other, Blocks.BOOKSHELF.defaultBlockState(), Block.UPDATE_ALL);
        removed.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.salisMundus));
        h.assertTrue(use(removed, other, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.SUCCESS, "Owner-valid initial attempt accepted");
        removed.discard();
        long start = h.getTick();
        at(h, start, 51, () -> {
            h.assertTrue(books(h, pos).size() == 1, "Identical captured states only swap once even after reentrant crafting");
            h.assertTrue(h.getLevel().getBlockState(other).is(Blocks.BOOKSHELF), "Modern invalid removed-owner boundary cancels only its pending attempt");
            assertNoBook(h, other); h.succeed();
        });
    }

    @GameTest(template = "scan_test", timeoutTicks = 40)
    public static void realWorldSpecialItemFirstTickSkipPositiveDampingFloatingAndExplosionImmunity(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(3, 6, 3));
        EntitySpecialItem entity = new EntitySpecialItem(h.getLevel(), pos.getX() + .5D, pos.getY() + .1D,
            pos.getZ() + .5D, new ItemStack(ItemsTC.thaumonomicon));
        entity.setDeltaMovement(.04D, .2D, -.04D);
        double y = entity.getY(); Vec3 motion = entity.getDeltaMovement();
        h.assertTrue(h.getLevel().addFreshEntity(entity), "Registered special entity actually joins ticking level");
        long start = h.getTick();
        at(h, start, 1, () -> h.assertTrue(entity.tickCount == 1 && entity.getY() == y && entity.getDeltaMovement().equals(motion)
            && entity.getAge() == 0, "World increments externally once; source special entity skips first entire vanilla update"));
        at(h, start, 2, () -> {
            double damped = .2D * .9F;
            h.assertTrue(entity.tickCount == 2 && entity.getAge() == 1 && Math.abs(entity.getY() - (y + damped)) < 1E-8D
                && Math.abs(entity.getDeltaMovement().y - damped * .98D) < 1E-8D,
                "Second real world tick uses source positive .9F damping and exactly canceled legacy .04F gravity before modern friction");
            h.assertFalse(entity.hurt(h.getLevel().damageSources().explosion(null), 100F), "Explosion damage is explicitly refused");
            h.assertFalse(entity.isRemoved(), "Explosion did not destroy actual special item");
            entity.hurt(h.getLevel().damageSources().generic(), 100F);
            h.assertTrue(entity.isRemoved(), "Non-explosion damage delegates to real vanilla item damage");
            h.succeed();
        });
    }

    @GameTest(template = "scan_test", timeoutTicks = 140)
    public static void delayedPlaceListenerReentrantUseBeginsItsFiftyDecrementsOnlyOnNextEnd(GameTestHelper h) {
        ServerPlayer a = owner(h, "TC_Dust_ReentrantPlace", true); BlockPos first = shelf(h);
        BlockPos second = h.absolutePos(new BlockPos(8, 3, 3));
        h.getLevel().setBlock(second.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        h.getLevel().setBlock(second, Blocks.BOOKSHELF.defaultBlockState(), Block.UPDATE_ALL);
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.salisMundus, 2));
        PlaceProbe probe = new PlaceProbe(h, a, first, event -> {
            h.assertTrue(use(a, second, InteractionHand.MAIN_HAND, Direction.NORTH) == InteractionResult.SUCCESS,
                "Actual place listener schedules a second real dust attempt");
            h.assertTrue(a.getMainHandItem().isEmpty() && h.getLevel().getBlockState(second).is(Blocks.BOOKSHELF),
                "Reentrant use consumes immediately but never swaps within its crafting callback");
        });
        try { h.assertTrue(use(a, first, InteractionHand.MAIN_HAND, Direction.UP) == InteractionResult.SUCCESS, "First attempt queued"); }
        catch (RuntimeException | Error failure) { probe.close(); throw failure; }
        long start = h.getTick();
        at(h, start, 51, () -> { try {
            h.assertTrue(probe.calls == 1 && books(h, first).size() == 1, "First source attempt executes on END51");
            assertNoBook(h, second);
        } finally { probe.close(); } });
        at(h, start, 101, () -> {
            h.assertTrue(h.getLevel().getBlockState(second).is(Blocks.BOOKSHELF),
                "Second attempt has only fifty decrements at END101; place callback did not decrement in its creation END51");
            assertNoBook(h, second);
        });
        at(h, start, 102, () -> {
            h.assertTrue(h.getLevel().getBlockState(second).isAir() && books(h, second).size() == 1,
                "Reentrant place attempt executes on its own END51 (global END102)");
            h.succeed();
        });
    }

    public static final class CraftProbe implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer owner;
        private final BlockPos pos;
        private final InteractionHand hand;
        private final int remaining;
        private final Consumer<PlayerEvent.ItemCraftedEvent> listener;
        int calls;
        CraftProbe(GameTestHelper helper, ServerPlayer owner, BlockPos pos, InteractionHand hand, int remaining,
                   Consumer<PlayerEvent.ItemCraftedEvent> listener) {
            this.helper = helper; this.owner = owner; this.pos = pos; this.hand = hand; this.remaining = remaining; this.listener = listener;
            NeoForge.EVENT_BUS.register(this);
        }
        @SubscribeEvent public void crafted(PlayerEvent.ItemCraftedEvent event) {
            if (event.getEntity() != owner) return;
            calls++;
            helper.assertTrue(event.getCrafting().is(ItemsTC.thaumonomicon) && event.getCrafting().getCount() == 1
                && event.getInventory().getContainerSize() == 1 && event.getInventory().isEmpty(), "Real immediate source crafting result and empty one-slot matrix");
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.BOOKSHELF)
                && (remaining < 0 || owner.getItemInHand(hand).getCount() == remaining), "Dust consumed BEFORE event, original target not yet swapped");
            listener.accept(event);
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
    public static final class PlaceProbe implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer owner;
        private final BlockPos pos;
        private final Consumer<BlockEvent.EntityPlaceEvent> listener;
        int calls;
        PlaceProbe(GameTestHelper helper, ServerPlayer owner, BlockPos pos, Consumer<BlockEvent.EntityPlaceEvent> listener) {
            this.helper = helper; this.owner = owner; this.pos = pos; this.listener = listener;
            NeoForge.EVENT_BUS.register(this);
        }
        @SubscribeEvent public void place(BlockEvent.EntityPlaceEvent event) {
            if (event.getEntity() != owner || !event.getPos().equals(pos)) return;
            calls++;
            helper.assertTrue(event.getBlockSnapshot().getState().is(Blocks.BOOKSHELF) && event.getPlacedBlock().is(Blocks.BOOKSHELF)
                && event.getPlacedAgainst().is(Blocks.STONE), "Actual source old-state place event uses fixed UP against block below, never clicked side");
            listener.accept(event);
            close(); // One-shot even when the event is canceled.
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
