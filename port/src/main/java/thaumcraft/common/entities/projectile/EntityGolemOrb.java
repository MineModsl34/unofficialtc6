package thaumcraft.common.entities.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.entities.CultEntities;
import thaumcraft.common.lib.SoundsTC;

/** Source golem/cleric homing orb, zero gravity, red1.0/normal0.6 attack damage,
 * 240/160-tick lifetime and source look-vector deflection without changing the owner. */
public final class EntityGolemOrb extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(EntityGolemOrb.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RED = SynchedEntityData.defineId(EntityGolemOrb.class, EntityDataSerializers.BOOLEAN);
    public EntityGolemOrb(EntityType<? extends EntityGolemOrb> type, Level level) { super(type, level); }
    public EntityGolemOrb(Level level, LivingEntity shooter, LivingEntity target, boolean red) {
        super(CultEntities.GOLEM_ORB, shooter, level); entityData.set(TARGET, target.getId()); entityData.set(RED, red);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(TARGET, -1); builder.define(RED, false); }
    @Override protected double getDefaultGravity() { return 0; }
    @Override public Vec3 getMovementToShoot(double x, double y, double z, float speed, float spread) {
        float length = (float)Math.sqrt(x * x + y * y + z * z);
        return new Vec3(x / length + random.nextGaussian() * .007499999832361937D * spread,
            y / length + random.nextGaussian() * .007499999832361937D * spread,
            z / length + random.nextGaussian() * .007499999832361937D * spread).scale(speed);
    }
    public boolean isRed() { return entityData.get(RED); }
    public LivingEntity homingTarget() { var entity = level().getEntity(entityData.get(TARGET)); return entity instanceof LivingEntity target ? target : null; }
    @Override public void tick() {
        super.tick(); if (tickCount > (isRed() ? 240 : 160)) discard();
        LivingEntity target = homingTarget();
        if (target != null) {
            double distance = distanceToSqr(target);
            Vec3 motion = getDeltaMovement().add((target.getX() - getX()) / distance * .2,
                (target.getBoundingBox().minY + target.getBbHeight() * .6 - getY()) / distance * .2, (target.getZ() - getZ()) / distance * .2);
            setDeltaMovement(net.minecraft.util.Mth.clamp((float)motion.x, -.25F, .25F), net.minecraft.util.Mth.clamp((float)motion.y, -.25F, .25F), net.minecraft.util.Mth.clamp((float)motion.z, -.25F, .25F));
        }
    }
    @Override public void onHit(HitResult result) {
        // The original impact override does not call super: no modern block callbacks,
        // redirect or game-event side effects are appended to the TC6 attack.
        if (!level().isClientSide && getOwner() instanceof LivingEntity shooter && result instanceof EntityHitResult entity)
            entity.getEntity().hurt(damageSources().indirectMagic(this, shooter), (float)shooter.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRed() ? 1 : .6F));
        playSound(SoundsTC.shock, 1, 1 + (random.nextFloat() - random.nextFloat()) * .2F);
        if (level().isClientSide) thaumcraft.common.entities.CultistPresentationHooks.burst(level(), getX(), getY(), getZ(), 1); discard();
    }
    @Override public boolean hurt(DamageSource source, float damage) {
        if (isInvulnerableTo(source) || source.getEntity() == null) return false;
        setDeltaMovement(source.getEntity().getLookAngle().scale(.9)); playSound(SoundsTC.zap, 1, 1 + (random.nextFloat() - random.nextFloat()) * .2F); return true;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); /* Source did not persist target/red in entity NBT. */ }
}
