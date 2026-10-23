package thaumcraft.common.golems.ai;

import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;

public class AIArrowAttack extends RangedAttackGoal {
    private final Mob host;
    private final RangedAttackMob attacker;
    private int attackTime = -1;
    private final double moveSpeed;
    private int seeTime;
    private final int minInterval, maxInterval;
    private final float radius, maxDistanceSquared;
    public AIArrowAttack(RangedAttackMob attacker, double speed, int minInterval, int maxInterval, float radius) {
        super(attacker, speed, minInterval, maxInterval, radius);
        if (!(attacker instanceof Mob mob)) throw new IllegalArgumentException("ArrowAttackGoal requires Mob implements RangedAttackMob");
        host = mob;
        this.attacker = attacker;
        moveSpeed = speed;
        this.minInterval = minInterval;
        this.maxInterval = maxInterval;
        this.radius = radius;
        maxDistanceSquared = radius * radius;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public boolean canUse() { return host.getTarget() != null; }
    @Override public boolean canContinueToUse() { return canUse() || !host.getNavigation().isDone(); }
    @Override public void stop() { seeTime = 0; attackTime = -1; }
    @Override public void tick() {
        var target = host.getTarget();
        if (target == null) return;
        double distance = host.distanceToSqr(target.getX(), target.getBoundingBox().minY, target.getZ());
        boolean sees = host.getSensing().hasLineOfSight(target);
        seeTime = sees ? seeTime + 1 : 0;
        if (distance <= maxDistanceSquared && seeTime >= 20) host.getNavigation().stop();
        else host.getNavigation().moveTo(target, moveSpeed);
        host.getLookControl().setLookAt(target, 10, 30);
        if (--attackTime == 0) {
            if (distance > maxDistanceSquared || !sees) return;
            float distanceFactor = (float) Math.sqrt(distance) / radius;
            attacker.performRangedAttack(target, Mth.clamp(distanceFactor, 0.1F, 1));
            attackTime = Mth.floor(distanceFactor * (maxInterval - minInterval) + minInterval);
        } else if (attackTime < 0) attackTime = Mth.floor((float) Math.sqrt(distance) / radius * (maxInterval - minInterval) + minInterval);
    }
}
