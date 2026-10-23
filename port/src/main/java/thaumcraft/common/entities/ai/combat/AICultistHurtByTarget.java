package thaumcraft.common.entities.ai.combat;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.entities.monster.boss.EntityCultistLeader;
import net.minecraft.world.entity.PathfinderMob;

/** Source retaliation alerts BOTH knight and cleric classes, not modern same-concrete-type only. */
public final class AICultistHurtByTarget extends TargetGoal {
    private static final TargetingConditions SUITABLE = TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();
    private final PathfinderMob owner;
    private final boolean help;
    private int timestamp;
    public AICultistHurtByTarget(PathfinderMob owner, boolean help) { super(owner, false); this.owner = owner; this.help = help; setFlags(EnumSet.of(Goal.Flag.TARGET)); }
    @Override public boolean canUse() {
        return owner.getLastHurtByMobTimestamp() != timestamp && owner.getLastHurtByMob() != null && canAttack(owner.getLastHurtByMob(), SUITABLE);
    }
    @Override public void start() {
        owner.setTarget(owner.getLastHurtByMob()); targetMob = owner.getTarget(); timestamp = owner.getLastHurtByMobTimestamp(); unseenMemoryTicks = 300;
        if (help) alertOthers(); super.start();
    }
    public void alertOthers() {
        LivingEntity target = owner.getLastHurtByMob(); if (target == null) return;
        double range = getFollowDistance();
        AABB bounds = new AABB(owner.getX(), owner.getY(), owner.getZ(), owner.getX() + 1, owner.getY() + 1, owner.getZ() + 1).inflate(range, 10, range);
        for (PathfinderMob other : owner.level().getEntitiesOfClass(PathfinderMob.class, bounds,
            entity -> entity instanceof EntityCultist || entity instanceof EntityCultistLeader))
            if (other != owner && other.getTarget() == null && !other.isAlliedTo(target)) other.setTarget(target);
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
}
