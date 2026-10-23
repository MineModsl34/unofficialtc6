package thaumcraft.common.tiles.essentia;

/** Source arithmetic seams. Suction ties retain DOWN/UP/NORTH/SOUTH/WEST/EAST iteration order. */
public final class TransportRules {
    private TransportRules() {}
    public static int propagatedSuction(int neighbor, boolean restrict) { return restrict ? neighbor / 2 : neighbor - 1; }
    public static boolean supersedes(int neighbor, int current) { return neighbor > 0 && neighbor > current + 1; }
    public static boolean ventConflict(int suction, int neighbor, Object type, Object neighborType, boolean neighborFilter) {
        return suction > 0 && (neighbor == suction || neighbor == suction - 1) && type != neighborType && !neighborFilter;
    }
    public static int bufferSuction(int bellows, int choke) { return choke == 2 ? 0 : bellows > 0 && choke != 1 ? bellows * 32 : 1; }
    public static int comparator(int amount) { return (int)Math.floor(amount / 10F * 14F) + (amount > 0 ? 1 : 0); }
    public static int nextChoke(int choke) { return choke >= 2 ? 0 : choke + 1; }
    /** EnumFacing.getDirectionFromEntityLiving uses position/eye height, not nearest view vector. */
    public static int placementFacing(double entityX, double eyeY, double entityZ, int blockX, int blockY, int blockZ, int horizontalOpposite) {
        if (Math.abs(entityX - blockX) < 2 && Math.abs(entityZ - blockZ) < 2) { if (eyeY - blockY > 2) return 1; if (blockY - eyeY > 0) return 0; }
        return horizontalOpposite;
    }
}
