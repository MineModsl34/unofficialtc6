package thaumcraft.common.world.features;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.ore.BlockCrystal;
import thaumcraft.common.blocks.world.ore.ShardType;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.TCTags;
import thaumcraft.common.world.biomes.BiomeHandler;

/**
 * One pass of TC6 generateOres per chunk: cinnabar -> quartz -> amber -> crystals, with a shared RNG.
 * These are single blocks, not OreFeature veins. The PlacedFeature must not add count/in_square.
 */
public class TCUndergroundFeature extends Feature<NoneFeatureConfiguration> {
    public TCUndergroundFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (level.getLevel().dimension().equals(Level.NETHER)) {
            // In TC6 the whole ore and crystal branch was inside dimension != -1.
            return false;
        }
        RandomSource random = context.random();
        int centerX = (context.origin().getX() & ~15) + 8;
        int centerZ = (context.origin().getZ() & ~15) + 8;
        if (!ModConfig.CONFIG_WORLD.worldgenPolicy.allowsOres(level.getLevel().dimension().location(),
            BiomeHandler.biomeKey(level.getBiome(new BlockPos(centerX, 50, centerZ))))) {
            return false;
        }
        int density = ModConfig.CONFIG_WORLD.oreDensity;
        boolean cinnabar = ModConfig.CONFIG_WORLD.generateCinnabar;
        boolean quartz = ModConfig.CONFIG_WORLD.generateQuartz;
        boolean amber = ModConfig.CONFIG_WORLD.generateAmber;
        boolean crystals = ModConfig.CONFIG_WORLD.generateCrystals;
        int placed = 0;
        if (cinnabar) {
            placed += placeLowOre(level, random, centerX, centerZ, density, WorldgenRules.CINNABAR_HEIGHT_BOUND,
                BlocksTC.oreCinnabar.defaultBlockState());
        }
        if (quartz) {
            placed += placeLowOre(level, random, centerX, centerZ, density, WorldgenRules.QUARTZ_HEIGHT_BOUND,
                BlocksTC.oreQuartz.defaultBlockState());
        }
        if (amber) {
            for (int i = 0; i < WorldgenRules.scaledAttempts(20, density); i++) {
                int x = centerX + random.nextInt(13) - 6;
                int z = centerZ + random.nextInt(13) - 6;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - random.nextInt(25);
                if (tryPlaceOre(level, new BlockPos(x, y, z), BlocksTC.oreAmber.defaultBlockState())) {
                    placed++;
                }
            }
        }
        if (crystals) {
            int totalSize = 0;
            int budget = WorldgenRules.scaledAttempts(64, density);
            for (int i = 0; i < WorldgenRules.scaledAttempts(8, density); i++) {
                int x = centerX + random.nextInt(13) - 6;
                int z = centerZ + random.nextInt(13) - 6;
                // TC6: nextInt(surface - 5) from Y 0. In 1.21 the range starts at the world bottom (deepslate), the top is unchanged.
                int minY = level.getMinBuildHeight();
                int y = minY + random.nextInt(Math.max(5, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 5 - minY));
                BlockPos seed = new BlockPos(x, y, z);
                int meta = random.nextInt(6);
                if (random.nextInt(3) == 0) {
                    Aspect aspect = BiomeHandler.getRandomBiomeTag(level.getBiome(seed), random);
                    meta = aspect == null ? random.nextInt(6) : ShardType.getMetaByAspect(aspect);
                }
                Block crystal = ShardType.byMetadata(meta).getOre();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (random.nextInt(3) != 0) {
                                BlockPos pos = seed.offset(dx, dy, dz);
                                if (canPlaceCrystal(level, pos)) {
                                    int size = 1 + random.nextInt(3);
                                    if (tryPlaceCrystal(level, pos, crystal.defaultBlockState().setValue(BlockCrystal.SIZE, size))) {
                                        totalSize += size;
                                        placed++;
                                    }
                                }
                            }
                        }
                    }
                }
                // Exactly the SIZE sum, strictly > and only after the full cube, as in TC6.
                if (totalSize > budget) {
                    break;
                }
            }
        }
        return placed > 0;
    }

    private static int placeLowOre(WorldGenLevel level, RandomSource random, int centerX, int centerZ,
        int density, int heightBound, BlockState ore) {
        int placed = 0;
        for (int i = 0; i < WorldgenRules.scaledAttempts(18, density); i++) {
            int x = centerX + random.nextInt(13) - 6;
            // Like crystals: from the world bottom to TC6's old upper bound.
            int minY = level.getMinBuildHeight();
            int y = minY + random.nextInt(heightBound - minY);
            int z = centerZ + random.nextInt(13) - 6;
            if (tryPlaceOre(level, new BlockPos(x, y, z), ore)) {
                placed++;
            }
        }
        return placed;
    }

    /** The old BlockMatcher(Blocks.STONE): stone and its three meta variants; the tag allows an explicit datapack override. */
    public static boolean tryPlaceOre(WorldGenLevel level, BlockPos pos, BlockState ore) {
        return TCWorldgenBounds.canAccess(level, pos)
            && level.getBlockState(pos).is(TCTags.Blocks.TC_ORE_REPLACEABLES)
            && level.setBlock(pos, ore, Block.UPDATE_CLIENTS);
    }

    private static boolean canPlaceCrystal(WorldGenLevel level, BlockPos pos) {
        if (!TCWorldgenBounds.canAccess(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && (state.isAir() || state.canBeReplaced())
            && BlockCrystal.isTouchingSupport(level, pos);
    }

    /** Material ROCK is replaced by the existing support tag; the faces of the new multipart model are set right away. */
    public static boolean tryPlaceCrystal(WorldGenLevel level, BlockPos pos, BlockState crystal) {
        return canPlaceCrystal(level, pos)
            && level.setBlock(pos, BlockCrystal.withConnections(crystal, level, pos), Block.UPDATE_CLIENTS);
    }
}
