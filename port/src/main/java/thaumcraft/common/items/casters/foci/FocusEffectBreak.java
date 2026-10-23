package thaumcraft.common.items.casters.foci;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.*;

/** BETA26 node metadata/settings/formulas; native gameplay implementation is FocusEffectRuntime. */


public class FocusEffectBreak extends FocusEffect {
    @Override
    public String getResearch() {
        return "FOCUSBREAK";
    }

    @Override
    public String getKey() {
        return "thaumcraft.BREAK";
    }

    @Override
    public Aspect getAspect() {
        return Aspect.ENTROPY;
    }

    @Override
    public int getComplexity() {
        return this.getSettingValue("power") * 3
            + this.getSettingValue("silk") * 4
            + (this.getSettingValue("fortune") == 0 ? 0 : (this.getSettingValue("fortune") + 1) * 3);
    }

    @Override
    public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) { return FocusEffectRuntime.execute(this,target,trajectory,finalPower,num); }

    @Override
    public NodeSetting[] createSettings() {
        int[] silk = new int[]{0, 1};
        String[] silkDesc = new String[]{"focus.common.no", "focus.common.yes"};
        int[] fortune = new int[]{0, 1, 2, 3, 4};
        String[] fortuneDesc = new String[]{"focus.common.no", "I", "II", "III", "IV"};
        return new NodeSetting[]{
            new NodeSetting("power", "focus.break.power", new NodeSetting.NodeSettingIntRange(1, 5)),
            new NodeSetting("fortune", "focus.common.fortune", new NodeSetting.NodeSettingIntList(fortune, fortuneDesc)),
            new NodeSetting("silk", "focus.common.silk", new NodeSetting.NodeSettingIntList(silk, silkDesc))
        };
    }

    @Override
    public void renderParticleFX(Level world,double x,double y,double z,double vx,double vy,double vz) { FocusEffectRuntime.particle(this,world,x,y,z,vx,vy,vz); }

    @Override
    public void onCast(Entity caster) { FocusEffectRuntime.onCast(this,caster); }
}

