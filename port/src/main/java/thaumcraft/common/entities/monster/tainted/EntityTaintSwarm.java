package thaumcraft.common.entities.monster.tainted;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.common.blocks.world.taint.TaintHelper;
import thaumcraft.common.entities.Mob8BossRegistries;
import thaumcraft.common.lib.SoundsTC;
public final class EntityTaintSwarm extends Monster implements ITaintedMob {
    private static final EntityDataAccessor<Boolean> SUMMONED=SynchedEntityData.defineId(EntityTaintSwarm.class,EntityDataSerializers.BOOLEAN);
    private BlockPos currentFlightTarget;public int damBonus;private int attackTime;
    @Override protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable(){return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;}
    public EntityTaintSwarm(EntityType<? extends EntityTaintSwarm> type,Level level){super(type,level);}
    public EntityTaintSwarm(Level level){this(Mob8BossRegistries.TAINT_SWARM,level);}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.ATTACK_DAMAGE,2);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(SUMMONED,false);}
    public boolean getIsSummoned(){return entityData.get(SUMMONED);}public void setIsSummoned(boolean summoned){entityData.set(SUMMONED,summoned);}
    @Override public boolean isAlliedTo(Entity entity){return entity instanceof ITaintedMob||super.isAlliedTo(entity);}@Override public boolean canAttackType(EntityType<?> type){return !Mob8BossRegistries.isTainted(type)&&super.canAttackType(type);}@Override public boolean isPushable(){return false;}@Override public boolean isIgnoringBlockTriggers(){return true;}@Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return false;}
    @Override public void tick(){super.tick();setDeltaMovement(getDeltaMovement().multiply(1,.6,1));if(level().isClientSide)thaumcraft.common.entities.Mob8BossPresentationHooks.swarmTick.accept(this);}
    @Override public void aiStep(){super.aiStep();if(level().isClientSide)return;if(attackTime>0)attackTime--;LivingEntity target=getTarget();if(target==null){if(getIsSummoned())hurt(damageSources().generic(),5);if(currentFlightTarget!=null&&(!level().isEmptyBlock(currentFlightTarget)||currentFlightTarget.getY()<level().getMinBuildHeight()+1||currentFlightTarget.getY()>level().getHeight(Heightmap.Types.MOTION_BLOCKING,currentFlightTarget.getX(),currentFlightTarget.getZ())+2||!TaintHelper.isNearTaintSeed(level(),currentFlightTarget)))currentFlightTarget=null;if(currentFlightTarget==null||random.nextInt(30)==0||position().distanceToSqr(Vec3.atCenterOf(currentFlightTarget))<4)currentFlightTarget=new BlockPos((int)getX()+random.nextInt(7)-random.nextInt(7),(int)getY()+random.nextInt(6)-2,(int)getZ()+random.nextInt(7)-random.nextInt(7));steer(currentFlightTarget.getX()+.5,currentFlightTarget.getY()+.1,currentFlightTarget.getZ()+.5,.015000000014901161);if(!getIsSummoned())setTarget(level().getNearestPlayer(this,8));}else{steer(target.getX(),target.getEyeY(),target.getZ(),.025000000149011613);if(!target.isAlive()||target instanceof Player player&&player.getAbilities().invulnerable){setTarget(null);return;}if(hasLineOfSight(target)&&attackTime<=0&&distanceTo(target)<3&&target.getBoundingBox().maxY>getBoundingBox().minY&&target.getBoundingBox().minY<getBoundingBox().maxY){if(getIsSummoned())target.lastHurtByPlayerTime=100;attackTime=15+random.nextInt(10);Vec3 motion=target.getDeltaMovement();if(doHurtTarget(target))target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,100));target.setDeltaMovement(motion);target.hurtMarked=false;playSound(SoundsTC.swarmattack,.3F,.9F+random.nextFloat()*.2F);}}}
    private void steer(double x,double y,double z,double rate){Vec3 motion=getDeltaMovement();setDeltaMovement(motion.x+(Math.signum(x-getX())*.5-motion.x)*rate,motion.y+(Math.signum(y-getY())*.7F-motion.y)*.1F,motion.z+(Math.signum(z-getZ())*.5-motion.z)*rate);setYRot(getYRot()+Mth.wrapDegrees((float)(Math.atan2(getDeltaMovement().z,getDeltaMovement().x)*180/Math.PI)-90-getYRot()));}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("summoned",getIsSummoned());tag.putByte("damBonus",(byte)damBonus);}@Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);setIsSummoned(tag.getBoolean("summoned"));damBonus=tag.getByte("damBonus");/* Source's loaded damBonus never reapplies an attribute modifier. */}
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){if(random.nextBoolean())spawnAtLocation(ThaumcraftApiHelper.makeCrystal(Aspect.FLUX),getBbHeight()/2);}
    @Override protected SoundEvent getAmbientSound(){return SoundsTC.swarm;}@Override protected SoundEvent getHurtSound(DamageSource source){return SoundsTC.swarmattack;}@Override protected SoundEvent getDeathSound(){return SoundsTC.swarmattack;}@Override protected float getSoundVolume(){return .1F;}
}
