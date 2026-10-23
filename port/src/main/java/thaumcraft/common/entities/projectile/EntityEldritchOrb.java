package thaumcraft.common.entities.projectile;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.WarpEntities;

/** Source zero-gravity, 100-tick orb with 2block magic AoE excluding owner and undead. */
public final class EntityEldritchOrb extends ThrowableProjectile {
    public EntityEldritchOrb(EntityType<? extends EntityEldritchOrb> type, Level level) { super(type, level); }
    public EntityEldritchOrb(Level level, LivingEntity owner) {
        super(WarpEntities.ELDRITCH_ORB, owner, level);
        shootFromRotation(owner, owner.getXRot(), owner.getYRot(), -5.0F, 0.75F, 0.0F);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected double getDefaultGravity() { return 0; }
    @Override public Vec3 getMovementToShoot(double x,double y,double z,float velocity,float inaccuracy) {
        float length=(float)Math.sqrt(x*x+y*y+z*z);
        // Original Gaussian calls still consume RNG when source guardian uses spread0.
        return new Vec3(x/length+random.nextGaussian()*.007499999832361937*inaccuracy,
            y/length+random.nextGaussian()*.007499999832361937*inaccuracy,
            z/length+random.nextGaussian()*.007499999832361937*inaccuracy).scale(velocity);
    }
    @Override public void tick() {
        super.tick();
        if (tickCount > 100) discard();
    }
    @Override public void onHit(HitResult result) {
        // Source overrides impact directly: no new block callbacks/redirect/game-event side effects.
        if (level().isClientSide || !(getOwner() instanceof LivingEntity shooter)) return;
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.0), other -> other != shooter)) {
            if (!target.getType().is(EntityTypeTags.UNDEAD)) {
                target.hurt(damageSources().indirectMagic(this, shooter), (float) shooter.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.666F);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0));
            }
        }
        playSound(SoundEvents.LAVA_EXTINGUISH, 0.5F, 2.6F + (random.nextFloat() - random.nextFloat()) * 0.8F);
        discard();
    }
}
