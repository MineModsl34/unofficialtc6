package thaumcraft.common.entities.monster.mods;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.SoundsTC;

/** Literal fourteen source effects. Dispatch roles are enforced by ChampionEvents. */
final class ChampionEffects {
    private ChampionEffects() {}
    static float perform(int id,LivingEntity mob,LivingEntity target,DamageSource source,float amount) {
        switch(id) {
            case 0,3: return 0; // Source never dispatches role -1; buffs belong to creation.
            case 1:
                if(target!=null && !"thorns".equalsIgnoreCase(source.getMsgId())) {
                    target.hurt(mob.damageSources().thorns(mob),1+mob.level().getRandom().nextInt(3));
                    target.playSound(SoundEvents.THORNS_HIT,.5F,1);
                }
                break;
            case 2: return source.is(DamageTypeTags.BYPASSES_ARMOR)?amount:ChampionRules.armor(amount);
            case 4: if(mob.level().getRandom().nextFloat()<.4F)target.addEffect(new MobEffectInstance(MobEffects.WITHER,200));break;
            case 5:
                if(mob.invulnerableTime<=0 && mob.tickCount%25==0 && mob.getAbsorptionAmount()<ChampionRules.shield(mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue()))
                    ChampionSystem.addAbsorption(mob,1);
                break;
            case 6:
                if(mob.level().getRandom().nextFloat()<.33F && target instanceof Player player) {
                    var warp=ThaumcraftCapabilities.getWarp(player);
                    // Source uses the storage add, NOT internalMethods.addWarpToPlayer:
                    // do not reset the event counter or invent a warp warning/unlock.
                    warp.add(IPlayerWarp.EnumWarpType.TEMPORARY,1+mob.level().getRandom().nextInt(3));
                    if(player instanceof ServerPlayer owner)warp.sync(owner);
                }
                break;
            case 7: if(mob.tickCount%20==0)mob.heal(1);break;
            case 8: if(mob.level().getRandom().nextFloat()<.4F)target.igniteForSeconds(4);break;
            case 9: if(mob.level().getRandom().nextFloat()<.4F)target.addEffect(new MobEffectInstance(MobEffects.HUNGER,500));break;
            case 10: if(mob.level().getRandom().nextFloat()<.4F)target.addEffect(new MobEffectInstance(MobEffects.POISON,100));break;
            case 11: mob.heal(ChampionRules.vampire(amount));break;
            case 12:
                if(mob.level().getRandom().nextFloat()<.4F && !mob.level().isClientSide) {
                    var crawler=thaumcraft.common.entities.Mob8BossRegistries.TAINT_CRAWLER.create(mob.level());
                    if(crawler!=null) {
                        crawler.moveTo(mob.getX(),mob.getY()+mob.getBbHeight()/2,mob.getZ(),mob.level().getRandom().nextFloat()*360,0);
                        mob.level().addFreshEntity(crawler);mob.playSound(SoundsTC.gore,.5F,1);
                    }
                }
                break;
            case 13: if(mob instanceof PathfinderMob critter)ChampionSystem.resetTaintedAI(critter);break;
            default: throw new IllegalArgumentException("Invalid champion modifier "+id);
        }
        return amount;
    }
}
