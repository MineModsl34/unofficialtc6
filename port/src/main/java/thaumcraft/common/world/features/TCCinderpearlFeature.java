package thaumcraft.common.world.features;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.common.Tags;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.world.biomes.BiomeHandler;

/** Cinderpearl from generateVegetation: one 1/30 chance per hot sandy chunk, then 18 flower attempts. */
public class TCCinderpearlFeature extends Feature<NoneFeatureConfiguration> {
    public TCCinderpearlFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!ModConfig.CONFIG_WORLD.generateTrees || context.chunkGenerator() instanceof FlatLevelSource) {
            return false;
        }
        // Standalone configured feature retained for tests/datapacks; natural decoration uses TCVegetationFeature.
        return placeAtChunkCentre(context);
    }

    public static boolean placeAtChunkCentre(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        int x = (context.origin().getX() & ~15) + 8;
        int z = (context.origin().getZ() & ~15) + 8;
        if (!ModConfig.CONFIG_WORLD.worldgenPolicy.allowsVegetation(level.getLevel().dimension().location(),
            BiomeHandler.biomeKey(level.getBiome(new BlockPos(x, 50, z))))) {
            return false;
        }
        // Live heightmap includes earlier trees in the shared vegetation pass, as TC6 world.getHeight did.
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
        var biome = level.getBiome(pos);
        // The biome's topBlock was removed in 1.21: the sandy biome type plus real sand under the chunk centre.
        var soil = level.getBlockState(pos.below());
        if (!(biome.is(Tags.Biomes.IS_SANDY) || biome.is(BiomeTags.IS_BADLANDS))
            || biome.value().getBaseTemperature() <= 1.0F || !(soil.is(Blocks.SAND) || soil.is(Blocks.RED_SAND))
            || context.random().nextInt(30) != 0) {
            return false;
        }
        return TCFlowersFeature.placePatch(level, context.random(), pos, BlocksTC.cinderpearl.defaultBlockState()) > 0;
    }
}
