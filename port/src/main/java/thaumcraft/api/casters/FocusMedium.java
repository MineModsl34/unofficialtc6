package thaumcraft.api.casters;
public abstract class FocusMedium extends FocusNode {
    public EnumUnitType getType(){return EnumUnitType.MEDIUM;}
    public final EnumSupplyType[] mustBeSupplied(){return this instanceof FocusMediumRoot?null:new EnumSupplyType[]{EnumSupplyType.TRAJECTORY};}
    public EnumSupplyType[] willSupply(){return new EnumSupplyType[]{EnumSupplyType.TARGET};}
    public boolean hasIntermediary(){return false;}public boolean execute(Trajectory trajectory){return true;}
}
