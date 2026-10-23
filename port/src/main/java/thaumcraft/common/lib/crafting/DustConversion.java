package thaumcraft.common.lib.crafting;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.EntitySpecialItem;
import thaumcraft.common.lib.fx.SourceFX;

/** Source DustTriggerSimple/Ore paid swap path, including cauldron -> crucible. Payment is
 * done by ItemMagicDust; crafting callback is immediate and source state is captured after it.
 * Delay50 is a real level END countdown, mutation END51. Source fixed-UP placement cancellation
 * leaves both payment and crafting receipt intact. No source-block drops/water preservation.
 * Bounded queues, live-owner/chunk/thread checks, post-listener state checks, and rejecting a
 * rewritten output are deliberate modern safety adaptations, not original guarantees.
 * Successful source changes emit the genuine default-color sound+flair BAMF through the sided FX bridge.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class DustConversion {
    public static final int MAX_PENDING_PER_LEVEL = 4096;
    private static final Map<ServerLevel, Queue> QUEUES = new WeakHashMap<>();
    private DustConversion() {}

    public static boolean canSchedule(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        requireThread(level);
        Queue queue = QUEUES.get(level);
        return sameOwner(level, owner) && level.hasChunkAt(pos) && (queue == null || queue.count < MAX_PENDING_PER_LEVEL);
    }

    /** Trusted item-use/API seam only, no request-supplied owner or position. Dust was paid. */
    public static boolean schedulePaid(ServerLevel level, ServerPlayer owner, BlockPos pos, ItemStack output) {
        if (!canSchedule(level, owner, pos) || !isSupportedOutput(output)) return false;
        Queue queue = QUEUES.computeIfAbsent(level, ignored -> new Queue());
        queue.count++; // Reserve BEFORE reentrant listeners.
        try {
            ItemStack result = output.copy();
            ItemStack expected = result.copy();
            EventHooks.firePlayerCraftingEvent(owner, result, new SimpleContainer(1));
            queue.entries.add(new Pending(owner, pos.immutable(), level.getBlockState(pos), result, expected));
            return true;
        } catch (RuntimeException | Error failure) { queue.count--; throw failure; }
    }

    public static boolean isSupportedOutput(ItemStack output) {
        return !output.isEmpty() && output.getCount() > 0 && output.getCount() <= output.getMaxStackSize()
            && (!(output.getItem() instanceof BlockItem) || output.getCount() == 1);
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        requireThread(level);
        Queue queue = QUEUES.get(level); if (queue == null || queue.entries.isEmpty()) return;
        int initial = queue.entries.size();
        ArrayDeque<Pending> ready = new ArrayDeque<>(), delayed = new ArrayDeque<>();
        for (int i = 0; i < initial; i++) {
            Pending pending = queue.entries.remove();
            (pending.delay.readyOnEnd() ? ready : delayed).add(pending);
        }
        queue.entries.addAll(delayed);
        for (Pending pending : ready) { queue.count--; execute(level, pending); }
        if (queue.count == 0) QUEUES.remove(level);
    }

    private static void execute(ServerLevel level, Pending pending) {
        if (!valid(level, pending)) return;
        if (EventHooks.onBlockPlace(pending.owner, BlockSnapshot.create(level.dimension(), level, pending.pos), Direction.UP)) return;
        if (!valid(level, pending)) return;
        if (pending.result.getItem() instanceof BlockItem item) {
            BlockState target = item.getBlock().defaultBlockState();
            var properties = pending.result.get(DataComponents.BLOCK_STATE);
            if (properties != null) target = properties.apply(target);
            if(level.setBlock(pending.pos, target, Block.UPDATE_ALL))SourceFX.bamf(level,Vec3.atCenterOf(pending.pos),-9999,true,true,null);
        } else if (level.setBlock(pending.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
            EntitySpecialItem drop = new EntitySpecialItem(level, pending.pos.getX() + .5D,
                pending.pos.getY() + .1D, pending.pos.getZ() + .5D, pending.result.copy());
            drop.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(drop);
            SourceFX.bamf(level,Vec3.atCenterOf(pending.pos),-9999,true,true,null);
        }
    }

    private static boolean valid(ServerLevel level, Pending pending) {
        if (!isSupportedOutput(pending.result) || !ItemStack.matches(pending.result, pending.expected)
            || !sameOwner(level, pending.owner) || !level.hasChunkAt(pending.pos)
            || level.getBlockState(pending.pos) != pending.source || pending.source.getDestroySpeed(level, pending.pos) < 0F
            || !level.mayInteract(pending.owner, pending.pos)) return false;
        // Source ignores same-item replacements, independently of result count.
        return !(pending.result.getItem() instanceof BlockItem item && item.getBlock() == pending.source.getBlock());
    }
    private static boolean sameOwner(ServerLevel level, ServerPlayer owner) {
        return owner != null && owner.level() == level && owner.getServer() == level.getServer() && !owner.isRemoved() && owner.isAlive();
    }
    private static void requireThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Dust conversion requires owning server thread");
    }
    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel level) QUEUES.remove(level); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { QUEUES.keySet().removeIf(level -> level.getServer() == event.getServer()); }
    public static int pendingCount(ServerLevel level) {
        requireThread(level); Queue queue = QUEUES.get(level); return queue == null ? 0 : queue.count;
    }
    private static final class Queue { final ArrayDeque<Pending> entries = new ArrayDeque<>(); int count; }
    private static final class Pending {
        final ServerPlayer owner;
        final BlockPos pos;
        final BlockState source;
        final ItemStack result, expected;
        final DustConversionRules.Countdown delay = new DustConversionRules.Countdown(DustConversionRules.DELAY_DECREMENTS);
        Pending(ServerPlayer owner, BlockPos pos, BlockState source, ItemStack result, ItemStack expected) {
            this.owner = owner; this.pos = pos; this.source = source; this.result = result; this.expected = expected;
        }
    }
}
