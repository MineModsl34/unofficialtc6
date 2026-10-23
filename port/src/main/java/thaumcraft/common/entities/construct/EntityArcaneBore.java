package thaumcraft.common.entities.construct;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import thaumcraft.common.lib.enchantment.SpecialMiningResults;
import thaumcraft.common.lib.enchantment.ToolMiningRules;
import thaumcraft.common.world.aura.AuraHandler;

/** BETA26 bore scan/charge/tool/drop pipeline, using native fake-player protection events.
 * Source forcibly deleted a target after denied harvest: this port explicitly does not.
 */
@EventBusSubscriber(modid="thaumcraft")
public final class EntityArcaneBore extends EntityOwnedConstruct {
    private static final EntityDataAccessor<Direction> FACING=SynchedEntityData.defineId(EntityArcaneBore.class,EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Boolean> ACTIVE=SynchedEntityData.defineId(EntityArcaneBore.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> CHARGE=SynchedEntityData.defineId(EntityArcaneBore.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<BlockPos>> TARGET=SynchedEntityData.defineId(EntityArcaneBore.class,EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> DIG_DELAY=SynchedEntityData.defineId(EntityArcaneBore.class,EntityDataSerializers.INT);
    private static final GameProfile PROFILE=new GameProfile(UUID.nameUUIDFromBytes("Thaumcraft:FakeThaumcraftBore".getBytes(StandardCharsets.UTF_8)),"FakeThaumcraftBore");
    private static final class HarvestCapture { final FakePlayer player;final List<ItemStack> drops=new ArrayList<>();boolean harvested;HarvestCapture(FakePlayer player){this.player=player;} }
    private static final ThreadLocal<HarvestCapture> CAPTURE=new ThreadLocal<>();
    private BlockPos previousTarget;
    private int breakCounter,digDelay;
    public int spiral;
    public float currentRadius;
    private long soundDelay;
    public boolean clientDigging;
    public EntityArcaneBore(EntityType<? extends EntityArcaneBore> type,Level level){super(type,level);}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,50).add(Attributes.FOLLOW_RANGE,32);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(FACING,Direction.DOWN);builder.define(ACTIVE,false);builder.define(CHARGE,0F);builder.define(TARGET,Optional.empty());builder.define(DIG_DELAY,0);}
    public Direction getFacing(){return entityData.get(FACING);}public void setFacing(Direction facing){entityData.set(FACING,facing);}
    public boolean isActive(){return entityData.get(ACTIVE);}public void setActive(boolean active){entityData.set(ACTIVE,active);}
    public float getCharge(){return entityData.get(CHARGE);}public void setCharge(float charge){entityData.set(CHARGE,Float.isFinite(charge)?charge:0);}
    public BlockPos getDigTarget(){return entityData.get(TARGET).orElse(null);}private void target(BlockPos pos){entityData.set(TARGET,Optional.ofNullable(pos));}
    public int getDigDelayMax(){return entityData.get(DIG_DELAY);}
    public static boolean isPick(ItemStack stack){return !stack.isEmpty() && (stack.getItem() instanceof PickaxeItem || stack.canPerformAction(ItemAbilities.PICKAXE_DIG));}
    public boolean validInventory(){var tool=getMainHandItem();return isPick(tool) && tool.getDamageValue()+1<tool.getMaxDamage();}
    private int infusion(EnumInfusionEnchantment enchantment){return EnumInfusionEnchantment.getInfusionEnchantmentLevel(getMainHandItem(),enchantment);}
    private int enchant(net.minecraft.resources.ResourceKey<Enchantment> key){return EnchantmentHelper.getItemEnchantmentLevel(registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key),getMainHandItem());}
    public int getDigRadius(){return isPick(getMainHandItem())?ConstructRules.radius(getMainHandItem().getItem().getEnchantmentValue(getMainHandItem()),infusion(EnumInfusionEnchantment.DESTRUCTIVE)):2;}
    public int getDigDepth(){return ConstructRules.depth(getDigRadius(),infusion(EnumInfusionEnchantment.BURROWING));}
    public int getFortune(){return validInventory()?Math.max(enchant(Enchantments.FORTUNE),infusion(EnumInfusionEnchantment.SOUNDING)):0;}
    public int getDigSpeed(BlockState state){return validInventory()?(int)(getMainHandItem().getDestroySpeed(state)/2F)+enchant(Enchantments.EFFICIENCY):0;}
    public int getRefining(){return infusion(EnumInfusionEnchantment.REFINING);}public boolean hasSilkTouch(){return !getMainHandItem().isEmpty() && enchant(Enchantments.SILK_TOUCH)>0;}
    private boolean canSilkTouch(BlockPos pos,BlockState state){return hasSilkTouch() && BoreSilkHarvest.allowed(level(),pos,state);}
    @Override public void tick(){
        super.tick();if(!isAlive())return;
        if(!level().isClientSide){setYRot(yHeadRot);if(tickCount%50==0)heal(1);if(tickCount%10==0 && getCharge()<10)setCharge(getCharge()+AuraHandler.drainVis(level(),blockPosition(),10,false));BlockPos rail=blockPosition();if(level().getBlockState(rail.below()).getBlock() instanceof BaseRailBlock)rail=rail.below();var state=level().getBlockState(rail);if(state.getBlock() instanceof BaseRailBlock){if(state.is(BlocksTC.activatorRail))setActive(!state.getValue(PoweredRailBlock.POWERED));}else if(!isPassenger())setActive(level().hasNeighborSignal(blockPosition().below()));if(validInventory())getMainHandItem().inventoryTick(level(),this,0,true);}
        if(!isActive()){target(null);getLookControl().setLookAt(getX()+getFacing().getStepX(),getY(),getZ()+getFacing().getStepZ(),10,33);}
        var target=getDigTarget();
        if(target!=null && getCharge()>=.25F && !level().isClientSide){getLookControl().setLookAt(target.getX()+.5,target.getY(),target.getZ()+.5,10,90);if(digDelay--<=0 && dig()){setCharge(ConstructRules.paidCharge(getCharge()));if(soundDelay<System.currentTimeMillis()){soundDelay=System.currentTimeMillis()+1200+level().random.nextInt(100);playSound(SoundsTC.rumble,.25F,.9F+level().random.nextFloat()*.2F);}}}
        if(!level().isClientSide && getDigTarget()==null && isActive() && validInventory()){findNextBlockToDig();if(getDigTarget()!=null){level().broadcastEntityEvent(this,(byte)16);thaumcraft.common.lib.fx.SourceFX.emit(level(),thaumcraft.common.lib.fx.SourceFX.event(thaumcraft.common.lib.network.fx.PacketSourceFX.Kind.BORE,Vec3.atLowerCornerOf(getDigTarget()),Vec3.ZERO,getId(),digDelay,0,-1,""));}else{level().broadcastEntityEvent(this,(byte)17);getLookControl().setLookAt(getX()+getFacing().getStepX()*2,getY()+getFacing().getStepY()*2+getEyeHeight(),getZ()+getFacing().getStepZ()*2,10,33);}}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void harvestBlockEvent(BlockDropsEvent event){var capture=CAPTURE.get();if(capture==null || event.getBreaker()!=capture.player || event.isCanceled())return;capture.harvested=true;for(var drop:event.getDrops())if(!drop.getItem().isEmpty())capture.drops.add(drop.getItem().copy());event.getDrops().clear();}
    private boolean harvest(ServerLevel level,FakePlayer player,BlockPos pos){
        if(!level.hasChunkAt(pos) || !level.mayInteract(player,pos) || player.blockActionRestricted(level,pos,player.gameMode.getGameModeForPlayer()))return false;
        var state=level.getBlockState(pos);if(state.isAir() || state.getDestroySpeed(level,pos)<0 || CommonHooks.fireBlockBreak(level,player.gameMode.getGameModeForPlayer(),player,pos,state).isCanceled())return false;
        if(state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks())return false;
        var block=state.getBlock();var tile=level.getBlockEntity(pos);var tool=player.getMainHandItem().copy();
        // BlockUtils' fortuneOverride includes SOUNDING, but never enchants the real pick.
        var fortune=registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        int override=canSilkTouch(pos,state)?0:getFortune();if(override>EnchantmentHelper.getItemEnchantmentLevel(fortune,tool))tool.enchant(fortune,override);
        state=block.playerWillDestroy(level,pos,state,player);boolean allowed=state.canHarvestBlock(level,pos,player);level.levelEvent(player,2001,pos,Block.getId(state));boolean removed=state.onDestroyedByPlayer(level,pos,player,allowed,state.getFluidState());if(removed){block.destroy(level,pos,state);if(allowed)block.playerDestroy(level,player,pos,state,tile,tool);}return removed;
    }
    private boolean dig(){
        BlockPos pos=getDigTarget();target(null);if(pos==null || !(level() instanceof ServerLevel level) || !level.hasChunkAt(pos) || level.getBlockState(pos).isAir() || CAPTURE.get()!=null)return false;
        var originalState=level.getBlockState(pos);boolean silk=canSilkTouch(pos,originalState);
        FakePlayer player=FakePlayerFactory.get(level,PROFILE);player.setPos(position());player.setYRot(getYRot());player.setXRot(getXRot());player.setItemSlot(EquipmentSlot.MAINHAND,getMainHandItem());player.takeXpDelay=1;
        var capture=new HarvestCapture(player);CAPTURE.set(capture);boolean removed;
        try{removed=harvest(level,player,pos);}finally{CAPTURE.remove();}
        // Nested native ToolEvents burrowing may succeed even while canceling the origin event.
        boolean worked=removed || capture.harvested;if(!worked)return false;
        for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.5),Entity::isAlive)){capture.drops.add(item.getItem().copy());item.discard();}
        for(var stack:capture.drops){var result=!silk && getRefining()>0?SpecialMiningResults.find(stack,(getRefining()+1)*.125F,level.random):stack.copy();if(!result.isEmpty())eject(level,result);}
        breakCounter+=player.takeXpDelay;if(!getMainHandItem().isEmpty()){if(breakCounter>=50){breakCounter-=50;getMainHandItem().hurtAndBreak(1,this,EquipmentSlot.MAINHAND);}}else breakCounter=0;
        // Source returns the LAST setBlockToAir, not the harvest result. For ordinary
        // already-removed blocks this is false, so byte-charge/rumble does not run.
        // Denied origins and listener-created replacement blocks must not be erased.
        return removed && level.getBlockState(pos).getBlock()==originalState.getBlock() && !level.getBlockState(pos).isAir() && level.removeBlock(pos,false);
    }
    private void eject(ServerLevel level,ItemStack result){
        Direction chosen=null;for(Direction side:Direction.values()){var neighbor=blockPosition().relative(side);if(level.hasChunkAt(neighbor) && level.getCapability(Capabilities.ItemHandler.BLOCK,neighbor,side)!=null){chosen=side;break;}}
        if(chosen==null)chosen=getFacing().getOpposite();var neighbor=blockPosition().relative(chosen);var handler=level.hasChunkAt(neighbor)?level.getCapability(Capabilities.ItemHandler.BLOCK,neighbor,chosen.getOpposite()):null;var remainder=handler==null?result.copy():ItemHandlerHelper.insertItemStacked(handler,result.copy(),false);if(remainder.isEmpty())return;
        BlockPos origin=blockPosition();if(level.getBlockState(neighbor).isCollisionShapeFullBlock(level,neighbor))origin=origin.relative(chosen.getOpposite());var item=new ItemEntity(level,origin.getX()+.5+chosen.getStepX(),origin.getY()+chosen.getStepY(),origin.getZ()+.5+chosen.getStepZ(),remainder);item.setDeltaMovement(chosen.getStepX()*.3,chosen.getStepY()*.3,chosen.getStepZ()*.3);level.addFreshEntity(item);
    }
    /** Loaded-only native collider trace: an unloaded region never generates chunks for a bore. */
    private BlockHitResult ray(Vec3 from,Vec3 to){BlockGetter loaded=new BlockGetter(){public BlockState getBlockState(BlockPos pos){return level().hasChunkAt(pos)?level().getBlockState(pos):Blocks.AIR.defaultBlockState();}public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}public BlockEntity getBlockEntity(BlockPos pos){return level().hasChunkAt(pos)?level().getBlockEntity(pos):null;}public int getHeight(){return level().getHeight();}public int getMinBuildHeight(){return level().getMinBuildHeight();}};return loaded.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));}
    private void findNextBlockToDig(){
        int radius=getDigRadius(),depth=getDigDepth();if(previousTarget==null || distanceToSqr(Vec3.atCenterOf(previousTarget))>(radius+1D)*(radius+1D))previousTarget=blockPosition();var facing=getFacing();var first=ray(Vec3.atCenterOf(previousTarget),Vec3.atCenterOf(previousTarget.relative(facing,depth)));
        if(first.getType()!=HitResult.Type.MISS){var source=position().add(facing.getStepX(),getEyeHeight()+facing.getStepY(),facing.getStepZ());var hit=ray(source,Vec3.atCenterOf(first.getBlockPos()));if(hit.getType()!=HitResult.Type.MISS){var pos=hit.getBlockPos();var state=level().getBlockState(pos);if(state.getDestroySpeed(level(),pos)>-1 && !state.getCollisionShape(level(),pos).isEmpty() && !pos.equals(blockPosition()) && !pos.equals(blockPosition().below())){digDelay=ConstructRules.delay(state.getDestroySpeed(level(),pos),getDigSpeed(state));entityData.set(DIG_DELAY,digDelay);target(pos.immutable());return;}}}
        // Original float/trig spiral and truncation-before-floor origin are retained.
        for(int attempts=0;attempts<4096;attempts++){if(Math.abs(currentRadius)>radius)currentRadius=radius;spiral=ConstructRules.nextSpiral(spiral,currentRadius);if(spiral>=360){spiral-=360;currentRadius+=1;if(currentRadius>radius || currentRadius< -radius)currentRadius=0;}Vec3 offset=rotateZ(new Vec3(0,currentRadius,0),spiral/180F*(float)Math.PI);offset=rotateY(offset,(float)(Math.PI/2)*facing.getStepX());offset=rotateX(offset,(float)(Math.PI/2)*facing.getStepY());var source=new Vec3((int)getX()+.5+facing.getStepX(),getY()+facing.getStepY()+getEyeHeight(),(int)getZ()+.5+facing.getStepZ());var next=BlockPos.containing(source.add(offset));if(!next.equals(previousTarget)){previousTarget=next;return;}}
    }
    private static Vec3 rotateZ(Vec3 v,float angle){float c=net.minecraft.util.Mth.cos(angle),s=net.minecraft.util.Mth.sin(angle);return new Vec3(v.x*c+v.y*s,v.y*c-v.x*s,v.z);}
    private static Vec3 rotateY(Vec3 v,float angle){float c=net.minecraft.util.Mth.cos(angle),s=net.minecraft.util.Mth.sin(angle);return new Vec3(v.x*c+v.z*s,v.y,v.z*c-v.x*s);}
    private static Vec3 rotateX(Vec3 v,float angle){float c=net.minecraft.util.Mth.cos(angle),s=net.minecraft.util.Mth.sin(angle);return new Vec3(v.x,v.y*c+v.z*s,v.z*c-v.y*s);}
    /** EnumFacing.getDirectionFromEntityLiving's positional, not look-vector, heuristic. */
    private Direction ownerFacing(LivingEntity owner){return ToolMiningRules.fallbackFace(blockPosition(),owner.getX(),owner.getEyeY(),owner.getZ(),owner.getDirection());}
    @Override public boolean hurt(DamageSource source,float amount){if(source.getEntity() instanceof LivingEntity owner && isOwner(owner)){var facing=ownerFacing(owner);if(facing!=Direction.DOWN)setFacing(facing);return false;}setYRot((float)(getYRot()+random.nextGaussian()*45));setXRot((float)(getXRot()+random.nextGaussian()*20));return super.hurt(source,amount);}
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){if(player.getItemInHand(hand).is(Items.NAME_TAG))return InteractionResult.PASS;if(!level().isClientSide && isOwner(player) && isAlive()){if(player.isShiftKeyDown()){playSound(SoundsTC.zap,1,1);dropStuff();spawnAtLocation(new ItemStack(Mob8ConstructRegistries.BORE_PLACER),.5F);discard();player.swing(hand);}else player.openMenu(new SimpleMenuProvider((id,inv,owner)->new thaumcraft.common.container.ContainerConstruct(id,inv,this),getDisplayName()),buffer->buffer.writeVarInt(getId()));return InteractionResult.SUCCESS;}return super.mobInteract(player,hand);}
    private void dropStuff(){if(!getMainHandItem().isEmpty()){spawnAtLocation(getMainHandItem().copy(),.5F);setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);}}
    @Override public void die(DamageSource source){super.die(source);if(!level().isClientSide)dropStuff();}
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){float b=ConstructDrops.looting(level,source)*.15F;ConstructDrops.roll(this,ItemsTC.mindClockwork,.2F+b);ConstructDrops.roll(this,ItemsTC.morphicResonator,.2F+b);ConstructDrops.roll(this,BlocksTC.crystalAir.asItem(),.2F+b);ConstructDrops.roll(this,BlocksTC.crystalEarth.asItem(),.2F+b);ConstructDrops.roll(this,ItemsTC.mechanismSimple,.5F+b);ConstructDrops.roll(this,ItemsTC.plateBrass,.5F+b);ConstructDrops.roll(this,BlocksTC.plankGreatwood.asItem(),.5F+b);}
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x/10,z/10);var motion=getDeltaMovement();if(motion.y>.1)setDeltaMovement(motion.x,.1,motion.z);}
    @Override public void move(MoverType type,Vec3 motion){super.move(type,motion.multiply(.2,1,.2));}
    @Override public boolean isPushable(){return true;}@Override public boolean canBeCollidedWith(){return true;}@Override public int getMaxHeadXRot(){return 10;}
    @Override public void handleEntityEvent(byte event){if(event==16)clientDigging=true;else if(event==17)clientDigging=false;else super.handleEntityEvent(event);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putFloat("charge",getCharge());tag.putByte("faceing",(byte)getFacing().ordinal());tag.putBoolean("active",isActive());}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);setCharge(tag.getFloat("charge"));int face=tag.getByte("faceing");setFacing(face>=0 && face<6?Direction.from3DDataValue(face):Direction.DOWN);setActive(tag.getBoolean("active"));target(null);}
}
