package thaumcraft.common.items.misc;

/** Source iteration order and integer accumulation are observable with fractional chunk flux. */
public final class CreativeFluxSpongeRules {
    public static final int CHUNK_RADIUS = 4;
    public static final int CHUNK_COUNT = 81;
    public static final float DRAIN_PER_CHUNK = 500.0F;
    public static final double RIFT_RANGE = 32.0;
    private CreativeFluxSpongeRules() {}

    public static int accumulate(int previous, float drained) { return (int)(previous + drained); }
}
