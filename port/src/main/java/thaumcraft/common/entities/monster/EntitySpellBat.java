package thaumcraft.common.entities.monster;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.*;
import thaumcraft.api.casters.*;
import thaumcraft.common.items.casters.*;
/** Actual source summoned mob, 5 health, forty-tick attacks and one health lost per spell. */
public class EntitySpellBat extends Monster {
    private static final EntityDataAccessor<Boolean> FRIENDLY=SynchedEntityData.defineId(EntitySpellBat.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> PACK=SynchedEntityData.defineId(EntitySpellBat.class,EntityDataSerializers.COMPOUND_TAG);
    public LivingEntity owner;public int damBonus,color=0xffffff;private UUID ownerUniqueId;private FocusPackage focusPackage;private BlockPos currentFlightTarget;private int attackTime;
    public EntitySpellBat(EntityType<? extends EntitySpellBat> type,Level level){super(type,level);setNoGravity(true);}
    public EntitySpellBat(FocusPackage pack,boolean friendly){this(CasterRegistries.SPELLBAT,pack.world);focusPackage=pack;setOwner(pack.getCaster());setIsFriendly(friendly);entityData.set(PACK,pack.serialize());}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,5).add(Attributes.ATTACK_DAMAGE,1);}
    @Override protected void registerGoals(){}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(FRIENDLY,false);builder.define(PACK,new CompoundTag());}
    public boolean getIsFriendly(){return entityData.get(FRIENDLY);}public void setIsFriendly(boolean value){entityData.set(FRIENDLY,value);}public void setOwner(LivingEntity owner){this.owner=owner;ownerUniqueId=owner==null?null:owner.getUUID();}public LivingEntity getOwner(){if(owner==null&&ownerUniqueId!=null&&level() instanceof net.minecraft.server.level.ServerLevel server&&server.getEntity(ownerUniqueId) instanceof LivingEntity living)owner=living;return owner;}
    public FocusPackage getFocusPackage(){if(focusPackage==null){focusPackage=new FocusPackage();focusPackage.deserialize(entityData.get(PACK));focusPackage.world=level();}return focusPackage;}
    @Override public boolean isPushable(){return false;}@Override public boolean isIgnoringBlockTriggers(){return true;}@Override public boolean causeFallDamage(float distance,float multiplier,net.minecraft.world.damagesource.DamageSource source){return false;}
    @Override protected boolean shouldDropLoot(){return false;}@Override protected SoundEvent getAmbientSound(){return SoundEvents.BAT_AMBIENT;}@Override protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source){return SoundEvents.BAT_HURT;}@Override protected SoundEvent getDeathSound(){return SoundEvents.BAT_DEATH;}@Override protected float getSoundVolume(){return .1F;}
    @Override public boolean isAlliedTo(Entity entity){var owner=getOwner();return owner==null?super.isAlliedTo(entity):entity==owner||owner.isAlliedTo(entity)||entity.isAlliedTo(owner);}
    @Override public void tick(){super.tick();setDeltaMovement(getDeltaMovement().multiply(1,.6,1));if(!level().isClientSide&&(tickCount>600||getOwner()==null)){discard();return;}if(level().isClientSide){var effects=getFocusPackage().getFocusEffects();if(effects.length>0){int r=0,g=0,b=0;for(var e:effects){int c=FocusEngine.getElementColor(e.getKey());r+=(c>>16)&255;g+=(c>>8)&255;b+=c&255;}color=r/effects.length<<16|g/effects.length<<8|b/effects.length;effects[random.nextInt(effects.length)].renderParticleFX(level(),getX()+random.nextGaussian()*.125,getY()+getBbHeight()/2+random.nextGaussian()*.125,getZ()+random.nextGaussian()*.125,0,0,0);}}}
    @Override protected void customServerAiStep(){super.customServerAiStep();if(attackTime>0)attackTime--;var target=getTarget();if(target==null){if(currentFlightTarget!=null&&(!level().isEmptyBlock(currentFlightTarget)||currentFlightTarget.getY()<1))currentFlightTarget=null;if(currentFlightTarget==null||random.nextInt(30)==0||distanceToSqr(Vec3.atCenterOf(currentFlightTarget))<4)currentFlightTarget=new BlockPos((int)getX()+random.nextInt(7)-random.nextInt(7),(int)getY()+random.nextInt(6)-2,(int)getZ()+random.nextInt(7)-random.nextInt(7));for(var candidate:FocusTargeting.nearby(level(),this,12))if(FocusTargeting.friendly(getOwner(),candidate)==getIsFriendly()&&(getIsFriendly()||!isAlliedTo(candidate))){setTarget(candidate);break;}}Vec3 destination=target==null?new Vec3(currentFlightTarget.getX()+.5,currentFlightTarget.getY()+.1,currentFlightTarget.getZ()+.5):target.position().add(0,target.getEyeHeight()*.66,0);var delta=destination.subtract(position());var motion=getDeltaMovement();setDeltaMovement(motion.add((Math.signum(delta.x)*.5-motion.x)*.1,(Math.signum(delta.y)*.7-motion.y)*.1,(Math.signum(delta.z)*.5-motion.z)*.1));setYRot((float)(Math.atan2(getDeltaMovement().z,getDeltaMovement().x)*180/Math.PI)-90);if(target==null)return;if(!target.isAlive()||!getIsFriendly()&&target instanceof Player player&&player.getAbilities().invulnerable){setTarget(null);return;}if(attackTime<=0&&distanceTo(target)<Math.max(2.5F,target.getBbWidth()*1.1F)&&target.getBoundingBox().maxY>getBoundingBox().minY&&target.getBoundingBox().minY<getBoundingBox().maxY&&FocusTargeting.visible(this,target)){attackTime=40;var center=target.position().add(0,target.getBbHeight()/2,0);FocusEngine.runFocusPackage(getFocusPackage().copy(getOwner()),new Trajectory[]{new Trajectory(position(),center.subtract(position()))},new HitResult[]{new EntityHitResult(target,center)});setHealth(getHealth()-1);playSound(SoundEvents.BAT_HURT,.5F,.9F+random.nextFloat()*.2F);}}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(ownerUniqueId!=null)tag.putUUID("OwnerUUID",ownerUniqueId);tag.put("pack",getFocusPackage().serialize());tag.putBoolean("friendly",getIsFriendly());}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);ownerUniqueId=tag.hasUUID("OwnerUUID")?tag.getUUID("OwnerUUID"):null;setIsFriendly(tag.getBoolean("friendly"));focusPackage=new FocusPackage();focusPackage.deserialize(tag.getCompound("pack"));focusPackage.world=level();entityData.set(PACK,focusPackage.serialize());}
}
