package thaumcraft.api.research;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.research.LegacyScanIdentity;

/** TC6 all-match scan dispatch. Registration and gameplay calls are owner-thread operations. */
public class ScanningManager {
    static volatile java.util.List<IScanThing> things = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.Map<Object, java.util.List<IScanThing>> groups = new java.util.IdentityHashMap<>();

    public static synchronized void addScannableThing(IScanThing object) {
        // Source appends without deduplication, including constructor-created ScanItems.
        things.add(object);
    }

    /** Replaces only a lifecycle-owned group; addon append registrations and duplicates survive. */
    public static synchronized void replaceScannableGroup(Object owner, java.util.List<? extends IScanThing> replacement) {
        ArrayList<IScanThing> next = new ArrayList<>(things);
        int insertion = next.size();
        for (IScanThing old : groups.getOrDefault(owner, java.util.List.of())) {
            int index = next.indexOf(old);
            if (index >= 0) { insertion = Math.min(insertion, index); next.remove(index); }
        }
        java.util.List<IScanThing> snapshot = java.util.List.copyOf(replacement);
        next.addAll(Math.min(insertion, next.size()), snapshot);
        groups.put(owner, snapshot);
        things = new java.util.concurrent.CopyOnWriteArrayList<>(next);
    }

    public static void scanTheThing(Player player, Object object) {
        ScanResult result = scanHandlers(player, object, things,
            key -> ThaumcraftApi.internalMethods.progressResearch(player, key));
        if (!result.suppress()) {
            status(player, result.found() ? "tc.knownobject" : "tc.unknownobject",
                result.found() ? ChatFormatting.GREEN : ChatFormatting.DARK_PURPLE);
        }
        if (object instanceof BlockPos pos) {
            IItemHandler handler = player.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
            if (handler != null) {
                scanInventory(handler, stack -> scanTheThing(player, stack),
                    () -> status(player, "tc.invtoolarge", ChatFormatting.DARK_PURPLE));
            }
        }
    }

    private static void status(Player player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(key).withStyle(color, ChatFormatting.ITALIC), true);
    }

    // These small package-private seams exercise the actual dispatch without a fabricated Player.
    static ScanResult scanHandlers(Player player, Object object, Iterable<IScanThing> handlers,
                                   Predicate<String> progress) {
        boolean found = false;
        boolean suppress = false;
        for (IScanThing thing : handlers) {
            // Do not cache getResearchKey: TC6 called it repeatedly, observable by addon handlers.
            if (thing.checkThing(player, object)
                && (thing.getResearchKey(player, object) == null
                    || thing.getResearchKey(player, object).isEmpty()
                    || progress.test(thing.getResearchKey(player, object)))) {
                if (thing.getResearchKey(player, object) == null || thing.getResearchKey(player, object).isEmpty()) {
                    suppress = true;
                }
                found = true;
                thing.onSuccess(player, object);
            }
        }
        return new ScanResult(found, suppress);
    }

    static void scanInventory(IItemHandler handler, Consumer<ItemStack> scan, Runnable tooLarge) {
        int scanned = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack != null && !stack.isEmpty()) {
                scan.accept(stack);
                scanned++;
            }
            if (scanned >= 100) {
                tooLarge.run();
                break;
            }
        }
    }

    public static boolean isThingStillScannable(Player player, Object object) {
        return stillScannable(player, object, things, key -> ThaumcraftCapabilities.knowsResearch(player, key));
    }

    static boolean stillScannable(Player player, Object object, Iterable<IScanThing> handlers,
                                 Predicate<String> known) {
        for (IScanThing thing : handlers) {
            // The source only catches key/knowledge-query exceptions, NOT checkThing exceptions.
            if (thing.checkThing(player, object)) {
                try {
                    if (!known.test(thing.getResearchKey(player, object))) return true;
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    public static ItemStack getItemFromParms(Player player, Object object) {
        ItemStack stack = ItemStack.EMPTY;
        if (object instanceof ItemStack item) stack = item;
        if (object instanceof ItemEntity entity && entity.getItem() != null) stack = entity.getItem();
        if (object instanceof BlockPos pos) {
            BlockState state = player.level().getBlockState(pos);
            // Flattening merged the old no-ItemBlock double TC slab with its half slab.
            // Old getItem AND getPickBlock both yielded EMPTY for these exact six doubles.
            if (LegacyScanIdentity.isLegacyDoubleSlab(state)) return ItemStack.EMPTY;
            // Block.getItem in 1.12 first used its BlockItem, not the pick-block hook.
            stack = new ItemStack(state.getBlock());
            try {
                if (stack == null || stack.isEmpty()) {
                    stack = state.getCloneItemStack(rayTrace(player), player.level(), pos, player);
                }
            } catch (Exception ignored) {}
            try {
                // Source Material.WATER/LAVA means the liquid block, not a waterlogged solid.
                if ((stack == null || stack.isEmpty()) && state.is(Blocks.WATER)) stack = new ItemStack(Items.WATER_BUCKET);
                if ((stack == null || stack.isEmpty()) && state.is(Blocks.LAVA)) stack = new ItemStack(Items.LAVA_BUCKET);
            } catch (Exception ignored) {}
        }
        return stack;
    }

    private static BlockHitResult rayTrace(Player player) {
        Vec3 eye = player.getEyePosition(0.0F);
        Vec3 end = eye.add(player.getViewVector(0.0F).scale(4.0));
        // Old BlockLiquid.canCollideCheck accepted LEVEL == 0 only.
        return player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
    }

    record ScanResult(boolean found, boolean suppress) {}
}
