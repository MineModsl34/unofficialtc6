package thaumcraft.common.lib.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.api.crafting.Part;

/** Source executable multiblock dust adapter. Pattern search, absolute ItemStack facing,
 * per-Part priorities and AIR removals are genuine. Unsupported target descriptors fail closed.
 * The source incorrectly reported infernalFurnace for every machine; callers supply an actual
 * crafting receipt (infusionMatrix for all three infusion structures), never a phantom furnace.
 */
public final class DustTriggerMultiblock implements IDustTrigger {
    private final DustMultiblockPattern pattern;
    private final Supplier<ItemStack> receipt;
    public DustTriggerMultiblock(String research, Part[][][] blueprint) {
        this(research, blueprint, () -> firstTarget(blueprint));
    }
    public DustTriggerMultiblock(String research, Part[][][] blueprint, Supplier<ItemStack> receipt) {
        pattern = new DustMultiblockPattern(research, blueprint); this.receipt = Objects.requireNonNull(receipt, "receipt");
    }
    public DustMultiblockPattern pattern() { return pattern; }
    @Override public Placement getValidFace(Level world, Player player, BlockPos pos, Direction face) {
        return pattern.getValidFace(world, player, pos);
    }
    @Override public boolean canExecute(Level world, Player player, BlockPos pos, Placement placement) {
        if (world instanceof ServerLevel server && player instanceof ServerPlayer owner)
            return DustMultiblockConversion.canSchedule(server, owner, pattern, pos, placement, receipt.get());
        return world.isClientSide && player != null && DustConversion.isSupportedOutput(receipt.get()) && targetsSupported(pattern.cells(pos, placement));
    }
    @Override public void execute(Level world, Player player, BlockPos pos, Placement placement, Direction side) {
        if (world instanceof ServerLevel server && player instanceof ServerPlayer owner)
            DustMultiblockConversion.schedulePaid(server, owner, pattern, pos, placement, side, receipt.get());
    }
    @Override public List<BlockPos> sparkle(Level world, Player player, BlockPos pos, Placement placement) {
        List<BlockPos> exposed = new ArrayList<>();
        for (var cell : pattern.cells(pos, placement)) if (cell.part().getSource() != null) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = cell.pos().relative(direction);
                if (world.hasChunkAt(neighbor) && !world.getBlockState(neighbor).isSolidRender(world, neighbor)) { exposed.add(cell.pos()); break; }
            }
        }
        return List.copyOf(exposed);
    }
    static boolean targetsSupported(List<DustMultiblockPattern.Cell> cells) {
        boolean changes = false;
        for (var cell : cells) if (cell.part().getTarget() != null) {
            changes = true;
            if (!targetSupported(cell.part().getTarget()) || cell.part().getPriority() > 12000) return false;
        }
        return changes;
    }
    static boolean targetSupported(Object target) {
        return target instanceof Block || target instanceof BlockState || "AIR".equals(target)
            || target instanceof ItemStack stack && DustConversion.isSupportedOutput(stack);
    }
    /** A source Block target takes structure/player facing; a source ItemStack's explicitly
     * encoded state is absolute and is NOT rotated. The infusion blueprint uses this latter path.
     */
    static Target target(Part part, Direction structure, Direction clicked, Direction ownerFacing) {
        Object object = part.getTarget();
        if (object instanceof Block block) {
            BlockState state = block.defaultBlockState();
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                Direction facing = part.getApplyPlayerFacing() ? (clicked.getAxis().isVertical() ? ownerFacing.getOpposite() : clicked)
                    : part.isOpp() ? structure.getOpposite() : structure;
                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            }
            return new Target(state, ItemStack.EMPTY);
        }
        if (object instanceof BlockState state) return new Target(state, ItemStack.EMPTY); // Explicit native state extension.
        if (object instanceof ItemStack stack && DustConversion.isSupportedOutput(stack)) {
            if (stack.getItem() instanceof BlockItem block) {
                BlockState state = block.getBlock().defaultBlockState();
                var properties = stack.get(DataComponents.BLOCK_STATE); if (properties != null) state = properties.apply(state);
                return new Target(state, ItemStack.EMPTY);
            }
            return new Target(Blocks.AIR.defaultBlockState(), stack.copy());
        }
        if ("AIR".equals(object)) return new Target(Blocks.AIR.defaultBlockState(), ItemStack.EMPTY);
        throw new IllegalArgumentException("Unresolved multiblock target: " + object);
    }
    static ItemStack firstTarget(Part[][][] blueprint) {
        for (Part[][] layer : blueprint) for (Part[] row : layer) for (Part part : row) if (part != null) {
            Object output = part.getTarget();
            if (output instanceof ItemStack stack && DustConversion.isSupportedOutput(stack)) return stack.copy();
            if (output instanceof Block block && block != Blocks.AIR) return new ItemStack(block);
            if (output instanceof BlockState state && !state.isAir()) return new ItemStack(state.getBlock());
        }
        return ItemStack.EMPTY;
    }
    record Target(BlockState state, ItemStack drop) {}
}
