package thaumcraft.common.world.features;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import thaumcraft.api.blocks.BlocksTC;

/** TC6 silverwood geometry, including its guarded roots and natural-only shimmerleaf patch. */
public class TCSilverwoodTreeFeature extends Feature<TCSilverwoodTreeFeature.Configuration> {
    public TCSilverwoodTreeFeature() {
        super(Configuration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Configuration> context) {
        Configuration config = context.config();
        return placeTree(context.level(), context.random(), context.origin(),
            config.minHeight(), config.randomHeight(), config.natural());
    }

    /** Ordinary natural trees and saplings use (7, 4); magical forest trees use (8, 5). */
    public static boolean placeTree(WorldGenLevel level, RandomSource random, BlockPos origin,
                                    int minHeight, int randomHeight, boolean natural) {
        int height = random.nextInt(randomHeight) + minHeight;
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        int minimum = level.getMinBuildHeight();
        int maximum = level.getMaxBuildHeight();
        if (y < minimum + 1 || y + height + 1 > maximum) {
            return false;
        }

        // TC6 checks only above the origin: an occupied origin does not itself fail clearance.
        for (int yy = y; yy <= y + height + 1; yy++) {
            int spread = yy == y ? 0 : 1;
            if (yy >= y + height - 1) {
                spread = 3;
            }
            for (int xx = x - spread; xx <= x + spread; xx++) {
                for (int zz = z - spread; zz <= z + spread; zz++) {
                    BlockPos pos = new BlockPos(xx, yy, zz);
                    if (!TCWorldgenBounds.canAccess(level, pos)
                        || (yy > y && !TCTreeSupport.isTreeReplaceable(level.getBlockState(pos)))) {
                        return false;
                    }
                }
            }
        }

        BlockPos soil = origin.below();
        // The second upper-height check in TC6 is strictly '<', unlike the initial '<='.
        if (!TCTreeSupport.canSustainTree(level, soil) || y >= maximum - height - 1) {
            return false;
        }
        TCTreeSupport.onPlantGrow(level, soil, random, height);

        int flags = natural ? Block.UPDATE_CLIENTS : Block.UPDATE_ALL;
        Set<BlockPos> logs = new HashSet<>();
        Set<BlockPos> leaves = new HashSet<>();
        BlockState foliage = BlocksTC.leafSilverwood.defaultBlockState()
            .setValue(LeavesBlock.PERSISTENT, false);
        BlockState vertical = BlocksTC.logSilverwood.defaultBlockState()
            .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState horizontalX = vertical.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState horizontalZ = vertical.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);

        int crownStart = y + height - 5;
        int crownEnd = y + height + 3 + random.nextInt(3);
        for (int yy = crownStart; yy <= crownEnd; yy++) {
            int centerY = Mth.clamp(yy, y + height - 3, y + height);
            for (int xx = x - 5; xx <= x + 5; xx++) {
                for (int zz = z - 5; zz <= z + 5; zz++) {
                    double dx = xx - x;
                    double dy = yy - centerY;
                    double dz = zz - z;
                    double distanceSquared = dx * dx + dy * dy + dz * dz;
                    BlockPos pos = new BlockPos(xx, yy, zz);
                    // Every crown cell consumes a draw, even cells outside the spherical crown.
                    int threshold = 10 + random.nextInt(8);
                    if (distanceSquared < threshold && TCWorldgenBounds.canAccess(level, pos)
                        && TCTreeSupport.canBeReplacedByLeaves(level.getBlockState(pos))) {
                        put(level, pos, foliage, flags, logs, leaves);
                    }
                }
            }
        }

        for (int trunkY = 0; trunkY < height; trunkY++) {
            BlockPos center = origin.above(trunkY);
            if (TCTreeSupport.isTreeReplaceable(level.getBlockState(center))) {
                put(level, center, vertical, flags, logs, leaves);
                put(level, center.west(), vertical, flags, logs, leaves);
                put(level, center.east(), vertical, flags, logs, leaves);
                put(level, center.north(), vertical, flags, logs, leaves);
                put(level, center.south(), vertical, flags, logs, leaves);
            }
        }

        put(level, origin.above(height), vertical, flags, logs, leaves);
        putCorners(level, origin, vertical, flags, logs, leaves);
        putRandomCorners(level, random, origin.above(), vertical, false, flags, logs, leaves);

        put(level, origin.offset(-2, 0, 0), horizontalX, flags, logs, leaves);
        put(level, origin.offset(2, 0, 0), horizontalX, flags, logs, leaves);
        put(level, origin.offset(0, 0, -2), horizontalZ, flags, logs, leaves);
        put(level, origin.offset(0, 0, 2), horizontalZ, flags, logs, leaves);
        // TC6's write guard refuses solid soil here; it does not unconditionally carve roots.
        put(level, origin.offset(-2, -1, 0), vertical, flags, logs, leaves);
        put(level, origin.offset(2, -1, 0), vertical, flags, logs, leaves);
        put(level, origin.offset(0, -1, -2), vertical, flags, logs, leaves);
        put(level, origin.offset(0, -1, 2), vertical, flags, logs, leaves);

        BlockPos upperBranch = origin.above(height - 4);
        putCorners(level, upperBranch, vertical, flags, logs, leaves);
        putRandomCorners(level, random, origin.above(height - 5), vertical, true, flags, logs, leaves);
        put(level, upperBranch.offset(-2, 0, 0), horizontalX, flags, logs, leaves);
        put(level, upperBranch.offset(2, 0, 0), horizontalX, flags, logs, leaves);
        put(level, upperBranch.offset(0, 0, -2), horizontalZ, flags, logs, leaves);
        put(level, upperBranch.offset(0, 0, 2), horizontalZ, flags, logs, leaves);

        // Modern leaves need their distance initialized; no extra random draws are introduced.
        TCTreeSupport.updateLeafDistances(level, logs, leaves, flags);
        if (natural) {
            TCFlowersFeature.placePatch(level, random, origin, BlocksTC.shimmerleaf.defaultBlockState());
        }
        return true;
    }

    private static void putCorners(WorldGenLevel level, BlockPos center, BlockState state, int flags,
                                   Set<BlockPos> logs, Set<BlockPos> leaves) {
        put(level, center.offset(-1, 0, -1), state, flags, logs, leaves);
        put(level, center.offset(1, 0, 1), state, flags, logs, leaves);
        put(level, center.offset(-1, 0, 1), state, flags, logs, leaves);
        put(level, center.offset(1, 0, -1), state, flags, logs, leaves);
    }

    private static void putRandomCorners(WorldGenLevel level, RandomSource random, BlockPos center,
                                         BlockState state, boolean zeroPlaces, int flags,
                                         Set<BlockPos> logs, Set<BlockPos> leaves) {
        // Preserve TC6's diagonal order and opposite probabilities at the base and upper branch.
        if ((random.nextInt(3) == 0) == zeroPlaces) {
            put(level, center.offset(-1, 0, -1), state, flags, logs, leaves);
        }
        if ((random.nextInt(3) == 0) == zeroPlaces) {
            put(level, center.offset(1, 0, 1), state, flags, logs, leaves);
        }
        if ((random.nextInt(3) == 0) == zeroPlaces) {
            put(level, center.offset(-1, 0, 1), state, flags, logs, leaves);
        }
        if ((random.nextInt(3) == 0) == zeroPlaces) {
            put(level, center.offset(1, 0, -1), state, flags, logs, leaves);
        }
    }

    private static void put(WorldGenLevel level, BlockPos pos, BlockState state, int flags,
                            Set<BlockPos> logs, Set<BlockPos> leaves) {
        if (!TCWorldgenBounds.canAccess(level, pos)
            || !TCTreeSupport.isTreeReplaceable(level.getBlockState(pos))
            || !level.setBlock(pos, state, flags)) {
            return;
        }
        BlockPos immutable = pos.immutable();
        if (state.is(BlocksTC.logSilverwood)) {
            logs.add(immutable);
            leaves.remove(immutable);
        } else {
            leaves.add(immutable);
        }
    }

    public record Configuration(int minHeight, int randomHeight, boolean natural) implements FeatureConfiguration {
        public static final Codec<Configuration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 64).fieldOf("min_height").forGetter(Configuration::minHeight),
            Codec.intRange(1, 64).fieldOf("random_height").forGetter(Configuration::randomHeight),
            Codec.BOOL.fieldOf("natural").forGetter(Configuration::natural)
        ).apply(instance, Configuration::new));
    }
}
