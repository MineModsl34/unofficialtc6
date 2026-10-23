package thaumcraft.common.blocks.misc;

/** Scalar rules of BETA26 BlockFluidDeath and Forge 1.12 BlockFluidClassic (four quanta). */
public final class DeathFluidRules {
    public static final int QUANTA = 4;
    public static final int TICK_RATE = 1500 / 200;
    private DeathFluidRules() {}
    public static int quanta(int metadata) {
        if (metadata < 0 || metadata >= QUANTA) throw new IllegalArgumentException("Liquid death metadata must be 0..3");
        return QUANTA - metadata;
    }
    public static int metadata(int amount) {
        if (amount < 1 || amount > QUANTA) throw new IllegalArgumentException("Liquid death amount must be 1..4");
        return QUANTA - amount;
    }
    public static float slowdown(int metadata) { return 1 - quanta(metadata) / 8F; }
    public static int dissolveDamage(int metadata) { return quanta(metadata) + 1; }
    public static float renderHeight(int metadata) { return quanta(metadata) / 5F; }
    public static int expectedQuanta(boolean verticalFeed, int largestNeighbor) {
        return verticalFeed ? QUANTA - 1 : largestNeighbor - 1;
    }
    public static int outwardMetadata(int remaining, boolean verticalFeed) {
        return verticalFeed ? 1 : QUANTA - remaining + 1;
    }
    public static boolean downwardCurrent(boolean sameFluid, boolean sourceIce, boolean attachException, boolean stair, boolean solidFace) {
        return !sameFluid && !sourceIce && !attachException && !stair && solidFace;
    }
}
