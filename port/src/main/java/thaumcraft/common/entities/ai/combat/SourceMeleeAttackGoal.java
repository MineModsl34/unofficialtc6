package thaumcraft.common.entities.ai.combat;

import java.util.EnumSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;

/** TC6's 1.12 melee baseline: spherical source reach,20-tick attacks and original path/RNG order. */
public class SourceMeleeAttackGoal extends Goal {
    protected final PathfinderMob mob;
    protected int attackTicks;
    private final double speed;
    private final boolean longMemory;
    private Path path;
    private int delay;
    private double targetX, targetY, targetZ;
    public SourceMeleeAttackGoal(PathfinderMob mob, double speed, boolean longMemory) {
        this.mob = mob; this.speed = speed; this.longMemory = longMemory;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        path = mob.getNavigation().createPath(target, 0);
        return path != null || getAttackReachSqr(target) >= distance(target);
    }
    @Override public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (!longMemory) return !mob.getNavigation().isDone();
        if (!mob.isWithinRestriction(target.blockPosition())) return false;
        return !(target instanceof Player player) || !player.isSpectator() && !player.isCreative();
    }
    @Override public void start() { mob.getNavigation().moveTo(path, speed); delay = 0; }
    @Override public void stop() {
        LivingEntity target = mob.getTarget();
        if (target instanceof Player player && (player.isSpectator() || player.isCreative())) mob.setTarget(null);
        mob.getNavigation().stop();
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    private double distance(LivingEntity target) { return mob.distanceToSqr(target.getX(), target.getBoundingBox().minY, target.getZ()); }
    @Override public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        mob.getLookControl().setLookAt(target, 30, 30);
        double distance = distance(target);
        --delay;
        if ((longMemory || mob.getSensing().hasLineOfSight(target)) && delay <= 0
            && (targetX == 0 && targetY == 0 && targetZ == 0 || target.distanceToSqr(targetX, targetY, targetZ) >= 1 || mob.getRandom().nextFloat() < 0.05F)) {
            targetX = target.getX(); targetY = target.getBoundingBox().minY; targetZ = target.getZ();
            delay = 4 + mob.getRandom().nextInt(7);
            if (distance > 1024) delay += 10;
            else if (distance > 256) delay += 5;
            if (!mob.getNavigation().moveTo(target, speed)) delay += 15;
        }
        attackTicks = Math.max(attackTicks - 1, 0);
        if (distance <= getAttackReachSqr(target) && attackTicks <= 0) {
            attackTicks = 20; mob.swing(InteractionHand.MAIN_HAND); performAttack(target);
        }
    }
    protected double getAttackReachSqr(LivingEntity target) { return mob.getBbWidth() * 2 * mob.getBbWidth() * 2 + target.getBbWidth(); }
    protected void performAttack(LivingEntity target) { mob.doHurtTarget(target); }
}
