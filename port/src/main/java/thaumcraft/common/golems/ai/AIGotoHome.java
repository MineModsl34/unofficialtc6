package thaumcraft.common.golems.ai;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.golems.EntityThaumcraftGolem;

public class AIGotoHome extends Goal {
    private final EntityThaumcraftGolem golem;
    private int idleCounter = 10;
    private Vec3 movePos;
    public AIGotoHome(EntityThaumcraftGolem golem) { this.golem = golem; setFlags(EnumSet.of(Flag.MOVE)); }
    @Override public boolean canUse() {
        if (idleCounter > 0) { idleCounter--; return false; }
        idleCounter = 50;
        double distance = golem.getRestrictCenter().distToCenterSqr(golem.position());
        if (distance < 5) return false;
        Vec3 home = Vec3.atLowerCornerOf(golem.getRestrictCenter());
        movePos = distance > 1024 ? DefaultRandomPos.getPosTowards(golem, 16, 7, home, Math.PI / 2) : home;
        return movePos != null;
    }
    @Override public void start() { golem.getNavigation().moveTo(movePos.x, movePos.y, movePos.z, golem.getGolemMoveSpeed()); }
    @Override public boolean canContinueToUse() {
        return golem.getTask() == null && !golem.getNavigation().isDone() && golem.getRestrictCenter().distToCenterSqr(golem.position()) > 3;
    }
    @Override public void stop() { idleCounter = 50; golem.getNavigation().stop(); }
}
