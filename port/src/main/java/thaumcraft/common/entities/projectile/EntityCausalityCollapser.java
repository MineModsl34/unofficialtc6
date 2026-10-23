package thaumcraft.common.entities.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.entities.IFluxRift;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.Mob8MiscRegistries;

/** Source fixed .8 velocity, radius3 collapse targets and radius2 terrain explosion (no fire). */
public final class EntityCausalityCollapser extends ThrowableItemProjectile {
    public EntityCausalityCollapser(EntityType<? extends EntityCausalityCollapser> type,Level level){super(type,level);}
    public EntityCausalityCollapser(Level level,LivingEntity owner){super(Mob8MiscRegistries.CAUSALITY_COLLAPSER,owner,level);}
    @Override protected Item getDefaultItem(){return ItemsTC.causalityCollapser;}
    @Override protected double getDefaultGravity(){return .03F;}
    @Override public Vec3 getMovementToShoot(double x,double y,double z,float velocity,float inaccuracy) {
        double spread=.007499999832361937D*inaccuracy;
        return new Vec3(x,y,z).normalize().add(random.nextGaussian()*spread,random.nextGaussian()*spread,random.nextGaussian()*spread).scale(.8F);
    }
    @Override public void tick() {
        super.tick();if(level().isClientSide)thaumcraft.common.lib.fx.SourceFX.local(level(),"causality",this.position(),net.minecraft.world.phys.Vec3.ZERO,this.getId(),0);
    }
    @Override protected void onHit(HitResult hit) {
        if(level().isClientSide)return;
        level().explode(this,damageSources().explosion(this,null),null,getX(),getY(),getZ(),2,false,Level.ExplosionInteraction.BLOCK);
        for(var entity:level().getEntities(this,new net.minecraft.world.phys.AABB(getX(),getY(),getZ(),getX(),getY(),getZ()).inflate(3),entity->entity instanceof IFluxRift))((IFluxRift)entity).setCollapse(true);
        discard();
    }
}
