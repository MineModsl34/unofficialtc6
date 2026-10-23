package thaumcraft.common.entities.monster.boss;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.common.entities.Mob8BossRegistries;
import thaumcraft.common.entities.WarpEntities;
import thaumcraft.common.entities.ai.combat.*;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.entities.projectile.EntityEldritchOrb;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.network.fx.PacketFXSonic;
import thaumcraft.common.lib.warp.WarpGameplay;
import net.neoforged.neoforge.network.PacketDistributor;
public final class EntityEldritchWarden extends EntityThaumcraftBoss implements RangedAttackMob,IEldritchMob {
    private final ServerBossEvent shieldInfo=new ServerBossEvent(Component.empty(),BossEvent.BossBarColor.BLUE,BossEvent.BossBarOverlay.NOTCHED_10);
    private static final EntityDataAccessor<Byte> NAME=SynchedEntityData.defineId(EntityEldritchWarden.class,EntityDataSerializers.BYTE);
    public static final String[] TITLES={"Aphoom-Zhah","Basatan","Chaugnar Faugn","Mnomquah","Nyogtha","Oorn","Shaikorth","Rhan-Tegoth","Rhogog","Shudde M'ell","Vulthoom","Yag-Kosha","Yibb-Tstll","Zathog","Zushakon"};
    private boolean fieldFrenzy,lastBlast;private int fieldFrenzyCounter;public float armLiftL,armLiftR;
    public EntityEldritchWarden(EntityType<? extends EntityEldritchWarden> type,Level level){super(type,level);}
    public EntityEldritchWarden(Level level){this(Mob8BossRegistries.ELDRITCH_WARDEN,level);}
    public static AttributeSupplier.Builder createAttributes(){return EntityThaumcraftBoss.createAttributes().add(Attributes.MAX_HEALTH,400).add(Attributes.MOVEMENT_SPEED,.33).add(Attributes.ATTACK_DAMAGE,10).add(Attributes.ARMOR,4).add(Attributes.MAX_ABSORPTION,264);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(NAME,(byte)0);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new AILongRangeAttack(this,3,1,20,40,24));goalSelector.addGoal(3,new SourceMeleeAttackGoal(this,1.1,false));goalSelector.addGoal(5,new MoveTowardsRestrictionGoal(this,.8));goalSelector.addGoal(7,new RandomStrollGoal(this,1));goalSelector.addGoal(8,new LookAtPlayerGoal(this,Player.class,8));goalSelector.addGoal(8,new RandomLookAroundGoal(this));targetSelector.addGoal(1,new HurtByTargetGoal(this));targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));targetSelector.addGoal(3,new NearestAttackableTargetGoal<>(this,EntityCultist.class,true));}
    public String getTitle(){return TITLES[Math.floorMod(entityData.get(NAME),TITLES.length)];}
    @Override public void generateName(){if(thaumcraft.common.entities.monster.mods.ChampionSystem.modifier(this)>=0)setCustomName(Component.translatable("entity.Thaumcraft.EldritchWarden.name.custom",getTitle(),thaumcraft.common.entities.monster.mods.ChampionSystem.getModifierName(this)));}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putByte("title",entityData.get(NAME));}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(NAME,tag.getByte("title"));}
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,MobSpawnType reason,SpawnGroupData data){spawnTimer=150;entityData.set(NAME,(byte)random.nextInt(TITLES.length));thaumcraft.common.entities.monster.mods.ChampionSystem.addAbsorption(this,(float)getAttribute(Attributes.MAX_HEALTH).getBaseValue()*.66F);return super.finalizeSpawn(level,difficulty,reason,data);}
    @Override protected boolean sourceAiPaused(){return super.sourceAiPaused()||fieldFrenzyCounter>0;}
    private void updateShield(){int max=Boss8Rules.shield((float)getAttribute(Attributes.MAX_HEALTH).getBaseValue());if(invulnerableTime<=0&&tickCount%25==0&&getAbsorptionAmount()<max)thaumcraft.common.entities.monster.mods.ChampionSystem.addAbsorption(this,1);shieldInfo.setProgress(getAbsorptionAmount()/max);}
    @Override public void startSeenByPlayer(ServerPlayer player){super.startSeenByPlayer(player);shieldInfo.addPlayer(player);}
    @Override public void stopSeenByPlayer(ServerPlayer player){super.stopSeenByPlayer(player);shieldInfo.removePlayer(player);}
    @Override public void tick(){if(spawnTimer==150&&!level().isClientSide)level().broadcastEntityEvent(this,(byte)18);super.tick();if(level().isClientSide){armLiftL=Math.max(0,armLiftL-.05F);armLiftR=Math.max(0,armLiftR-.05F);thaumcraft.common.entities.Mob8BossPresentationHooks.wardenTick.accept(this);}else{updateShield();for(int i=0;i<4;i++){BlockPos pos=BlockPos.containing(getX()+(i%2*2-1)*.25,getY(),getZ()+(i/2%2*2-1)*.25);if(level().isEmptyBlock(pos))level().setBlock(pos,BlocksTC.effectSap.defaultBlockState(),3);}if(fieldFrenzyCounter>0){if(fieldFrenzyCounter==150)teleportHome();performFieldFrenzy();}}}
    private void performFieldFrenzy(){if(Boss8Rules.frenzyPulse(fieldFrenzyCounter)){level().broadcastEntityEvent(this,(byte)17);double radius=Boss8Rules.frenzyRadius(fieldFrenzyCounter);int spacing=1+fieldFrenzyCounter/8;BlockPos center=blockPosition();for(int i=0;i<180/spacing;i++){double angle=Math.toRadians(i*2*spacing);BlockPos pos=center.offset((int)(radius*Math.cos(angle)),0,(int)(radius*Math.sin(angle)));if(level().isEmptyBlock(pos)&&level().getBlockState(pos.below()).isRedstoneConductor(level(),pos.below())){level().setBlock(pos,BlocksTC.effectSap.defaultBlockState(),3);level().scheduleTick(pos,BlocksTC.effectSap,250+random.nextInt(150));}}playSound(SoundsTC.zap,1,.9F+random.nextFloat()*.1F);}fieldFrenzyCounter--;}
    protected void teleportHome(){if(!hasRestriction())return;BlockPos home=getRestrictCenter();EntityTeleportEvent.EnderEntity event=new EntityTeleportEvent.EnderEntity(this,home.getX(),home.getY(),home.getZ());if(NeoForge.EVENT_BUS.post(event).isCanceled())return;Vec3 previous=position();BlockPos pos=BlockPos.containing(event.getTargetX(),event.getTargetY(),event.getTargetZ());if(!level().hasChunkAt(pos))return;
        // Source's retry loop keeps bp unchanged; preserve its initial-ground decision, not invented terrain navigation.
        if(!level().getBlockState(pos.below()).blocksMotion()||level().getBlockState(pos).blocksMotion())return;setPos(pos.getX()+.5,pos.getY()+.1,pos.getZ()+.5);if(!level().noCollision(this)){setPos(previous);return;}playSound(SoundEvents.ENDERMAN_TELEPORT,1,1);}
    @Override public boolean isInvulnerableTo(DamageSource source){return fieldFrenzyCounter>0||source.is(DamageTypes.DROWN)||source.is(DamageTypes.WITHER)||super.isInvulnerableTo(source);}
    @Override public boolean hurt(DamageSource source,float damage){if(isInvulnerableTo(source))return false;boolean hit=super.hurt(source,damage);if(!level().isClientSide&&hit&&!fieldFrenzy&&getAbsorptionAmount()<=0){fieldFrenzy=true;fieldFrenzyCounter=150;}return hit;}
    @Override public void performRangedAttack(LivingEntity target,float power){if(random.nextFloat()>.2F){EntityEldritchOrb orb=new EntityEldritchOrb(level(),this);lastBlast=!lastBlast;level().broadcastEntityEvent(this,(byte)(lastBlast?16:15));float angle=(getYRot()+(lastBlast?90:180))*Mth.DEG_TO_RAD;orb.setPos(orb.getX()-Mth.cos(angle)*.5,orb.getY()-.13,orb.getZ()-Mth.sin(angle)*.5);orb.shoot(target.getX()+target.getDeltaMovement().x-getX(),target.getY()-getY()-target.getBbHeight()/2,target.getZ()+target.getDeltaMovement().z-getZ(),1,2);playSound(SoundsTC.egattack,2,1+random.nextFloat()*.1F);level().addFreshEntity(orb);}else if(hasLineOfSight(target)){PacketDistributor.sendToPlayersNear((ServerLevel)level(),null,getX(),getY(),getZ(),32,new PacketFXSonic(getId()));target.push(-Mth.sin(getYRot()*Mth.DEG_TO_RAD)*1.5,.1,Mth.cos(getYRot()*Mth.DEG_TO_RAD)*1.5);target.addEffect(new MobEffectInstance(MobEffects.WITHER,400));target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,400));if(target instanceof Player player)WarpGameplay.addWarpToPlayer(player,3+random.nextInt(3),IPlayerWarp.EnumWarpType.TEMPORARY);playSound(SoundsTC.egscreech,4,1+random.nextFloat()*.1F);}}
    @Override public void handleEntityEvent(byte event){if(event==15)armLiftL=.5F;else if(event==16)armLiftR=.5F;else if(event==17){armLiftL=.9F;armLiftR=.9F;}else if(event==18)spawnTimer=150;else super.handleEntityEvent(event);}
    @Override public boolean canAttackType(EntityType<?> type){return type!=WarpEntities.ELDRITCH_GUARDIAN&&super.canAttackType(type);}
    @Override protected SoundEvent getAmbientSound(){return SoundsTC.egidle;}@Override protected SoundEvent getDeathSound(){return SoundsTC.egdeath;}@Override public int getAmbientSoundInterval(){return 500;}
}
