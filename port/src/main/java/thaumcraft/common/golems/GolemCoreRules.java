package thaumcraft.common.golems;

/** Source scalar stat/flag formulas, independent of world/registry bootstrap. */
public final class GolemCoreRules {
    private GolemCoreRules() {}
    public static long joinProperties(int high, int low) { return ((long) high << 32) | Integer.toUnsignedLong(low); }
    public static int color(int flags) { return (byte) (flags >>> 24); }
    public static int flags(int flags) { return (byte) (flags >>> 16); }
    public static int withColor(int packed, byte color) { return packed & 0x00ffffff | (color & 255) << 24; }
    public static int withFlags(int packed, byte flags) { return packed & 0xff00ffff | (flags & 255) << 16; }
    public static int health(int modifier, int rank, boolean fragile) { return (fragile ? (int) ((10 + modifier) * 0.75) : 10 + modifier) + rank; }
    public static int armor(int base, boolean armored, boolean fragile) {
        int armor = armored ? (int) Math.max(base * 1.5, base + 1) : base;
        return fragile ? (int) (armor * 0.75) : armor;
    }
    public static double damage(int base, int rank, boolean fighter, boolean brutal) {
        return fighter ? (brutal ? Math.max(base * 1.5, base + 1) : base) + rank * 0.25 : 0;
    }
    public static float moveSpeed(int rank, boolean light, boolean heavy, boolean flyer, boolean wheeled) {
        return 1F + rank * 0.025F + (light ? 0.2F : 0) - (heavy ? 0.175F : 0) - (flyer ? 0.33F : 0) + (wheeled ? 0.25F : 0);
    }
    public static float blastDamage(float health, float damage) { return Math.min(health / 2, damage * 0.3F); }
}
