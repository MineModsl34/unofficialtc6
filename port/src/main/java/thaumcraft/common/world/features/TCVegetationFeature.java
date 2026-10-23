package thaumcraft.common.world.features;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.common.Tags;
import thaumcraft.Thaumcraft;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.world.biomes.BiomeHandler;

/** One vegetation pass preserves TC6's silverwood -> greatwood -> cinderpearl RNG order. */
public class TCVegetationFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceLocation MAGICAL_FOREST = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "magical_forest");

    public TCVegetationFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!ModConfig.CONFIG_WORLD.generateTrees || context.chunkGenerator() instanceof FlatLevelSource) {
            return false;
        }
        // Legacy forest/birch hills IDs 18/28 no longer exist in 1.21's biome registry.
        var level = context.level();
        var random = context.random();
        int x = (context.origin().getX() & ~15) + 8;
        int z = (context.origin().getZ() & ~15) + 8;
        if (!ModConfig.CONFIG_WORLD.worldgenPolicy.allowsVegetation(level.getLevel().dimension().location(),
            BiomeHandler.biomeKey(level.getBiome(new BlockPos(x, 50, z))))) {
            return false;
        }
        boolean placed = false;
        if (random.nextInt(80) == 3) {
            int treeX = x + random.nextInt(9) - 4;
            int treeZ = z + random.nextInt(9) - 4;
            BlockPos pos = new BlockPos(treeX, level.getHeight(Heightmap.Types.MOTION_BLOCKING, treeX, treeZ), treeZ);
            Holder<Biome> biome = level.getBiome(pos);
            float roll = random.nextFloat();
            // Original is an OR, not a ban on magical biomes. Own magical forest keeps the ordinary probability.
            if (BiomeHandler.getBiomeSupportsGreatwood(biome) / 2.0F > roll
                || (biome.is(Tags.Biomes.IS_MAGICAL) && !biome.is(MAGICAL_FOREST))) {
                placed |= level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                    .getHolderOrThrow(TCWorldgen.SILVERWOOD_NATURAL).value()
                    .place(level, context.chunkGenerator(), random, pos);
            }
        }
        if (random.nextInt(25) == 7) {
            int treeX = x + random.nextInt(9) - 4;
            int treeZ = z + random.nextInt(9) - 4;
            BlockPos pos = new BlockPos(treeX, level.getHeight(Heightmap.Types.MOTION_BLOCKING, treeX, treeZ), treeZ);
            if (BiomeHandler.getBiomeSupportsGreatwood(level.getBiome(pos)) > random.nextFloat()) {
                var key = random.nextInt(8) == 0 ? TCWorldgen.GREATWOOD_SPIDER : TCWorldgen.GREATWOOD_NATURAL;
                placed |= level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                    .getHolderOrThrow(key).value().place(level, context.chunkGenerator(), random, pos);
            }
        }
        return TCCinderpearlFeature.placeAtChunkCentre(context) || placed;
    }
}
