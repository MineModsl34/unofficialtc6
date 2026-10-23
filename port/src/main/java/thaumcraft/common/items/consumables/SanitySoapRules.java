package thaumcraft.common.items.consumables;

/** Pure BETA26 soap timing and warp reductions; source has no research gate or cooldown. */
public final class SanitySoapRules {
    public static final int USE_DURATION = 100;
    private SanitySoapRules() {}

    public static boolean completes(int remaining) {
        return USE_DURATION - remaining > 95;
    }

    public static int normalReduction(boolean ward, boolean purifyingFluid) {
        return 1 + (ward ? 1 : 0) + (purifyingFluid ? 1 : 0);
    }
}
