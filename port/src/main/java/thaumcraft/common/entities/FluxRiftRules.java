package thaumcraft.common.entities;

/** Arithmetic lifted from BETA26 EntityFluxRift and TileVoidSiphon; no world/bootstrap required. */
public final class FluxRiftRules {
    public static final int MAX_SERIALIZED_SIZE = 4096;
    public static final int[] EVENT_WEIGHTS = {50, 10, 20, 20, 1};
    public static final int[] EVENT_COSTS = {5, 0, 10, 10, 0};
    private FluxRiftRules() {}
    public static int boundedSize(int size) { return Math.max(0, Math.min(MAX_SERIALIZED_SIZE, size)); }
    public static float stability(float value) { return Float.isFinite(value) ? Math.max(-100, Math.min(100, value)) : 0; }
    public static int category(float value) { return value > 50 ? 0 : value >= 0 ? 1 : value > -25 ? 2 : 3; }
    public static double growthCost(int size) { return Math.sqrt(size * 2); }
    public static int initialSize(float flux) { return (int)Math.sqrt(flux * 3F); }
    public static int collapseSeeds(int maximum) { return (int)Math.sqrt(maximum); }
    /** Source deliberately divides squared distance by 32, not radius-squared 1024. */
    public static int collapseExposure(double distanceSquared, int scale) { return (int)((1 - distanceSquared / 32) * scale); }
    public static int siphonProgress(int old, int size) { return (int)(old + Math.sqrt(size)); }
    public static float siphonStability(float old, int size) { return (float)(old - Math.sqrt(size) / 15); }
    public static int event(double unit) {
        double total = 101, threshold = unit * total;
        for (int i = 0; i < EVENT_WEIGHTS.length; i++) { threshold -= EVENT_WEIGHTS[i]; if (threshold <= 0) return i; }
        return 4;
    }
}
