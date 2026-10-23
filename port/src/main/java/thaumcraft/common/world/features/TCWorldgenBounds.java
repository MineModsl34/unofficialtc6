package thaumcraft.common.world.features;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;

/** Quiet preflight: rejected probes must not produce WorldGenRegion's far-chunk error. */
public final class TCWorldgenBounds {
    private TCWorldgenBounds() {}

    public static boolean canAccess(WorldGenLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return false;
        }
        if (level instanceof WorldGenRegion region
            && !withinWriteRadius(pos.getX(), pos.getZ(), region.getCenter().x, region.getCenter().z,
                region.generatingStep.blockStateWriteRadius())) {
            return false;
        }
        // Retain the engine's upgrading-chunk and other write constraints.
        return level.ensureCanWrite(pos);
    }

    static boolean withinWriteRadius(int blockX, int blockZ, int centerX, int centerZ, int radius) {
        return Math.abs((long) (blockX >> 4) - centerX) <= radius
            && Math.abs((long) (blockZ >> 4) - centerZ) <= radius;
    }
}
