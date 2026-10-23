package thaumcraft.common.lib.crafting;

/** Pure numeric rules transcribed from TC6 TileCrucible/BlockCrucible. */
public final class CrucibleRules {
    public static final int CAPACITY = 1000, MAX_TAGS = 500;
    private CrucibleRules() {}
    public static short heat(short previous, boolean water, boolean source) {
        if (water && source) return previous < 200 ? (short) (previous + 1) : previous;
        return previous > 0 ? (short) (previous - 1) : previous;
    }
    public static boolean syncHeat(short previous, short next, boolean water, boolean source) {
        return water && (source ? previous < 151 && next >= 151 : previous > 0 && next == 149);
    }
    public static float fluidHeight(int water, int aspects) {
        float base = .3F + .5F * (water / 1000F);
        float height = base + aspects / 500F * (1F - base);
        if (height > 1F) height = 1.001F;
        if (height == 1F) height = .9999F;
        return height;
    }
    public static int comparator(int total) { return (int) Math.floor(total / 500F * 14F) + (total > 0 ? 1 : 0); }
    /** The source loop shrinks its own upper bound, intentionally NOT a full-stack conversion. */
    public static int sourceBatchRemainder(int size) { for (int a = 0; a < size; a++) size--; return size; }
    public static float decayFlux(boolean fluxAspect) { return fluxAspect ? 1F : .25F; }
}
