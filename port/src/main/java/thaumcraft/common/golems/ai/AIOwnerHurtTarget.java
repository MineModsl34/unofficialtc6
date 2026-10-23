package thaumcraft.common.golems.ai;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import thaumcraft.common.entities.construct.EntityOwnedConstruct;

public class AIOwnerHurtTarget extends TargetGoal {
    private final EntityOwnedConstruct construct;
    private LivingEntity target;
    private int timestamp;
    public AIOwnerHurtTarget(EntityOwnedConstruct construct) {
        super(construct, false);
        this.construct = construct;
        setFlags(EnumSet.of(Flag.TARGET));
    }
    @Override public boolean canUse() {
        if (!construct.isOwned() || construct.getOwnerEntity() == null) return false;
        LivingEntity owner = construct.getOwnerEntity();
        target = owner.getLastHurtMob();
        return owner.getLastHurtMobTimestamp() != timestamp && canAttack(target, TargetingConditions.forCombat().ignoreLineOfSight());
    }
    @Override public void start() {
        mob.setTarget(target);
        if (construct.getOwnerEntity() != null) timestamp = construct.getOwnerEntity().getLastHurtMobTimestamp();
        super.start();
    }
}
