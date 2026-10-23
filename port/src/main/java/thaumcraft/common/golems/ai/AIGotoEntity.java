package thaumcraft.common.golems.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.tasks.TaskHandler;

public class AIGotoEntity extends AIGoto {
    public AIGotoEntity(EntityThaumcraftGolem golem) { super(golem, (byte) 1); }
    @Override public void tick() {
        super.tick();
        if (golem.getTask() != null && golem.getTask().getEntity() != null)
            golem.getLookControl().setLookAt(golem.getTask().getEntity(), 10, golem.getMaxHeadXRot());
    }
    @Override protected void moveTo() {
        if (golem.getTask() != null && golem.getTask().getEntity() != null)
            golem.getNavigation().moveTo(golem.getTask().getEntity(), golem.getGolemMoveSpeed());
    }
    @Override protected boolean findDestination() {
        for (Task ticket : TaskHandler.getEntityTasksSorted(golem.level().dimension(), golem.getUUID(), golem)) {
            if (areGolemTagsValidForTask(ticket) && ticket.canGolemPerformTask(golem) && golem.isWithinRestriction(ticket.getPos())
                    && isValidDestination(golem.level(), ticket.getPos()) && canEasilyReach(ticket.getEntity())) {
                golem.setTask(ticket);
                ticket.setReserved(true);
                double radius = ticket.getEntity().getBbWidth() / 2.0F;
                minDist = 3.5 + radius * radius;
                if (ModConfig.CONFIG_GRAPHICS.showGolemEmotes) golem.level().broadcastEntityEvent(golem, (byte) 5);
                return true;
            }
        }
        return false;
    }
    private boolean canEasilyReach(Entity entity) {
        if (golem.distanceToSqr(entity) < minDist) return true;
        var path = golem.getNavigation().createPath(entity, 0);
        if (path == null || path.getEndNode() == null) return false;
        var node = path.getEndNode();
        int x = node.x - Mth.floor(entity.getX()), z = node.z - Mth.floor(entity.getZ());
        return x * x + z * z < minDist;
    }
}
