package thaumcraft.common.golems.ai;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.Target;
import net.minecraft.world.phys.AABB;

/** BETA26 ground evaluator, including its deliberately non-vanilla diagonal rule. */
public class GolemNodeProcessor extends NodeEvaluator {
    private float avoidsWater;

    @Override public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        avoidsWater = mob.getPathfindingMalus(PathType.WATER);
    }
    @Override public void done() {
        mob.setPathfindingMalus(PathType.WATER, avoidsWater);
        super.done();
    }
    @Override public Node getStart() {
        int y;
        if (canFloat() && mob.isInWater()) {
            y = Mth.floor(mob.getBoundingBox().minY);
            while (y < currentContext.level().getMaxBuildHeight()
                    && currentContext.getBlockState(new BlockPos(mob.getBlockX(), y, mob.getBlockZ())).getFluidState().is(FluidTags.WATER)) y++;
        } else if (mob.onGround()) {
            y = Mth.floor(mob.getBoundingBox().minY + 0.5);
        } else {
            BlockPos pos = BlockPos.containing(mob.getX(), mob.getY(), mob.getZ());
            while (pos.getY() > currentContext.level().getMinBuildHeight()
                    && (currentContext.getBlockState(pos).isAir() || currentContext.getBlockState(pos).isPathfindable(PathComputationType.LAND))) pos = pos.below();
            y = pos.above().getY();
        }
        BlockPos center = new BlockPos(mob.getBlockX(), y, mob.getBlockZ());
        if (mob.getPathfindingMalus(getPathTypeOfMob(currentContext, center.getX(), y, center.getZ(), mob)) < 0) {
            AABB bounds = mob.getBoundingBox();
            BlockPos[] corners = {BlockPos.containing(bounds.minX, y, bounds.minZ), BlockPos.containing(bounds.minX, y, bounds.maxZ),
                    BlockPos.containing(bounds.maxX, y, bounds.minZ), BlockPos.containing(bounds.maxX, y, bounds.maxZ)};
            for (BlockPos corner : corners) {
                if (mob.getPathfindingMalus(getPathTypeOfMob(currentContext, corner.getX(), y, corner.getZ(), mob)) >= 0) return getNode(corner);
            }
        }
        return getNode(center);
    }
    @Override public Target getTarget(double x, double y, double z) {
        return getTargetNodeAt(x - mob.getBbWidth() / 2.0F, y, z - mob.getBbWidth() / 2.0F);
    }
    @Override public int getNeighbors(Node[] output, Node node) {
        int step = mob.getPathfindingMalus(type(node.x, node.y + 1, node.z)) >= 0 ? Mth.floor(Math.max(1, mob.maxUpStep())) : 0;
        double floor = floor(node.x, node.y, node.z);
        Node south = safe(node.x, node.y, node.z + 1, step, floor, Direction.SOUTH);
        Node west = safe(node.x - 1, node.y, node.z, step, floor, Direction.WEST);
        Node east = safe(node.x + 1, node.y, node.z, step, floor, Direction.EAST);
        Node north = safe(node.x, node.y, node.z - 1, step, floor, Direction.NORTH);
        int count = 0;
        for (Node neighbor : new Node[]{south, west, east, north}) if (neighbor != null && !neighbor.closed) output[count++] = neighbor;
        if (diagonalSide(north) && diagonalSide(west)) count = append(output, count, safe(node.x - 1, node.y, node.z - 1, step, floor, Direction.NORTH));
        if (diagonalSide(north) && diagonalSide(east)) count = append(output, count, safe(node.x + 1, node.y, node.z - 1, step, floor, Direction.NORTH));
        if (diagonalSide(south) && diagonalSide(west)) count = append(output, count, safe(node.x - 1, node.y, node.z + 1, step, floor, Direction.SOUTH));
        if (diagonalSide(south) && diagonalSide(east)) count = append(output, count, safe(node.x + 1, node.y, node.z + 1, step, floor, Direction.SOUTH));
        return count;
    }
    private static boolean diagonalSide(Node node) { return node == null || node.type == PathType.OPEN || node.costMalus != 0; }
    private static int append(Node[] nodes, int count, Node node) {
        if (node != null && !node.closed) nodes[count++] = node;
        return count;
    }
    private PathType type(int x, int y, int z) { return getPathTypeOfMob(currentContext, x, y, z, mob); }
    private double floor(int x, int y, int z) {
        BlockPos below = new BlockPos(x, y - 1, z);
        var shape = currentContext.getBlockState(below).getShape(currentContext.level(), below);
        return y - (1.0 - (shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y)));
    }
    private Node classified(int x, int y, int z, PathType type, float cost) {
        Node node = getNode(x, y, z);
        node.type = type;
        node.costMalus = Math.max(node.costMalus, cost);
        return node;
    }
    private Node safe(int x, int y, int z, int steps, double previousFloor, Direction facing) {
        if (floor(x, y, z) - previousFloor > 1.125) return null;
        PathType type = type(x, y, z);
        float cost = mob.getPathfindingMalus(type);
        double radius = mob.getBbWidth() / 2.0;
        Node node = cost >= 0 ? classified(x, y, z, type, cost) : null;
        if (type == PathType.WALKABLE) return node;
        if (node == null && steps > 0 && type != PathType.FENCE && type != PathType.TRAPDOOR) {
            node = safe(x, y + 1, z, steps - 1, previousFloor, facing);
            if (node != null && (node.type == PathType.OPEN || node.type == PathType.WALKABLE) && mob.getBbWidth() < 1) {
                double xx = x - facing.getStepX() + 0.5;
                double zz = z - facing.getStepZ() + 0.5;
                BlockPos pos = new BlockPos(x, y, z);
                var shape = currentContext.getBlockState(pos).getShape(currentContext.level(), pos);
                double top = shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
                AABB box = new AABB(xx - radius, y + 0.001, zz - radius, xx + radius, y + mob.getBbHeight(), zz + radius)
                        .expandTowards(0, top - 0.002, 0);
                if (!currentContext.level().noCollision(mob, box)) node = null;
            }
        }
        if (type == PathType.OPEN) {
            AABB box = new AABB(x - radius + 0.5, y + 0.001, z - radius + 0.5, x + radius + 0.5, y + mob.getBbHeight(), z + radius + 0.5);
            if (!currentContext.level().noCollision(mob, box)) return null;
            if (mob.getBbWidth() >= 1 && type(x, y - 1, z) == PathType.BLOCKED) return classified(x, y, z, PathType.WALKABLE, cost);
            int fall = 0;
            while (y > currentContext.level().getMinBuildHeight() && type == PathType.OPEN) {
                y--;
                if (fall++ >= mob.getMaxFallDistance()) return null;
                type = type(x, y, z);
                cost = mob.getPathfindingMalus(type);
                if (type != PathType.OPEN && cost >= 0) {
                    node = classified(x, y, z, type, cost);
                    break;
                }
                if (cost < 0) return null;
            }
        }
        return node;
    }
    @Override public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        return volumeType(context, x, y, z, mob, entityWidth, entityHeight, entityDepth, canOpenDoors(), canPassDoors());
    }
    public PathType volumeType(PathfindingContext context, int x, int y, int z, Mob mob, int width, int height, int depth, boolean openDoors, boolean passDoors) {
        EnumSet<PathType> types = EnumSet.noneOf(PathType.class);
        PathType first = PathType.BLOCKED;
        BlockPos origin = context.mobPosition();
        boolean onRail = context.getBlockState(origin).getBlock() instanceof BaseRailBlock || context.getBlockState(origin.below()).getBlock() instanceof BaseRailBlock;
        for (int xx = 0; xx < width; xx++) for (int yy = 0; yy < height; yy++) for (int zz = 0; zz < depth; zz++) {
            PathType type = getPathType(context, x + xx, y + yy, z + zz);
            if (type == PathType.DOOR_WOOD_CLOSED && openDoors && passDoors) type = PathType.WALKABLE;
            if (type == PathType.DOOR_OPEN && !passDoors) type = PathType.BLOCKED;
            if (type == PathType.RAIL && !onRail) type = PathType.FENCE;
            if (xx == 0 && yy == 0 && zz == 0) first = type;
            types.add(type);
        }
        if (types.contains(PathType.FENCE)) return PathType.FENCE;
        PathType chosen = PathType.BLOCKED;
        for (PathType type : types) {
            if (mob.getPathfindingMalus(type) < 0) return type;
            if (mob.getPathfindingMalus(type) >= mob.getPathfindingMalus(chosen)) chosen = type;
        }
        return first == PathType.OPEN && mob.getPathfindingMalus(chosen) == 0 ? PathType.OPEN : chosen;
    }
    @Override public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        PathType type = raw(context, new BlockPos(x, y, z));
        if (type == PathType.OPEN && y > context.level().getMinBuildHeight()) {
            PathType below = raw(context, new BlockPos(x, y - 1, z));
            type = below != PathType.WALKABLE && below != PathType.OPEN && below != PathType.WATER && below != PathType.LAVA ? PathType.WALKABLE : PathType.OPEN;
            if (below == PathType.DAMAGE_FIRE || context.getBlockState(new BlockPos(x, y - 1, z)).is(Blocks.MAGMA_BLOCK)) type = PathType.DAMAGE_FIRE;
            if (below == PathType.DAMAGE_OTHER) type = PathType.DAMAGE_OTHER;
        }
        if (type == PathType.WALKABLE) {
            for (int xx = -1; xx <= 1; xx++) for (int zz = -1; zz <= 1; zz++) if (xx != 0 || zz != 0) {
                BlockState state = context.getBlockState(new BlockPos(x + xx, y, z + zz));
                if (state.is(Blocks.CACTUS)) type = PathType.DANGER_OTHER;
                else if (state.is(BlockTags.FIRE)) type = PathType.DANGER_FIRE;
            }
        }
        return type;
    }
    private static PathType raw(PathfindingContext context, BlockPos pos) {
        BlockState state = context.getBlockState(pos);
        if (state.isAir()) return PathType.OPEN;
        if (state.getBlock() instanceof TrapDoorBlock || state.is(Blocks.LILY_PAD)) return PathType.TRAPDOOR;
        if (state.is(BlockTags.FIRE)) return PathType.DAMAGE_FIRE;
        if (state.is(Blocks.CACTUS)) return PathType.DAMAGE_OTHER; // Native removed the dedicated CACTUS path type.
        if (state.getBlock() instanceof DoorBlock door) {
            if (state.getValue(DoorBlock.OPEN)) return PathType.DOOR_OPEN;
            return door.type().canOpenByHand() ? PathType.DOOR_WOOD_CLOSED : PathType.DOOR_IRON_CLOSED;
        }
        if (state.getBlock() instanceof BaseRailBlock) return PathType.RAIL;
        if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock
                || state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) return PathType.FENCE;
        if (state.getFluidState().is(FluidTags.WATER)) return PathType.WATER;
        if (state.getFluidState().is(FluidTags.LAVA)) return PathType.LAVA;
        return state.isPathfindable(PathComputationType.LAND) ? PathType.OPEN : PathType.BLOCKED;
    }
}
