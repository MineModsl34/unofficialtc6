package thaumcraft.common.entities.monster;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.common.entities.ai.combat.AILongRangeAttack;
import thaumcraft.common.entities.ai.combat.SourceMeleeAttackGoal;
import thaumcraft.common.entities.projectile.EntityEldritchOrb;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.fx.PacketFXSonic;
import thaumcraft.common.lib.network.misc.PacketMiscEvent;
import thaumcraft.common.lib.warp.WarpGameplay;

/** Actual source guardian dependency used by warp mist; not a renamed vanilla mob. */
public class EntityEldritchGuardian extends Monster implements RangedAttackMob, IEldritchMob {
    public static final TagKey<EntityType<?>> CULTISTS = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("thaumcraft:cultists"));
    private static final TagKey<DamageType> MAGIC = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("thaumcraft:source_magic"));
    public float armLiftL, armLiftR;
    private boolean lastBlast;
    public EntityEldritchGuardian(EntityType<? extends EntityEldritchGuardian> type, Level level) {
        super(type, level);
        ((GroundPathNavigation) getNavigation()).setCanOpenDoors(true);
        xpReward = 20;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 50.0).add(Attributes.FOLLOW_RANGE, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.ATTACK_DAMAGE, 7.0).add(Attributes.ARMOR, 4.0);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new AILongRangeAttack(this, 8.0, 1.0, 20, 40, 24.0F));
        goalSelector.addGoal(3, new SourceMeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(7, new RandomStrollGoal(this, 1.0));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, mob -> mob.getType().is(CULTISTS)));
    }
    public boolean inOuterLands() { return level().dimension().location().equals(ResourceLocation.parse("thaumcraft:outer_lands")); }
    @Override public boolean hurt(DamageSource source, float amount) { return super.hurt(source, source.is(MAGIC) ? amount / 2.0F : amount); }
    @Override public boolean canPickUpLoot() { return false; }
    @Override public boolean checkSpawnRules(net.minecraft.world.level.LevelAccessor level, MobSpawnType reason) {
        var densityBox = new net.minecraft.world.phys.AABB(getX(),getY(),getZ(),getX()+1,getY()+1,getZ()+1).inflate(32,16,32);
        return level().getEntitiesOfClass(EntityEldritchGuardian.class,densityBox,other -> other != this).isEmpty()
            && super.checkSpawnRules(level,reason);
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (armLiftL > 0) armLiftL -= 0.05F;
            if (armLiftR > 0) armLiftR -= 0.05F;
        } else if (!inOuterLands() && (tickCount == 0 || tickCount % 100 == 0) && level().getDifficulty() != Difficulty.EASY) {
            double distance = level().getDifficulty() == Difficulty.HARD ? 576.0 : 256.0;
            for (Player player : level().players()) {
                if (player instanceof net.minecraft.server.level.ServerPlayer owner && owner.isAlive() && distanceToSqr(owner) < distance) {
                    PlayerDataSync.sendMiscEvent(owner, new PacketMiscEvent(PacketMiscEvent.MIST_EVENT_SHORT));
                }
            }
        }
    }
    @Override public boolean doHurtTarget(Entity target) {
        boolean success = super.doHurtTarget(target);
        // Source tests heldItem == null, not isEmpty; vanilla returns a non-null empty stack.
        // Its apparently intended fire-melee branch was unreachable and is not newly activated.
        return success;
    }
    @Override protected SoundEvent getAmbientSound() { return SoundsTC.egidle; }
    @Override protected SoundEvent getDeathSound() { return SoundsTC.egdeath; }
    @Override public int getAmbientSoundInterval() { return 500; }
    @Override protected float getSoundVolume() { return 1.5F; }
    @Override public boolean removeWhenFarAway(double distance) { return !hasRestriction(); }
    @Override public boolean requiresCustomPersistence() { return hasRestriction() || super.requiresCustomPersistence(); }
    @Override public boolean isAlliedTo(Entity entity) { return entity instanceof IEldritchMob; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (hasRestriction() && getRestrictRadius() > 0) {
            tag.putInt("HomeD", (int) getRestrictRadius());
            tag.putInt("HomeX", getRestrictCenter().getX()); tag.putInt("HomeY", getRestrictCenter().getY()); tag.putInt("HomeZ", getRestrictCenter().getZ());
        }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeD")) restrictTo(new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ")), tag.getInt("HomeD"));
    }
    @Nullable @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, @Nullable SpawnGroupData group) {
        var result = super.finalizeSpawn(level, difficulty, reason, group);
        if (inOuterLands()) thaumcraft.common.entities.monster.mods.ChampionSystem.addAbsorption(this, ((int) getAttribute(Attributes.MAX_HEALTH).getBaseValue() / 2));
        return result;
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (inOuterLands() && invulnerableTime <= 0 && tickCount % 25 == 0) {
            int maximum = (int) getAttribute(Attributes.MAX_HEALTH).getBaseValue() / 2;
            if (getAbsorptionAmount() < maximum) thaumcraft.common.entities.monster.mods.ChampionSystem.addAbsorption(this, 1.0F);
        }
    }
    @Override public void handleEntityEvent(byte event) {
        if (event == 15) armLiftL = 0.5F;
        else if (event == 16) armLiftR = 0.5F;
        else if (event == 17) { armLiftL = 0.9F; armLiftR = 0.9F; }
        else super.handleEntityEvent(event);
    }
    @Override public void performRangedAttack(LivingEntity target, float power) {
        if (random.nextFloat() > 0.15F) {
            EntityEldritchOrb orb = new EntityEldritchOrb(level(), this);
            lastBlast = !lastBlast;
            level().broadcastEntityEvent(this, (byte) (lastBlast ? 16 : 15));
            int rotation = lastBlast ? 90 : 180;
            float angle = ((getYRot() + rotation) % 360.0F) / 180.0F * (float)Math.PI;
            orb.setPos(orb.getX() - net.minecraft.util.Mth.cos(angle) * 0.5F, orb.getY(), orb.getZ() - net.minecraft.util.Mth.sin(angle) * 0.5F);
            Vec3 direction = target.position().add(target.getDeltaMovement().scale(10.0)).subtract(position()).normalize();
            orb.shoot(direction.x, direction.y, direction.z, 1.1F, 2.0F);
            playSound(SoundsTC.egattack, 2.0F, 1.0F + random.nextFloat() * 0.1F);
            level().addFreshEntity(orb);
        } else if (hasLineOfSight(target)) {
            PacketDistributor.sendToPlayersNear((net.minecraft.server.level.ServerLevel) level(), null, getX(), getY(), getZ(), 32.0, new PacketFXSonic(getId()));
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 0));
            if (target instanceof Player player) WarpGameplay.addWarpToPlayer(player, 1 + level().getRandom().nextInt(3), EnumWarpType.TEMPORARY);
            playSound(SoundsTC.egscreech, 3.0F, 1.0F + random.nextFloat() * 0.1F);
        }
    }
}
