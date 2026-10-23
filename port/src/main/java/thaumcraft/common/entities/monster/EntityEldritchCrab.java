package thaumcraft.common.entities.monster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.lib.SoundsTC;

public final class EntityEldritchCrab extends Monster implements IEldritchMob {
    private static final EntityDataAccessor<Boolean> HELM=SynchedEntityData.defineId(EntityEldritchCrab.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> RIDING=SynchedEntityData.defineId(EntityEldritchCrab.class,EntityDataSerializers.INT);
    private int attackTime;
    public EntityEldritchCrab(EntityType<? extends EntityEldritchCrab> type,Level level){super(type,level);xpReward=6;((GroundPathNavigation)getNavigation()).setCanOpenDoors(true);}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.ATTACK_DAMAGE,4).add(Attributes.MOVEMENT_SPEED,.3);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new LeapAtTargetGoal(this,.63F));goalSelector.addGoal(3,new MeleeAttackGoal(this,1,false));goalSelector.addGoal(7,new RandomStrollGoal(this,.8));goalSelector.addGoal(8,new RandomLookAroundGoal(this));targetSelector.addGoal(1,new HurtByTargetGoal(this).setAlertOthers());targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));targetSelector.addGoal(3,new NearestAttackableTargetGoal<>(this,EntityCultist.class,true));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(HELM,false);builder.define(RIDING,-1);}
    public boolean hasHelm(){return entityData.get(HELM);}public void setHelm(boolean value){entityData.set(HELM,value);}public int getRiding(){return entityData.get(RIDING);}public void setRiding(int id){entityData.set(RIDING,id);}
    @Override public net.minecraft.world.phys.Vec3 getVehicleAttachmentPoint(Entity vehicle){return isPassenger()?new net.minecraft.world.phys.Vec3(0,-.5,0):super.getVehicleAttachmentPoint(vehicle);}
    @Override public int getArmorValue(){return hasHelm()?5:0;}@Override public boolean canPickUpLoot(){return false;}@Override public boolean isAlliedTo(Entity entity){return entity instanceof EntityEldritchCrab;}
    @Override public boolean canBeAffected(MobEffectInstance effect){return !effect.getEffect().equals(MobEffects.POISON)&&super.canBeAffected(effect);}
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor world,DifficultyInstance difficulty,MobSpawnType reason,SpawnGroupData data){setHelm(world.getDifficulty()==Difficulty.HARD||random.nextFloat()<.33F);if(data==null){data=new CrabGroup();if(world.getDifficulty()==Difficulty.HARD&&world.getRandom().nextFloat()<.1F*difficulty.getSpecialMultiplier()){int r=world.getRandom().nextInt(5);((CrabGroup)data).effect=r<=1?MobEffects.MOVEMENT_SPEED:r==2?MobEffects.DAMAGE_BOOST:r==3?MobEffects.REGENERATION:MobEffects.INVISIBILITY;}}if(data instanceof CrabGroup group&&group.effect!=null)addEffect(new MobEffectInstance(group.effect,Integer.MAX_VALUE));return super.finalizeSpawn(world,difficulty,reason,data);}
    private static final class CrabGroup implements SpawnGroupData {net.minecraft.core.Holder<MobEffect> effect;}
    @Override public void tick(){super.tick();attackTime--;if(tickCount<20)fallDistance=0;if(!level().isClientSide){var target=getTarget();if(getVehicle()==null&&target!=null&&!target.isVehicle()&&!onGround()&&!hasHelm()&&target.isAlive()&&getY()-target.getY()>=target.getBbHeight()/2&&distanceToSqr(target)<4){startRiding(target);setRiding(target.getId());}if(getVehicle()!=null&&isAlive()&&attackTime<=0){attackTime=10+random.nextInt(10);doHurtTarget(getVehicle());if(random.nextFloat()<.2){stopRiding();setRiding(-1);}}if(getVehicle()==null&&getRiding()!=-1)setRiding(-1);}else if(getVehicle()==null&&getRiding()!=-1){var vehicle=level().getEntity(getRiding());if(vehicle!=null)startRiding(vehicle);}else if(getVehicle()!=null&&getRiding()==-1)stopRiding();}
    @Override public boolean doHurtTarget(Entity target){if(!super.doHurtTarget(target))return false;playSound(SoundsTC.crabclaw,1,.9F+random.nextFloat()*.2F);return true;}
    @Override protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable(){return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;}
    @Override public boolean hurt(DamageSource source,float damage){boolean result=super.hurt(source,damage);if(hasHelm()&&getHealth()/getMaxHealth()<=.5F){setHelm(false);getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.3);if(level() instanceof ServerLevel server)server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM,new ItemStack(ItemsTC.crimsonPlateChest)),getX(),getY()+.3,getZ(),5,.2,.1,.2,.02);}return result;}
    @Override protected void dropCustomDeathLoot(ServerLevel server,DamageSource source,boolean recent){super.dropCustomDeathLoot(server,source,recent);int looting=EntityBrainyZombie.looting(source);if(recent&&(random.nextInt(3)==0||random.nextInt(1+looting)>0))spawnAtLocation(Items.ENDER_PEARL);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("helm",hasHelm());}@Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);setHelm(tag.getBoolean("helm"));if(!hasHelm())getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.3);}
    @Override protected SoundEvent getAmbientSound(){return SoundsTC.crabtalk;}@Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.GENERIC_HURT;}@Override protected SoundEvent getDeathSound(){return SoundsTC.crabdeath;}@Override public int getAmbientSoundInterval(){return 160;}@Override protected void playStepSound(BlockPos pos,BlockState state){playSound(SoundEvents.SPIDER_STEP,.15F,1);}
}
