package thaumcraft.common.lib.crafting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.EntitySpecialItem;
import thaumcraft.common.lib.fx.SourceFX;

/** Source delayed Runnable -> VirtualSwapper path. Parts mutate independently at their own
 * END(priority+1), without resource drops, refunds or invented all-or-nothing rollback. Blocks
 * are break-locked until execution/cancellation. Reservations precede reentrant craft listeners.
 * Pending work is transient like the source; unload/stop cancels and unlocks instead of saving
 * live player references or replaying already-paid transformations after a restart.
 * Modern safety: bounded queue, finite descriptors, owning server thread, loaded chunks, live
 * owner, stale source/receipt rejection, fixed-UP cancellable place hook and post-listener checks.
 * Each successful part emits original default-color sound BAMF without the simple-trigger flair.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class DustMultiblockConversion {
    public static final int MAX_PENDING_PER_LEVEL = 4096;
    private static final Map<ServerLevel, Queue> QUEUES = new WeakHashMap<>();
    private DustMultiblockConversion() {}

    public static boolean canSchedule(ServerLevel level, ServerPlayer owner, DustMultiblockPattern pattern,
                                      BlockPos clicked, thaumcraft.api.crafting.IDustTrigger.Placement placement, ItemStack receipt) {
        requireThread(level);
        if (!sameOwner(level, owner) || placement == null || placement.facing == null || placement.facing.getAxis().isVertical()
            || !DustConversion.isSupportedOutput(receipt)) return false;
        List<DustMultiblockPattern.Cell> cells = pattern.cells(clicked, placement);
        if (!DustTriggerMultiblock.targetsSupported(cells)) return false;
        Queue queue = QUEUES.get(level); int changes = 0;
        for (var cell : cells) {
            if (!level.hasChunkAt(cell.pos()) || level.isOutsideBuildHeight(cell.pos()) || !DustMultiblockPattern.matchesSource(level.getBlockState(cell.pos()), cell.part().getSource())) return false;
            if (cell.part().getTarget() != null) {
                changes++;
                if (!level.mayInteract(owner, cell.pos()) || level.getBlockState(cell.pos()).getDestroySpeed(level, cell.pos()) < 0F
                    || queue != null && queue.locks.containsKey(cell.pos())) return false;
            }
        }
        return (queue == null ? 0 : queue.count) + changes <= MAX_PENDING_PER_LEVEL;
    }
    public static boolean schedulePaid(ServerLevel level, ServerPlayer owner, DustMultiblockPattern pattern, BlockPos clicked,
                                       thaumcraft.api.crafting.IDustTrigger.Placement placement, Direction side, ItemStack receipt) {
        if (side == null || !canSchedule(level, owner, pattern, clicked, placement, receipt)) return false;
        Queue queue = QUEUES.computeIfAbsent(level, ignored -> new Queue());
        List<Reserved> reserved = new ArrayList<>();
        for (var cell : pattern.cells(clicked, placement)) if (cell.part().getTarget() != null) {
            var target = DustTriggerMultiblock.target(cell.part(), placement.facing, side, owner.getDirection());
            reserved.add(new Reserved(cell.pos().immutable(), cell.part().getSource(), target, Math.max(0, cell.part().getPriority())));
        }
        for (Reserved reservation : reserved) { queue.count++; queue.locks.put(reservation.pos, true); }
        try {
            ItemStack expected = receipt.copy(), result = receipt.copy();
            EventHooks.firePlayerCraftingEvent(owner, result, new SimpleContainer(1));
            // Original captures Block sources AFTER crafting listeners. Flattened cauldron/
            // anvil BlockFamily is also one original Block: capture exact current state here,
            // never keep the family predicate or allow subsequent level/wear changes. Capturing
            // a listener's foreign replacement likewise cannot expand the accepted family.
            // Snapshots remain immutable even if an addon mutates its descriptors later.
            ArrayList<Pending> completed = new ArrayList<>();
            for (Reserved reservation : reserved) {
                if (!level.hasChunkAt(reservation.pos)) {
                    for (Reserved entry : reserved) { queue.count--; queue.locks.remove(entry.pos); }
                    if (queue.count == 0) QUEUES.remove(level);
                    return false;
                }
                Object original = reservation.original;
                Object source = original instanceof Block || original instanceof DustMultiblockPattern.BlockFamily ? level.getBlockState(reservation.pos)
                    : original instanceof BlockState || original instanceof DustMultiblockPattern.MaterialKind ? original : null;
                completed.add(new Pending(owner, reservation.pos, source, reservation.target, reservation.priority, result, expected));
            }
            queue.entries.addAll(completed);
            return true;
        } catch (RuntimeException | Error failure) {
            for (Reserved reservation : reserved) { queue.count--; queue.locks.remove(reservation.pos); }
            if (queue.count == 0) QUEUES.remove(level);
            throw failure;
        }
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        requireThread(level); Queue queue = QUEUES.get(level); if (queue == null || queue.entries.isEmpty()) return;
        int initial = queue.entries.size(); ArrayDeque<Pending> ready = new ArrayDeque<>(), delayed = new ArrayDeque<>();
        for (int i = 0; i < initial; i++) { Pending pending = queue.entries.remove(); (pending.delay.readyOnEnd() ? ready : delayed).add(pending); }
        queue.entries.addAll(delayed);
        for (Pending pending : ready) {
            // Source clears ToolEvents block lock after enqueuing its same-END swap.
            queue.count--; queue.locks.remove(pending.pos);
            try { execute(level, pending); }
            catch (RuntimeException addonFailure) { Thaumcraft.log.warn("Multiblock dust swap failed at {}", pending.pos, addonFailure); }
        }
        if (queue.count == 0) QUEUES.remove(level);
    }
    private static void execute(ServerLevel level, Pending pending) {
        if (!valid(level, pending)) return;
        if (EventHooks.onBlockPlace(pending.owner, BlockSnapshot.create(level.dimension(), level, pending.pos), Direction.UP)) return;
        if (!valid(level, pending)) return;
        boolean changed=level.setBlock(pending.pos, pending.target.state(), Block.UPDATE_ALL);
        if (changed && !pending.target.drop().isEmpty()) {
            EntitySpecialItem drop = new EntitySpecialItem(level, pending.pos.getX() + .5, pending.pos.getY() + .1, pending.pos.getZ() + .5, pending.target.drop().copy());
            drop.setDeltaMovement(Vec3.ZERO); level.addFreshEntity(drop);
        }
        if(changed)SourceFX.bamf(level,Vec3.atCenterOf(pending.pos),-9999,true,false,null);
    }
    private static boolean valid(ServerLevel level, Pending pending) {
        if (!sameOwner(level, pending.owner) || !level.hasChunkAt(pending.pos) || level.isOutsideBuildHeight(pending.pos)
            || !DustConversion.isSupportedOutput(pending.receipt) || !ItemStack.matches(pending.receipt, pending.expected)
            || !level.mayInteract(pending.owner, pending.pos)) return false;
        BlockState state = level.getBlockState(pending.pos);
        if (state.getDestroySpeed(level, pending.pos) < 0F || !DustMultiblockPattern.matchesSource(state, pending.source)) return false;
        // Source skips the same item+metadata replacement. AIR has an empty source stack and is
        // still a removal, so don't infer a false same-item skip for an AIR target.
        return pending.target.state().isAir() || state != pending.target.state() || !pending.target.drop().isEmpty();
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Queue queue = QUEUES.get(level); if (queue != null && queue.locks.containsKey(event.getPos())) event.setCanceled(true);
        }
    }
    private static boolean sameOwner(ServerLevel level, ServerPlayer owner) {
        return owner != null && owner.level() == level && owner.getServer() == level.getServer() && !owner.isRemoved() && owner.isAlive();
    }
    private static void requireThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Multiblock dust conversion requires owning server thread");
    }
    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel level) QUEUES.remove(level); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { QUEUES.keySet().removeIf(level -> level.getServer() == event.getServer()); }
    public static int pendingCount(ServerLevel level) { requireThread(level); Queue queue = QUEUES.get(level); return queue == null ? 0 : queue.count; }
    public static boolean isBlocked(ServerLevel level, BlockPos pos) { requireThread(level); Queue queue = QUEUES.get(level); return queue != null && queue.locks.containsKey(pos); }
    private static final class Queue { final ArrayDeque<Pending> entries = new ArrayDeque<>(); final Map<BlockPos, Boolean> locks = new HashMap<>(); int count; }
    private record Reserved(BlockPos pos, Object original, DustTriggerMultiblock.Target target, int priority) {}
    private static final class Pending {
        final ServerPlayer owner; final BlockPos pos; final Object source; final DustTriggerMultiblock.Target target;
        final ItemStack receipt, expected; final DustConversionRules.Countdown delay;
        Pending(ServerPlayer owner, BlockPos pos, Object source, DustTriggerMultiblock.Target target, int priority, ItemStack receipt, ItemStack expected) {
            this.owner = owner; this.pos = pos; this.source = source; this.target = target; this.receipt = receipt; this.expected = expected;
            delay = new DustConversionRules.Countdown(priority);
        }
    }
}
