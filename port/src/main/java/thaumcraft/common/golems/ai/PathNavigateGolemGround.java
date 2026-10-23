package thaumcraft.common.golems.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.phys.Vec3;

public class PathNavigateGolemGround extends GroundPathNavigation {
    public PathNavigateGolemGround(Mob mob, Level world) { super(mob, world); }
    @Override protected PathFinder createPathFinder(int maxNodes) {
        nodeEvaluator = new GolemNodeProcessor();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, maxNodes);
    }
    @Override protected boolean canUpdatePath() { return mob.onGround() || canFloat() && mob.isInLiquid() || mob.isPassenger(); }
    // Native GroundPathNavigation's target redirection, sixteen-block water scan and cauldron
    // node adjustment are the same source routines, now bounded by the world's build height.
    @Override protected boolean canMoveDirectly(Vec3 from, Vec3 to) {
        int width = Mth.ceil(mob.getBbWidth()), height = Mth.ceil(mob.getBbHeight()), depth = width;
        int x = Mth.floor(from.x), z = Mth.floor(from.z);
        double dx = to.x - from.x, dz = to.z - from.z;
        double squared = dx * dx + dz * dz;
        if (squared < 1.0E-8) return false;
        double norm = 1 / Math.sqrt(squared);
        dx *= norm;
        dz *= norm;
        if (!safeStand(x, (int) from.y, z, width + 2, height, depth + 2, from, dx, dz)) return false;
        double stepX = 1 / Math.abs(dx), stepZ = 1 / Math.abs(dz);
        double nextX = (x - from.x + (dx >= 0 ? 1 : 0)) / dx;
        double nextZ = (z - from.z + (dz >= 0 ? 1 : 0)) / dz;
        int signX = dx < 0 ? -1 : 1, signZ = dz < 0 ? -1 : 1;
        int endX = Mth.floor(to.x), endZ = Mth.floor(to.z);
        while ((endX - x) * signX > 0 || (endZ - z) * signZ > 0) {
            if (nextX < nextZ) { nextX += stepX; x += signX; }
            else { nextZ += stepZ; z += signZ; }
            if (!safeStand(x, (int) from.y, z, width, height, depth, from, dx, dz)) return false;
        }
        return true;
    }
    private boolean safeStand(int x, int y, int z, int width, int height, int depth, Vec3 from, double dx, double dz) {
        int minX = x - width / 2, minZ = z - depth / 2;
        for (BlockPos pos : BlockPos.betweenClosed(minX, y, minZ, minX + width - 1, y + height - 1, minZ + depth - 1)) {
            if (!level.hasChunkAt(pos)) return false;
            if ((pos.getX() + 0.5 - from.x) * dx + (pos.getZ() + 0.5 - from.z) * dz >= 0
                    && !level.getBlockState(pos).isPathfindable(PathComputationType.LAND)) return false;
        }
        PathfindingContext context = new PathfindingContext(level, mob);
        GolemNodeProcessor evaluator = (GolemNodeProcessor) nodeEvaluator;
        for (int xx = minX; xx < minX + width; xx++) for (int zz = minZ; zz < minZ + depth; zz++) {
            if ((xx + 0.5 - from.x) * dx + (zz + 0.5 - from.z) * dz < 0) continue;
            PathType below = evaluator.volumeType(context, xx, y - 1, zz, mob, width, height, depth, true, true);
            if (below == PathType.WATER || below == PathType.LAVA || below == PathType.OPEN) return false;
            PathType above = evaluator.volumeType(context, xx, y, zz, mob, width, height, depth, true, true);
            float cost = mob.getPathfindingMalus(above);
            if (cost < 0 || cost >= 8 || above == PathType.DAMAGE_FIRE || above == PathType.DANGER_FIRE || above == PathType.DAMAGE_OTHER) return false;
        }
        return true;
    }
    public void setEnterDoors(boolean value) { nodeEvaluator.setCanPassDoors(value); }
    public boolean getEnterDoors() { return nodeEvaluator.canPassDoors(); }
    public void setCanSwim(boolean value) { setCanFloat(value); }
    public boolean getCanSwim() { return canFloat(); }
    @Override public void setAvoidSun(boolean ignored) { /* Source compatibility: deliberately has no effect. */ }
}
