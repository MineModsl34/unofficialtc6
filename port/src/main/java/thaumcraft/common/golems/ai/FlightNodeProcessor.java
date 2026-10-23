package thaumcraft.common.golems.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.Target;

/** Source six-neighbour flight evaluator (not native diagonal flying navigation). */
public class FlightNodeProcessor extends NodeEvaluator {
    @Override public Node getStart() {
        var bounds = mob.getBoundingBox();
        return getNode(BlockPos.containing(bounds.minX, bounds.minY + 0.5, bounds.minZ));
    }
    @Override public Target getTarget(double x, double y, double z) {
        return getTargetNodeAt(x - mob.getBbWidth() / 2.0F, y + 0.5, z - mob.getBbWidth() / 2.0F);
    }
    @Override public int getNeighbors(Node[] output, Node node) {
        int count = 0;
        for (Direction direction : Direction.values()) {
            Node next = safe(node.x + direction.getStepX(), node.y + direction.getStepY(), node.z + direction.getStepZ());
            if (next != null && !next.closed) output[count++] = next;
        }
        return count;
    }
    private Node safe(int x, int y, int z) {
        if (y < currentContext.level().getMinBuildHeight() || y + entityHeight > currentContext.level().getMaxBuildHeight()) return null;
        for (int xx = 0; xx < entityWidth; xx++) for (int yy = 0; yy < entityHeight; yy++) for (int zz = 0; zz < entityDepth; zz++) {
            BlockPos pos = new BlockPos(x + xx, y + yy, z + zz);
            if (!mob.level().hasChunkAt(pos)) return null;
            var state = currentContext.getBlockState(pos);
            if (!state.isAir() && !state.isPathfindable(PathComputationType.LAND)) return null;
        }
        Node node = getNode(x, y, z);
        node.type = PathType.WALKABLE;
        return node;
    }
    @Override public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) { return PathType.WATER; }
    @Override public PathType getPathType(PathfindingContext context, int x, int y, int z) { return PathType.WATER; }
}
