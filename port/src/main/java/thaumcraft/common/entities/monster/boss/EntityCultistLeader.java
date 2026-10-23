package thaumcraft.common.entities.monster.boss;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.*;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.*;
import thaumcraft.common.entities.ai.combat.*;
import thaumcraft.common.entities.monster.cult.*;
import thaumcraft.common.entities.projectile.EntityGolemOrb;
import thaumcraft.common.lib.SoundsTC;
public final class EntityCultistLeader extends EntityThaumcraftBoss implements RangedAttackMob {
    private static final EntityDataAccessor<Byte> NAME=SynchedEntityData.defineId(EntityCultistLeader.class,EntityDataSerializers.BYTE);
    public static final String[] TITLES={"Alberic","Anselm","Bastian","Beturian","Chabier","Chorache","Chuse","Dodorol","Ebardo","Ferrando","Fertus","Guillen","Larpe","Obano","Zelipe"};
    public EntityCultistLeader(EntityType<? extends EntityCultistLeader> type,Level level){super(type,level);xpReward=40;}
    public EntityCultistLeader(Level level){this(Mob8BossRegistries.CULTIST_LEADER,level);}
    public static AttributeSupplier.Builder createAttributes(){return EntityThaumcraftBoss.createAttributes().add(Attributes.MOVEMENT_SPEED,.32).add(Attributes.MAX_HEALTH,150).add(Attributes.ATTACK_DAMAGE,5);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(NAME,(byte)0);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new AILongRangeAttack(this,16,1,30,40,24));goalSelector.addGoal(3,new SourceMeleeAttackGoal(this,1.1,false));goalSelector.addGoal(6,new MoveTowardsRestrictionGoal(this,.8));goalSelector.addGoal(7,new RandomStrollGoal(this,.8));goalSelector.addGoal(8,new LookAtPlayerGoal(this,Player.class,8));goalSelector.addGoal(8,new RandomLookAroundGoal(this));targetSelector.addGoal(1,new AICultistHurtByTarget(this,true));targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));}
    public String getTitle(){return TITLES[Math.floorMod(entityData.get(NAME),TITLES.length)];}
    @Override public void generateName(){if(thaumcraft.common.entities.monster.mods.ChampionSystem.modifier(this)>=0)setCustomName(Component.translatable("entity.Thaumcraft.CultistLeader.name.custom",getTitle(),thaumcraft.common.entities.monster.mods.ChampionSystem.getModifierName(this)));}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putByte("title",entityData.get(NAME));}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(NAME,tag.getByte("title"));}
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,MobSpawnType reason,SpawnGroupData data){setItemSlot(EquipmentSlot.HEAD,new ItemStack(ItemsTC.crimsonPraetorHelm));setItemSlot(EquipmentSlot.CHEST,new ItemStack(ItemsTC.crimsonPraetorChest));setItemSlot(EquipmentSlot.LEGS,new ItemStack(ItemsTC.crimsonPraetorLegs));setItemSlot(EquipmentSlot.FEET,new ItemStack(ItemsTC.crimsonBoots));setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(level.getDifficulty()==Difficulty.EASY?ItemsTC.voidSword:ItemsTC.crimsonBlade));float f=difficulty.getSpecialMultiplier();if(random.nextFloat()<.5F*f)setItemSlot(EquipmentSlot.MAINHAND,EnchantmentHelper.enchantItem(random,getMainHandItem(),(int)(7+f*random.nextInt(22)),registryAccess(),Optional.empty()));entityData.set(NAME,(byte)random.nextInt(TITLES.length));return super.finalizeSpawn(level,difficulty,reason,data);}
    @Override protected void customServerAiStep(){super.customServerAiStep();for(EntityCultist cultist:thaumcraft.common.lib.utils.EntityUtils.getEntitiesInRange(level(),getX(),getY(),getZ(),this,EntityCultist.class,8))if(!cultist.hasEffect(MobEffects.REGENERATION))cultist.addEffect(new MobEffectInstance(MobEffects.REGENERATION,60,1));}
    @Override public boolean isAlliedTo(Entity entity){return entity instanceof EntityCultist||entity instanceof EntityCultistLeader;}
    @Override public boolean canAttackType(EntityType<?> type){return type!=CultEntities.KNIGHT&&type!=CultEntities.CLERIC&&type!=Mob8BossRegistries.CULTIST_LEADER&&super.canAttackType(type);}
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){dropRareBag();}
    @Override public void performRangedAttack(LivingEntity target,float power){if(!hasLineOfSight(target))return;swing(InteractionHand.MAIN_HAND);getLookControl().setLookAt(target.getX(),target.getBoundingBox().minY+target.getBbHeight()/2,target.getZ(),30,30);EntityGolemOrb orb=new EntityGolemOrb(level(),this,target,true);orb.setPos(orb.getX()+orb.getDeltaMovement().x/2,orb.getY(),orb.getZ()+orb.getDeltaMovement().z/2);orb.shoot(target.getX()-getX(),target.getBoundingBox().minY+target.getBbHeight()/2-(getY()+getBbHeight()/2)+2,target.getZ()-getZ(),.66F,3);playSound(SoundsTC.egattack,1,1+random.nextFloat()*.1F);level().addFreshEntity(orb);}
    public void spawnExplosionParticle(){if(!level().isClientSide)level().broadcastEntityEvent(this,(byte)20);else for(int i=0;i<20;i++){double x=random.nextGaussian()*.05,y=random.nextGaussian()*.05,z=random.nextGaussian()*.05;CultistPresentationHooks.spawn(level(),getX()+random.nextFloat()*getBbWidth()*2-getBbWidth()+x*2,getY()+random.nextFloat()*getBbHeight()+y*2,getZ()+random.nextFloat()*getBbWidth()*2-getBbWidth()+z*2,x,y,z);}}
    @Override public void handleEntityEvent(byte event){if(event==20)spawnExplosionParticle();else super.handleEntityEvent(event);}
}
