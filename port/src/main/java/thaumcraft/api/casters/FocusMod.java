package thaumcraft.api.casters;
import thaumcraft.api.aspects.Aspect;
public abstract class FocusMod extends FocusNode {
    public EnumUnitType getType(){return EnumUnitType.MOD;}public abstract boolean execute();public Aspect getAspect(){return null;}
}
