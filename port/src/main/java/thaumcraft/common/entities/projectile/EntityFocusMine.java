package thaumcraft.common.entities.projectile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import thaumcraft.api.casters.*;
import thaumcraft.common.items.casters.*;
public class EntityFocusMine extends Projectile {
    private static final EntityDataAccessor<Boolean> ARMED=SynchedEntityData.defineId(EntityFocusMine.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> PACK=SynchedEntityData.defineId(EntityFocusMine.class,EntityDataSerializers.COMPOUND_TAG);
    FocusPackage focusPackage;public int counter=40;private boolean friendly;
    public EntityFocusMine(EntityType<? extends EntityFocusMine> type,Level level){super(type,level);}
    public EntityFocusMine(FocusPackage pack,Trajectory t,boolean friendly){this(CasterRegistries.MINE,pack.world);focusPackage=pack;setOwner(pack.getCaster());this.friendly=friendly;setPos(t.source);setDeltaMovement(Vec3.ZERO);entityData.set(PACK,pack.serialize());}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(ARMED,false);builder.define(PACK,new CompoundTag());}
    public boolean getIsArmed(){return entityData.get(ARMED);}public void setIsArmed(boolean armed){entityData.set(ARMED,armed);}public LivingEntity getThrower(){return getOwner() instanceof LivingEntity living?living:null;}
    public FocusPackage getFocusPackage(){if(focusPackage==null){focusPackage=new FocusPackage();focusPackage.deserialize(entityData.get(PACK));focusPackage.world=level();}return focusPackage;}
    @Override public void tick(){super.tick();if(tickCount>1200||!level().isClientSide&&getThrower()==null){discard();return;}var hit=ProjectileUtil.getHitResultOnMoveVector(this,this::canHitEntity);if(hit.getType()!=HitResult.Type.MISS&&getThrower()!=null&&!net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this,hit))setIsArmed(true);setPos(position().add(getDeltaMovement()));setDeltaMovement(getDeltaMovement().scale(.99).add(0,-.01,0));if(!level().noCollision(this)){moveTowardsClosestSpace(getX(),getY(),getZ());setDeltaMovement(getDeltaMovement().scale(.25));}if(!getIsArmed())return;if(counter>0)counter--;if(counter>0||tickCount%5!=0)return;if(level().isClientSide){var effects=getFocusPackage().getFocusEffects();if(effects.length>0)effects[random.nextInt(effects.length)].renderParticleFX(level(),getX()+random.nextGaussian()*.1,getY()+random.nextGaussian()*.1,getZ()+random.nextGaussian()*.1,random.nextGaussian()*.01,random.nextGaussian()*.01,random.nextGaussian()*.01);return;}int delay=0;for(var entity:FocusTargeting.nearby(level(),this,1))if(FocusTargeting.friendly(getThrower(),entity)==friendly){Vec3 center=entity.position().add(0,entity.getBbHeight()/2,0);var source=position();var owner=getThrower();FocusBlockActions.schedule(level(),()->FocusEngine.runFocusPackage(getFocusPackage().copy(owner),new Trajectory[]{new Trajectory(source,center.subtract(source).normalize())},new HitResult[]{new EntityHitResult(entity,center)}),delay++);}if(delay>0)discard();}
    @Override protected void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("armed",getIsArmed());tag.putBoolean("friendly",friendly);tag.put("pack",getFocusPackage().serialize());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);friendly=tag.getBoolean("friendly");setIsArmed(tag.getBoolean("armed"));counter=getIsArmed()?0:40;focusPackage=new FocusPackage();focusPackage.deserialize(tag.getCompound("pack"));focusPackage.world=level();entityData.set(PACK,focusPackage.serialize());}
}
