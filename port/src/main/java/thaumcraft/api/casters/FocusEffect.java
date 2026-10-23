package thaumcraft.api.casters;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
public abstract class FocusEffect extends FocusNode {
    public EnumUnitType getType(){return EnumUnitType.EFFECT;}public final EnumSupplyType[] mustBeSupplied(){return new EnumSupplyType[]{EnumSupplyType.TARGET};}public EnumSupplyType[] willSupply(){return null;}
    public abstract boolean execute(HitResult target,Trajectory trajectory,float power,int num);
    public float getDamageForDisplay(float finalPower){return 0;}
    public abstract void renderParticleFX(Level world,double x,double y,double z,double vx,double vy,double vz);
    public void onCast(Entity caster){}
}
