package thaumcraft.common.entities.projectile;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.common.entities.Mob8NaturalRules;
/** Genuine vanilla arrow type, as TC6 used, with its old Gaussian launch distribution. */
public final class SourcePechArrow extends Arrow {
    public SourcePechArrow(Level level,LivingEntity owner,ItemStack pickup,ItemStack weapon){super(level,owner,pickup,weapon);}
    @Override public void shoot(double x,double y,double z,float speed,float inaccuracy){double[] motion=Mob8NaturalRules.arrowHeading(x,y,z,speed,inaccuracy,random.nextGaussian(),random.nextGaussian(),random.nextGaussian());setDeltaMovement(motion[0],motion[1],motion[2]);hasImpulse=true;float horizontal=(float)Math.sqrt(motion[0]*motion[0]+motion[2]*motion[2]);setYRot((float)(Mth.atan2(motion[0],motion[2])*(180D/Math.PI)));setXRot((float)(Mth.atan2(motion[1],horizontal)*(180D/Math.PI)));yRotO=getYRot();xRotO=getXRot();}
}
