package thaumcraft.common.entities.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** TC6's fire-immune, ceiling-hanging suicide attacker (not vanilla Bat AI). */
public final class EntityFireBat extends Monster {
    private static final EntityDataAccessor<Boolean> HANGING=SynchedEntityData.defineId(EntityFireBat.class,EntityDataSerializers.BOOLEAN);
    private BlockPos currentFlightTarget;
    private int attackTime;
    public LivingEntity owner;
    public int damBonus;
    public EntityFireBat(EntityType<? extends EntityFireBat> type,Level level){super(type,level);setIsBatHanging(true);}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,5).add(Attributes.ATTACK_DAMAGE,1);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(HANGING,false);}
    public boolean getIsBatHanging(){return entityData.get(HANGING);}public void setIsBatHanging(boolean value){entityData.set(HANGING,value);}
    @Override public void aiStep(){if(isInWaterOrRain())hurt(damageSources().drown(),1);super.aiStep();}
    @Override public void tick(){super.tick();if(getIsBatHanging()){setDeltaMovement(Vec3.ZERO);setPos(getX(),Mth.floor(getY())+1-getBbHeight(),getZ());}else setDeltaMovement(getDeltaMovement().multiply(1,.6F,1));}
    @Override protected void customServerAiStep(){super.customServerAiStep();if(attackTime>0)attackTime--;BlockPos above=blockPosition().above();boolean ceiling=level().getBlockState(above).isRedstoneConductor(level(),above);
        if(getIsBatHanging()){if(!ceiling||level().getNearestPlayer(this,4)!=null){setIsBatHanging(false);level().levelEvent(null,1025,blockPosition(),0);}else if(random.nextInt(200)==0)setYHeadRot(random.nextInt(360));}
        else if(getTarget()==null){if(currentFlightTarget!=null&&(!level().isEmptyBlock(currentFlightTarget)||currentFlightTarget.getY()<1))currentFlightTarget=null;if(currentFlightTarget==null||random.nextInt(30)==0||currentFlightTarget.distToCenterSqr(position())<4)currentFlightTarget=new BlockPos((int)getX()+random.nextInt(7)-random.nextInt(7),(int)getY()+random.nextInt(6)-2,(int)getZ()+random.nextInt(7)-random.nextInt(7));steer(currentFlightTarget.getX()+.5,currentFlightTarget.getY()+.1,currentFlightTarget.getZ()+.5);if(random.nextInt(100)==0&&ceiling)setIsBatHanging(true);}
        else steer(getTarget().getX(),getTarget().getY()+getTarget().getEyeHeight()*.66F,getTarget().getZ());
        if(getTarget()==null)setTarget(level().getNearestPlayer(this,12));else if(!getTarget().isAlive())setTarget(null);else if(isAlive()&&getSensing().hasLineOfSight(getTarget()))attackEntity(getTarget());if(getTarget() instanceof Player player&&player.getAbilities().invulnerable)setTarget(null);
    }
    private void steer(double x,double y,double z){Vec3 v=getDeltaMovement();setDeltaMovement(v.x+(Math.signum(x-getX())*.5-v.x)*.1F,v.y+(Math.signum(y-getY())*.7F-v.y)*.1F,v.z+(Math.signum(z-getZ())*.5-v.z)*.1F);v=getDeltaMovement();zza=.5F;setYRot(getYRot()+Mth.wrapDegrees((float)(Math.atan2(v.z,v.x)*180/Math.PI)-90-getYRot()));}
    private void attackEntity(LivingEntity target){if(attackTime>0||distanceTo(target)>=Math.max(2.5F,target.getBbWidth()*1.1F)||target.getBoundingBox().maxY<=getBoundingBox().minY||target.getBoundingBox().minY>=getBoundingBox().maxY)return;var worldRandom=level().getRandom();attackTime=20+worldRandom.nextInt(20);if(worldRandom.nextInt(10)==0){target.invulnerableTime=0;level().explode(this,getX(),getY(),getZ(),1.5F,false,Level.ExplosionInteraction.NONE);discard();}playSound(SoundEvents.BAT_HURT,.5F,.9F+worldRandom.nextFloat()*.2F);doHurtTarget(target);}
    @Override public boolean hurt(DamageSource source,float amount){if(isInvulnerableTo(source)||source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypeTags.IS_EXPLOSION))return false;if(!level().isClientSide)setIsBatHanging(false);return super.hurt(source,amount);}
    @Override public boolean isPushable(){return false;}@Override public boolean isIgnoringBlockTriggers(){return true;}
    @Override protected MovementEmission getMovementEmission(){return MovementEmission.NONE;}
    @Override protected void checkFallDamage(double y,boolean ground,BlockState state,BlockPos pos){}@Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return false;}
    @Override protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable(){return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;}
    @Override protected void dropCustomDeathLoot(ServerLevel server,DamageSource source,boolean recent){super.dropCustomDeathLoot(server,source,recent);int count=random.nextInt(3),looting=EntityBrainyZombie.looting(source);if(looting>0)count+=random.nextInt(looting+1);for(int i=0;i<count;i++)spawnAtLocation(Items.GUNPOWDER);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("hang",getIsBatHanging());tag.putByte("damBonus",(byte)damBonus);}@Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);setIsBatHanging(tag.getBoolean("hang"));damBonus=tag.getByte("damBonus");}
    @Override protected SoundEvent getAmbientSound(){return getIsBatHanging()&&random.nextInt(4)!=0?null:SoundEvents.BAT_AMBIENT;}@Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.BAT_HURT;}@Override protected SoundEvent getDeathSound(){return SoundEvents.BAT_DEATH;}@Override protected float getSoundVolume(){return .1F;}@Override public float getVoicePitch(){return super.getVoicePitch()*.95F;}
}
