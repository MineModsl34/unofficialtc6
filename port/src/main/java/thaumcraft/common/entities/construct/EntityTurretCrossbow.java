package thaumcraft.common.entities.construct;

import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.SoundsTC;

public class EntityTurretCrossbow extends EntityOwnedConstruct implements RangedAttackMob {
    private int loadTicks; private boolean loading;
    private float loadProgress, previousLoad;
    public float loadProgressForRender;
    public EntityTurretCrossbow(EntityType<? extends EntityTurretCrossbow> type, Level level) { super(type,level); }
    public static AttributeSupplier.Builder attributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.FOLLOW_RANGE,24).add(Attributes.ARMOR,2).add(Attributes.STEP_HEIGHT,0); }
    protected int maximumAttackInterval() { return 60; }
    @Override protected void registerGoals() { goalSelector.addGoal(1,new RangedAttackGoal(this,0,20,maximumAttackInterval(),24)); goalSelector.addGoal(2,new WatchTarget()); targetSelector.addGoal(1,new Retaliate()); targetSelector.addGoal(2,new NearestTarget()); }
    protected boolean acceptsTarget(LivingEntity target, boolean sight) { return target instanceof Mob && target instanceof Enemy && canAttackType(target.getType()) && target.isAlive() && target!=this && !isAlliedTo(target) && !ownerProtected(target) && isWithinRestriction(target.blockPosition()) && (!sight || getSensing().hasLineOfSight(target)); }
    private boolean ownerProtected(LivingEntity target){var owner=getOwnerUUID();return owner!=null && (target==getOwnerEntity() || target instanceof OwnableEntity owned && owner.equals(owned.getOwnerUUID()));}
    protected boolean retaliationClassAllowed(LivingEntity target){return canAttackType(target.getType());}
    protected boolean continueTarget(LivingEntity target) { return target!=null && target.isAlive() && !isAlliedTo(target) && distanceToSqr(target)<=576; }
    @Override public void performRangedAttack(LivingEntity target,float range) {
        var ammo=getMainHandItem(); if (ammo.isEmpty()) return;
        // Source always constructs a tipped arrow, even when handed a spectral arrow.
        var arrow=new Arrow(level(),this,ammo.copyWithCount(1),null);
        arrow.setBaseDamage(2.25+range*2F+random.nextGaussian()*.25);
        Vec3 look=getLookAngle(); arrow.setPos(arrow.position().add(look.scale(isPassenger()?1.75:-.9F))); arrow.pickup=AbstractArrow.Pickup.DISALLOWED;
        shootSource(arrow,target.getX()-getX(),target.getBoundingBox().minY+target.getEyeHeight()+range*range*3F-arrow.getY(),target.getZ()-getZ(),2,2);
        level().addFreshEntity(arrow); level().broadcastEntityEvent(this,(byte)16); playSound(SoundEvents.ARROW_SHOOT,1,1/(random.nextFloat()*.4F+.8F)); ammo.shrink(1); if(ammo.isEmpty())setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
    }
    private static void shootSource(AbstractArrow arrow,double x,double y,double z,float speed,float spread){var random=arrow.getRandom();var heading=ConstructRules.arrowHeading(x,y,z,speed,spread,random.nextGaussian(),random.nextGaussian(),random.nextGaussian());arrow.setDeltaMovement(heading[0],heading[1],heading[2]);arrow.hasImpulse=true;float horizontal=(float)Math.sqrt(heading[0]*heading[0]+heading[2]*heading[2]);arrow.setYRot((float)(net.minecraft.util.Mth.atan2(heading[0],heading[2])*(180D/Math.PI)));arrow.setXRot((float)(net.minecraft.util.Mth.atan2(heading[1],horizontal)*(180D/Math.PI)));arrow.yRotO=arrow.getYRot();arrow.xRotO=arrow.getXRot();}
    @Override public void tick() {
        previousLoad=loadProgress;
        if(!level().isClientSide && getMainHandItem().isEmpty()) { var below=blockPosition().below(); var state=level().getBlockState(below); if(level().getBlockEntity(below) instanceof DispenserBlockEntity inventory && state.hasProperty(DispenserBlock.FACING) && state.getValue(DispenserBlock.FACING)==Direction.UP)for(int slot=0;slot<inventory.getContainerSize();slot++)if(inventory.getItem(slot).getItem() instanceof ArrowItem){setItemSlot(EquipmentSlot.MAINHAND,inventory.removeItem(slot,inventory.getItem(slot).getCount()));playSound(SoundsTC.ticks,1,1);level().broadcastEntityEvent(this,(byte)17);break;} }
        super.tick(); if(!isAlive())return;
        if(getTarget()!=null && (!getTarget().isAlive() || isAlliedTo(getTarget())))setTarget(null);
        if(!level().isClientSide){setYRot(yHeadRot);if(tickCount%80==0)heal(1);BlockPos rail=blockPosition();if(level().getBlockState(rail.below()).getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock)rail=rail.below();var state=level().getBlockState(rail);if(state.is(BlocksTC.activatorRail))setNoAi(state.getValue(PoweredRailBlock.POWERED));}
        else { if(loading){if(++loadTicks>=10){loadTicks=0;loading=false;}}else loadTicks=0;loadProgress=loadTicks/10F; }
    }
    public float getLoadProgress(float partial) { float delta=loadProgress-previousLoad; if(delta<0)delta++; return previousLoad+delta*partial; }
    @Override public void handleEntityEvent(byte event) { if(event==16)swing(InteractionHand.MAIN_HAND);else if(event==17){if(!loading){loadTicks=-1;loading=true;}}else super.handleEntityEvent(event); }
    @Override public boolean isPushable() { return true; }
    @Override public boolean canBeCollidedWith() { return true; }
    @Override public int getMaxHeadXRot() { return 20; }
    @Override public void move(MoverType type,Vec3 motion) { super.move(type,motion.multiply(1/20D,1,1/20D)); }
    @Override public boolean hurt(DamageSource source,float amount) { setYRot((float)(getYRot()+random.nextGaussian()*45));setXRot((float)(getXRot()+random.nextGaussian()*20));return super.hurt(source,amount); }
    @Override public void knockback(double strength,double x,double z) { super.knockback(strength,x/10,z/10);var motion=getDeltaMovement();if(motion.y>.1)setDeltaMovement(motion.x,.1,motion.z); }
    protected Item placer() { return Mob8ConstructRegistries.BASIC_PLACER; }
    protected net.minecraft.world.inventory.MenuType<?> menuType() { return Mob8ConstructRegistries.BASIC_MENU; }
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand) {
        if(!level().isClientSide && isOwner(player) && isAlive()){if(player.isShiftKeyDown()){playSound(SoundsTC.zap,1,1);dropAmmo();spawnAtLocation(new ItemStack(placer()),.5F);discard();player.swing(hand);}else player.openMenu(new SimpleMenuProvider((id,inv,owner)->new thaumcraft.common.container.ContainerConstruct(id,inv,this),getDisplayName()),buffer->buffer.writeVarInt(getId()));return InteractionResult.SUCCESS;}
        return super.mobInteract(player,hand);
    }
    protected void dropAmmo() { if(!getMainHandItem().isEmpty()){spawnAtLocation(getMainHandItem().copy(),.5F);setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);} }
    @Override public void die(DamageSource cause) { super.die(cause);if(!level().isClientSide)dropAmmo(); }
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource cause,boolean recentlyHit) { // main-hand is handled once by dropAmmo, not native random equipment rolls
        float bonus=ConstructDrops.looting(level,cause)*.15F;ConstructDrops.roll(this,ItemsTC.mindClockwork,.2F+bonus);ConstructDrops.roll(this,ItemsTC.mechanismSimple,.5F+bonus);ConstructDrops.roll(this,BlocksTC.plankGreatwood.asItem(),.5F+bonus);ConstructDrops.roll(this,BlocksTC.plankGreatwood.asItem(),.5F+bonus);
    }
    private final class NearestTarget extends Goal {
        private LivingEntity selected;private int unseen;
        NearestTarget(){setFlags(EnumSet.of(Flag.TARGET));}
        @Override public boolean canUse(){if(random.nextInt(5)!=0)return false;selected=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(24,4,24),target->!target.isSpectator() && acceptsTarget(target,true)).stream().min(Comparator.comparingDouble(EntityTurretCrossbow.this::distanceToSqr)).orElse(null);return selected!=null;}
        @Override public boolean canContinueToUse(){var target=getTarget();if(!continueTarget(target))return false;if(getSensing().hasLineOfSight(target))unseen=0;else if(++unseen>60)return false;return true;}
        @Override public void start(){unseen=0;setTarget(selected);}
        @Override public void stop(){selected=null;setTarget(null);}
        @Override public boolean requiresUpdateEveryTick(){return true;}
    }
    /** Original hurt-by goal: modern universal-anger/peaceful targeting rules did not exist. */
    private final class Retaliate extends Goal {
        private int timestamp;private LivingEntity attacker;
        Retaliate(){setFlags(EnumSet.of(Flag.TARGET));}
        @Override public boolean canUse(){attacker=getLastHurtByMob();if(getLastHurtByMobTimestamp()==timestamp || attacker==null || attacker==EntityTurretCrossbow.this || !attacker.isAlive() || !retaliationClassAllowed(attacker) || isAlliedTo(attacker) || !isWithinRestriction(attacker.blockPosition()))return false;var owner=getOwnerUUID();if(owner!=null){if(attacker instanceof OwnableEntity owned && owner.equals(owned.getOwnerUUID()))return false;if(attacker==getOwnerEntity())return false;}else if(attacker instanceof Player player && player.getAbilities().invulnerable)return false;return true;}
        @Override public boolean canContinueToUse(){var target=getTarget();return target!=null && target.isAlive() && !isAlliedTo(target) && distanceToSqr(target)<=576;}
        @Override public void start(){timestamp=getLastHurtByMobTimestamp();setTarget(attacker);}
        @Override public void stop(){attacker=null;setTarget(null);}
        @Override public boolean requiresUpdateEveryTick(){return true;}
    }
    private final class WatchTarget extends Goal {
        private LivingEntity watched;private int remaining;
        WatchTarget(){setFlags(EnumSet.of(Flag.LOOK));}
        @Override public boolean canUse(){if(getTarget()!=null)watched=getTarget();return watched!=null;}
        @Override public boolean canContinueToUse(){return watched!=null && watched.isAlive() && distanceToSqr(watched)<=576 && remaining>0;}
        @Override public void start(){remaining=40+random.nextInt(40);}
        @Override public void stop(){watched=null;}
        @Override public void tick(){getLookControl().setLookAt(watched.getX(),watched.getEyeY(),watched.getZ(),10,getMaxHeadXRot());remaining--;}
        @Override public boolean requiresUpdateEveryTick(){return true;}
    }
}
