package thaumcraft.common.entities.monster.boss;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.common.entities.Mob8BossRegistries;
import thaumcraft.common.entities.ai.combat.*;
import thaumcraft.common.entities.projectile.EntityGolemOrb;
import thaumcraft.common.lib.SoundsTC;
public final class EntityEldritchGolem extends EntityThaumcraftBoss implements IEldritchMob,RangedAttackMob {
    private static final EntityDataAccessor<Boolean> HEADLESS=SynchedEntityData.defineId(EntityEldritchGolem.class,EntityDataSerializers.BOOLEAN);
    private int beamCharge,attackTimer;private boolean chargingBeam,headlessGoal;
    public EntityEldritchGolem(EntityType<? extends EntityEldritchGolem> type,Level level){super(type,level);}
    public EntityEldritchGolem(Level level){this(Mob8BossRegistries.ELDRITCH_GOLEM,level);}
    public static AttributeSupplier.Builder createAttributes(){return EntityThaumcraftBoss.createAttributes().add(Attributes.MOVEMENT_SPEED,.3).add(Attributes.ATTACK_DAMAGE,10).add(Attributes.MAX_HEALTH,400).add(Attributes.ARMOR,6);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(3,new SourceMeleeAttackGoal(this,1.1,false));goalSelector.addGoal(6,new MoveTowardsRestrictionGoal(this,.8));goalSelector.addGoal(7,new RandomStrollGoal(this,.8));goalSelector.addGoal(8,new LookAtPlayerGoal(this,Player.class,8));goalSelector.addGoal(8,new RandomLookAroundGoal(this));targetSelector.addGoal(1,new HurtByTargetGoal(this).setAlertOthers());targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(HEADLESS,false);}
    public boolean isHeadless(){return entityData.get(HEADLESS);}public void setHeadless(boolean headless){entityData.set(HEADLESS,headless);if(headless)makeHeadless();}
    @Override protected EntityDimensions getDefaultDimensions(Pose pose){return super.getDefaultDimensions(pose).withEyeHeight(isHeadless()?3.33F:3);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key.equals(HEADLESS))refreshDimensions();}
    private void makeHeadless(){if(!headlessGoal){goalSelector.addGoal(2,new AILongRangeAttack(this,3,1,5,5,24));headlessGoal=true;}}
    @Override public void generateName(){if(thaumcraft.common.entities.monster.mods.ChampionSystem.modifier(this)>=0)setCustomName(Component.translatable("entity.Thaumcraft.EldritchGolem.name.custom",thaumcraft.common.entities.monster.mods.ChampionSystem.getModifierName(this)));}
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,MobSpawnType reason,SpawnGroupData data){spawnTimer=100;return super.finalizeSpawn(level,difficulty,reason,data);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("headless",isHeadless());}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);setHeadless(tag.getBoolean("headless"));}
    @Override public void tick(){if(spawnTimer==150&&!level().isClientSide)level().broadcastEntityEvent(this,(byte)18);if(spawnTimer>0)heal(2);super.tick();if(attackTimer>0)attackTimer--;
        BlockState ground=level().getBlockState(blockPosition());if(getDeltaMovement().horizontalDistanceSqr()>2.5000003E-7F&&random.nextInt(5)==0){if(!ground.isAir())level().addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,ground),getX()+(random.nextFloat()-.5)*getBbWidth(),getBoundingBox().minY+.1,getZ()+(random.nextFloat()-.5)*getBbWidth(),4*(random.nextFloat()-.5),.5,(random.nextFloat()-.5)*4);if(!level().isClientSide&&ground.getBlock() instanceof thaumcraft.common.blocks.world.BlockLootTC)level().destroyBlock(blockPosition(),true,this);}
        if(level().isClientSide){thaumcraft.common.lib.fx.SourceFX.local(level(),"eldritch_golem",this.position(),net.minecraft.world.phys.Vec3.ZERO,this.getId(),0);}
        else {BlockState state=level().getBlockState(blockPosition());float hardness=state.getDestroySpeed(level(),blockPosition());if(hardness>=0&&hardness<=.15F&&!state.isAir())level().destroyBlock(blockPosition(),true,this);if(isHeadless()&&beamCharge<=0)chargingBeam=true;if(isHeadless()&&chargingBeam){beamCharge++;level().broadcastEntityEvent(this,(byte)19);if(beamCharge==150)chargingBeam=false;}}}
    @Override public boolean hurt(DamageSource source,float damage){if(!level().isClientSide&&damage>getHealth()&&!isHeadless()){setHeadless(true);spawnTimer=100;double x=Mth.cos(getYRot()*Mth.DEG_TO_RAD)*.75,z=Mth.sin(getYRot()*Mth.DEG_TO_RAD)*.75;level().explode(this,getX()+x,getY()+getEyeHeight(),getZ()+z,2,Level.ExplosionInteraction.NONE);return false;}return super.hurt(source,damage);}
    @Override public boolean doHurtTarget(Entity target){if(attackTimer>0)return false;attackTimer=10;level().broadcastEntityEvent(this,(byte)4);boolean hit=target.hurt(damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE)*.75F);if(hit){target.push(0,.2000000059604645,0);if(isHeadless())target.push(-Mth.sin(getYRot()*Mth.DEG_TO_RAD)*1.5,.1,Mth.cos(getYRot()*Mth.DEG_TO_RAD)*1.5);}return hit;}
    @Override public void performRangedAttack(LivingEntity target,float power){if(!hasLineOfSight(target)||chargingBeam||beamCharge<=0)return;beamCharge-=15+random.nextInt(5);getLookControl().setLookAt(target.getX(),target.getBoundingBox().minY+target.getBbHeight()/2,target.getZ(),30,30);var look=getLookAngle();EntityGolemOrb orb=new EntityGolemOrb(level(),this,target,false);orb.setPos(orb.getX()+look.x,orb.getY(),orb.getZ()+look.z);orb.shoot(target.getX()+target.getDeltaMovement().x-getX(),target.getY()-getY()-target.getBbHeight()/2,target.getZ()+target.getDeltaMovement().z-getZ(),.66F,5);playSound(SoundsTC.egattack,1,1+random.nextFloat()*.1F);level().addFreshEntity(orb);}
    public int getAttackTimer(){return attackTimer;}
    @Override public void handleEntityEvent(byte id){if(id==4){attackTimer=10;playSound(SoundEvents.IRON_GOLEM_ATTACK,1,1);}else if(id==18)spawnTimer=150;else if(id==19){thaumcraft.common.lib.fx.SourceFX.local(level(),"eldritch_golem_charge",this.position(),net.minecraft.world.phys.Vec3.ZERO,this.getId(),0);}else super.handleEntityEvent(id);}
    @Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.IRON_GOLEM_HURT;}
    @Override protected SoundEvent getDeathSound(){return SoundEvents.IRON_GOLEM_DEATH;}
    @Override protected void playStepSound(BlockPos pos,BlockState state){playSound(SoundEvents.IRON_GOLEM_STEP,1,1);}
}
