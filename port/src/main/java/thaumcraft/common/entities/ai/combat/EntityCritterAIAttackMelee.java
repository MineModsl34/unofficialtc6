package thaumcraft.common.entities.ai.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/** Source tainted-animal attack ignores animal-specific doHurtTarget overrides. Modern enchant hooks preserve extensibility. */
public final class EntityCritterAIAttackMelee extends SourceMeleeAttackGoal {
    public EntityCritterAIAttackMelee(PathfinderMob mob,double speed,boolean memory){super(mob,speed,memory);}
    @Override protected void performAttack(LivingEntity target) {
        if(!(mob.level() instanceof ServerLevel level))return;
        float amount=(float)mob.getAttributeValue(Attributes.ATTACK_DAMAGE);var source=mob.damageSources().mobAttack(mob);var weapon=mob.getMainHandItem();
        amount=EnchantmentHelper.modifyDamage(level,weapon,target,source,amount);
        if(!target.hurt(source,amount))return;
        float knockback=EnchantmentHelper.modifyKnockback(level,weapon,target,source,0);
        if(knockback>0){target.knockback(knockback*.5F,Mth.sin(mob.getYRot()*(float)Math.PI/180),-Mth.cos(mob.getYRot()*(float)Math.PI/180));mob.setDeltaMovement(mob.getDeltaMovement().multiply(.6,1,.6));}
        EnchantmentHelper.doPostAttackEffectsWithItemSource(level,target,source,weapon);
        if(target instanceof Player player&&player.isUsingItem()&&!weapon.isEmpty()&&weapon.canDisableShield(player.getUseItem(),player,mob)){
            int efficiency=weapon.getEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY));
            if(mob.getRandom().nextFloat()<.25F+efficiency*.05F){player.getCooldowns().addCooldown(player.getUseItem().getItem(),100);level.broadcastEntityEvent(player,(byte)30);}
        }
    }
}
