package thaumcraft.common.entities.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.Mob8MiscRegistries;
import thaumcraft.common.lib.potions.WarpEffects;

/** Source radius5/100tick infection and ten exact placement trials, native collision and status sync. */
public final class EntityBottleTaint extends ThrowableItemProjectile {
    public EntityBottleTaint(EntityType<? extends EntityBottleTaint> type,Level level){super(type,level);}
    public EntityBottleTaint(Level level,LivingEntity owner){super(Mob8MiscRegistries.BOTTLE_TAINT,owner,level);}
    @Override protected Item getDefaultItem(){return ItemsTC.bottleTaint;}
    @Override protected double getDefaultGravity(){return .03F;}
    @Override public net.minecraft.world.phys.Vec3 getMovementToShoot(double x,double y,double z,float velocity,float inaccuracy) {
        // EntityThrowable 1.12 used Gaussian spread, not Projectile 1.21's triangular distribution.
        double spread=.007499999832361937D*inaccuracy;
        return new net.minecraft.world.phys.Vec3(x,y,z).normalize().add(random.nextGaussian()*spread,random.nextGaussian()*spread,random.nextGaussian()*spread).scale(velocity);
    }
    @Override protected void onHit(HitResult hit) {
        if(level().isClientSide)return;
        for(var target:level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(getX(),getY(),getZ(),getX(),getY(),getZ()).inflate(5)))
            if(!(target instanceof ITaintedMob)&&!target.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD))
                target.addEffect(new MobEffectInstance(WarpEffects.FLUX_TAINT,100,0,false,true));
        for(int trial=0;trial<10;trial++) {
            int x=(int)((random.nextFloat()-random.nextFloat())*4),z=(int)((random.nextFloat()-random.nextFloat())*4);
            BlockPos pos=blockPosition().offset(x,0,z);
            if(level().random.nextBoolean()) {
                if(!tryGoo(pos))tryGoo(pos.below());
            }
        }
        level().broadcastEntityEvent(this,(byte)3);discard();
    }
    private boolean tryGoo(BlockPos pos) {
        if(!level().hasChunkAt(pos)||!level().hasChunkAt(pos.below()))return false;
        if(level().getBlockState(pos.below()).isRedstoneConductor(level(),pos.below())&&level().getBlockState(pos).canBeReplaced()) {
            return level().setBlockAndUpdate(pos,BlocksTC.fluxGoo.defaultBlockState());
        }
        return false;
    }
    @Override public void handleEntityEvent(byte status) {
        if(status==3) {
            thaumcraft.common.lib.fx.SourceFX.local(level(),"bottle_taint",this.position(),net.minecraft.world.phys.Vec3.ZERO,this.getId(),0);
        } else super.handleEntityEvent(status);
    }
}
