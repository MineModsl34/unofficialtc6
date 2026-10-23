package thaumcraft.common.entities.projectile;
import java.util.*;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import thaumcraft.common.entities.Mob8BossRegistries;
import thaumcraft.common.lib.SoundsTC;
public final class EntityHomingShard extends ThrowableProjectile {
    private static final EntityDataAccessor<Byte> STRENGTH=SynchedEntityData.defineId(EntityHomingShard.class,EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET=SynchedEntityData.defineId(EntityHomingShard.class,EntityDataSerializers.INT);
    private Class<? extends LivingEntity> targetClass;private boolean persistent;public final List<Vec3> trail=new ArrayList<>();
    public EntityHomingShard(EntityType<? extends EntityHomingShard> type,Level level){super(type,level);}
    public EntityHomingShard(Level level,LivingEntity shooter,LivingEntity target,int strength,boolean persistent){super(Mob8BossRegistries.HOMING_SHARD,shooter,level);entityData.set(TARGET,target.getId());targetClass=target.getClass();this.persistent=persistent;setStrength(strength);Vec3 look=shooter.getLookAngle();moveTo(shooter.getX()+look.x/2,shooter.getEyeY()+look.y/2,shooter.getZ()+look.z/2,shooter.getYRot(),shooter.getXRot());float yaw=shooter.getYRot()+(random.nextFloat()-random.nextFloat())*60,pitch=shooter.getXRot()+(random.nextFloat()-random.nextFloat())*60;double yr=Math.toRadians(yaw),pr=Math.toRadians(pitch);setDeltaMovement(-Math.sin(yr)*Math.cos(pr)*.5,-Math.sin(pr)*.5,Math.cos(yr)*Math.cos(pr)*.5);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(STRENGTH,(byte)0);builder.define(TARGET,-1);}
    public void setStrength(int strength){entityData.set(STRENGTH,(byte)strength);}public int getStrength(){return entityData.get(STRENGTH);}
    public LivingEntity getTarget(){Entity target=level().getEntity(entityData.get(TARGET));return target instanceof LivingEntity living?living:null;}
    @Override protected double getDefaultGravity(){return 0;}
    @Override protected void onHit(HitResult hit){if(hit instanceof EntityHitResult entity&&!level().isClientSide&&entity.getEntity()!=getOwner()){entity.getEntity().hurt(damageSources().indirectMagic(this,getOwner()),1+getStrength()*.5F);playSound(SoundsTC.zap,1,1+(random.nextFloat()-random.nextFloat())*.2F);burst();}else if(hit instanceof BlockHitResult block){Vec3 movement=getDeltaMovement();switch(block.getDirection().getAxis()){case X->setDeltaMovement(movement.multiply(-.8,1,1));case Y->setDeltaMovement(movement.multiply(1,-.8,1));case Z->setDeltaMovement(movement.multiply(1,1,-.8));}}}
    private void burst(){if(!level().isClientSide)level().broadcastEntityEvent(this,(byte)16);discard();}
    @Override public void handleEntityEvent(byte id){if(id==16)thaumcraft.common.entities.CultistPresentationHooks.burst(level(),getX(),getY(),getZ(),.3F);else super.handleEntityEvent(id);}
    @Override public void tick(){trail.add(0,new Vec3(xo,yo,zo));if(trail.size()>6)trail.remove(trail.size()-1);super.tick();LivingEntity target=getTarget();if(!level().isClientSide){if(persistent&&(target==null||!target.isAlive()||target.distanceToSqr(this)>1250)&&targetClass!=null){for(LivingEntity candidate:thaumcraft.common.lib.utils.EntityUtils.getEntitiesInRange(level(),getX(),getY(),getZ(),this,targetClass,16))if(candidate.isAlive()&&candidate!=getOwner()){target=candidate;entityData.set(TARGET,candidate.getId());break;}}if(target==null||!target.isAlive())burst();}if(tickCount>300)burst();if(tickCount%20==0&&target!=null&&target.isAlive()){double distance=distanceTo(target);if(distance>0)setDeltaMovement((target.getX()-getX())/distance,(target.getBoundingBox().minY+target.getBbHeight()*.6-getY())/distance,(target.getZ()-getZ())/distance);}setDeltaMovement(getDeltaMovement().scale(.85));}
}
