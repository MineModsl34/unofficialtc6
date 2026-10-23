package thaumcraft.common.world.structures;

import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

/** Pure TC6 generateSurface selection. Both native structures use this same, unsalted stream. */
public final class TCSurfaceAttemptRules {
    public enum Kind { NONE, MOUND, PORTAL }

    public record Attempt(Kind kind, int x, int originY, int z) {
        public int supportY() { return originY + 8; }
        public int entityY() { return originY + 9; }
    }

    /** Saved terminal outcomes also reject repeated FEATURES calls after the entity has died. */
    public enum Completion {
        PENDING, REJECTED, SPAWNED;
        public boolean processed() { return this != PENDING; }
        public Completion resolve(boolean spawned) { return processed() ? this : spawned ? SPAWNED : REJECTED; }
        public static Completion fromSaved(int value) {
            if (value < 0 || value >= values().length) throw new IllegalArgumentException("Invalid saved surface attempt outcome: " + value);
            return values()[value];
        }
    }

    private TCSurfaceAttemptRules() {}

    /** Two offsets, height/max guard, mound roll, and ONLY on its failure the portal roll. */
    public static Attempt roll(IntUnaryOperator nextInt, int chunkX, int chunkZ,
                               IntBinaryOperator precipitationHeight, int maxHeight) {
        Objects.requireNonNull(nextInt);
        Objects.requireNonNull(precipitationHeight);
        int x = chunkX * 16 + 8 + nextInt.applyAsInt(9) - 4;
        int z = chunkZ * 16 + 8 + nextInt.applyAsInt(9) - 4;
        int originY = precipitationHeight.applyAsInt(x, z) - 9;
        if (originY >= maxHeight) return new Attempt(Kind.NONE, x, originY, z);
        if (nextInt.applyAsInt(100) == 0) return new Attempt(Kind.MOUND, x, originY, z);
        return new Attempt(nextInt.applyAsInt(500) == 0 ? Kind.PORTAL : Kind.NONE, x, originY, z);
    }

    /** Source checks the dimension blacklist alone; no biome blacklist, mob cap or difficulty gate. */
    public static boolean allowsGeneration(boolean enabled, int dimensionBlacklist, boolean overworld,
                                           boolean flat, boolean newGeneration, boolean regenerateStructures) {
        return enabled && dimensionBlacklist == -1 && overworld && !flat && (newGeneration || regenerateStructures);
    }

    /** A native spawn listener must not move this one-shot entity into another chunk's save lifecycle. */
    public static boolean allowsPortalInsertion(boolean spawnCancelled, boolean removed, int entityChunkX, int entityChunkZ,
                                                int ownerX, int ownerZ, int entityY, int minHeight, int maxHeight) {
        return !spawnCancelled && !removed && entityChunkX == ownerX && entityChunkZ == ownerZ
            && entityY > minHeight && entityY < maxHeight;
    }
}
