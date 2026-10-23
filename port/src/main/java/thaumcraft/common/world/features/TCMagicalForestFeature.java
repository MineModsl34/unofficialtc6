package thaumcraft.common.world.features;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import thaumcraft.api.blocks.BlocksTC;

/** TC6 magical-forest-owned decoration. Global generateTrees/blacklists never gated this path. */
public final class TCMagicalForestFeature extends Feature<NoneFeatureConfiguration> {
    private final TCBigMagicTreeFeature.LegacyState bigTree = new TCBigMagicTreeFeature.LegacyState();

    public TCMagicalForestFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos chunk = new BlockPos(context.origin().getX() & ~15, 0, context.origin().getZ() & ~15);
        boolean placed = placeAmbientGrass(level, random, chunk);
        int blobs = random.nextInt(3);
        for (int attempt = 0; attempt < blobs; attempt++) {
            BlockPos pos = chunk.offset(random.nextInt(16) + 8, 0, random.nextInt(16) + 8);
            placed |= TCLegacyBiomeDecoration.mossyBlob(level, random, TCLegacyBiomeDecoration.surface(level, pos));
        }
        for (int cellX = 0; cellX < 4; cellX++) {
            for (int cellZ = 0; cellZ < 4; cellZ++) {
                if (random.nextInt(40) == 0) {
                    BlockPos pos = chunk.offset(cellX * 4 + 9 + random.nextInt(3), 0, cellZ * 4 + 9 + random.nextInt(3));
                    placed |= TCLegacyBiomeDecoration.giantMushroom(level, random, TCLegacyBiomeDecoration.surface(level, pos));
                }
            }
        }
        int trees = 2;
        if (random.nextFloat() < 0.1F) {
            trees++;
        }
        for (int attempt = 0; attempt < trees; attempt++) {
            BlockPos column = chunk.offset(random.nextInt(16) + 8, 0, random.nextInt(16) + 8);
            // The species is chosen before asking for the column's height in the old decorator.
            int species = random.nextInt(18) == 0 ? 0 : random.nextInt(12) == 0 ? 1 : 2;
            boolean spiders = species == 1 && random.nextInt(8) == 0;
            BlockPos pos = TCLegacyBiomeDecoration.surface(level, column);
            placed |= switch (species) {
                case 0 -> TCSilverwoodTreeFeature.placeTree(level, random, pos, 8, 5, true);
                case 1 -> TCGreatwoodTreeFeature.placeTree(level, random, pos, true, spiders);
                default -> bigTree.placeTree(level, random, pos);
            };
        }
        placed |= TCLegacyBiomeDecoration.decoratePlants(level, random, chunk, 10, 12, 6, 6, true);
        return placeVishrooms(level, random, chunk) || placed;
    }

    public static boolean placeAmbientGrass(WorldGenLevel level, RandomSource random, BlockPos chunk) {
        for (int attempt = 0; attempt < 3; attempt++) {
            BlockPos pos = chunk.offset(4 + random.nextInt(8), 0, 4 + random.nextInt(8));
            pos = TCLegacyBiomeDecoration.surface(level, pos);
            while (pos.getY() > 30 && TCLegacyBiomeDecoration.canAccess(level, pos)
                && !level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                pos = pos.below();
            }
            if (TCLegacyBiomeDecoration.canAccess(level, pos) && level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                // As in TC6, the first suitable grass cell ends the search even if a write is rejected.
                return level.setBlock(pos, BlocksTC.grassAmbient.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return false;
    }

    public static boolean placeVishrooms(WorldGenLevel level, RandomSource random, BlockPos chunk) {
        boolean placed = false;
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos pos = chunk.offset(random.nextInt(16), 0, random.nextInt(16));
            pos = TCLegacyBiomeDecoration.surface(level, pos);
            while (pos.getY() > 50 && TCLegacyBiomeDecoration.canAccess(level, pos)
                && !level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                pos = pos.below();
            }
            BlockPos above = pos.above();
            if (TCLegacyBiomeDecoration.canAccess(level, pos) && TCLegacyBiomeDecoration.canAccess(level, above)
                && level.getBlockState(pos).is(Blocks.GRASS_BLOCK)
                && level.getBlockState(above).canBeReplaced() && isAdjacentToWood(level, above)) {
                placed |= level.setBlock(above, BlocksTC.vishroom.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return placed;
    }

    private static boolean isAdjacentToWood(WorldGenLevel level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos neighbor = pos.offset(dx, dy, dz);
                    if (TCLegacyBiomeDecoration.canAccess(level, neighbor) && level.getBlockState(neighbor).is(BlockTags.LOGS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
