package thaumcraft.common.items.casters;

/** Pure source aura payment, including source drain order/portion reuse. No integer
 * crafting-cost rounding: caster vis is float. Stops a no-progress loop (old code hung). */
public final class CasterVisConsumption {
    // EnumFacing.HORIZONTALS indexed by horizontalIndex: SOUTH, WEST, NORTH, EAST.
    private static final int[][] CROSS = {{0, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 0}};
    private CasterVisConsumption() {}
    public interface Aura {
        float available(int x, int z);
        float drain(int x, int z, float amount);
    }
    public static float modifier(float discount) { return Math.max(1F - discount, .1F); }
    public static boolean consume(float amount, int area, boolean simulate, Aura aura) {
        if (!Float.isFinite(amount)) return false;
        float total = 0;
        if (area == 2) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) total += aura.available(x, z);
        } else if (area == 1) {
            for (int[] offset : CROSS) total += aura.available(offset[0], offset[1]);
        } else total = aura.available(0, 0);
        if (total < amount) return false;
        if (simulate) return true;
        if (area != 1 && area != 2) return amount - aura.drain(0, 0, amount) <= 0;
        float portion = amount / (area == 1 ? 5F : 9F);
        while (amount > 0) {
            float previous = amount;
            portion = Math.min(portion, amount);
            if (area == 1) {
                amount -= aura.drain(0, 0, portion);
                if (amount <= 0) return true;
                portion = Math.min(portion, amount);
                for (int index = 1; index < CROSS.length; index++) {
                    amount -= aura.drain(CROSS[index][0], CROSS[index][1], portion);
                    if (amount <= 0) return true;
                }
            } else {
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    amount -= aura.drain(x, z, portion);
                    if (amount <= 0) return true;
                }
            }
            if (!(amount < previous)) return false;
        }
        return amount <= 0;
    }
}
