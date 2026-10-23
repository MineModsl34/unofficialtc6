package thaumcraft.common.entities.projectile;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import thaumcraft.common.entities.construct.Mob8ConstructRegistries;
/** Original .2-sized vanilla-arrow dart; its customizable golem shooter belongs to stage 9. */
public final class EntityGolemDart extends AbstractArrow {
    public EntityGolemDart(EntityType<? extends EntityGolemDart> type,Level level){super(type,level);}
    public EntityGolemDart(Level level,LivingEntity owner){super(Mob8ConstructRegistries.DART,owner,level,new ItemStack(Items.ARROW),null);}
    public EntityGolemDart(Level level,double x,double y,double z){super(Mob8ConstructRegistries.DART,x,y,z,level,new ItemStack(Items.ARROW),null);}
    @Override protected ItemStack getDefaultPickupItem(){return new ItemStack(Items.ARROW);}
    /** BETA26 golem dart launch: float normalization and arrow-owned Gaussian RNG. */
    public void shootSource(double x,double y,double z,float speed,float inaccuracy){
        double[] motion=thaumcraft.common.entities.construct.ConstructRules.arrowHeading(x,y,z,speed,inaccuracy,random.nextGaussian(),random.nextGaussian(),random.nextGaussian());
        setDeltaMovement(motion[0],motion[1],motion[2]);hasImpulse=true;
        float horizontal=(float)Math.sqrt(motion[0]*motion[0]+motion[2]*motion[2]);
        setYRot((float)(net.minecraft.util.Mth.atan2(motion[0],motion[2])*180D/Math.PI));
        setXRot((float)(net.minecraft.util.Mth.atan2(motion[1],horizontal)*180D/Math.PI));yRotO=getYRot();xRotO=getXRot();
    }
}
