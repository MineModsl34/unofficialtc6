package thaumcraft.common.lib.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.api.items.ItemsTC;

/** Original ConfigRecipes.initializeCompoundRecipes first five registrations, in source order.
 * Actual furnace/infusion/thaumatorium/golem-press adapters follow in their own source owners;
 * no trigger registers a nonexistent output or a decorative stand-in.
 */
public final class DustTriggers {
    public static final int MAX_TRIGGERS_PER_USE = 4096;
    private static boolean initialized;
    private DustTriggers() {}

    /** Common setup hook; lazy item-use fallback permits existing stage-4 launch fixtures. */
    public static synchronized void initialize() {
        if (initialized) return;
        List<IDustTrigger> defaults = List.of(
            simple("!gotdream", Blocks.BOOKSHELF, () -> ItemsTC.thaumonomicon == null ? ItemStack.EMPTY : new ItemStack(ItemsTC.thaumonomicon), Kind.BOOK),
            ore("!gotdream", "bookshelf", () -> ItemsTC.thaumonomicon == null ? ItemStack.EMPTY : new ItemStack(ItemsTC.thaumonomicon), Kind.BOOK),
            simple("FIRSTSTEPS@1", Blocks.CRAFTING_TABLE, () -> BlocksTC.arcaneWorkbench == null ? ItemStack.EMPTY : new ItemStack(BlocksTC.arcaneWorkbench), Kind.WORKBENCH),
            ore("FIRSTSTEPS@1", "workbench", () -> BlocksTC.arcaneWorkbench == null ? ItemStack.EMPTY : new ItemStack(BlocksTC.arcaneWorkbench), Kind.WORKBENCH),
            new DustTriggerSimple("UNLOCKALCHEMY@1", Blocks.CAULDRON,
                () -> BlocksTC.crucible == null ? ItemStack.EMPTY : new ItemStack(BlocksTC.crucible)));
        // Builtin source registrations precede addon registrations even with lazy initialization.
        IDustTrigger.triggers.addAll(0, defaults);
        initialized = true;
        InfusionDustTriggers.initializeInfusion();
        for (var blueprint : InfusionDustTriggers.blueprints()) thaumcraft.api.ThaumcraftApi.addMultiblockRecipeToCatalog(blueprint.id(),
            new thaumcraft.api.ThaumcraftApi.BluePrint(blueprint.research(),blueprint.parts(),blueprint.ingredients().toArray(ItemStack[]::new)));
        FutureDustTriggers.initializeFuture();
    }

    public static Match match(Level world, Player player, BlockPos pos, Direction face) {
        initialize();
        // Snapshot: a trigger's callback may register another trigger, but it cannot join this use.
        int bound = Math.min(MAX_TRIGGERS_PER_USE, IDustTrigger.triggers.size());
        return firstMatch(new ArrayList<>(IDustTrigger.triggers.subList(0, bound)), trigger -> trigger.getValidFace(world, player, pos, face));
    }

    /** Shared, engine-independent ordered dispatch seam for focused unit coverage. */
    static Match firstMatch(List<IDustTrigger> triggers, Function<IDustTrigger, IDustTrigger.Placement> matcher) {
        int bound = Math.min(MAX_TRIGGERS_PER_USE, triggers.size());
        for (int i = 0; i < bound; i++) {
            IDustTrigger trigger = triggers.get(i);
            if (trigger == null) continue;
            IDustTrigger.Placement placement = matcher.apply(trigger);
            if (placement != null) return new Match(trigger, placement);
        }
        return null;
    }
    public record Match(IDustTrigger trigger, IDustTrigger.Placement placement) {}

    private enum Kind { BOOK, WORKBENCH }
    private static boolean canExecute(Kind kind, Level world, Player player, BlockPos pos, Supplier<ItemStack> output) {
        if (output.get().isEmpty()) return false;
        if (!(world instanceof ServerLevel level)) return true;
        if (!(player instanceof ServerPlayer owner)) return false;
        return kind == Kind.BOOK ? BookshelfDustConversion.canSchedule(level, owner, pos) : WorkbenchDustConversion.canSchedule(level, owner, pos);
    }
    private static void execute(Kind kind, Level world, Player player, BlockPos pos) {
        if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer owner)) return;
        if (kind == Kind.BOOK) BookshelfDustConversion.schedulePaid(level, owner, pos);
        else WorkbenchDustConversion.schedulePaid(level, owner, pos);
    }
    private static IDustTrigger simple(String research, Block block, Supplier<ItemStack> output, Kind kind) {
        return new DustTriggerSimple(research, block, output) {
            @Override public boolean canExecute(Level world, Player player, BlockPos pos, Placement placement) {
                return DustTriggers.canExecute(kind, world, player, pos, output);
            }
            @Override public void execute(Level world, Player player, BlockPos pos, Placement placement, Direction side) {
                DustTriggers.execute(kind, world, player, pos);
            }
        };
    }
    private static IDustTrigger ore(String research, String tag, Supplier<ItemStack> output, Kind kind) {
        return new DustTriggerOre(research, DustTriggerOre.legacyTag(tag), output) {
            @Override public boolean canExecute(Level world, Player player, BlockPos pos, Placement placement) {
                return DustTriggers.canExecute(kind, world, player, pos, output);
            }
            @Override public void execute(Level world, Player player, BlockPos pos, Placement placement, Direction side) {
                DustTriggers.execute(kind, world, player, pos);
            }
        };
    }
}
