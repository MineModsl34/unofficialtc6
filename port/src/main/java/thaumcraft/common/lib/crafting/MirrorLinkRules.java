package thaumcraft.common.lib.crafting;

/** Pure source constants/order; native mirror ticks call these rules. */
public final class MirrorLinkRules {
    private MirrorLinkRules() {}
    public static int retryInterval(int current, boolean valid) { return valid ? 40 : Math.min(600,current + 20); }
    public static int pollutionThreshold(boolean essentia) { return essentia ? 64 : 128; }
    /** The original pollutes only when STRICTLY above threshold, once per tick. */
    public static boolean pollutes(int instability, boolean essentia) { return instability > pollutionThreshold(essentia); }
    public static boolean canEject(int ticks, int queued) { return ticks > 20 && queued > 0; }
    public static boolean acceptsEssentiaTransfer(int amount) { return amount > 0 && amount <= 1; }
}
