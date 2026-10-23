package thaumcraft.common.golems;

/** ByteBuffer BIG_ENDIAN layout of source ThaumcraftApiHelper, NOT little-endian part IDs. */
public final class GolemPropertyRules {
    private GolemPropertyRules() {}
    public static int byteAt(long packed,int index) {
        if (index < 0 || index > 7) throw new IllegalArgumentException("Byte index");
        return (int)((packed >>> ((7 - index) * 8)) & 255L);
    }
    public static long putByte(long packed,int index,int value) {
        if (index < 0 || index > 7 || value < 0 || value > 127) throw new IllegalArgumentException("Signed catalog byte");
        int shift = (7 - index) * 8; return (packed & ~(255L << shift)) | ((long)value << shift);
    }
    public static boolean valid(long packed,int materials,int heads,int arms,int legs,int addons) {
        int[] limits = {materials,heads,arms,legs,addons};
        for (int i = 0; i < limits.length; i++) if (byteAt(packed,i) >= limits[i]) return false;
        return byteAt(packed,5) <= 10;
    }
    /** Source builder only generates newborn rank-zero catalog parts; reserved low16 not payment input. */
    public static boolean newborn(long packed) { return byteAt(packed,5) == 0 && (packed & 65535L) == 0; }
    /** Opposites annihilate the previous trait; the new opposite is NOT added in that branch. */
    public static <T> void addTrait(java.util.Set<T> traits,T trait,java.util.function.Function<T,T> opposite) {
        T other = opposite.apply(trait); if (other != null && traits.contains(other)) traits.remove(other); else traits.add(trait);
    }
    public static int workCost(int traitCount,int[] componentCounts) {
        int cost = Math.multiplyExact(traitCount,2);
        for (int count : componentCounts) { if (count < 1 || count > 4096) throw new IllegalArgumentException("Component count"); cost = Math.addExact(cost,count); }
        if (cost < 1 || cost > 65535) throw new IllegalArgumentException("Builder cost"); return cost;
    }
}
