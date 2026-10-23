package thaumcraft.common.golems.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.tasks.TaskHandler;

public class AIGotoBlock extends AIGoto {
    public AIGotoBlock(EntityThaumcraftGolem golem) { super(golem, (byte) 0); }
    @Override public void tick() {
        super.tick();
        if (golem.getTask() != null) {
            BlockPos pos = golem.getTask().getPos();
            golem.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, golem.getMaxHeadXRot());
        }
    }
    @Override protected void moveTo() {
        if (golem.getTask() == null) return;
        BlockPos pos = targetBlock == null ? golem.getTask().getPos() : targetBlock;
        golem.getNavigation().moveTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, golem.getGolemMoveSpeed());
    }
    @Override protected boolean findDestination() {
        for (Task ticket : TaskHandler.getBlockTasksSorted(golem.level().dimension(), golem.getUUID(), golem)) {
            if (areGolemTagsValidForTask(ticket) && ticket.canGolemPerformTask(golem) && golem.isWithinRestriction(ticket.getPos())
                    && isValidDestination(golem.level(), ticket.getPos()) && canEasilyReach(ticket.getPos())) {
                targetBlock = adjacent(ticket.getPos());
                golem.setTask(ticket);
                ticket.setReserved(true);
                if (ModConfig.CONFIG_GRAPHICS.showGolemEmotes) golem.level().broadcastEntityEvent(golem, (byte) 5);
                return true;
            }
        }
        return false;
    }
    private BlockPos adjacent(BlockPos pos) {
        BlockPos closest = null;
        double best = Double.MAX_VALUE;
        for (Direction face : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = pos.relative(face);
            if (!golem.level().getBlockState(candidate).blocksMotion()) {
                double distance = candidate.distToCenterSqr(golem.position());
                if (distance < best) { best = distance; closest = candidate; }
            }
        }
        return closest;
    }
    private boolean canEasilyReach(BlockPos pos) {
        if (pos.distToCenterSqr(golem.position()) < minDist) return true;
        var path = golem.getNavigation().createPath(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0);
        if (path == null || path.getEndNode() == null) return false;
        var end = path.getEndNode();
        int x = end.x - pos.getX(), y = end.y - pos.getY(), z = end.z - pos.getZ();
        if (x == 0 && z == 0 && y == 2) y--;
        return x * x + y * y + z * z < 2.25;
    }
}
