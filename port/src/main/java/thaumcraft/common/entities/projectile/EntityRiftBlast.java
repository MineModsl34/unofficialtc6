package thaumcraft.common.entities.projectile;
import java.util.*;
import net.minecraft.network.syncher.*;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import thaumcraft.common.entities.Mob8BossRegistries;
import thaumcraft.common.lib.SoundsTC;
/** Source homing spell projectile. This is NOT an EntityFluxRift world-ecology substitute. */
public final class EntityRiftBlast extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> TARGET=SynchedEntityData.defineId(EntityRiftBlast.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RED=SynchedEntityData.defineId(EntityRiftBlast.class,EntityDataSerializers.BOOLEAN);
    public final List<Vec3> trail=new ArrayList<>();
    public EntityRiftBlast(EntityType<? extends EntityRiftBlast> type,Level level){super(type,level);}
    public EntityRiftBlast(Level level,LivingEntity shooter,LivingEntity target,boolean red){super(Mob8BossRegistries.RIFT_BLAST,shooter,level);entityData.set(TARGET,target.getId());entityData.set(RED,red);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(TARGET,-1);builder.define(RED,false);}
    public boolean isRed(){return entityData.get(RED);}public LivingEntity getTarget(){Entity target=level().getEntity(entityData.get(TARGET));return target instanceof LivingEntity living?living:null;}
    @Override protected double getDefaultGravity(){return 0;}
    @Override protected void onHit(HitResult hit){if(!level().isClientSide&&getOwner() instanceof LivingEntity shooter&&hit instanceof EntityHitResult entity)entity.getEntity().hurt(damageSources().indirectMagic(this,shooter),(float)shooter.getAttributeValue(Attributes.ATTACK_DAMAGE)*(isRed()?1:.6F));playSound(SoundsTC.shock,1,1+(random.nextFloat()-random.nextFloat())*.2F);discard();}
    @Override public void tick(){super.tick();if(tickCount>(isRed()?240:160))discard();LivingEntity target=getTarget();if(target!=null){if(!target.isAlive())discard();double distance=distanceToSqr(target);if(distance>0){Vec3 movement=getDeltaMovement().add((target.getX()-getX())/distance,(target.getBoundingBox().minY+target.getBbHeight()*.6-getY())/distance,(target.getZ()-getZ())/distance);setDeltaMovement(Mth.clamp((float)movement.x,-.33F,.33F),Mth.clamp((float)movement.y,-.33F,.33F),Mth.clamp((float)movement.z,-.33F,.33F));}if(level().isClientSide){var point=position().add(random.nextGaussian()*.05,random.nextGaussian()*.05,random.nextGaussian()*.05);trail.add(point);float size=.3F+random.nextFloat()*.2F;var tint=new Vec3(random.nextFloat(),random.nextFloat()*.2F,random.nextFloat()*.2F);thaumcraft.common.lib.fx.SourceFX.local(level(),"rift_blast",point,tint,Float.floatToIntBits(size),random.nextInt(2));if(trail.size()>9)trail.remove(0);}}}
    @Override public boolean hurt(DamageSource source,float amount){if(isInvulnerableTo(source)||source.getEntity()==null)return false;setDeltaMovement(source.getEntity().getLookAngle().scale(.9));playSound(SoundsTC.zap,1,1+(random.nextFloat()-random.nextFloat())*.2F);return true;}
}
