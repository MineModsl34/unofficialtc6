package thaumcraft.common.entities.projectile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.sounds.SoundEvents;
import thaumcraft.api.casters.*;
import thaumcraft.common.items.casters.*;
import thaumcraft.common.lib.fx.SourceFX;
/** BETA26 bouncing/seeking focus projectile, native synchronized package and owner. */
public class EntityFocusProjectile extends Projectile {
    private static final EntityDataAccessor<Integer> SPECIAL=SynchedEntityData.defineId(EntityFocusProjectile.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> PACK=SynchedEntityData.defineId(EntityFocusProjectile.class,EntityDataSerializers.COMPOUND_TAG);
    protected FocusPackage focusPackage;private LivingEntity target;private boolean firstParticle;private FocusEffect[] effects;public float lastRenderTick;
    public EntityFocusProjectile(EntityType<? extends EntityFocusProjectile> type,Level level){super(type,level);}
    public EntityFocusProjectile(FocusPackage pack,float speed,Trajectory trajectory,int special){this(CasterRegistries.PROJECTILE,pack.world);focusPackage=pack;setOwner(pack.getCaster());setPos(trajectory.source.add(trajectory.direction.scale(pack.getCaster().getBbWidth()*2.1)));setDeltaMovement(trajectory.direction.normalize().scale(speed));setSpecial(special);entityData.set(PACK,pack.serialize());}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(SPECIAL,0);builder.define(PACK,new CompoundTag());}
    public int getSpecial(){return entityData.get(SPECIAL);}public void setSpecial(int value){entityData.set(SPECIAL,value);}public LivingEntity getThrower(){return getOwner() instanceof LivingEntity living?living:null;}
    public FocusPackage getFocusPackage(){if(focusPackage==null){focusPackage=new FocusPackage();focusPackage.deserialize(entityData.get(PACK));focusPackage.world=level();}return focusPackage;}
    protected void impact(HitResult hit){if(getSpecial()==1&&hit instanceof BlockHitResult block){if(level().getBlockState(block.getBlockPos()).getCollisionShape(level(),block.getBlockPos()).isEmpty())return;var v=getDeltaMovement();setPos(position().subtract(v));v=new Vec3(block.getDirection().getStepX()!=0?-v.x:v.x,block.getDirection().getStepY()!=0?-.9*v.y:v.y,block.getDirection().getStepZ()!=0?-v.z:v.z).scale(.9);setDeltaMovement(v);setPos(position().subtract(v.normalize().scale(.05)));if(!level().isClientSide){playSound(SoundEvents.LEASH_KNOT_PLACE,.25F,1);if(v.length()<.2)discard();}return;}if(!level().isClientSide){var targetHit=hit instanceof EntityHitResult e?new EntityHitResult(e.getEntity(),position()):hit;var trajectory=new Trajectory(new Vec3(xo,yo,zo),getDeltaMovement().normalize());var pack=getFocusPackage();FocusBlockActions.schedule(level(),()->FocusEngine.runFocusPackage(pack,new Trajectory[]{trajectory},new HitResult[]{targetHit}),0);discard();}}
    @Override public void tick(){super.tick();if(tickCount>1200||!level().isClientSide&&getThrower()==null){discard();return;}var hit=ProjectileUtil.getHitResultOnMoveVector(this,this::canHitEntity);if(hit.getType()!=HitResult.Type.MISS&&!net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this,hit))impact(hit);if(isRemoved())return;setPos(position().add(getDeltaMovement()));setDeltaMovement(getDeltaMovement().scale(isInWater()?.8:.99).add(0,getSpecial()>1?-.005:-.01,0));firstParticle=true;if(target==null&&tickCount%5==0&&getSpecial()>1)for(var entity:FocusTargeting.nearby(level(),this,16))if(FocusTargeting.cone(this,entity,1.75F,16)&&FocusTargeting.visible(this,entity)&&FocusTargeting.friendly(getThrower(),entity)==(getSpecial()==3)){target=entity;break;}if(target!=null){var current=getDeltaMovement();var direction=target.position().add(0,target.getBbHeight()*.6,0).subtract(position()).normalize();setDeltaMovement(current.normalize().add(direction.scale(.275)).normalize().scale(current.length()));if(tickCount%5==0&&(!target.isAlive()||!FocusTargeting.cone(this,target,1.75F,16)||!FocusTargeting.visible(this,target)))target=null;}}
    /** Genuine RenderNoProjectile frame-driven coefficient, not one Gaussian effect per tick. */
    public void renderParticle(float coefficient){if(!level().isClientSide)return;lastRenderTick=coefficient;if(effects==null)effects=getFocusPackage().getFocusEffects();if(effects.length==0)return;FocusEffect effect=effects[random.nextInt(effects.length)];
        double x=xo+(getX()-xo)*coefficient,y=yo+(getY()-yo)*coefficient,z=zo+(getZ()-zo)*coefficient;
        SourceFX.local(level(),"focus_projectile_mote",new Vec3((float)x,(float)y+getBbHeight()/2F,(float)z),new Vec3(.0125F*(random.nextFloat()-.5F),.0125F*(random.nextFloat()-.5F),.0125F*(random.nextFloat()-.5F)),FocusEngine.getElementColor(effect.getKey()),0);
        if(firstParticle){firstParticle=false;var worldRandom=level().random;effect.renderParticleFX(level(),x+worldRandom.nextGaussian()*.1F,y+getBbHeight()/2F+worldRandom.nextGaussian()*.1F,z+worldRandom.nextGaussian()*.1F,worldRandom.nextGaussian()*.01F,worldRandom.nextGaussian()*.01F,worldRandom.nextGaussian()*.01F);}
    }
    @Override public Vec3 getLookAngle(){return getDeltaMovement().normalize();}
    @Override protected void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.put("pack",getFocusPackage().serialize());tag.putInt("special",getSpecial());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);setSpecial(tag.getInt("special"));focusPackage=new FocusPackage();focusPackage.deserialize(tag.getCompound("pack"));focusPackage.world=level();entityData.set(PACK,focusPackage.serialize());}
}
