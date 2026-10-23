package thaumcraft.common.entities.monster.boss;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.EntitySpecialItem;
import thaumcraft.common.lib.utils.EntityUtils;

/** BETA26 common boss lifecycle, including the original aggro-loop indexing oddities. */
public class EntityThaumcraftBoss extends Monster {
    protected final ServerBossEvent bossInfo = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private static final EntityDataAccessor<Integer> AGGRO = SynchedEntityData.defineId(EntityThaumcraftBoss.class, EntityDataSerializers.INT);
    protected final Map<Integer,Integer> aggro = new HashMap<>();
    protected int spawnTimer;
    public EntityThaumcraftBoss(EntityType<? extends EntityThaumcraftBoss> type, Level level) { super(type,level); xpReward=50; bossInfo.setDarkenScreen(true); }
    public static AttributeSupplier.Builder createAttributes() { return Monster.createMonsterAttributes().add(Attributes.KNOCKBACK_RESISTANCE,.95).add(Attributes.FOLLOW_RANGE,40); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder);builder.define(AGGRO,0); }
    public int getAnger(){return entityData.get(AGGRO);} public void setAnger(int value){entityData.set(AGGRO,value);} public int getSpawnTimer(){return spawnTimer;}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(hasRestriction()){tag.putInt("HomeD",(int)getRestrictRadius());tag.putInt("HomeX",getRestrictCenter().getX());tag.putInt("HomeY",getRestrictCenter().getY());tag.putInt("HomeZ",getRestrictCenter().getZ());}}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);if(tag.contains("HomeD"))restrictTo(new net.minecraft.core.BlockPos(tag.getInt("HomeX"),tag.getInt("HomeY"),tag.getInt("HomeZ")),tag.getInt("HomeD"));bossInfo.setName(getDisplayName());}
    // Mob.serverAiStep is final in 1.21: gate the entire goal/navigation pass, not only its final custom hook.
    protected boolean sourceAiPaused(){return spawnTimer>0;}
    @Override public boolean isEffectiveAi(){return super.isEffectiveAi()&&!sourceAiPaused();}
    @Override protected void customServerAiStep(){super.customServerAiStep();if(getTarget()!=null&&!getTarget().isAlive())setTarget(null);}
    @Override public void startSeenByPlayer(ServerPlayer p){super.startSeenByPlayer(p);bossInfo.addPlayer(p);}
    @Override public void stopSeenByPlayer(ServerPlayer p){super.stopSeenByPlayer(p);bossInfo.removePlayer(p);}
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,MobSpawnType reason,SpawnGroupData data){restrictTo(blockPosition(),24);generateName();bossInfo.setName(getDisplayName());return data;}
    public void generateName() {}
    @Override protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable(){return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;}
    @Override public void tick(){super.tick();if(spawnTimer>0)spawnTimer--;if(getAnger()>0)setAnger(getAnger()-1);
        if(level().isClientSide&&random.nextInt(15)==0&&getAnger()>0)level().addParticle(ParticleTypes.ANGRY_VILLAGER,getRandomX(1),getBoundingBox().minY+getBbHeight()+random.nextFloat()*.5,getRandomZ(1),random.nextGaussian()*.02,random.nextGaussian()*.02,random.nextGaussian()*.02);
        if(!level().isClientSide){bossInfo.setProgress(getHealth()/getMaxHealth());if(tickCount%30==0)heal(1);if(getTarget()!=null&&tickCount%20==0)updateSourceAggro();}}
    private void updateSourceAggro(){List<Integer> expired=new ArrayList<>();int players=0;int id=getTarget().getId(),damage=aggro.getOrDefault(id,0),last=damage;Entity selected=null;
        for(var entry:aggro.entrySet())if(Boss8Rules.sourceAggroCandidate(entry.getValue(),damage,last)){
            // Source looks up the PREVIOUS id, then stores candidate id as last-damage; not silently corrected.
            selected=level().getEntity(id);if(selected!=null&&selected.isAlive()&&distanceToSqr(selected)<=16384){id=entry.getKey();last=entry.getKey();if(selected instanceof Player)players++;}else expired.add(entry.getKey());}
        expired.forEach(aggro::remove);if(selected instanceof LivingEntity living&&id!=getTarget().getId())setTarget(living);
        float oldMax=getMaxHealth();AttributeInstance health=getAttribute(Attributes.MAX_HEALTH),attack=getAttribute(Attributes.ATTACK_DAMAGE);
        for(int i=0;i<5;i++){health.removeModifier(EntityUtils.HPBUFF[i].id());attack.removeModifier(EntityUtils.DMGBUFF[i].id());}
        for(int i=0;i<Math.min(5,players-1);i++){health.addTransientModifier(EntityUtils.HPBUFF[i]);attack.addTransientModifier(EntityUtils.DMGBUFF[i]);}setHealth(getHealth()*getMaxHealth()/oldMax);
    }
    @Override public boolean hurt(DamageSource source,float damage){if(!level().isClientSide){if(source.getEntity() instanceof LivingEntity attacker)aggro.merge(attacker.getId(),(int)damage,Integer::sum);damage=enrage(this,source,damage,getAnger()==0); }return super.hurt(source,damage);}
    public static float enrage(LivingEntity boss,DamageSource source,float damage,boolean idle){if(damage>35){if(idle){boss.addEffect(new MobEffectInstance(MobEffects.REGENERATION,200,Boss8Rules.regenerationAmplifier(damage)));boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,200,Boss8Rules.strengthAmplifier(damage)));boss.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED,200,Boss8Rules.hasteAmplifier(damage)));if(boss instanceof EntityThaumcraftBoss b)b.setAnger(200);if(boss instanceof EntityTaintacleGiant b)b.setAnger(200);if(source.getEntity() instanceof Player p)p.displayClientMessage(Component.empty().append(boss.getName()).append(" ").append(Component.translatable("tc.boss.enrage")),true);}damage=35;}return damage;}
    @Override public boolean isInvulnerableTo(DamageSource source){return spawnTimer>0||super.isInvulnerableTo(source);}
    @Override public boolean isPushable(){return super.isPushable()&&spawnTimer==0;}
    @Override protected int decreaseAirSupply(int air){return air;}
    @Override public void makeStuckInBlock(BlockState state,Vec3 factor){}
    @Override public boolean canPickUpLoot(){return false;}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean isAlliedTo(Entity entity){return entity instanceof IEldritchMob;}
    @Override protected void populateDefaultEquipmentEnchantments(ServerLevelAccessor level,net.minecraft.util.RandomSource random,DifficultyInstance difficulty){}
    protected void dropPearl(){level().addFreshEntity(new EntitySpecialItem(level(),getX(),getY()+getBbHeight()/2,getZ(),new ItemStack(ItemsTC.primordialPearl)));}
    protected void dropRareBag(){spawnAtLocation(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("thaumcraft:loot_bag_rare"))),1.5F);}
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){super.dropCustomDeathLoot(level,source,recentlyHit);dropPearl();dropRareBag();}
}
