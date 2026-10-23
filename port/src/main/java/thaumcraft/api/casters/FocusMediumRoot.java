package thaumcraft.api.casters;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import thaumcraft.api.aspects.Aspect;
public class FocusMediumRoot extends FocusMedium {
    Trajectory[] trajectories;HitResult[] targets;
    public FocusMediumRoot(){}public FocusMediumRoot(Trajectory[] trajectories,HitResult[] targets){this.trajectories=trajectories;this.targets=targets;}
    public String getResearch(){return "BASEAUROMANCY";}public String getKey(){return "ROOT";}public int getComplexity(){return 0;}public Aspect getAspect(){return null;}
    public EnumSupplyType[] willSupply(){return new EnumSupplyType[]{EnumSupplyType.TARGET,EnumSupplyType.TRAJECTORY};}
    public HitResult[] supplyTargets(){return targets;}public Trajectory[] supplyTrajectories(){return trajectories;}
    public void setupFromCaster(LivingEntity caster){trajectories=new Trajectory[]{new Trajectory(generateSourceVector(caster),caster.getLookAngle())};targets=new HitResult[]{new EntityHitResult(caster)};}
    public void setupFromCasterToTarget(LivingEntity caster,Entity target,double offset){var source=generateSourceVector(caster);trajectories=new Trajectory[]{new Trajectory(source,new Vec3(target.getX()-source.x,target.getBoundingBox().minY+target.getBbHeight()/2-source.y+offset,target.getZ()-source.z).normalize())};targets=new HitResult[]{new EntityHitResult(caster)};}
    public void setupFromCasterToTargetLoc(LivingEntity caster,double x,double y,double z){var source=generateSourceVector(caster);trajectories=new Trajectory[]{new Trajectory(source,new Vec3(x,y,z).subtract(source).normalize())};targets=new HitResult[]{new EntityHitResult(caster)};}
    private Vec3 generateSourceVector(LivingEntity caster){return caster.position().add(0,caster.getEyeHeight()-.1F,0);}
}
