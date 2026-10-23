package thaumcraft.common.entities.ai.combat;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;

/** Source EntityAIAttackRanged extra lower-range boundary (8blocks for guardian). */
public final class AILongRangeAttack extends RangedAttackGoal {
    private final Mob wielder;
    private final double minimumSquared;
    public AILongRangeAttack(RangedAttackMob ranged, double minimum, double speed, int intervalMin,
                             int intervalMax, float range) {
        super(ranged, speed, intervalMin, intervalMax, range);
        wielder = (Mob) ranged;
        minimumSquared = minimum * minimum;
    }
    @Override public boolean canUse() {
        boolean ready = super.canUse();
        if (ready) {
            var target = wielder.getTarget();
            if (target == null) return false;
            if (!target.isAlive()) { wielder.setTarget(null); return false; }
            if (wielder.distanceToSqr(target.getX(), target.getBoundingBox().minY, target.getZ()) < minimumSquared) return false;
        }
        return ready;
    }
}
