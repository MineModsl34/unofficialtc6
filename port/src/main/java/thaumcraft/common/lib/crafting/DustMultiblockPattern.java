package thaumcraft.common.lib.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.api.crafting.Part;

/** Read-only source DustTriggerMultiblock pattern/rotation/search; intentionally NOT IDustTrigger.
 * Matching alone must not consume dust or pretend that future infusion/furnace machines work.
 * Modern missing legacy Material/metadata require explicit native state/material descriptors;
 * unknown descriptors fail closed. Top layer is blueprint[0], search includes -size..0 exactly.
 * The finite <=8-axis structure snapshot is a safety adaptation; Part fields remain source-live.
 */
public final class DustMultiblockPattern {
    private static final Direction[] SOURCE_HORIZONTAL_ORDER = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
    private final String research;
    private final Part[][][] blueprint;
    private final int ySize, xSize, zSize;
    private final List<List<RelativeCell>> rotations;

    public enum MaterialKind { AIR, WATER, LAVA }
    /** Explicit native flattening of ONE old Block (cauldron/anvil), never a broad tag.
     * Initial pattern matching ignores its old metadata, like source instanceof Block.
     * Delayed conversion still captures exact native BlockState AFTER craft listeners.
     */
    public record BlockFamily(List<Block> blocks) {
        public BlockFamily {
            blocks = List.copyOf(blocks);
            if (blocks.isEmpty() || blocks.size() > 16) throw new IllegalArgumentException("Empty/oversized source block family");
        }
    }
    public record Cell(BlockPos pos, Part part) {}
    private record RelativeCell(int x, int y, int z, Part part) {}

    public DustMultiblockPattern(String research, Part[][][] input) {
        Objects.requireNonNull(input, "blueprint");
        if (input.length == 0 || input.length > 8 || input[0] == null || input[0].length == 0 || input[0].length > 8
            || input[0][0] == null || input[0][0].length == 0 || input[0][0].length > 8) throw new IllegalArgumentException("Empty/oversized dust blueprint");
        this.research = research;
        ySize = input.length; xSize = input[0].length; zSize = input[0][0].length;
        blueprint = new Part[ySize][xSize][zSize];
        for (int y = 0; y < ySize; y++) {
            if (input[y] == null || input[y].length != xSize) throw new IllegalArgumentException("Ragged dust blueprint");
            for (int x = 0; x < xSize; x++) {
                if (input[y][x] == null || input[y][x].length != zSize) throw new IllegalArgumentException("Ragged dust blueprint");
                System.arraycopy(input[y][x], 0, blueprint[y][x], 0, zSize);
            }
        }
        List<List<RelativeCell>> rotated = new ArrayList<>();
        for (Direction face : SOURCE_HORIZONTAL_ORDER) {
            List<RelativeCell> cells = new ArrayList<>();
            for (int y = 0; y < ySize; y++) {
                Matrix matrix = new Matrix(blueprint[y]);
                matrix.Rotate90DegRight(3 - face.get2DDataValue());
                for (int x = 0; x < matrix.rows; x++) for (int z = 0; z < matrix.cols; z++) {
                    Part part = matrix.matrix[x][z];
                    if (part != null) cells.add(new RelativeCell(x, -y + ySize - 1, z, part));
                }
            }
            rotated.add(List.copyOf(cells));
        }
        rotations = List.copyOf(rotated);
    }

    public IDustTrigger.Placement getValidFace(Level world, Player player, BlockPos pos) {
        if (player == null || research != null && !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(research)) return null;
        return findPlacement(pos, (at, part) -> world.hasChunkAt(at) && matchesSource(world.getBlockState(at), part.getSource()));
    }

    /** Pure geometry seam; current world adapter above performs loaded-state/source matching. */
    public IDustTrigger.Placement findPlacement(BlockPos clicked, BiPredicate<BlockPos, Part> match) {
        for (int yy = -ySize; yy <= 0; yy++) for (int xx = -xSize; xx <= 0; xx++) for (int zz = -zSize; zz <= 0; zz++) {
            BlockPos origin = clicked.offset(xx, yy, zz);
            for (Direction face : SOURCE_HORIZONTAL_ORDER) {
                boolean fits = true;
                for (RelativeCell cell : rotations.get(face.get2DDataValue())) {
                    if (!match.test(origin.offset(cell.x, cell.y, cell.z), cell.part)) { fits = false; break; }
                }
                if (fits) return new IDustTrigger.Placement(xx, yy, zz, face);
            }
        }
        return null;
    }

    public List<Cell> cells(BlockPos clicked, IDustTrigger.Placement placement) {
        return cells(clicked.offset(placement.xOffset, placement.yOffset, placement.zOffset), placement.facing);
    }
    private List<Cell> cells(BlockPos origin, Direction face) {
        if (face == null || face.getAxis().isVertical()) throw new IllegalArgumentException("Dust blueprint needs a horizontal facing");
        List<Cell> cells = new ArrayList<>();
        for (RelativeCell cell : rotations.get(face.get2DDataValue())) cells.add(new Cell(origin.offset(cell.x, cell.y, cell.z), cell.part));
        return List.copyOf(cells);
    }

    public static boolean matchesSource(BlockState state, Object source) {
        if (source == null) return true; // Non-null Part with no source had no source restriction.
        if (source instanceof Block block) return state.is(block);
        if (source instanceof BlockFamily family) return family.blocks().contains(state.getBlock());
        if (source instanceof BlockState expected) return state == expected;
        if (source instanceof ItemStack stack) {
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return false;
            BlockState expected = item.getBlock().defaultBlockState();
            var properties = stack.get(DataComponents.BLOCK_STATE);
            if (properties != null) expected = properties.apply(expected);
            return state == expected; // Explicit modern native state, no guessed old numeric metadata.
        }
        if (source instanceof MaterialKind material) return switch (material) {
            case AIR -> state.isAir();
            case WATER -> state.getBlock() instanceof LiquidBlock && state.getFluidState().is(FluidTags.WATER);
            case LAVA -> state.getBlock() instanceof LiquidBlock && state.getFluidState().is(FluidTags.LAVA);
        };
        return false;
    }
}
