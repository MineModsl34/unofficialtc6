package thaumcraft.common.golems.ai;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import thaumcraft.common.entities.construct.EntityOwnedConstruct;

public class AIOwnerHurtByTarget extends TargetGoal {
    private final EntityOwnedConstruct construct;
    private LivingEntity attacker;
    private int timestamp;
    public AIOwnerHurtByTarget(EntityOwnedConstruct construct) {
        super(construct, false);
        this.construct = construct;
        setFlags(EnumSet.of(Flag.TARGET));
    }
    @Override public boolean canUse() {
        if (!construct.isOwned() || construct.getOwnerEntity() == null) return false;
        LivingEntity owner = construct.getOwnerEntity();
        attacker = owner.getLastHurtByMob();
        return owner.getLastHurtByMobTimestamp() != timestamp && canAttack(attacker, TargetingConditions.forCombat().ignoreLineOfSight());
    }
    @Override public void start() {
        mob.setTarget(attacker);
        if (construct.getOwnerEntity() != null) timestamp = construct.getOwnerEntity().getLastHurtByMobTimestamp();
        super.start();
    }
}
