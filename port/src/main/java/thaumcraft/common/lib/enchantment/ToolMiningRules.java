package thaumcraft.common.lib.enchantment;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Original greedy, strictly-outward burrowing search; NOT flood-fill/tree vein mining.
 * Source returns immediately at the first bounding-box violation. This iterative form
 * keeps that order and behavior without source's shared static cursor/recursive stack. */
public final class ToolMiningRules {
    private ToolMiningRules() {}
    public static BlockPos furthest(BlockPos origin, int reach, Predicate<BlockPos> valid) {
        BlockPos cursor = origin; double distance = 0;
        search: while (true) {
            for (int x = -reach; x <= reach; x++) for (int y = reach; y >= -reach; y--) for (int z = -reach; z <= reach; z++) {
                BlockPos next = cursor.offset(x, y, z);
                if (Math.abs(next.getX() - origin.getX()) > 24 || Math.abs(next.getY() - origin.getY()) > 48
                    || Math.abs(next.getZ() - origin.getZ()) > 24) return cursor;
                double candidateDistance = next.distSqr(origin);
                if (candidateDistance > distance && valid.test(next)) { cursor = next; distance = candidateDistance; continue search; }
            }
            return cursor;
        }
    }
    public static BlockPos destructiveOffset(BlockPos origin, Direction face, int first, int second) {
        return switch (face.getAxis()) {
            case Y -> origin.offset(first, 0, second);
            case Z -> origin.offset(first, second, 0);
            case X -> origin.offset(0, second, first);
        };
    }
    public static float refiningChance(int rank) { return (1 + rank) * .125F; }
    public static Direction fallbackFace(BlockPos pos, double playerX, double eyeY, double playerZ, Direction horizontal) {
        if (Math.abs(playerX - ((float)pos.getX() + .5F)) < 2 && Math.abs(playerZ - ((float)pos.getZ() + .5F)) < 2) {
            if (eyeY - pos.getY() > 2) return Direction.UP;
            if (pos.getY() - eyeY > 0) return Direction.DOWN;
        }
        return horizontal.getOpposite();
    }
    public static int soundingRange(int rank) { return 4 + rank * 4; }
}
