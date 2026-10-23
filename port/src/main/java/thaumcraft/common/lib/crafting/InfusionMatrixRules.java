package thaumcraft.common.lib.crafting;

/** Pure TC6 TileInfusionMatrix arithmetic, shared by the live server state machine. */
public final class InfusionMatrixRules {
    private InfusionMatrixRules() {}
    public enum Stability { VERY_STABLE, STABLE, UNSTABLE, VERY_UNSTABLE }
    public enum Foundation { ARCANE_OR_MIXED, ANCIENT, ELDRITCH }
    public enum Upgrade { NONE, SPEED, COST }
    public enum Pedestal { ARCANE, ANCIENT, ELDRITCH }
    public enum Mishap { EJECT, WARP, ZAP_ONE, ZAP_ALL, EJECT_GOO, EJECT_FLUX, EJECT_GOO_3, DESTROY_FLUX,
        HARM_ONE, EJECT_EXPLOSION, HARM_ALL, EXPLOSION }
    public record Modifiers(int cycleTime, int countDelay, float cost, float replenish) {}
    public record ComponentStep(int remaining, boolean begin, boolean consume) {}

    /** Source sets5 on the first successful component cycle, then post-decrements on each
     * subsequent cycle. The sixth matching cycle consumes; failed lookups never decrement.
     */
    public static ComponentStep component(int remaining) {
        return remaining == 0 ? new ComponentStep(5, true, false) : new ComponentStep(remaining - 1, false, remaining <= 1);
    }

    public static Stability stability(float current, int cap) {
        // The source cap/2 is INTEGER division: at the default25, VERY_STABLE starts above12.
        return current > cap / 2 ? Stability.VERY_STABLE : current >= 0 ? Stability.STABLE
            : current > -25 ? Stability.UNSTABLE : Stability.VERY_UNSTABLE;
    }
    public static float lossPerCycle(int instability, float current, int cap) {
        return instability / switch (stability(current, cap)) {
            case VERY_STABLE -> 5F; case STABLE -> 6F; case UNSTABLE -> 7F; case VERY_UNSTABLE -> 8F;
        };
    }
    public static float craftStability(float current, int cap, int instability, float replenish, float roll) {
        current -= roll * lossPerCycle(instability, current, cap);
        current += replenish;
        if (current < -100) current = -100;
        if (current > cap) current = cap;
        return current;
    }
    public static float idleStability(float current, int cap, float replenish) {
        return Math.min(cap, current + Math.max(.1F, replenish));
    }
    public static boolean mishap(boolean inputValid, float stability, int roll1500) {
        return !inputValid || stability < 0 && roll1500 <= Math.abs(stability);
    }
    public static Mishap mishap(int roll24) {
        if (roll24 < 0 || roll24 >= 24) throw new IllegalArgumentException("Infusion mishap roll outside source24");
        return switch (roll24) {
            case 0, 1, 2, 3 -> Mishap.EJECT;
            case 4, 5, 6 -> Mishap.WARP;
            case 7, 8, 9 -> Mishap.ZAP_ONE;
            case 10, 11 -> Mishap.ZAP_ALL;
            case 12, 13 -> Mishap.EJECT_GOO;
            case 14, 15 -> Mishap.EJECT_FLUX;
            case 16 -> Mishap.EJECT_GOO_3;
            case 17 -> Mishap.DESTROY_FLUX;
            case 18, 19 -> Mishap.HARM_ONE;
            case 20, 21 -> Mishap.EJECT_EXPLOSION;
            case 22 -> Mishap.HARM_ALL;
            default -> Mishap.EXPLOSION;
        };
    }
    public static float diminishing(float base, int previouslyMatchedPairs) {
        return previouslyMatchedPairs > 0 ? (float) (base * Math.pow(.75, previouslyMatchedPairs)) : base;
    }
    public static int cost(int original, float multiplier) { return (int) (original * Math.max(.5F, multiplier)); }
    public static int damage(int inputDamage, int inputMax, int outputMax) {
        return (int) (outputMax * ((float) inputDamage / inputMax));
    }
    // BlockPos changed its packing from X/Y/Z to X/Z/Y after 1.12. Stabilizer HashSet
    // iteration influences which unequal addon amounts receive diminishing returns first.
    // Use the source keys so a port does not silently change this arithmetic ordering.
    public static long sourcePosition(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) y & 0xfffL) << 26 | (long) z & 0x3ffffffL;
    }
    public static int sourceX(long key) { return (int) (key >> 38); }
    public static int sourceY(long key) { return (int) (key << 26 >> 52); }
    public static int sourceZ(long key) { return (int) (key << 38 >> 38); }
    public static Modifiers modifiers(Foundation foundation, Upgrade[] corners, Pedestal[] pedestals, float symmetry) {
        int cycle = 10; float cost = 1F, replenish = symmetry;
        if (foundation == Foundation.ANCIENT) { cycle--; cost -= .1F; replenish -= .1F; }
        if (foundation == Foundation.ELDRITCH) { cycle -= 3; cost += .05F; replenish += .2F; }
        for (Upgrade upgrade : corners) {
            if (upgrade == Upgrade.SPEED) { cycle--; cost += .01F; }
            if (upgrade == Upgrade.COST) { cycle++; cost -= .02F; }
        }
        for (Pedestal pedestal : pedestals) {
            if (pedestal == Pedestal.ELDRITCH) cost += .0025F;
            if (pedestal == Pedestal.ANCIENT) cost -= .01F;
        }
        return new Modifiers(cycle, Math.max(1, cycle / 2), cost, replenish);
    }
}
