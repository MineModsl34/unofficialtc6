package thaumcraft.common.items.casters.foci;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.*;

/** BETA26 node metadata/settings/formulas; native gameplay implementation is FocusEffectRuntime. */


public class FocusEffectRift extends FocusEffect {
    @Override
    public String getResearch() {
        return "FOCUSRIFT";
    }

    @Override
    public String getKey() {
        return "thaumcraft.RIFT";
    }

    @Override
    public Aspect getAspect() {
        return Aspect.ELDRITCH;
    }

    @Override
    public int getComplexity() {
        return 3 + this.getSettingValue("duration") / 2 + this.getSettingValue("depth") / 4;
    }

    @Override
    public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) { return FocusEffectRuntime.execute(this,target,trajectory,finalPower,num); }

    public static boolean createHole(Level world,BlockPos pos,Direction side,byte count,int max) { return thaumcraft.common.tiles.misc.TileHole.createHole(world,pos,side,count,max); }

    @Override
    public NodeSetting[] createSettings() {
        int[] depth = new int[]{8, 16, 24, 32};
        String[] depthDesc = new String[]{"8", "16", "24", "32"};
        return new NodeSetting[]{
            new NodeSetting("depth", "focus.rift.depth", new NodeSetting.NodeSettingIntList(depth, depthDesc)),
            new NodeSetting("duration", "focus.common.duration", new NodeSetting.NodeSettingIntRange(2, 10))
        };
    }

    @Override
    public void renderParticleFX(Level world,double x,double y,double z,double vx,double vy,double vz) { FocusEffectRuntime.particle(this,world,x,y,z,vx,vy,vz); }

    @Override
    public void onCast(Entity caster) { FocusEffectRuntime.onCast(this,caster); }
}

