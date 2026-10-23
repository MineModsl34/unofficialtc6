package thaumcraft.common.world.biomes;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BiomeDefaultFeatures;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import thaumcraft.Thaumcraft;

/** Data-backed TC biomes. Only magical forest belongs in the natural Overworld biome source. */
public final class TCBiomes {
    public static final ResourceKey<Biome> MAGICAL_FOREST = biome("magical_forest");
    public static final ResourceKey<Biome> EERIE = biome("eerie");
    public static final ResourceKey<Biome> ELDRITCH = biome("eldritch");
    public static final ResourceKey<PlacedFeature> MAGICAL_FOREST_DECORATION = placed("magical_forest_decoration");
    public static final ResourceKey<PlacedFeature> EERIE_DECORATION = placed("eerie_decoration");
    public static final int FOREST_GRASS_COLOR = 5635969;
    public static final int FOREST_FOLIAGE_COLOR = 6750149;
    public static final int FOREST_BLUE_GRASS_COLOR = 6728396;
    public static final int FOREST_BLUE_FOLIAGE_COLOR = 7851246;

    private TCBiomes() {
    }

    public static void bootstrap(BootstrapContext<Biome> context) {
        var features = context.lookup(Registries.PLACED_FEATURE);
        var carvers = context.lookup(Registries.CONFIGURED_CARVER);
        var forestGeneration = new BiomeGenerationSettings.Builder(features, carvers);
        var eerieGeneration = new BiomeGenerationSettings.Builder(features, carvers);
        overworldSubstrate(forestGeneration);
        overworldSubstrate(eerieGeneration);
        forestGeneration.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, MAGICAL_FOREST_DECORATION);
        eerieGeneration.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, EERIE_DECORATION);

        var forestMobs = legacyBaseSpawns(true);
        add(forestMobs, MobCategory.CREATURE, EntityType.WOLF, 2, 1, 3);
        add(forestMobs, MobCategory.CREATURE, EntityType.HORSE, 2, 1, 3);
        // Duplicates supplement the inherited entries, as in BiomeGenMagicalForest.
        add(forestMobs, MobCategory.MONSTER, EntityType.WITCH, 3, 1, 1);
        add(forestMobs, MobCategory.MONSTER, EntityType.ENDERMAN, 3, 1, 1);
        add(forestMobs, MobCategory.MONSTER, EntityType.VEX, 1, 1, 1);
        // Config-aware TCMobSpawnModifier adds actual source mobs, including postInit upsert overrides.

        var eerieMobs = legacyBaseSpawns(false);
        // TC6 put this extra bat entry in CREATURE, in addition to inherited cave bats; preserve the odd category.
        add(eerieMobs, MobCategory.CREATURE, EntityType.BAT, 3, 1, 1);
        add(eerieMobs, MobCategory.MONSTER, EntityType.WITCH, 8, 1, 1);
        add(eerieMobs, MobCategory.MONSTER, EntityType.ENDERMAN, 4, 1, 1);
        // Source TC mob weights are applied by TCMobSpawnModifier after the inherited lists exist.

        context.register(MAGICAL_FOREST, create(true, 0.4F, 30702, skyColor(0.8F),
            FOREST_GRASS_COLOR, FOREST_FOLIAGE_COLOR, forestMobs, forestGeneration));
        context.register(EERIE, create(false, 0.5F, 3035999, 2237081,
            4212800, 4212800, eerieMobs, eerieGeneration));
        // BETA26 registers this biome only: no source Outer Lands dimension implementation.
        // No vanilla decoration or inherited mobs are added to the original biome.
        // The modifier adds inhabited zombie and guardian unconditionally, as in BiomeGenEldritch.
        context.register(ELDRITCH, create(true, 0.2F, 4159204, 0,
            null, null, new MobSpawnSettings.Builder(), new BiomeGenerationSettings.Builder(features, carvers)));
    }

    private static void overworldSubstrate(BiomeGenerationSettings.Builder generation) {
        // Modern terrain/caves/vanilla ore tables are an explicit 1.21 adaptation, not TC6 coordinate parity.
        BiomeDefaultFeatures.addDefaultCarversAndLakes(generation);
        BiomeDefaultFeatures.addDefaultCrystalFormations(generation);
        BiomeDefaultFeatures.addDefaultMonsterRoom(generation);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(generation);
        BiomeDefaultFeatures.addDefaultOres(generation);
        BiomeDefaultFeatures.addDefaultSoftDisks(generation);
        BiomeDefaultFeatures.addDefaultSprings(generation);
        BiomeDefaultFeatures.addSurfaceFreezing(generation);
    }

    /** Exact inherited 1.12 Biome spawn weights, deliberately not the changed 1.21 commonSpawns. */
    private static MobSpawnSettings.Builder legacyBaseSpawns(boolean farmAnimals) {
        var mobs = new MobSpawnSettings.Builder();
        if (farmAnimals) {
            add(mobs, MobCategory.CREATURE, EntityType.SHEEP, 12, 4, 4);
            add(mobs, MobCategory.CREATURE, EntityType.PIG, 10, 4, 4);
            add(mobs, MobCategory.CREATURE, EntityType.CHICKEN, 10, 4, 4);
            add(mobs, MobCategory.CREATURE, EntityType.COW, 8, 4, 4);
        }
        add(mobs, MobCategory.MONSTER, EntityType.SPIDER, 100, 4, 4);
        add(mobs, MobCategory.MONSTER, EntityType.ZOMBIE, 95, 4, 4);
        add(mobs, MobCategory.MONSTER, EntityType.ZOMBIE_VILLAGER, 5, 1, 1);
        add(mobs, MobCategory.MONSTER, EntityType.SKELETON, 100, 4, 4);
        add(mobs, MobCategory.MONSTER, EntityType.CREEPER, 100, 4, 4);
        add(mobs, MobCategory.MONSTER, EntityType.SLIME, 100, 4, 4);
        add(mobs, MobCategory.MONSTER, EntityType.ENDERMAN, 10, 1, 4);
        add(mobs, MobCategory.MONSTER, EntityType.WITCH, 5, 1, 1);
        add(mobs, MobCategory.WATER_CREATURE, EntityType.SQUID, 10, 4, 4);
        add(mobs, MobCategory.AMBIENT, EntityType.BAT, 10, 8, 8);
        return mobs;
    }

    private static void add(MobSpawnSettings.Builder mobs, MobCategory category, EntityType<?> type, int weight, int min, int max) {
        mobs.addSpawn(category, new MobSpawnSettings.SpawnerData(type, weight, min, max));
    }

    private static Biome create(boolean rain, float downfall, int water, int sky, Integer grass, Integer foliage,
                                MobSpawnSettings.Builder mobs, BiomeGenerationSettings.Builder generation) {
        var effects = new BiomeSpecialEffects.Builder().waterColor(water).waterFogColor(329011)
            .fogColor(12638463).skyColor(sky).ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS);
        if (grass != null) {
            effects.grassColorOverride(grass);
        }
        if (foliage != null) {
            effects.foliageColorOverride(foliage);
        }
        return new Biome.BiomeBuilder().hasPrecipitation(rain).temperature(0.8F).downfall(downfall)
            .specialEffects(effects.build()).mobSpawnSettings(mobs.build()).generationSettings(generation.build()).build();
    }

    private static int skyColor(float temperature) {
        float value = Mth.clamp(temperature / 3.0F, -1.0F, 1.0F);
        return Mth.hsvToRgb(0.62222224F - value * 0.05F, 0.5F + value * 0.1F, 1.0F);
    }

    private static ResourceKey<Biome> biome(String path) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, path));
    }

    private static ResourceKey<PlacedFeature> placed(String path) {
        return ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, path));
    }
}
