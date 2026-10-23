package thaumcraft.common.world.features;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

/** TC6 WorldGenCustomFlowers: eighteen attempts on grass or either old BlockSand variant. */
public class TCFlowersFeature extends Feature<BlockStateConfiguration> {
    public TCFlowersFeature() {
        super(BlockStateConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockStateConfiguration> context) {
        return placePatch(context.level(), context.random(), context.origin(), context.config().state) > 0;
    }

    /** Shared by cinderpearl decoration and the silverwood flower patch. */
    public static int placePatch(WorldGenLevel level, RandomSource random, BlockPos origin, BlockState state) {
        int placed = 0;
        for (int attempt = 0; attempt < 18; attempt++) {
            BlockPos pos = origin.offset(
                random.nextInt(8) - random.nextInt(8),
                random.nextInt(4) - random.nextInt(4),
                random.nextInt(8) - random.nextInt(8));
            if (!TCWorldgenBounds.canAccess(level, pos) || !TCWorldgenBounds.canAccess(level, pos.below())) {
                continue;
            }
            BlockState ground = level.getBlockState(pos.below());
            if (level.isEmptyBlock(pos) && (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.SAND) || ground.is(Blocks.RED_SAND))
                && level.setBlock(pos, state, Block.UPDATE_ALL)) {
                placed++;
            }
        }
        return placed;
    }
}
