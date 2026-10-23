package thaumcraft.common.entities.monster.boss;

/** Scalar BETA26 rules, isolated from world state for bounded pure tests. */
public final class Boss8Rules {
    private Boss8Rules() {}
    public static float cappedHit(float damage) { return Math.min(35F, damage); }
    public static int regenerationAmplifier(float damage) { return (int)(damage / 15F); }
    public static int strengthAmplifier(float damage) { return (int)(damage / 10F); }
    public static int hasteAmplifier(float damage) { return (int)(damage / 40F); }
    public static int shield(float baseHealth) { return (int)(baseHealth * .66D); }
    public static int portalDelay(int stage, int nearbyCultists, int randomValue) {
        if (stage < 5) return 15 + randomValue - stage;
        if (stage == 12) return 50 + nearbyCultists * 40 + randomValue;
        return nearbyCultists * 20 + randomValue;
    }
    public static double frenzyRadius(int counter) { return (150 - counter) / 8D; }
    public static boolean frenzyPulse(int counter) { return counter < 121 && counter % 10 == 0; }
    public static boolean sourceAggroCandidate(int candidateDamage, int currentDamage, int last) {
        return candidateDamage > currentDamage + 25 && candidateDamage > currentDamage * 1.1 && candidateDamage > last;
    }
}
