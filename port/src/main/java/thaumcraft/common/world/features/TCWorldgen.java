package thaumcraft.common.world.features;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.holdersets.AnyHolderSet;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.world.biomes.TCBiomes;
import thaumcraft.common.world.structures.TCStructures;

/** Feature types are a normal registry; configured/placed features and biome modifiers are JSON from datagen. */
public final class TCWorldgen {
    public static final ResourceKey<ConfiguredFeature<?, ?>> UNDERGROUND = configured("underground");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CINDERPEARL = configured("cinderpearl");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SHIMMERLEAF_PATCH = configured("shimmerleaf_patch");
    public static final ResourceKey<ConfiguredFeature<?, ?>> GREATWOOD = configured("greatwood");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SILVERWOOD = configured("silverwood");
    public static final ResourceKey<ConfiguredFeature<?, ?>> GREATWOOD_NATURAL = configured("greatwood_natural");
    public static final ResourceKey<ConfiguredFeature<?, ?>> GREATWOOD_SPIDER = configured("greatwood_spider");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SILVERWOOD_NATURAL = configured("silverwood_natural");
    public static final ResourceKey<ConfiguredFeature<?, ?>> VEGETATION = configured("vegetation");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BIG_MAGIC_TREE = configured("big_magic_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MAGICAL_FOREST_DECORATION = configured("magical_forest_decoration");
    public static final ResourceKey<ConfiguredFeature<?, ?>> EERIE_DECORATION = configured("eerie_decoration");
    public static final ResourceKey<PlacedFeature> UNDERGROUND_PLACED = placed("underground");
    public static final ResourceKey<PlacedFeature> VEGETATION_PLACED = placed("vegetation");
    private static TCUndergroundFeature undergroundFeature;
    private static TCCinderpearlFeature cinderpearlFeature;
    private static TCFlowersFeature flowersFeature;
    private static TCGreatwoodTreeFeature greatwoodFeature;
    private static TCSilverwoodTreeFeature silverwoodFeature;
    private static TCVegetationFeature vegetationFeature;
    private static TCBigMagicTreeFeature bigMagicTreeFeature;
    private static TCMagicalForestFeature magicalForestFeature;
    private static TCEerieDecorationFeature eerieDecorationFeature;

    private TCWorldgen() {
    }

    public static void register(RegisterEvent.RegisterHelper<Feature<?>> registry) {
        undergroundFeature = new TCUndergroundFeature();
        cinderpearlFeature = new TCCinderpearlFeature();
        flowersFeature = new TCFlowersFeature();
        greatwoodFeature = new TCGreatwoodTreeFeature();
        silverwoodFeature = new TCSilverwoodTreeFeature();
        vegetationFeature = new TCVegetationFeature();
        bigMagicTreeFeature = new TCBigMagicTreeFeature();
        magicalForestFeature = new TCMagicalForestFeature();
        eerieDecorationFeature = new TCEerieDecorationFeature();
        registry.register(id("underground"), undergroundFeature);
        registry.register(id("cinderpearl"), cinderpearlFeature);
        registry.register(id("flowers"), flowersFeature);
        registry.register(id("greatwood_tree"), greatwoodFeature);
        registry.register(id("silverwood_tree"), silverwoodFeature);
        registry.register(id("vegetation"), vegetationFeature);
        registry.register(id("big_magic_tree"), bigMagicTreeFeature);
        registry.register(id("magical_forest_decoration"), magicalForestFeature);
        registry.register(id("eerie_decoration"), eerieDecorationFeature);
    }

    public static RegistrySetBuilder builder(HolderLookup.Provider base) {
        return new RegistrySetBuilder()
            .add(Registries.BIOME, TCBiomes::bootstrap)
            .add(Registries.CONFIGURED_FEATURE, TCWorldgen::configuredFeatures)
            .add(Registries.PLACED_FEATURE, TCWorldgen::placedFeatures)
            .add(Registries.STRUCTURE, TCStructures::bootstrapStructures)
            .add(Registries.STRUCTURE_SET, TCStructures::bootstrapSets)
            .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, ctx -> biomeModifiers(ctx, base));
    }

    private static void configuredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> ctx) {
        ctx.register(UNDERGROUND, new ConfiguredFeature<>(undergroundFeature, NoneFeatureConfiguration.INSTANCE));
        ctx.register(CINDERPEARL, new ConfiguredFeature<>(cinderpearlFeature, NoneFeatureConfiguration.INSTANCE));
        // Does not scatter on its own: a natural silverwood calls the same patch algorithm after success.
        ctx.register(SHIMMERLEAF_PATCH, new ConfiguredFeature<>(flowersFeature,
            new BlockStateConfiguration(BlocksTC.shimmerleaf.defaultBlockState())));
        ctx.register(GREATWOOD, new ConfiguredFeature<>(greatwoodFeature, new TCGreatwoodTreeFeature.Configuration(false, false)));
        ctx.register(SILVERWOOD, new ConfiguredFeature<>(silverwoodFeature, new TCSilverwoodTreeFeature.Configuration(7, 4, false)));
        ctx.register(GREATWOOD_NATURAL, new ConfiguredFeature<>(greatwoodFeature, new TCGreatwoodTreeFeature.Configuration(true, false)));
        ctx.register(GREATWOOD_SPIDER, new ConfiguredFeature<>(greatwoodFeature, new TCGreatwoodTreeFeature.Configuration(true, true)));
        ctx.register(SILVERWOOD_NATURAL, new ConfiguredFeature<>(silverwoodFeature, new TCSilverwoodTreeFeature.Configuration(7, 4, true)));
        ctx.register(VEGETATION, new ConfiguredFeature<>(vegetationFeature, NoneFeatureConfiguration.INSTANCE));
        ctx.register(BIG_MAGIC_TREE, new ConfiguredFeature<>(bigMagicTreeFeature, NoneFeatureConfiguration.INSTANCE));
        ctx.register(MAGICAL_FOREST_DECORATION, new ConfiguredFeature<>(magicalForestFeature, NoneFeatureConfiguration.INSTANCE));
        ctx.register(EERIE_DECORATION, new ConfiguredFeature<>(eerieDecorationFeature, NoneFeatureConfiguration.INSTANCE));
    }

    private static void placedFeatures(BootstrapContext<PlacedFeature> ctx) {
        var configured = ctx.lookup(Registries.CONFIGURED_FEATURE);
        ctx.register(UNDERGROUND_PLACED, new PlacedFeature(configured.getOrThrow(UNDERGROUND), List.of(BiomeFilter.biome())));
        ctx.register(VEGETATION_PLACED, new PlacedFeature(configured.getOrThrow(VEGETATION), List.of(BiomeFilter.biome())));
        ctx.register(TCBiomes.MAGICAL_FOREST_DECORATION,
            new PlacedFeature(configured.getOrThrow(MAGICAL_FOREST_DECORATION),
                List.of(HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG), BiomeFilter.biome())));
        ctx.register(TCBiomes.EERIE_DECORATION,
            new PlacedFeature(configured.getOrThrow(EERIE_DECORATION),
                List.of(HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG), BiomeFilter.biome())));
    }

    private static void biomeModifiers(BootstrapContext<BiomeModifier> ctx, HolderLookup.Provider base) {
        ctx.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, id("source_mob_spawns")),
            new thaumcraft.common.world.biomes.TCMobSpawnModifier());
        var features = ctx.lookup(Registries.PLACED_FEATURE);
        // TC6 allowed normal generation in third-party dimensions too. The Nether is filtered out inside underground.
        var biomes = new AnyHolderSet<>(base.lookupOrThrow(Registries.BIOME));
        ctx.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, id("underground")),
            new BiomeModifiers.AddFeaturesBiomeModifier(biomes, HolderSet.direct(features.getOrThrow(UNDERGROUND_PLACED)),
                GenerationStep.Decoration.UNDERGROUND_ORES));
        ctx.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, id("vegetation")),
            new BiomeModifiers.AddFeaturesBiomeModifier(biomes, HolderSet.direct(features.getOrThrow(VEGETATION_PLACED)),
                GenerationStep.Decoration.VEGETAL_DECORATION));
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> configured(String path) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, id(path));
    }

    private static ResourceKey<PlacedFeature> placed(String path) {
        return ResourceKey.create(Registries.PLACED_FEATURE, id(path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, path);
    }
}
