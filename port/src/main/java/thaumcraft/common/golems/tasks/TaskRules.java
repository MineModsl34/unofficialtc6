package thaumcraft.common.golems.tasks;

/** Scalar BETA26 task rules, kept separate from entity/bootstrap state. */
public final class TaskRules {
    private TaskRules() {}
    public static double selectionScore(double distanceSquared, byte priority) {
        return distanceSquared - priority * 256;
    }
    public static boolean cleanupTick(long ticks) { return ticks % 20 == 0; }
    public static short completionLifespan(short lifespan) { return (short) (lifespan + 1); }
    public static short reservationLifespan(short lifespan) { return (short) (lifespan + 120); }
    public static int rankThreshold(int rank) { return (rank + 1) * (rank + 1) * 1000; }
}
