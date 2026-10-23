package thaumcraft.common.lib.crafting;

/** Engine-independent source TileSmelter arithmetic; output speed is not input cook time. */
public final class EssentiaSmelterRules {
    private EssentiaSmelterRules() {}
    public static int outputPeriod(int type,boolean alumentum) { int base = 20 - (type == 1 ? 10 : 5); return alumentum ? (int)(base * .8F) : base; }
    public static float efficiency(int type) { return type == 1 ? .9F : type == 2 ? .95F : .8F; }
    public static int cookTime(int vis,int bellows) { return (int)(vis * 2 * (1F - .125F * bellows)); }
}
