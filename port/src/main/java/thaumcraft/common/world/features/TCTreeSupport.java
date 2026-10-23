package thaumcraft.common.world.features;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.TriState;

/** Legacy tree predicates plus initialization required by modern LeavesBlock. */
public final class TCTreeSupport {
    private TCTreeSupport() {
    }

    public static boolean canSustainTree(WorldGenLevel level, BlockPos soilPos) {
        if (!TCWorldgenBounds.canAccess(level, soilPos)) {
            return false;
        }
        BlockState soil = level.getBlockState(soilPos);
        TriState override = soil.canSustainPlant(level, soilPos, Direction.UP, Blocks.OAK_SAPLING.defaultBlockState());
        if (!override.isDefault()) {
            return override.isTrue();
        }
        return isLegacySoil(soil);
    }

    public static boolean isLegacySoil(BlockState soil) {
        // BlockDirt's three variants in 1.12, ordinary grass and farmland; no new moss/rooted dirt.
        return soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.COARSE_DIRT)
            || soil.is(Blocks.PODZOL) || soil.is(Blocks.FARMLAND);
    }

    public static void onPlantGrow(WorldGenLevel level, BlockPos soilPos) {
        onPlantGrow(level, soilPos, level.getRandom(), 7);
    }

    public static void onPlantGrow(WorldGenLevel level, BlockPos soilPos, net.minecraft.util.RandomSource random, int height) {
        BlockState soil = level.getBlockState(soilPos);
        var configuration = new net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration.TreeConfigurationBuilder(
            net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(thaumcraft.api.blocks.BlocksTC.logSilverwood),
            new net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer(height, 0, 0),
            net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(thaumcraft.api.blocks.BlocksTC.leafSilverwood),
            new net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer(net.minecraft.util.valueproviders.ConstantInt.of(3), net.minecraft.util.valueproviders.ConstantInt.of(0), 3),
            new net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize(1, 0, 1)).build();
        if (soil.onTreeGrow(level, (pos, state) -> {
                if (TCWorldgenBounds.canAccess(level, pos)) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }, random, soilPos, configuration)) return;
        // Exact vanilla 1.12 onPlantGrow behavior, with no extra RNG draws.
        if (soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.FARMLAND)) {
            level.setBlock(soilPos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static boolean isTreeReplaceable(BlockState state) {
        return state.isAir() || state.is(BlockTags.LEAVES) || state.canBeReplaced();
    }

    public static boolean canBeReplacedByLeaves(BlockState state) {
        // Forge 1.12's default leaf replacement did not include arbitrary replaceable plants.
        return state.isAir() || state.is(BlockTags.LEAVES);
    }

    public static void updateLeafDistances(WorldGenLevel level, Set<BlockPos> logs, Set<BlockPos> leaves, int flags) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, Integer> distances = new HashMap<>();
        for (BlockPos log : logs) {
            distances.put(log, 0);
            queue.add(log);
        }
        // A new crown can be supported through an older crown/log at its boundary.
        for (BlockPos leaf : leaves) {
            int best = 7;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = leaf.relative(direction);
                if (!leaves.contains(neighbor) && TCWorldgenBounds.canAccess(level, neighbor)) {
                    best = Math.min(best, LeavesBlock.getOptionalDistanceAt(level.getBlockState(neighbor)).orElse(7) + 1);
                }
            }
            if (best < distances.getOrDefault(leaf, 7)) {
                distances.put(leaf, best);
                queue.add(leaf);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            int nextDistance = distances.get(pos) + 1;
            if (nextDistance >= 7) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (leaves.contains(neighbor) && nextDistance < distances.getOrDefault(neighbor, 7)) {
                    distances.put(neighbor, nextDistance);
                    queue.add(neighbor);
                }
            }
        }
        for (BlockPos pos : leaves) {
            if (!TCWorldgenBounds.canAccess(level, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(LeavesBlock.DISTANCE)) {
                int distance = distances.getOrDefault(pos, 7);
                if (state.getValue(LeavesBlock.DISTANCE) != distance) {
                    level.setBlock(pos, state.setValue(LeavesBlock.DISTANCE, distance), flags);
                }
            }
        }
    }
}
