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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.EntitySpecialItem;
import thaumcraft.common.lib.fx.SourceFX;

/**
 * Only the first two source ConfigRecipes dust triggers (vanilla bookshelf, then ore bookshelf).
 * c:bookshelves is the finite modern BlockItem-tag bridge, not the whole legacy OreDictionary.
 * Dust is consumed before the immediate crafting event; state is captured AFTER that event.
 * Source delay50 decrements on fifty real level END events, and executes on event51.
 * Canceled/stale swaps neither refund dust nor undo the already emitted crafting event.
 * Safety adaptations: cap4096 pending before consumption, no implicit chunk loading, invalid
 * removed/dead/dimension-changed owner rejection, and state/permission recheck after place listeners.
 * A listener-rewritten output other than exactly one registered book is unsupported/fail-closed;
 * its listener effects/consumed dust remain, rather than inventing the full source block-swap branch.
 * NeoForge EntityPlaceEvent has no legacy hand field; Direction.UP remains the source fixed side.
 * The stage-5 IDustTrigger dispatcher delegates these two source entries here without changing
 * their accepted stage-4 timer/callback semantics. DustConversion handles other simple outputs.
 * After an approved successful change/drop, genuine source default-color sound+flair BAMF is emitted.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class BookshelfDustConversion {
    public static final int DELAY_DECREMENTS = 50;
    public static final int MAX_PENDING_PER_LEVEL = 4096;
    private static final Map<ServerLevel, Queue> QUEUES = new WeakHashMap<>();

    private BookshelfDustConversion() {}

    public static boolean canConvert(Level level, Player player, BlockPos pos) {
        if (player == null || !level.hasChunkAt(pos) || !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("!gotdream")) return false;
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.BOOKSHELF)) return true; // Source simple trigger wins before ore trigger.
        var item = state.getBlock().asItem();
        return item instanceof BlockItem && new ItemStack(item).is(Tags.Items.BOOKSHELVES);
    }

    /** Trusted Item.use-first seam, never a C2S request supplied position/owner. */
    public static boolean schedule(ServerLevel level, ServerPlayer owner, BlockPos pos, InteractionHand hand) {
        requireThread(level);
        if (!canSchedule(level, owner, pos)
            || !owner.getItemInHand(hand).is(ItemsTC.salisMundus)) return false;
        if (!owner.getAbilities().instabuild) owner.getItemInHand(hand).shrink(1);
        return schedulePaid(level, owner, pos);
    }

    public static boolean canSchedule(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        requireThread(level);
        Queue queue = QUEUES.get(level);
        return sameOwner(level, owner) && canConvert(level, owner, pos) && (queue == null || queue.count < MAX_PENDING_PER_LEVEL);
    }

    /** Dust was consumed by the ordered ItemMagicDust trigger dispatcher. */
    public static boolean schedulePaid(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        if (!canSchedule(level, owner, pos)) return false;
        Queue queue = QUEUES.computeIfAbsent(level, ignored -> new Queue());
        // Reserve before callbacks so a reentrant listener cannot overrun the bounded queue.
        queue.count++;
        try {
            ItemStack result = new ItemStack(ItemsTC.thaumonomicon);
            EventHooks.firePlayerCraftingEvent(owner, result, new SimpleContainer(1));
            queue.entries.add(new Pending(owner, pos.immutable(), level.getBlockState(pos), result));
            return true;
        } catch (RuntimeException | Error failure) {
            queue.count--;
            throw failure; // Do not silently refund/cancel listener-owned side effects.
        }
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        requireThread(level);
        Queue queue = QUEUES.get(level);
        if (queue == null || queue.entries.isEmpty()) return;
        // Source processes its complete runnable queue BEFORE its block-swap queue.
        // In particular a place listener's new dust use must not decrement in this END.
        int initialEntries = queue.entries.size();
        ArrayDeque<Pending> delayed = new ArrayDeque<>(), ready = new ArrayDeque<>();
        for (int index = 0; index < initialEntries; index++) {
            Pending pending = queue.entries.remove();
            if (pending.delay > 0) { pending.delay--; delayed.add(pending); }
            else ready.add(pending);
        }
        // Delayed original runnables precede new runnables appended by place listeners.
        queue.entries.addAll(delayed);
        for (Pending pending : ready) { queue.count--; execute(level, pending); }
        if (queue.count == 0) QUEUES.remove(level);
    }

    private static void execute(ServerLevel level, Pending pending) {
        if (!valid(level, pending)) return;
        // Source invokes place on the OLD state before mutation, always UP, regardless clicked side/hand.
        if (EventHooks.onBlockPlace(pending.owner, BlockSnapshot.create(level.dimension(), level, pending.pos), Direction.UP)) return;
        if (!valid(level, pending)) return; // Retain listener changes instead of overwriting a new block/owner context.
        if (!level.setBlock(pending.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) return;
        EntitySpecialItem book = new EntitySpecialItem(level, pending.pos.getX() + .5D, pending.pos.getY() + .1D,
            pending.pos.getZ() + .5D, pending.result.copy());
        book.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(book); // No shelf drops/pickup-inventory insertion or owner restriction in source.
        SourceFX.bamf(level,Vec3.atCenterOf(pending.pos),-9999,true,true,null);
    }

    private static boolean valid(ServerLevel level, Pending pending) {
        return pending.result.is(ItemsTC.thaumonomicon) && pending.result.getCount() == 1
            && sameOwner(level, pending.owner) && level.hasChunkAt(pending.pos)
            && level.getBlockState(pending.pos) == pending.source
            && pending.source.getDestroySpeed(level, pending.pos) >= 0F && level.mayInteract(pending.owner, pending.pos);
    }
    private static boolean sameOwner(ServerLevel level, ServerPlayer owner) {
        return owner != null && owner.level() == level && owner.getServer() == level.getServer()
            && !owner.isRemoved() && owner.isAlive();
    }
    private static void requireThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Dust conversion requires the owning server thread");
    }

    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) QUEUES.remove(level);
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        QUEUES.keySet().removeIf(level -> level.getServer() == event.getServer());
    }

    /** Read-only diagnostic for bounded queue tests; cannot advance/inject a timer. */
    public static int pendingCount(ServerLevel level) {
        requireThread(level);
        Queue queue = QUEUES.get(level);
        return queue == null ? 0 : queue.count;
    }
    private static final class Queue {
        final ArrayDeque<Pending> entries = new ArrayDeque<>();
        int count;
    }
    private static final class Pending {
        final ServerPlayer owner;
        final BlockPos pos;
        final BlockState source;
        final ItemStack result;
        int delay = DELAY_DECREMENTS;
        Pending(ServerPlayer owner, BlockPos pos, BlockState source, ItemStack result) {
            this.owner = owner; this.pos = pos; this.source = source; this.result = result;
        }
    }
}
