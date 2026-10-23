package thaumcraft.common.entities.monster;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.loot.LootTable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.*;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.container.ContainerPech;
import thaumcraft.common.entities.*;
import thaumcraft.common.entities.ai.pech.*;
import thaumcraft.common.items.casters.foci.*;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

public final class EntityPech extends Monster implements RangedAttackMob {
    private static final EntityDataAccessor<Byte> TYPE=SynchedEntityData.defineId(EntityPech.class,EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> ANGER=SynchedEntityData.defineId(EntityPech.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TAMED=SynchedEntityData.defineId(EntityPech.class,EntityDataSerializers.BOOLEAN);
    public static final Map<Integer,Integer> valuedItems=new HashMap<>();
    /** Native typed replacement of source raw List<Integer,ItemStack> rows; addons may append. */
    public static final Map<Integer,List<PechTrades.Trade>> tradeInventory=new HashMap<>();
    public NonNullList<ItemStack> loot=NonNullList.withSize(9,ItemStack.EMPTY);
    public boolean trading;
    public float mumble;
    private int chargeCount;
    private UUID tradingPlayer;
    private MeleeAttackGoal melee;
    private RangedAttackGoal arrows,blasts;
    private AvoidEntityGoal<Player> avoid;
    public EntityPech(EntityType<? extends EntityPech> type,Level level){super(type,level);xpReward=8;setCombatTask();}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.ATTACK_DAMAGE,6).add(Attributes.MOVEMENT_SPEED,.5);}
    @Override protected void registerGoals(){((GroundPathNavigation)getNavigation()).setCanFloat(false);setDropChance(EquipmentSlot.MAINHAND,.2F);setDropChance(EquipmentSlot.OFFHAND,.2F);melee=new MeleeAttackGoal(this,.6,false);arrows=new RangedAttackGoal(this,.6,20,50,15);blasts=new RangedAttackGoal(this,.6,20,50,15);avoid=new AvoidEntityGoal<>(this,Player.class,8,.5,.6);goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new AIPechTradePlayer(this));goalSelector.addGoal(3,new AIPechItemEntityGoto(this));goalSelector.addGoal(5,new OpenDoorGoal(this,true));goalSelector.addGoal(6,new MoveTowardsRestrictionGoal(this,.5));goalSelector.addGoal(6,new MoveThroughVillageGoal(this,1,false,4,()->false));goalSelector.addGoal(9,new RandomStrollGoal(this,.6));goalSelector.addGoal(9,new LookAtPlayerGoal(this,Player.class,3,1));goalSelector.addGoal(10,new LookAtPlayerGoal(this,Mob.class,8));goalSelector.addGoal(11,new RandomLookAroundGoal(this));targetSelector.addGoal(1,new HurtByTargetGoal(this));targetSelector.addGoal(2,new AINearestAttackableTargetPech(this));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(TYPE,(byte)0);builder.define(ANGER,0);builder.define(TAMED,false);}
    public int getPechType(){return entityData.get(TYPE);}public void setPechType(int value){entityData.set(TYPE,(byte)Math.max(0,Math.min(2,value)));}public int getAnger(){return entityData.get(ANGER);}public void setAnger(int value){entityData.set(ANGER,Math.max(0,value));}public boolean isTamed(){return entityData.get(TAMED);}public void setTamed(boolean value){entityData.set(TAMED,value);}
    @Override protected Component getTypeName(){return Component.translatable(getPechType()==0?"entity.thaumcraft.pech":getPechType()==1?"entity.thaumcraft.pech.mage":"entity.thaumcraft.pech.archer");}
    public void setCombatTask(){if(level()==null||level().isClientSide||melee==null)return;goalSelector.removeGoal(melee);goalSelector.removeGoal(arrows);goalSelector.removeGoal(blasts);goalSelector.removeGoal(avoid);goalSelector.addGoal(2,getMainHandItem().is(Items.BOW)?arrows:getMainHandItem().is(ItemsTC.pechWand)?blasts:melee);if(!isTamed())goalSelector.addGoal(4,avoid);}
    @Override public void setItemSlot(EquipmentSlot slot,ItemStack stack){super.setItemSlot(slot,stack);if(slot==EquipmentSlot.MAINHAND)setCombatTask();}
    @Override protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random,DifficultyInstance difficulty){super.populateDefaultEquipmentSlots(random,difficulty);int roll=random.nextInt(20);Item item=switch(roll){case 0,12->ItemsTC.pechWand;case 1->Items.STONE_SWORD;case 2,4,10,11,13->Items.BOW;case 3->Items.STONE_AXE;case 5->Items.IRON_SWORD;case 6->Items.IRON_AXE;case 7->Items.FISHING_ROD;case 8->Items.STONE_PICKAXE;case 9->Items.IRON_PICKAXE;default->Items.AIR;};if(item!=Items.AIR)setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));}
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor world,DifficultyInstance difficulty,MobSpawnType reason,SpawnGroupData data){populateDefaultEquipmentSlots(random,difficulty);if(getMainHandItem().is(ItemsTC.pechWand)){setPechType(1);setDropChance(EquipmentSlot.MAINHAND,.1F);}else if(!getMainHandItem().isEmpty()){if(getMainHandItem().is(Items.BOW))setPechType(2);populateDefaultEquipmentEnchantments(world,random,difficulty);}setCanPickUpLoot(random.nextFloat()<.75F*difficulty.getSpecialMultiplier());setCombatTask();return super.finalizeSpawn(world,difficulty,reason,data);}
    @Override public void performRangedAttack(LivingEntity target,float power){if(getPechType()==2){ItemStack projectile=new ItemStack(Items.ARROW),bow=new ItemStack(Items.BOW);var enchantments=registryAccess().registryOrThrow(Registries.ENCHANTMENT);int punch=EnchantmentHelper.getEnchantmentLevel(enchantments.getHolderOrThrow(Enchantments.PUNCH),this);if(punch>0)bow.enchant(enchantments.getHolderOrThrow(Enchantments.PUNCH),punch);Arrow arrow=new thaumcraft.common.entities.projectile.SourcePechArrow(level(),this,projectile,bow);if(level().getRandom().nextFloat()<.2F)arrow.addEffect(new MobEffectInstance(MobEffects.POISON,40));double dx=target.getX()-getX(),dz=target.getZ()-getZ(),dy=target.getBoundingBox().minY+target.getBbHeight()/3-arrow.getY();arrow.shoot(dx,dy+(float)Math.sqrt(dx*dx+dz*dz)*.2F,dz,1.6F,14-level().getDifficulty().getId()*4);int strength=EnchantmentHelper.getEnchantmentLevel(enchantments.getHolderOrThrow(Enchantments.POWER),this);arrow.setBaseDamage(power*2+random.nextGaussian()*.25+level().getDifficulty().getId()*.11F+(strength>0?strength*.5+.5:0));if(EnchantmentHelper.getEnchantmentLevel(enchantments.getHolderOrThrow(Enchantments.FLAME),this)>0)arrow.igniteForSeconds(100);playSound(SoundEvents.ARROW_SHOOT,1,1/(random.nextFloat()*.4F+.8F));level().addFreshEntity(arrow);}
        else if(getPechType()==1){FocusPackage pack=new FocusPackage(this);FocusMediumRoot root=new FocusMediumRoot();root.setupFromCasterToTarget(this,target,distanceTo(target)/6F);pack.addNode(root);FocusMediumProjectile projectile=new FocusMediumProjectile();projectile.initialize();projectile.getSetting("speed").setValue(2);pack.addNode(projectile);pack.addNode(random.nextBoolean()?new FocusEffectCurse():random.nextBoolean()?new FocusEffectFlux():random.nextBoolean()?new FocusEffectEarth():random.nextBoolean()?new FocusEffectAir():new FocusEffectFire());FocusEngine.castFocusPackage(this,pack,true);swing(getUsedItemHand());}}
    public boolean isValued(ItemStack stack){if(stack.isEmpty())return false;return valuedItems.containsKey(Item.getId(stack.getItem()))||ThaumcraftCraftingManager.getObjectTags(stack).getAmount(Aspect.DESIRE)>1;}
    public int getValue(ItemStack stack){if(stack.isEmpty())return 0;int explicit=valuedItems.getOrDefault(Item.getId(stack.getItem()),0);return explicit!=0?explicit:Mob8NaturalRules.pechValue(0,ThaumcraftCraftingManager.getObjectTags(stack).getAmount(Aspect.DESIRE));}
    public boolean canPickup(ItemStack stack){if(stack.isEmpty())return false;if(!isTamed()&&valuedItems.containsKey(Item.getId(stack.getItem())))return true;for(ItemStack held:loot)if(held.isEmpty()||ItemStack.isSameItemSameComponents(held,stack)&&held.getCount()+stack.getCount()<=held.getMaxStackSize())return true;return false;}
    public ItemStack pickupItem(ItemStack stack){if(stack.isEmpty())return ItemStack.EMPTY;if(!isTamed()&&isValued(stack)){if(random.nextInt(10)<getValue(stack)){setTamed(true);setCombatTask();level().broadcastEntityEvent(this,(byte)18);}stack.shrink(1);return stack;}
        for(int i=0;i<loot.size()&&!stack.isEmpty();i++){ItemStack held=loot.get(i);if(!held.isEmpty()&&ItemStack.isSameItemSameComponents(held,stack)){int amount=Math.min(stack.getCount(),held.getMaxStackSize()-held.getCount());held.grow(amount);stack.shrink(amount);}}
        if(!stack.isEmpty())for(int i=0;i<loot.size();i++)if(loot.get(i).isEmpty()){loot.set(i,stack.copy());return ItemStack.EMPTY;}return stack;}
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){if(player.isCrouching())return InteractionResult.PASS;if(player.getItemInHand(hand).is(Items.NAME_TAG))return super.mobInteract(player,hand);if(!isTamed())return super.mobInteract(player,hand);if(level().isClientSide)return InteractionResult.SUCCESS;if(player instanceof ServerPlayer server&&(!trading||Objects.equals(tradingPlayer,player.getUUID()))){PechTrades.ensure(level());trading=true;tradingPlayer=player.getUUID();var opened=server.openMenu(new SimpleMenuProvider((id,inv,p)->new ContainerPech(id,inv,this),getDisplayName()),buf->buf.writeVarInt(getId()));if(opened.isEmpty()){trading=false;tradingPlayer=null;return InteractionResult.PASS;}return InteractionResult.CONSUME;}return InteractionResult.PASS;}
    public boolean isTradingWith(Player player){return trading&&Objects.equals(tradingPlayer,player.getUUID());}public void closeTrade(Player player){if(Objects.equals(tradingPlayer,player.getUUID())){trading=false;tradingPlayer=null;}}
    @Override public boolean hurt(DamageSource source,float amount){if(isInvulnerableTo(source))return false;if(source.getEntity() instanceof Player player&&!player.getAbilities().instabuild){for(EntityPech pech:level().getEntitiesOfClass(EntityPech.class,getBoundingBox().inflate(32,16,32)))if(pech!=this)pech.becomeAngryAt(player);becomeAngryAt(player);}return super.hurt(source,amount);}
    private void becomeAngryAt(Player player){if(getAnger()<=0){level().broadcastEntityEvent(this,(byte)19);playSound(SoundsTC.pech_charge,getSoundVolume(),getVoicePitch());}setTarget(player);setAnger(400+random.nextInt(400));setTamed(false);setCombatTask();}
    @Override public void tick(){mumble*=.75F;if(getAnger()>0)setAnger(getAnger()-1);if(!level().isClientSide&&getAnger()>0&&getTarget()!=null){if(chargeCount>0)chargeCount--;if(chargeCount==0){chargeCount=100;playSound(SoundsTC.pech_charge,getSoundVolume(),getVoicePitch());}level().broadcastEntityEvent(this,(byte)17);}if(level().isClientSide){if(getAnger()>0&&random.nextInt(15)==0)ambientParticle(false);if(isTamed()&&random.nextInt(25)==0)ambientParticle(true);}super.tick();if(!level().isClientSide){PechTrades.ensure(level());if(tickCount%40==0)heal(1);}}
    private void ambientParticle(boolean happy){level().addParticle(happy?ParticleTypes.HAPPY_VILLAGER:ParticleTypes.ANGRY_VILLAGER,getX()+random.nextFloat()*getBbWidth()*2-getBbWidth(),getY()+.5+random.nextFloat()*getBbHeight(),getZ()+random.nextFloat()*getBbWidth()*2-getBbWidth(),random.nextGaussian()*.02,random.nextGaussian()*.02,random.nextGaussian()*.02);}
    @Override public void handleEntityEvent(byte event){if(event==16)mumble=(float)Math.PI;else if(event==17)mumble=(float)Math.PI*2;else if(event==18||event==19){for(int i=0;i<5;i++)level().addParticle(event==18?ParticleTypes.HAPPY_VILLAGER:ParticleTypes.ANGRY_VILLAGER,getX()+random.nextFloat()*getBbWidth()*2-getBbWidth(),getY()+.5+random.nextFloat()*getBbHeight(),getZ()+random.nextFloat()*getBbWidth()*2-getBbWidth(),random.nextGaussian()*.02,random.nextGaussian()*.02,random.nextGaussian()*.02);if(event==19)mumble=(float)Math.PI*2;}else super.handleEntityEvent(event);}
    @Override public void playAmbientSound(){if(!level().isClientSide){if(random.nextInt(3)==0&&level().getEntitiesOfClass(EntityPech.class,getBoundingBox().inflate(4,2,4)).stream().anyMatch(p->p!=this)){level().broadcastEntityEvent(this,(byte)17);playSound(SoundsTC.pech_trade,getSoundVolume(),getVoicePitch());return;}level().broadcastEntityEvent(this,(byte)16);}super.playAmbientSound();}
    @Override protected SoundEvent getAmbientSound(){return SoundsTC.pech_idle;}@Override protected SoundEvent getHurtSound(DamageSource source){return SoundsTC.pech_hit;}@Override protected SoundEvent getDeathSound(){return SoundsTC.pech_death;}@Override public int getAmbientSoundInterval(){return 120;}@Override protected float getSoundVolume(){return .4F;}
    @Override public int getArmorValue(){return Math.min(20,super.getArmorValue()+2);}@Override public boolean canBeLeashed(){return false;}
    @Override public boolean removeWhenFarAway(double distance){return loot.stream().filter(x->!x.isEmpty()).count()<5;}@Override public boolean requiresCustomPersistence(){return loot.stream().filter(x->!x.isEmpty()).count()>=5||super.requiresCustomPersistence();}
    @Override public boolean checkSpawnObstruction(LevelReader reader){return level().getEntitiesOfClass(EntityPech.class,getBoundingBox().inflate(16)).size()<4&&super.checkSpawnObstruction(reader);}
    @Override protected ResourceKey<LootTable> getDefaultLootTable(){return ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.parse("thaumcraft:entities/pech"));}
    @Override protected void dropFromLootTable(DamageSource source,boolean recent){for(ItemStack stack:loot)if(!stack.isEmpty()&&random.nextFloat()<.33F)spawnAtLocation(stack.copy(),1.5F);super.dropFromLootTable(source,recent);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putByte("PechType",(byte)getPechType());tag.putShort("Anger",(short)getAnger());tag.putBoolean("Tamed",isTamed());ContainerHelper.saveAllItems(tag,loot,registryAccess());}@Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);setPechType(tag.getByte("PechType"));setAnger(tag.getShort("Anger"));setTamed(tag.getBoolean("Tamed"));loot=NonNullList.withSize(9,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,loot,registryAccess());setCombatTask();}
}
