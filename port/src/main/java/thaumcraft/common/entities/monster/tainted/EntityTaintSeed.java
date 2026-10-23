package thaumcraft.common.entities.monster.tainted;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.common.blocks.world.taint.TaintHelper;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.entities.Mob8BossRegistries;
import thaumcraft.common.entities.ai.combat.SourceMeleeAttackGoal;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.potions.WarpEffects;
import thaumcraft.common.world.aura.AuraHandler;
public class EntityTaintSeed extends Monster implements ITaintedMob {
    public int boost;private boolean firstRun;public float attackAnim;
    @Override protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable(){return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;}
    public EntityTaintSeed(EntityType<? extends EntityTaintSeed> type,Level level){super(type,level);xpReward=8;}
    public EntityTaintSeed(Level level){this(Mob8BossRegistries.TAINT_SEED,level);}
    protected int getArea(){return 1;}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,75).add(Attributes.ATTACK_DAMAGE,4).add(Attributes.MOVEMENT_SPEED,0);}
    @Override protected void registerGoals(){goalSelector.addGoal(1,new SourceMeleeAttackGoal(this,1,false));targetSelector.addGoal(0,new HurtByTargetGoal(this));targetSelector.addGoal(1,new NearestAttackableTargetGoal<>(this,Player.class,true));}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("boost",boost);}@Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);boost=tag.getInt("boost");}
    @Override public boolean canAttackType(EntityType<?> type){return !Mob8BossRegistries.isTainted(type)&&super.canAttackType(type);}@Override public boolean isAlliedTo(Entity entity){return entity instanceof ITaintedMob||super.isAlliedTo(entity);}
    @Override public boolean checkSpawnRules(LevelAccessor level,MobSpawnType reason){return level.getDifficulty()!=Difficulty.PEACEFUL&&checkSpawnObstruction(level)&&thaumcraft.common.lib.utils.EntityUtils.getEntitiesInRange(level(),blockPosition(),this,EntityTaintSeed.class,ModConfig.CONFIG_WORLD.taintSpreadArea*.8).isEmpty();}
    @Override public boolean checkSpawnObstruction(LevelReader level){return !level.containsAnyLiquid(getBoundingBox())&&level.isUnobstructed(this);}
    @Override public void die(DamageSource source){TaintHelper.removeTaintSeed(level(),blockPosition());super.die(source);}
    @Override public void tick(){super.tick();if(!level().isClientSide){if(!firstRun||tickCount%1200==0){TaintHelper.removeTaintSeed(level(),blockPosition());TaintHelper.addTaintSeed(level(),blockPosition());firstRun=true;}if(isAlive()){boolean pulse=tickCount%20==0;if(boost>0||pulse){float saturation=boost>0?1:AuraHandler.getFluxSaturation(level(),blockPosition());if(boost>0)boost--;if(saturation<=0){hurt(damageSources().starve(),.5F);AuraHelper.polluteAura(level(),blockPosition(),.1F,false);}else{int area=getArea();TaintHelper.spreadFibres(level(),blockPosition().offset(random.nextInt(area*6+1)-area*3,random.nextInt(area*2+1)-area,random.nextInt(area*6+1)-area*3),true);}}if(pulse){if(getTarget()!=null&&distanceToSqr(getTarget())<getArea()*256&&hasLineOfSight(getTarget()))EntityTaintacle.spawnTentaclesAt(this,getTarget());for(LivingEntity living:thaumcraft.common.lib.utils.EntityUtils.getEntitiesInRange(level(),blockPosition(),this,LivingEntity.class,getArea()*4))living.addEffect(new MobEffectInstance(WarpEffects.FLUX_TAINT,100,getArea()-1,false,true));}}}else{attackAnim*=.75F;if(attackAnim<.001F)attackAnim=0;thaumcraft.common.entities.Mob8BossPresentationHooks.seedTick.accept(this);}}
    @Override public boolean doHurtTarget(Entity target){level().broadcastEntityEvent(this,(byte)16);playSound(SoundsTC.tentacle,getSoundVolume(),getVoicePitch());return super.doHurtTarget(target);}@Override public void handleEntityEvent(byte event){if(event==16)attackAnim=.5F;else super.handleEntityEvent(event);}
    @Override public boolean isPushable(){return false;}@Override public boolean isPickable(){return true;}@Override public void moveRelative(float amount,Vec3 vector){}@Override public void move(MoverType type,Vec3 vector){super.move(type,new Vec3(0,Math.min(0,vector.y),0));}@Override protected int decreaseAirSupply(int air){return air;}@Override public boolean removeWhenFarAway(double distance){return false;}
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){spawnAtLocation(ThaumcraftApiHelper.makeCrystal(Aspect.FLUX),getBbHeight()/2);}
    @Override protected SoundEvent getAmbientSound(){return SoundEvents.CHORUS_FLOWER_DEATH;}@Override protected SoundEvent getHurtSound(DamageSource source){return SoundsTC.tentacle;}@Override protected SoundEvent getDeathSound(){return SoundsTC.tentacle;}@Override public int getAmbientSoundInterval(){return 200;}@Override public float getVoicePitch(){return 1.3F-getBbHeight()/10;}@Override protected float getSoundVolume(){return getBbHeight()/8;}
}
