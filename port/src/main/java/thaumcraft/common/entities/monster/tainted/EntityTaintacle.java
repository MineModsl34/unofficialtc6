package thaumcraft.common.entities.monster.tainted;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.common.blocks.world.taint.TaintHelper;
import thaumcraft.common.entities.Mob8BossRegistries;
import thaumcraft.common.entities.ai.combat.SourceMeleeAttackGoal;
import thaumcraft.common.lib.SoundsTC;
public class EntityTaintacle extends Monster implements ITaintedMob {
    public float flailIntensity=1;
    @Override protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable(){return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;}
    public EntityTaintacle(EntityType<? extends EntityTaintacle> type,Level level){super(type,level);xpReward=8;}
    public EntityTaintacle(Level level){this(Mob8BossRegistries.TAINTACLE,level);}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,50).add(Attributes.ATTACK_DAMAGE,7).add(Attributes.MOVEMENT_SPEED,0);}
    @Override protected void registerGoals(){goalSelector.addGoal(1,new SourceMeleeAttackGoal(this,1,false));goalSelector.addGoal(2,new LookAtPlayerGoal(this,Player.class,6));goalSelector.addGoal(3,new RandomLookAroundGoal(this));targetSelector.addGoal(0,new HurtByTargetGoal(this));targetSelector.addGoal(1,new NearestAttackableTargetGoal<>(this,Player.class,true));}
    @Override public boolean canAttackType(EntityType<?> type){return !Mob8BossRegistries.isTainted(type)&&super.canAttackType(type);}@Override public boolean isAlliedTo(Entity entity){return entity instanceof ITaintedMob||super.isAlliedTo(entity);}
    @Override public boolean checkSpawnRules(LevelAccessor level,MobSpawnType reason){return level.getDifficulty()!=Difficulty.PEACEFUL&&(TaintHelper.isTaint(level.getBlockState(blockPosition()))||TaintHelper.isTaint(level.getBlockState(blockPosition().below())));}
    @Override public void move(MoverType type,Vec3 vector){super.move(type,new Vec3(0,Math.min(0,vector.y),0));}
    @Override public void tick(){super.tick();if(!level().isClientSide&&tickCount%20==0){if(!TaintHelper.onTaint(level(),blockPosition()))actuallyHurt(damageSources().starve(),1);if(!(this instanceof EntityTaintacleSmall)&&tickCount%40==0&&getTarget()!=null&&distanceToSqr(getTarget())>16&&distanceToSqr(getTarget())<256&&hasLineOfSight(getTarget()))spawnTentacles(getTarget());}if(level().isClientSide){if(flailIntensity>1)flailIntensity-=.01F;if(tickCount<getBbHeight()*10F&&onGround())thaumcraft.common.lib.fx.SourceFX.local(level(),"taintacle_arise",this.position(),net.minecraft.world.phys.Vec3.ZERO,this.getId(),0);}}
    protected void spawnTentacles(Entity target){spawnTentaclesAt(this,target);}
    public static void spawnTentaclesAt(Mob parent,Entity target){Level level=parent.level();boolean eldritch=level.getBiome(target.blockPosition()).is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,ResourceLocation.parse("thaumcraft:eldritch")))||level.getBiome(target.blockPosition()).is(ResourceLocation.parse("thaumcraft:eldritch"));if(!eldritch&&!TaintHelper.onTaint(level,target.blockPosition()))return;EntityTaintacleSmall small=new EntityTaintacleSmall(level);small.moveTo(target.getX()+level.random.nextFloat()-level.random.nextFloat(),target.getY(),target.getZ()+level.random.nextFloat()-level.random.nextFloat(),0,0);level.addFreshEntity(small);parent.playSound(SoundsTC.tentacle,parent.getBbHeight()/8,1.3F-parent.getBbHeight()/10);if(eldritch&&level.isEmptyBlock(target.blockPosition())&&TaintHelper.adjacentSolid(level,target.blockPosition()))level.setBlock(target.blockPosition(),BlocksTC.taintFibre.defaultBlockState(),3);}
    @Override public boolean doHurtTarget(Entity target){level().broadcastEntityEvent(this,(byte)16);playSound(SoundsTC.tentacle,getSoundVolume(),getVoicePitch());return super.doHurtTarget(target);}
    @Override public void handleEntityEvent(byte event){if(event==16)flailIntensity=3;else super.handleEntityEvent(event);}
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){spawnAtLocation(ThaumcraftApiHelper.makeCrystal(Aspect.FLUX),getBbHeight()/2);}
    @Override protected SoundEvent getAmbientSound(){return SoundEvents.CHORUS_FLOWER_DEATH;}@Override protected SoundEvent getHurtSound(DamageSource source){return SoundsTC.tentacle;}@Override protected SoundEvent getDeathSound(){return SoundsTC.tentacle;}@Override public int getAmbientSoundInterval(){return 200;}@Override public float getVoicePitch(){return 1.3F-getBbHeight()/10;}@Override protected float getSoundVolume(){return getBbHeight()/8;}
}
