package thaumcraft.api.crafting;

/** Deprecated source extension contract; current infusion uses IInfusionStabiliser/Ext. */
@Deprecated
public interface IStabilizable {
    @Deprecated void addStability();
    @Deprecated EnumStability getStability();
    @Deprecated enum EnumStability { VERY_STABLE, STABLE, UNSTABLE, VERY_UNSTABLE }
}
