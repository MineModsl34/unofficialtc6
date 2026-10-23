package thaumcraft.common.entities.monster.boss;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.*;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.*;
import thaumcraft.common.entities.monster.tainted.EntityTaintacle;
import thaumcraft.common.lib.utils.EntityUtils;
public final class EntityTaintacleGiant extends EntityTaintacle implements IEldritchMob {
    private final ServerBossEvent bossInfo=new ServerBossEvent(getDisplayName(),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS);
    private static final EntityDataAccessor<Integer> AGGRO=SynchedEntityData.defineId(EntityTaintacleGiant.class,EntityDataSerializers.INT);
    public EntityTaintacleGiant(EntityType<? extends EntityTaintacleGiant> type,Level level){super(type,level);xpReward=20;bossInfo.setDarkenScreen(true);}
    public EntityTaintacleGiant(Level level){this(Mob8BossRegistries.TAINTACLE_GIANT,level);}
    public static AttributeSupplier.Builder createAttributes(){return EntityTaintacle.createAttributes().add(Attributes.MAX_HEALTH,175).add(Attributes.ATTACK_DAMAGE,9);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(AGGRO,0);}
    public int getAnger(){return entityData.get(AGGRO);}public void setAnger(int amount){entityData.set(AGGRO,amount);}
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,MobSpawnType reason,SpawnGroupData data){EntityUtils.makeChampion(this,true);return data;}
    @Override public void tick(){super.tick();if(getAnger()>0)setAnger(getAnger()-1);if(level().isClientSide&&random.nextInt(15)==0&&getAnger()>0)level().addParticle(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,getRandomX(1),getBoundingBox().minY+getBbHeight()+random.nextFloat()*.5,getRandomZ(1),random.nextGaussian()*.02,random.nextGaussian()*.02,random.nextGaussian()*.02);if(!level().isClientSide){if(tickCount%30==0)heal(1);bossInfo.setProgress(getHealth()/getMaxHealth());}}
    @Override public boolean hurt(DamageSource source,float damage){if(!level().isClientSide)damage=EntityThaumcraftBoss.enrage(this,source,damage,getAnger()==0);return super.hurt(source,damage);}
    @Override public boolean checkSpawnRules(LevelAccessor level,MobSpawnType reason){return false;}@Override public boolean removeWhenFarAway(double distance){return false;}@Override protected int decreaseAirSupply(int air){return air;}
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){if(EntityUtils.getEntitiesInRange(level,getX(),getY(),getZ(),this,EntityTaintacleGiant.class,48).isEmpty())level.addFreshEntity(new EntitySpecialItem(level,getX(),getY()+getBbHeight()/2,getZ(),new net.minecraft.world.item.ItemStack(ItemsTC.primordialPearl)));}
    @Override public void startSeenByPlayer(ServerPlayer player){super.startSeenByPlayer(player);bossInfo.addPlayer(player);}@Override public void stopSeenByPlayer(ServerPlayer player){super.stopSeenByPlayer(player);bossInfo.removePlayer(player);}
}
