package thaumcraft.common.entities.construct;

/** BETA26 arithmetic, isolated from native registries/worlds for low-cost verification. */
public final class ConstructRules {
    private ConstructRules() {}
    public static int radius(int enchantability, int destructive) {
        int radius = enchantability / 3 + destructive * 2;
        return radius <= 1 ? 2 : radius;
    }
    public static int depth(int radius, int burrowing) { return radius * 8 + burrowing * 16; }
    public static int delay(float hardness, int speed) { return Math.max(1, Math.max(10 - speed, (int)(hardness * 2) - speed * 2)); }
    /** Deliberate original narrowing, not a fractional-charge correction. */
    public static float paidCharge(float charge) { return (byte)(charge - .25F); }
    public static int nextSpiral(int spiral, float radius) { return (int)(spiral + 3F + Math.max(0F, (10F - Math.abs(radius)) * 2F)); }
    public static double movementDivisor(boolean advanced, boolean bore) { return bore ? 5 : advanced ? 300 : 20; }
    public static int flag(int flags, int bit, boolean enabled) { return enabled ? flags | 1 << bit : flags & ~(1 << bit); }
    public static boolean teamAllowed(boolean hasTeam, boolean sameTeam, boolean friendly) { return !hasTeam || sameTeam == friendly; }
    /** EntityArrow.shoot in 1.12 used a float norm and Gaussian, not 1.21's triangle. */
    public static double[] arrowHeading(double x,double y,double z,float velocity,float inaccuracy,double gx,double gy,double gz){float length=(float)Math.sqrt(x*x+y*y+z*z);double noise=.007499999832361937D*inaccuracy;return new double[]{(x/length+gx*noise)*velocity,(y/length+gy*noise)*velocity,(z/length+gz*noise)*velocity};}
}
