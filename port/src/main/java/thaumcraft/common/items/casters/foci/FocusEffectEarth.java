package thaumcraft.common.items.casters.foci;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.*;

/** BETA26 node metadata/settings/formulas; native gameplay implementation is FocusEffectRuntime. */


public class FocusEffectEarth extends FocusEffect {
    @Override
    public String getResearch() {
        return "FOCUSELEMENTAL";
    }

    @Override
    public String getKey() {
        return "thaumcraft.EARTH";
    }

    @Override
    public Aspect getAspect() {
        return Aspect.EARTH;
    }

    @Override
    public int getComplexity() {
        return this.getSettingValue("power") * 3;
    }

    @Override
    public float getDamageForDisplay(float finalPower) {
        return 2 * this.getSettingValue("power") * finalPower;
    }

    @Override
    public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) { return FocusEffectRuntime.execute(this,target,trajectory,finalPower,num); }

    @Override
    public NodeSetting[] createSettings() {
        return new NodeSetting[]{new NodeSetting("power", "focus.common.power", new NodeSetting.NodeSettingIntRange(1, 5))};
    }

    @Override
    public void renderParticleFX(Level world,double x,double y,double z,double vx,double vy,double vz) { FocusEffectRuntime.particle(this,world,x,y,z,vx,vy,vz); }

    @Override
    public void onCast(Entity caster) { FocusEffectRuntime.onCast(this,caster); }
}

