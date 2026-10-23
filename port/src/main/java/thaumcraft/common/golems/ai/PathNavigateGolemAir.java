package thaumcraft.common.golems.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class PathNavigateGolemAir extends PathNavigation {
    public PathNavigateGolemAir(Mob mob, Level world) { super(mob, world); }
    @Override protected PathFinder createPathFinder(int maxNodes) {
        nodeEvaluator = new FlightNodeProcessor();
        return new PathFinder(nodeEvaluator, maxNodes);
    }
    @Override protected boolean canUpdatePath() { return true; }
    @Override protected Vec3 getTempMobPos() { return new Vec3(mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ()); }
    @Override protected double getGroundY(Vec3 vec) { return vec.y; }
    @Override public Path createPath(BlockPos pos, int accuracy) {
        return level.hasChunkAt(pos) ? super.createPath(pos, accuracy) : null;
    }
    @Override protected void followThePath() {
        Vec3 position = getTempMobPos();
        if (position.distanceToSqr(path.getNextEntityPos(mob)) < mob.getBbWidth() * mob.getBbWidth()) path.advance();
        int last = Math.min(path.getNextNodeIndex() + 6, path.getNodeCount() - 1);
        for (int index = last; index > path.getNextNodeIndex(); index--) {
            Vec3 target = path.getEntityPosAtNode(mob, index);
            if (target.distanceToSqr(position) <= 36 && canMoveDirectly(position, target)) {
                path.setNextNodeIndex(index);
                break;
            }
        }
        doStuckDetection(position);
    }
    @Override protected boolean canMoveDirectly(Vec3 from, Vec3 to) {
        int minX = Mth.floor(Math.min(from.x, to.x)) >> 4, maxX = Mth.floor(Math.max(from.x, to.x)) >> 4;
        int minZ = Mth.floor(Math.min(from.z, to.z)) >> 4, maxZ = Mth.floor(Math.max(from.z, to.z)) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return isClearForMovementBetween(mob, from, to, false);
    }
}
