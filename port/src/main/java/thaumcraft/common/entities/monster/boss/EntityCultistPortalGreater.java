package thaumcraft.common.entities.monster.boss;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.*;
import thaumcraft.common.entities.monster.cult.*;
import thaumcraft.common.lib.SoundsTC;
public final class EntityCultistPortalGreater extends Monster {
    private final ServerBossEvent bossInfo=new ServerBossEvent(getDisplayName(),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.NOTCHED_6);
    private int stage,stagecounter=200;public int pulse;
    public EntityCultistPortalGreater(EntityType<? extends EntityCultistPortalGreater> type,Level level){super(type,level);xpReward=30;bossInfo.setDarkenScreen(true);}
    public EntityCultistPortalGreater(Level level){this(Mob8BossRegistries.PORTAL_GREATER,level);}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,500).add(Attributes.ATTACK_DAMAGE,0).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.ARMOR,5);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("stage",stage);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);stage=tag.getInt("stage");}
    public int getStage(){return stage;}
    @Override protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable(){return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;}
    @Override public boolean isPushable(){return false;}@Override public boolean isPickable(){return true;}@Override public void move(MoverType type,Vec3 movement){}@Override public void aiStep(){}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<4096;}
    @Override public void tick(){super.tick();if(!level().isClientSide){if(stagecounter<=0){if(level().getNearestPlayer(this,48)!=null){level().broadcastEntityEvent(this,(byte)16);int nearby=getTiming()/20;if(stage<5){stagecounter=Boss8Rules.portalDelay(stage,nearby,random.nextInt(10-stage));spawnMinions();}else if(stage==12){stagecounter=Boss8Rules.portalDelay(stage,nearby,random.nextInt(50));spawnBoss();}else{int timing=nearby*20;stagecounter=Boss8Rules.portalDelay(stage,nearby,random.nextInt(5+timing/3));spawnMinions();}stage++;}else stagecounter=30+random.nextInt(30);}else{stagecounter--;if(stagecounter==160&&stage==0)placeBanners();if(stagecounter>20&&stagecounter<150&&stage==0&&stagecounter%13==0)placeLoot();}if(stage<12)heal(1);bossInfo.setProgress(getHealth()/getMaxHealth());for(Player player:level().getEntitiesOfClass(Player.class,getBoundingBox().inflate(1)))if(distanceToSqr(player)<3&&player.hurt(damageSources().indirectMagic(this,this),8))playSound(SoundsTC.zap,1,(random.nextFloat()-random.nextFloat())*.1F+1);}if(pulse>0)pulse--;}
    private void placeBanners(){for(Direction direction:Direction.Plane.HORIZONTAL){BlockPos pos=new BlockPos((int)getX()-direction.getStepX()*6,(int)getY(),(int)getZ()+direction.getStepZ()*6);level().setBlock(pos,BlocksTC.bannerCrimsonCult.defaultBlockState(),3);if(level().getBlockEntity(pos) instanceof thaumcraft.common.tiles.misc.TileBanner banner){banner.setBannerFacing((byte)switch(direction){case NORTH->8;case SOUTH->0;case WEST->12;default->4;});playSound(SoundsTC.wandfail,1,1);}}}
    private void placeLoot(){int x=(int)getX()+random.nextInt(5)-random.nextInt(5),z=(int)getZ()+random.nextInt(5)-random.nextInt(5);BlockPos pos=new BlockPos(x,(int)getY(),z);if(x!=(int)getX()&&z!=(int)getZ()&&level().isEmptyBlock(pos)){level().broadcastEntityEvent(this,(byte)16);float roll=random.nextFloat();var block=roll<.05F?BlocksTC.lootCrateRare:roll<.2F?BlocksTC.lootCrateUncommon:BlocksTC.lootCrateCommon;level().setBlock(pos,block.defaultBlockState(),3);playSound(SoundsTC.wandfail,1,1);}}
    int getTiming(){return thaumcraft.common.lib.utils.EntityUtils.getEntitiesInRange(level(),getX(),getY(),getZ(),this,EntityCultist.class,32).size()*20;}
    void spawnMinions(){EntityCultist cultist=(random.nextFloat()>.33F?CultEntities.KNIGHT:CultEntities.CLERIC).create(level());cultist.setPos(getX()+random.nextFloat()-random.nextFloat(),getY()+.25,getZ()+random.nextFloat()-random.nextFloat());cultist.finalizeSpawn((ServerLevel)level(),level().getCurrentDifficultyAt(cultist.blockPosition()),MobSpawnType.EVENT,null);cultist.restrictTo(blockPosition(),32);level().addFreshEntity(cultist);cultist.spawnExplosionParticle();cultist.playSound(SoundsTC.wandfail,1,1);if(stage>12)hurt(damageSources().fellOutOfWorld(),5+random.nextInt(5));}
    void spawnBoss(){EntityCultistLeader leader=new EntityCultistLeader(level());leader.setPos(getX()+random.nextFloat()-random.nextFloat(),getY()+.25,getZ()+random.nextFloat()-random.nextFloat());leader.finalizeSpawn((ServerLevel)level(),level().getCurrentDifficultyAt(leader.blockPosition()),MobSpawnType.EVENT,null);leader.restrictTo(blockPosition(),32);level().addFreshEntity(leader);leader.spawnExplosionParticle();leader.playSound(SoundsTC.wandfail,1,1);}
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){level.addFreshEntity(new EntitySpecialItem(level,getX(),getY()+getBbHeight()/2,getZ(),new ItemStack(ItemsTC.primordialPearl)));}
    @Override public boolean canBeAffected(MobEffectInstance effect){return false;}@Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return false;}
    @Override public void die(DamageSource source){if(!level().isClientSide)level().explode(this,getX(),getY(),getZ(),2,Level.ExplosionInteraction.NONE);super.die(source);}
    @Override public void handleEntityEvent(byte event){if(event==16)pulse=10;else super.handleEntityEvent(event);}
    @Override public void startSeenByPlayer(ServerPlayer player){super.startSeenByPlayer(player);bossInfo.addPlayer(player);}@Override public void stopSeenByPlayer(ServerPlayer player){super.stopSeenByPlayer(player);bossInfo.removePlayer(player);}
    @Override protected SoundEvent getAmbientSound(){return SoundsTC.monolith;}@Override protected SoundEvent getHurtSound(DamageSource source){return SoundsTC.zap;}@Override protected SoundEvent getDeathSound(){return SoundsTC.shock;}@Override public int getAmbientSoundInterval(){return 540;}@Override protected float getSoundVolume(){return .75F;}
}
