package thaumcraft.common.entities.monster;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.common.entities.ai.combat.SourceMeleeAttackGoal;

/** Source warp spider: one HP/damage, harmless viewer-specific hallucinations expire at tick1201. */
public class EntityMindSpider extends Spider implements IEldritchMob {
    private static final EntityDataAccessor<Boolean> HARMLESS = SynchedEntityData.defineId(EntityMindSpider.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> VIEWER = SynchedEntityData.defineId(EntityMindSpider.class, EntityDataSerializers.STRING);
    private int lifeSpan = Integer.MAX_VALUE;

    public EntityMindSpider(EntityType<? extends EntityMindSpider> type, Level world) {
        super(type, world);
        xpReward = 1;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Spider.createAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.ATTACK_DAMAGE, 1.0);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HARMLESS, false);
        builder.define(VIEWER, "");
    }
    @Override protected void registerGoals() {
        // Source vanilla spider goals, without the new 1.21 armadillo avoidance goal.
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(4, new SourceMeleeAttackGoal(this, 1.0, true) {
            @Override public boolean canContinueToUse() {
                if (getLightLevelDependentMagicValue() >= 0.5F && random.nextInt(100) == 0) {
                    setTarget(null);
                    return false;
                }
                return super.canContinueToUse();
            }
            @Override protected double getAttackReachSqr(LivingEntity target) { return 4.0F + target.getBbWidth(); }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new LightTargetGoal<>(this, Player.class));
        targetSelector.addGoal(3, new LightTargetGoal<>(this, IronGolem.class));
    }
    private static class LightTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        LightTargetGoal(EntityMindSpider spider, Class<T> target) { super(spider, target, true); }
        @Override public boolean canUse() { return mob.getLightLevelDependentMagicValue() < 0.5F && super.canUse(); }
    }
    public boolean isHarmless() { return entityData.get(HARMLESS); }
    public String getViewer() { return entityData.get(VIEWER); }
    public void setViewer(String player) { entityData.set(VIEWER, String.valueOf(player)); }
    public void setHarmless(boolean harmless) {
        if (harmless) lifeSpan = 1200;
        entityData.set(HARMLESS, harmless);
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > lifeSpan) discard();
    }
    @Override public float getVoicePitch() { return 0.7F; }
    @Override protected int getBaseExperienceReward() { return isHarmless() ? 0 : super.getBaseExperienceReward(); }
    @Override protected void dropFromLootTable(DamageSource source, boolean hitByPlayer) {}
    @Override public boolean doHurtTarget(Entity target) { return !isHarmless() && super.doHurtTarget(target); }
    @Override public boolean isIgnoringBlockTriggers() { return true; }
    @Override protected MovementEmission getMovementEmission() { return MovementEmission.NONE; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("harmless", isHarmless());
        tag.putString("viewer", getViewer());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setHarmless(tag.getBoolean("harmless"));
        setViewer(tag.getString("viewer"));
    }
    @Nullable @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty,
            MobSpawnType reason, @Nullable SpawnGroupData group) { return group; }
}
