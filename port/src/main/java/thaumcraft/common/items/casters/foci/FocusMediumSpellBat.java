package thaumcraft.common.items.casters.foci;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
/** BETA26 source node: rates, settings, supply and branch behavior preserved. */

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusMedium;
import thaumcraft.api.casters.FocusNode;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.entities.monster.EntitySpellBat;

public class FocusMediumSpellBat extends FocusMedium {
    @Override
    public String getResearch() {
        return "FOCUSSPELLBAT";
    }

    @Override
    public String getKey() {
        return "thaumcraft.SPELLBAT";
    }

    @Override
    public Aspect getAspect() {
        return Aspect.BEAST;
    }

    @Override
    public int getComplexity() {
        return 8;
    }

    @Override
    public FocusNode.EnumSupplyType[] willSupply() {
        return new FocusNode.EnumSupplyType[]{FocusNode.EnumSupplyType.TARGET};
    }

    @Override
    public boolean execute(Trajectory trajectory) {
        if (getRemainingPackage() == null || getRemainingPackage().getCaster() == null) return false;
        EntitySpellBat bat = new EntitySpellBat(this.getRemainingPackage(), this.getSettingValue("target") == 1);
        bat.setPos(trajectory.source.x, trajectory.source.y, trajectory.source.z);
        return this.getPackage().getCaster().level().addFreshEntity(bat);
    }

    @Override
    public boolean hasIntermediary() {
        return true;
    }

    @Override
    public float getPowerMultiplier() {
        return 0.33F;
    }

    @Override
    public NodeSetting[] createSettings() {
        int[] friend = new int[]{0, 1};
        String[] friendDesc = new String[]{"focus.common.enemy", "focus.common.friend"};
        return new NodeSetting[]{new NodeSetting("target", "focus.common.target", new NodeSetting.NodeSettingIntList(friend, friendDesc))};
    }
}
