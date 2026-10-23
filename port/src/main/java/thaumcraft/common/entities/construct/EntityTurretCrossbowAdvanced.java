package thaumcraft.common.entities.construct;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;

public final class EntityTurretCrossbowAdvanced extends EntityTurretCrossbow {
    private static final EntityDataAccessor<Byte> FLAGS=SynchedEntityData.defineId(EntityTurretCrossbowAdvanced.class,EntityDataSerializers.BYTE);
    public EntityTurretCrossbowAdvanced(EntityType<? extends EntityTurretCrossbowAdvanced> type,Level level){super(type,level);}
    public static AttributeSupplier.Builder attributes(){return EntityTurretCrossbow.attributes().add(Attributes.MAX_HEALTH,40).add(Attributes.ARMOR,8);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(FLAGS,(byte)2);}
    @Override protected int maximumAttackInterval(){return 40;}
    public boolean getTargetAnimal(){return flag(0);}public boolean getTargetMob(){return flag(1);}public boolean getTargetPlayer(){return flag(2);}public boolean getTargetFriendly(){return flag(3);}
    public void setTargetAnimal(boolean value){flag(0,value);}public void setTargetMob(boolean value){flag(1,value);}public void setTargetPlayer(boolean value){flag(2,value);}public void setTargetFriendly(boolean value){flag(3,value);}
    private boolean flag(int bit){return (entityData.get(FLAGS)&1<<bit)!=0;}private void flag(int bit,boolean value){entityData.set(FLAGS,(byte)ConstructRules.flag(entityData.get(FLAGS),bit,value));setTarget(null);}
    @Override public boolean canAttackType(EntityType<?> type){return true;} // Source override permits hostile ghasts.
    @Override protected boolean retaliationClassAllowed(LivingEntity target){boolean category=target instanceof Player?getTargetPlayer():target instanceof Enemy?getTargetMob():target instanceof Mob && getTargetAnimal();if(!category)return false;if(target instanceof Player && !level().isClientSide && !level().getServer().isPvpAllowed() && !getTargetFriendly()){setTargetPlayer(false);return false;}return true;}
    @Override protected boolean acceptsTarget(LivingEntity target,boolean sight){
        if(target==this || !target.isAlive() || !isWithinRestriction(target.blockPosition()))return false;
        // Source IAnimals includes many non-hostile Mob subclasses, not only vanilla Animal.
        boolean category=target instanceof Player?getTargetPlayer():target instanceof Enemy?getTargetMob():target instanceof Mob && getTargetAnimal();if(!category)return false;
        if(target instanceof Player && !level().isClientSide && !level().getServer().isPvpAllowed() && !getTargetFriendly()){setTargetPlayer(false);return false;}
        if(!ConstructRules.teamAllowed(getTeam()!=null,getTeam()==target.getTeam(),getTargetFriendly()))return false;
        var owner=getOwnerUUID();if(owner!=null){if(target instanceof OwnableEntity owned){if(owner.equals(owned.getOwnerUUID())!=getTargetFriendly())return false;}else if(!(target instanceof Player)&&getTargetFriendly())return false;if(target==getOwnerEntity()&&!getTargetFriendly())return false;}
        else if(target instanceof Player player && player.getAbilities().invulnerable && !getTargetFriendly())return false;
        if(target instanceof Player player){double range=24;if(player.isShiftKeyDown())range*=.8F;if(player.isInvisible())range*=.7F*Math.max(.1F,player.getArmorCoverPercentage());if(distanceTo(player)>range)return false;}
        return !sight || getSensing().hasLineOfSight(target);
    }
    @Override protected boolean continueTarget(LivingEntity target){return target!=null && target.isAlive() && ConstructRules.teamAllowed(getTeam()!=null,getTeam()==target.getTeam(),getTargetFriendly()) && distanceToSqr(target)<=576;}
    @Override public void tick(){super.tick();if(!level().isClientSide && !level().getServer().isPvpAllowed() && getTarget() instanceof Player && getTarget()!=getOwnerEntity())setTarget(null);}
    @Override public void move(MoverType type,Vec3 motion){super.move(type,motion.multiply(1/15D,1,1/15D));}
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x/10,z/10);}
    @Override protected Item placer(){return Mob8ConstructRegistries.ADVANCED_PLACER;}
    @Override protected net.minecraft.world.inventory.MenuType<?> menuType(){return Mob8ConstructRegistries.ADVANCED_MENU;}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putByte("targets",entityData.get(FLAGS));}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(FLAGS,tag.getByte("targets"));}
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource cause,boolean recentlyHit){float b=ConstructDrops.looting(level,cause)*.15F;ConstructDrops.roll(this,ItemsTC.mindBiothaumic,.2F+b);ConstructDrops.roll(this,ItemsTC.mechanismSimple,.5F+b);ConstructDrops.roll(this,BlocksTC.plankGreatwood.asItem(),.5F+b);ConstructDrops.roll(this,BlocksTC.plankGreatwood.asItem(),.5F+b);ConstructDrops.roll(this,ItemsTC.plateBrass,.3F+b);ConstructDrops.roll(this,ItemsTC.plateIron,.4F+b);ConstructDrops.roll(this,ItemsTC.plateIron,.4F+b);}
}
