package thaumcraft.common.entities.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.entities.Mob8NaturalRules;
import thaumcraft.common.entities.Mob8NaturalEffects;
import thaumcraft.common.lib.SoundsTC;

public final class EntityWisp extends FlyingMob implements Enemy {
    private static final EntityDataAccessor<String> ASPECT=SynchedEntityData.defineId(EntityWisp.class,EntityDataSerializers.STRING);
    private BlockPos currentFlightTarget;
    public int attackCounter,prevAttackCounter,aggroCooldown;
    public EntityWisp(EntityType<? extends EntityWisp> type,Level level){super(type,level);xpReward=5;setNoGravity(true);}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,22).add(Attributes.ATTACK_DAMAGE,3);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(ASPECT,"");}
    /** Native Entity.getType() is final; retain source NBT Type with an unambiguous API name. */
    public String getWispType(){return entityData.get(ASPECT);}public void setWispType(String type){entityData.set(ASPECT,type==null||type.length()>256?"":type);}
    public Aspect getAspect(){return Aspect.getAspect(getWispType());}
    @Override public boolean hurt(DamageSource source,float amount){if(source.getEntity() instanceof LivingEntity attacker){setTarget(attacker);aggroCooldown=200;}return super.hurt(source,amount);}
    @Override public void tick(){super.tick();setDeltaMovement(getDeltaMovement().multiply(1,.6F,1));if(level().isClientSide)Mob8NaturalEffects.wispAmbient(this);}
    @Override public void die(DamageSource source){super.die(source);if(level().isClientSide)Mob8NaturalEffects.wispDeath(this);}
    @Override protected void customServerAiStep(){super.customServerAiStep();var worldRandom=level().getRandom();if(getAspect()==null){var aspects=worldRandom.nextInt(10)==0?Aspect.getCompoundAspects():Aspect.getPrimalAspects();if(aspects.isEmpty())aspects=Aspect.getPrimalAspects();if(!aspects.isEmpty())setWispType(aspects.get(worldRandom.nextInt(aspects.size())).getTag());}if(level().getDifficulty()==Difficulty.PEACEFUL){discard();return;}prevAttackCounter=attackCounter;
        var target=getTarget();if(target!=null&&getSensing().hasLineOfSight(target)){if(distanceToSqr(target)>128)steer(target.getX(),target.getY()+target.getEyeHeight()*.66F,target.getZ(),.5F);}
        else{if(currentFlightTarget!=null&&(!level().isEmptyBlock(currentFlightTarget)||currentFlightTarget.getY()<1||currentFlightTarget.getY()>level().getHeight(Heightmap.Types.MOTION_BLOCKING,currentFlightTarget.getX(),currentFlightTarget.getZ())+8))currentFlightTarget=null;if(currentFlightTarget==null||random.nextInt(30)==0||currentFlightTarget.distToCenterSqr(position())<4)currentFlightTarget=new BlockPos((int)getX()+random.nextInt(7)-random.nextInt(7),(int)getY()+random.nextInt(6)-2,(int)getZ()+random.nextInt(7)-random.nextInt(7));steer(currentFlightTarget.getX()+.5,currentFlightTarget.getY()+.1,currentFlightTarget.getZ()+.5,.15F);}
        if(target!=null&&(!target.isAlive()||target instanceof Player player&&player.getAbilities().invulnerable))setTarget(null);aggroCooldown--;if(worldRandom.nextInt(1000)==0&&(getTarget()==null||aggroCooldown--<=0)){setTarget(level().getNearestPlayer(this,16));if(getTarget()!=null)aggroCooldown=50;}
        target=getTarget();if(isAlive()&&target!=null&&distanceToSqr(target)<256){setYRot(-(float)Math.atan2(target.getX()-getX(),target.getZ()-getZ())*180/3.141593F);yBodyRot=getYRot();if(getSensing().hasLineOfSight(target)){attackCounter++;if(attackCounter==20){playSound(SoundsTC.zap,1,1.1F);Mob8NaturalEffects.zap(this,target);Vec3 v=target.getDeltaMovement();boolean stationary=Mob8NaturalRules.wispStationary(v.x,v.y,v.z);if(worldRandom.nextFloat()<(stationary?.66F:.4F))target.hurt(damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE)+(stationary?1:0));attackCounter=-20+worldRandom.nextInt(20);}}else if(attackCounter>0)attackCounter--;}
    }
    private void steer(double x,double y,double z,float forward){Vec3 v=getDeltaMovement();setDeltaMovement(v.x+(Math.signum(x-getX())*.5-v.x)*.1F,v.y+(Math.signum(y-getY())*.7F-v.y)*.1F,v.z+(Math.signum(z-getZ())*.5-v.z)*.1F);v=getDeltaMovement();zza=forward;setYRot(getYRot()+Mth.wrapDegrees((float)(Math.atan2(v.z,v.x)*180/Math.PI)-90-getYRot()));}
    @Override protected int decreaseAirSupply(int air){return air;}@Override protected MovementEmission getMovementEmission(){return MovementEmission.NONE;}
    @Override protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable(){return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;}
    @Override protected void dropCustomDeathLoot(ServerLevel server,DamageSource source,boolean recent){super.dropCustomDeathLoot(server,source,recent);if(getAspect()!=null)spawnAtLocation(ThaumcraftApiHelper.makeCrystal(getAspect()));}
    @Override public boolean shouldDropExperience(){return true;}@Override public int getMaxSpawnClusterSize(){return 2;}
    @Override public boolean checkSpawnObstruction(LevelReader level){return this.level().getEntitiesOfClass(EntityWisp.class,getBoundingBox().inflate(16)).size()<8&&super.checkSpawnObstruction(level);}
    @Override protected SoundEvent getAmbientSound(){return SoundsTC.wisplive;}@Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.LAVA_EXTINGUISH;}@Override protected SoundEvent getDeathSound(){return SoundsTC.wispdead;}@Override protected float getSoundVolume(){return .25F;}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putString("Type",getWispType());}@Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);setWispType(tag.getString("Type"));}
}
