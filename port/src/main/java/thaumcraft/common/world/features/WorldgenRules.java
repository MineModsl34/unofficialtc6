package thaumcraft.common.world.features;

/** TC6 numbers for the normal 256-high world; the old ranges are not shifted into deepslate. */
public final class WorldgenRules {
    public static final int CINNABAR_HEIGHT_BOUND = 256 / 5;
    public static final int QUARTZ_HEIGHT_BOUND = 256 / 4;

    private WorldgenRules() {
    }

    public static int scaledAttempts(int baseCount, int oreDensity) {
        return Math.round(baseCount * (oreDensity / 100.0F));
    }
}
