package thaumcraft.common.items.casters.foci;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
/** BETA26 source node: rates, settings, supply and branch behavior preserved. */

import java.util.ArrayList;
import thaumcraft.api.casters.FocusMod;
import thaumcraft.api.casters.FocusNode;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;

public class FocusModScatter extends FocusMod {
    @Override
    public String getResearch() {
        return "FOCUSSCATTER";
    }

    @Override
    public String getKey() {
        return "thaumcraft.SCATTER";
    }

    @Override
    public int getComplexity() {
        return (int)Math.max(2.0F, 2.0F * (this.getSettingValue("forks") - this.getSettingValue("cone") / 45.0F));
    }

    @Override
    public NodeSetting[] createSettings() {
        int[] angles = new int[]{10, 30, 60, 90, 180, 270, 360};
        String[] anglesDesc = new String[]{"10", "30", "60", "90", "180", "270", "360"};
        return new NodeSetting[]{
            new NodeSetting("forks", "focus.scatter.forks", new NodeSetting.NodeSettingIntRange(2, 10)),
            new NodeSetting("cone", "focus.scatter.cone", new NodeSetting.NodeSettingIntList(angles, anglesDesc))
        };
    }

    @Override
    public FocusNode.EnumSupplyType[] mustBeSupplied() {
        return new FocusNode.EnumSupplyType[]{FocusNode.EnumSupplyType.TRAJECTORY};
    }

    @Override
    public FocusNode.EnumSupplyType[] willSupply() {
        return new FocusNode.EnumSupplyType[]{FocusNode.EnumSupplyType.TRAJECTORY};
    }

    @Override
    public Trajectory[] supplyTrajectories() {
        if (this.getParent() == null) {
            return new Trajectory[0];
        }

        ArrayList<Trajectory> tT = new ArrayList<>();
        int forks = this.getSettingValue("forks");
        int angle = this.getSettingValue("cone");
        if (this.getParent().supplyTrajectories() != null) {
            for (Trajectory sT : this.getParent().supplyTrajectories()) {
                for (int a = 0; a < forks; a++) {
                    Vec3 sV = sT.source;
                    Vec3 dV = sT.direction;
                    dV = dV.normalize();
                    dV = dV.add(
                        this.getPackage().world.random.nextGaussian() * 0.0075F * angle,
                        this.getPackage().world.random.nextGaussian() * 0.0075F * angle,
                        this.getPackage().world.random.nextGaussian() * 0.0075F * angle
                    );
                    tT.add(new Trajectory(sV, dV.normalize()));
                }
            }
        }

        return tT.toArray(new Trajectory[0]);
    }

    @Override
    public float getPowerMultiplier() {
        return 1.0F / (this.getSettingValue("forks") / 2.0F);
    }

    @Override
    public boolean execute() {
        return true;
    }

    @Override
    public boolean isExclusive() {
        return true;
    }
}

