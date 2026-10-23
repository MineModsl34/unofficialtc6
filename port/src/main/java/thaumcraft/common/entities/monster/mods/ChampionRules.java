package thaumcraft.common.entities.monster.mods;

/** JVM-light arithmetic from EntityEvents/EntityUtils; source oddities are intentional. */
public final class ChampionRules {
    private ChampionRules() {}
    public static final int[] TYPES = {-1,2,2,-1,1,0,1,0,1,1,1,1,2,0};
    public static int score(int roll, boolean easy, boolean hard, boolean enabled,
                            boolean outer, boolean spookyNetherEnd, int whitelistValue, boolean boss) {
        int c = roll;
        if (easy || !enabled) c += 2;
        if (hard) c -= enabled ? 2 : 0;
        if (outer) c -= 3;
        if (spookyNetherEnd) c -= enabled ? 2 : 1;
        if (enabled || boss) c -= Math.max(0, whitelistValue - 1);
        return c;
    }
    public static float armor(float amount) { return amount * 19.0F / 25.0F; }
    public static float vampire(float amount) { return Math.max(2.0F, amount / 2.0F); }
    public static int shield(double baseHealth) { return (int)baseHealth / 2; }
    public static int lootTier(int roll, int looting) { return Math.min(2, (int)Math.floor((roll + looting) / 5.0F)); }
    public static double critterDamage(float height, float width) { return Math.max(2.0F, (height + width) * 2.0F); }
}
