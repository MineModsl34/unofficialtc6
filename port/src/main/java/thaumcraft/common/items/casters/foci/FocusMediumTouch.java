package thaumcraft.common.items.casters.foci;
import java.util.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.*;
import thaumcraft.common.items.casters.FocusTargeting;
public class FocusMediumTouch extends FocusMedium {
    public String getResearch(){return "BASEAUROMANCY";}public String getKey(){return "thaumcraft.TOUCH";}public int getComplexity(){return 2;}public Aspect getAspect(){return Aspect.AVERSION;}
    public EnumSupplyType[] willSupply(){return new EnumSupplyType[]{EnumSupplyType.TRAJECTORY,EnumSupplyType.TARGET};}
    protected double range(){return this instanceof FocusMediumBolt?16:getPackage().getCaster() instanceof Player player?player.blockInteractionRange():0;}
    protected HitResult hit(Trajectory trajectory){var direction=trajectory.direction.normalize();var entity=FocusTargeting.pointed(getPackage().world,getPackage().getCaster(),trajectory.source,direction,.25,range(),.25F);return entity!=null?entity:FocusTargeting.block(getPackage().world,getPackage().getCaster(),trajectory.source,trajectory.source.add(direction.scale(range())));}
    public Trajectory[] supplyTrajectories(){if(getParent()==null||getParent().supplyTrajectories()==null)return new Trajectory[0];var result=new ArrayList<Trajectory>();for(var t:getParent().supplyTrajectories()){var direction=t.direction.normalize();var hit=hit(t);Vec3 end=t.source.add(direction.scale(range()));if(hit instanceof EntityHitResult entity)end=t.source.add(direction.scale(t.source.distanceTo(entity.getEntity().position())));else if(hit!=null)end=hit.getLocation();result.add(new Trajectory(end,direction));}return result.toArray(Trajectory[]::new);}
    public HitResult[] supplyTargets(){if(getParent()==null||!(getPackage().getCaster() instanceof Player)||getParent().supplyTrajectories()==null)return new HitResult[0];var result=new ArrayList<HitResult>();for(var t:getParent().supplyTrajectories()){var hit=hit(t);if(hit!=null)result.add(hit);}return result.toArray(HitResult[]::new);}
    public boolean execute(Trajectory t){FocusEffectRuntime.trail(getPackage().getFocusEffects(),getPackage().world,t.source,new Vec3((float)t.direction.x/2F,(float)t.direction.y/2F,(float)t.direction.z/2F));return true;}
}
