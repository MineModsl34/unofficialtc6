package thaumcraft.common.entities.monster.cult;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.ai.combat.AILongRangeAttack;
import thaumcraft.common.entities.ai.misc.AIAltarFocus;
import thaumcraft.common.entities.projectile.EntityGolemOrb;
import thaumcraft.common.lib.SoundsTC;

public final class EntityCultistCleric extends EntityCultist implements RangedAttackMob {
    private static final EntityDataAccessor<Boolean> RITUALIST = SynchedEntityData.defineId(EntityCultistCleric.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<net.minecraft.core.BlockPos> HOME = SynchedEntityData.defineId(EntityCultistCleric.class, EntityDataSerializers.BLOCK_POS);
    public int rage;
    public EntityCultistCleric(EntityType<? extends EntityCultistCleric> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() { return baseAttributes().add(Attributes.MAX_HEALTH, 24); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(RITUALIST, false); builder.define(HOME, net.minecraft.core.BlockPos.ZERO); }
    public boolean getIsRitualist() { return entityData.get(RITUALIST); }
    public void setIsRitualist(boolean value) { entityData.set(RITUALIST, value); if (value && hasRestriction()) entityData.set(HOME, getRestrictCenter()); }
    public net.minecraft.core.BlockPos ritualHome() { return level().isClientSide ? entityData.get(HOME) : getRestrictCenter(); }
    @Override protected void registerGoals() {
        registerCultistGoals(); goalSelector.addGoal(1, new AIAltarFocus(this)); goalSelector.addGoal(2, new AILongRangeAttack(this, 2, 1, 20, 40, 24));
    }
    @Override protected void setLoot(DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.HEAD, equipment(ItemsTC.crimsonRobeHelm)); setItemSlot(EquipmentSlot.CHEST, equipment(ItemsTC.crimsonRobeChest));
        setItemSlot(EquipmentSlot.LEGS, equipment(ItemsTC.crimsonRobeLegs));
        if (random.nextFloat() < (level().getDifficulty() == Difficulty.HARD ? .3F : .1F)) setItemSlot(EquipmentSlot.FEET, equipment(ItemsTC.crimsonBoots));
    }
    @Override public void performRangedAttack(LivingEntity target, float power) {
        double dx = target.getX() - getX(), dy = target.getBoundingBox().minY + target.getBbHeight() / 2 - (getY() + getBbHeight() / 2), dz = target.getZ() - getZ();
        swing(InteractionHand.MAIN_HAND);
        if (random.nextFloat() > .66F) {
            var orb = new EntityGolemOrb(level(), this, target, true);
            Vec3 ahead = target.position().add(target.getDeltaMovement().scale(10)).subtract(position()).normalize();
            orb.setPos(orb.getX() + ahead.x, orb.getY() + ahead.y, orb.getZ() + ahead.z); orb.shoot(ahead.x, ahead.y, ahead.z, .66F, 3);
            playSound(SoundsTC.egattack, 1, 1 + random.nextFloat() * .1F); level().addFreshEntity(orb);
        } else {
            float spread = (float)Math.sqrt(power) * .5F; level().levelEvent(null, 1009, blockPosition(), 0);
            for (int index = 0; index < 3; index++) {
                var fireball = new SmallFireball(level(), this, new Vec3(dx + random.nextGaussian() * spread, dy, dz + random.nextGaussian() * spread));
                fireball.setPos(fireball.getX(), getY() + getBbHeight() / 2 + .5, fireball.getZ()); level().addFreshEntity(fireball);
            }
        }
    }
    @Override public boolean hurt(DamageSource source, float amount) { if (isInvulnerableTo(source)) return false; setIsRitualist(false); return super.hurt(source, amount); }
    @Override public boolean removeWhenFarAway(double distance) { return !getIsRitualist(); }
    @Override public boolean requiresCustomPersistence() { return getIsRitualist() || super.requiresCustomPersistence(); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("ritualist", getIsRitualist()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); setIsRitualist(tag.getBoolean("ritualist")); }
    @Override public void tick() {
        super.tick();
        if (getIsRitualist()) {
            if (level().isClientSide) {
                Vec3 aim = Vec3.atLowerCornerOf(ritualHome()).add(.5, 1.5, .5).subtract(position().add(0, getEyeHeight(), 0));
                setXRot(turn(getXRot(), (float)(-Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)) * 180 / Math.PI), 10));
                setYHeadRot(turn(getYHeadRot(), (float)(Math.atan2(aim.z, aim.x) * 180 / Math.PI) - 90, getMaxHeadXRot()));
            } else if (rage >= 5) setIsRitualist(false);
        }
    }
    private static float turn(float actual, float target, float maximum) { return actual + net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(target - actual), -maximum, maximum); }
    @Override protected SoundEvent getAmbientSound() { return SoundsTC.chant; }
    @Override public int getAmbientSoundInterval() { return 500; }
    @Override public void handleEntityEvent(byte event) {
        if (event == 19) for (int index = 0; index < 3; index++) {
            double dx = random.nextGaussian() * .02, dy = random.nextGaussian() * .02, dz = random.nextGaussian() * .02;
            level().addParticle(ParticleTypes.ANGRY_VILLAGER, getX() + random.nextFloat() * getBbWidth() * 2 - getBbWidth(), getY() + .5 + random.nextFloat() * getBbHeight(),
                getZ() + random.nextFloat() * getBbWidth() * 2 - getBbWidth(), dx, dy, dz);
        } else super.handleEntityEvent(event);
    }
}
