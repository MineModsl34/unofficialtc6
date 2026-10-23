package thaumcraft.common.items.casters.foci;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
/** BETA26 source node: rates, settings, supply and branch behavior preserved. */

import thaumcraft.api.casters.FocusModSplit;
import thaumcraft.api.casters.FocusNode;

public class FocusModSplitTarget extends FocusModSplit {
    @Override
    public String getResearch() {
        return "FOCUSSPLIT";
    }

    @Override
    public String getKey() {
        return "thaumcraft.SPLITTARGET";
    }

    @Override
    public int getComplexity() {
        return 4;
    }

    @Override
    public FocusNode.EnumSupplyType[] mustBeSupplied() {
        return new FocusNode.EnumSupplyType[]{FocusNode.EnumSupplyType.TARGET};
    }

    @Override
    public FocusNode.EnumSupplyType[] willSupply() {
        return new FocusNode.EnumSupplyType[]{FocusNode.EnumSupplyType.TARGET};
    }

    @Override
    public HitResult[] supplyTargets() {
        return this.getParent().supplyTargets();
    }

    @Override
    public boolean execute() {
        return true;
    }
}

