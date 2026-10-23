package thaumcraft.common.golems.parts;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.parts.GolemArm;
import thaumcraft.common.entities.construct.ConstructRules;
import thaumcraft.common.entities.projectile.EntityGolemDart;
import thaumcraft.common.golems.ai.AIArrowAttack;

/** Actual source dart arm function, using the genuine stage8 projectile. */
public final class GolemArmDart implements GolemArm.IArmFunction {
    @Override public void onMeleeAttack(IGolemAPI golem,Entity target) {}
    @Override public void onUpdateTick(IGolemAPI golem) {}
    @Override public Goal getRangedAttackAI(RangedAttackMob golem) { return new AIArrowAttack(golem,1,20,25,16); }
    @Override public void onRangedAttack(IGolemAPI golem,LivingEntity target,float range) {
        var world = golem.getGolemWorld(); if (world.isClientSide) return;
        var owner = golem.getGolemEntity(); var dart = new EntityGolemDart(world,owner);
        dart.setBaseDamage(owner.getAttributeValue(Attributes.ATTACK_DAMAGE) / 3F + range + world.random.nextGaussian() * .25);
        dart.shootSource(target.getX() - owner.getX(),target.getBoundingBox().minY + target.getEyeHeight() + range * range - dart.getY(),
            target.getZ() - owner.getZ(),1.6F,3); world.addFreshEntity(dart);
        owner.playSound(SoundEvents.ARROW_SHOOT,1,1F / (world.random.nextFloat() * .4F + .8F));
    }
}
