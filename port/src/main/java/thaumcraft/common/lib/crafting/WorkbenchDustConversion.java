package thaumcraft.common.lib.crafting;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.research.ResearchItemResolver;
import thaumcraft.common.lib.fx.SourceFX;
import net.minecraft.world.phys.Vec3;

/** Source DustTriggerSimple FIRSTSTEPS@1 crafting-table conversion. Paid dust, immediate real
 * crafting event BEFORE state capture, 50 countdown END events, swap on END51, fixed UP place
 * check. Canceled/stale swaps retain the paid dust AND already emitted source craft receipt.
 * Modern bounds/thread/owner/chunk/state/protection checks mirror BookshelfDustConversion.
 * Both original workbench entries are supported: concrete vanilla table first, then the finite
 * live c:workbenches BlockItem tag bridge. Generic addon outputs use DustConversion.
 * Successful changes emit the original default-color sound+flair BAMF without changing the timer/payment.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class WorkbenchDustConversion {
    public static final int DELAY_DECREMENTS = 50, MAX_PENDING_PER_LEVEL = 4096;
    private static final Map<ServerLevel, Queue> QUEUES = new WeakHashMap<>();
    private WorkbenchDustConversion() {}
    public static boolean canConvert(Level level, Player player, BlockPos pos) {
        if (BlocksTC.arcaneWorkbench == null || player == null || !level.hasChunkAt(pos)
            || !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("FIRSTSTEPS@1")) return false;
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.CRAFTING_TABLE)) return true;
        var item = state.getBlock().asItem();
        return item instanceof BlockItem && new ItemStack(item).is(ResearchItemResolver.oreTag("workbench"));
    }
    public static boolean schedule(ServerLevel level, ServerPlayer owner, BlockPos pos, InteractionHand hand) {
        requireThread(level);
        if (!canSchedule(level, owner, pos) || !owner.getItemInHand(hand).is(ItemsTC.salisMundus)) return false;
        if (!owner.getAbilities().instabuild) owner.getItemInHand(hand).shrink(1);
        return schedulePaid(level, owner, pos);
    }
    public static boolean canSchedule(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        requireThread(level); Queue queue = QUEUES.get(level);
        return sameOwner(level, owner) && canConvert(level, owner, pos) && (queue == null || queue.count < MAX_PENDING_PER_LEVEL);
    }
    /** Dust was paid by the ordered dispatcher before the immediate crafting callback. */
    public static boolean schedulePaid(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        if (!canSchedule(level, owner, pos)) return false;
        Queue queue = QUEUES.computeIfAbsent(level, ignored -> new Queue());
        queue.count++;
        try {
            ItemStack result = new ItemStack(BlocksTC.arcaneWorkbench);
            EventHooks.firePlayerCraftingEvent(owner, result, new SimpleContainer(1));
            queue.entries.add(new Pending(owner, pos.immutable(), level.getBlockState(pos), result));
            return true;
        } catch (RuntimeException | Error failure) { queue.count--; throw failure; }
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        requireThread(level); Queue queue = QUEUES.get(level); if (queue == null || queue.entries.isEmpty()) return;
        int count = queue.entries.size(); ArrayDeque<Pending> ready = new ArrayDeque<>(), delayed = new ArrayDeque<>();
        for (int index = 0; index < count; index++) { Pending pending = queue.entries.remove(); if (pending.delay > 0) { pending.delay--; delayed.add(pending); } else ready.add(pending); }
        queue.entries.addAll(delayed);
        for (Pending pending : ready) { queue.count--; execute(level, pending); }
        if (queue.count == 0) QUEUES.remove(level);
    }
    private static void execute(ServerLevel level, Pending pending) {
        if (!valid(level, pending)) return;
        if (EventHooks.onBlockPlace(pending.owner, BlockSnapshot.create(level.dimension(), level, pending.pos), Direction.UP)) return;
        if (valid(level, pending) && level.setBlock(pending.pos, BlocksTC.arcaneWorkbench.defaultBlockState(), Block.UPDATE_ALL))
            SourceFX.bamf(level,Vec3.atCenterOf(pending.pos),-9999,true,true,null);
    }
    private static boolean valid(ServerLevel level, Pending pending) {
        return pending.result.is(BlocksTC.arcaneWorkbench.asItem()) && pending.result.getCount() == 1 && sameOwner(level, pending.owner)
            && level.hasChunkAt(pending.pos) && level.getBlockState(pending.pos) == pending.source
            && pending.source.getDestroySpeed(level, pending.pos) >= 0F && level.mayInteract(pending.owner, pending.pos);
    }
    private static boolean sameOwner(ServerLevel level, ServerPlayer owner) { return owner != null && owner.level() == level && owner.getServer() == level.getServer() && owner.isAlive() && !owner.isRemoved(); }
    private static void requireThread(ServerLevel level) { if (!level.getServer().isSameThread()) throw new IllegalStateException("Workbench conversion requires owning server thread"); }
    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel level) QUEUES.remove(level); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { QUEUES.keySet().removeIf(level -> level.getServer() == event.getServer()); }
    public static int pendingCount(ServerLevel level) { requireThread(level); Queue queue = QUEUES.get(level); return queue == null ? 0 : queue.count; }
    private static final class Queue { final ArrayDeque<Pending> entries = new ArrayDeque<>(); int count; }
    private static final class Pending {
        final ServerPlayer owner; final BlockPos pos; final BlockState source; final ItemStack result; int delay = DELAY_DECREMENTS;
        Pending(ServerPlayer owner, BlockPos pos, BlockState source, ItemStack result) { this.owner = owner; this.pos = pos; this.source = source; this.result = result; }
    }
}
