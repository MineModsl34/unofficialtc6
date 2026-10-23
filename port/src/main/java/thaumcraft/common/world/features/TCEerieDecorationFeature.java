package thaumcraft.common.world.features;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Eerie inherited vanilla tree selection and decorator counts: two trees, one flower, two grass. */
public final class TCEerieDecorationFeature extends Feature<NoneFeatureConfiguration> {
    public TCEerieDecorationFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        var random = context.random();
        BlockPos chunk = new BlockPos(context.origin().getX() & ~15, 0, context.origin().getZ() & ~15);
        int trees = 2;
        if (random.nextFloat() < 0.1F) {
            trees++;
        }
        boolean placed = false;
        for (int attempt = 0; attempt < trees; attempt++) {
            BlockPos pos = chunk.offset(random.nextInt(16) + 8, 0, random.nextInt(16) + 8);
            var key = random.nextInt(10) == 0 ? TreeFeatures.FANCY_OAK : TreeFeatures.OAK;
            // Ordinary Eerie trees use their supported 1.21 vanilla equivalents. Their inner RNG/geometry
            // differs from 1.12, unlike the separately ported magical forest's TC BigMagicTree.
            placed |= level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(key).value()
                .place(level, context.chunkGenerator(), random, TCLegacyBiomeDecoration.surface(level, pos));
        }
        return TCLegacyBiomeDecoration.decoratePlants(level, random, chunk, 1, 2, 0, 0, false) || placed;
    }
}
