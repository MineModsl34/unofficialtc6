package thaumcraft.common.entities.monster.cult;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.entities.CultEntities;
import thaumcraft.common.lib.SoundsTC;

public final class EntityCultistPortalLesser extends Monster {
    private static final EntityDataAccessor<Boolean> ACTIVE = SynchedEntityData.defineId(EntityCultistPortalLesser.class, EntityDataSerializers.BOOLEAN);
    private int stagecounter = 100;
    public int activeCounter, pulse;
    public EntityCultistPortalLesser(EntityType<? extends EntityCultistPortalLesser> type, Level level) { super(type, level); xpReward = 10; }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.ARMOR, 4);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(ACTIVE, false); }
    public boolean isActive() { return entityData.get(ACTIVE); }
    public void setActive(boolean active) { entityData.set(ACTIVE, active); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean requiresCustomPersistence() { return true; }
    @Override public boolean canBeCollidedWith() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public void move(MoverType type, Vec3 movement) {}
    @Override public void aiStep() {} // Source's empty onLivingUpdate: no walking/falling/goal animation.
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 4096; }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("active", isActive()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); setActive(tag.getBoolean("active")); }
    @Override public void tick() {
        super.tick();
        if (isActive()) activeCounter++;
        if (!level().isClientSide) {
            if (!isActive()) {
                if (tickCount % 10 == 0 && level().getNearestPlayer(this, 32) != null) { setActive(true); playSound(SoundsTC.craftstart, 1, 1); }
            } else if (stagecounter-- <= 0) {
                Player nearest = level().getNearestPlayer(this, 32);
                if (nearest != null && hasLineOfSight(nearest)) {
                    int maximum = level().getDifficulty() == Difficulty.HARD ? 6 : level().getDifficulty() == Difficulty.NORMAL ? 4 : 2;
                    int nearby = level().getEntitiesOfClass(EntityCultist.class, getBoundingBox().inflate(32)).size();
                    if (maximum > nearby) { level().broadcastEntityEvent(this, (byte)16); spawnMinions(); }
                }
                stagecounter = 50 + random.nextInt(50);
            }
        }
        if (pulse > 0) pulse--;
    }
    /** Genuine registered minion spawn shared by source scheduled tick and runtime tests. */
    public EntityCultist spawnMinions() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) throw new IllegalStateException("Server-only cultist minion spawn");
        EntityCultist cultist = random.nextFloat() > .33D ? CultEntities.KNIGHT.create(level()) : CultEntities.CLERIC.create(level());
        if (cultist == null) throw new IllegalStateException("Missing source cultist type");
        cultist.setPos(getX() + random.nextFloat() - random.nextFloat(), getY() + .25, getZ() + random.nextFloat() - random.nextFloat());
        cultist.finalizeSpawn(server, server.getCurrentDifficultyAt(cultist.blockPosition()), MobSpawnType.EVENT, null);
        server.addFreshEntity(cultist); cultist.spawnExplosionParticle(); cultist.playSound(SoundsTC.wandfail, 1, 1);
        hurt(damageSources().fellOutOfWorld(), 5 + random.nextInt(5)); return cultist;
    }
    @Override public void playerTouch(Player player) {
        if (distanceToSqr(player) < 3 && player.hurt(damageSources().indirectMagic(this, this), 4)) playSound(SoundsTC.zap, 1, (random.nextFloat() - random.nextFloat()) * .1F + 1);
    }
    @Override protected float getSoundVolume() { return .75F; }
    @Override public int getAmbientSoundInterval() { return 540; }
    @Override protected SoundEvent getAmbientSound() { return SoundsTC.monolith; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundsTC.zap; }
    @Override protected SoundEvent getDeathSound() { return SoundsTC.shock; }
    @Override public void handleEntityEvent(byte event) { if (event == 16) pulse = 10; else super.handleEntityEvent(event); }
    @Override public boolean addEffect(MobEffectInstance effect, Entity source) { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public void die(DamageSource source) {
        if (!level().isClientSide) level().explode(this, getX(), getY(), getZ(), 1.5F, false, Level.ExplosionInteraction.NONE);
        super.die(source);
    }
}
