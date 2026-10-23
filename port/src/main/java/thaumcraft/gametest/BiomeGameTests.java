package thaumcraft.gametest;

import com.mojang.datafixers.util.Pair;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import terrablender.api.ModifiedVanillaOverworldBuilder;
import terrablender.api.RegionType;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.entities.Mob8NaturalRegistries;
import thaumcraft.common.entities.WarpEntities;
import thaumcraft.common.world.biomes.BiomeHandler;
import thaumcraft.common.world.biomes.TCBiomeRegion;
import thaumcraft.common.world.biomes.TCBiomes;

/** Registry/biome-region checks only: no terrain generation, chunk loading or spawn side effects. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class BiomeGameTests {
    @GameTest(template = "empty")
    public static void biomeClimateAndEffectsKeepLegacyValues(GameTestHelper h) {
        Biome forest = biome(h, TCBiomes.MAGICAL_FOREST).value();
        Biome eerie = biome(h, TCBiomes.EERIE).value();
        Biome eldritch = biome(h, TCBiomes.ELDRITCH).value();
        for (Biome biome : List.of(forest, eerie, eldritch)) {
            h.assertTrue(biome.getBaseTemperature() == 0.8F, "all three legacy TC biome temperatures are 0.8");
        }
        h.assertTrue(forest.hasPrecipitation() && forest.getModifiedClimateSettings().downfall() == 0.4F,
            "magical forest keeps rain and 0.4 downfall");
        h.assertTrue(!eerie.hasPrecipitation() && eerie.getModifiedClimateSettings().downfall() == 0.5F,
            "eerie disables rain, but inherits 0.5 downfall");
        h.assertTrue(eldritch.hasPrecipitation() && eldritch.getModifiedClimateSettings().downfall() == 0.2F,
            "outer lands keeps rain and 0.2 downfall");
        h.assertTrue(forest.getWaterColor() == 30702 && forest.getGrassColor(0, 0) == TCBiomes.FOREST_GRASS_COLOR
            && forest.getFoliageColor() == TCBiomes.FOREST_FOLIAGE_COLOR, "forest uses the default TC6 green palette");
        h.assertTrue(eerie.getWaterColor() == 3035999 && eerie.getSkyColor() == 2237081
            && eerie.getGrassColor(0, 0) == 4212800 && eerie.getFoliageColor() == 4212800, "eerie keeps its four fixed colors");
        h.assertTrue(eldritch.getSkyColor() == 0, "outer lands has a black sky");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyVanillaSpawnsIncludeOriginalDuplicateEntries(GameTestHelper h) {
        var forest = biome(h, TCBiomes.MAGICAL_FOREST).value().getMobSettings();
        var eerie = biome(h, TCBiomes.EERIE).value().getMobSettings();
        var eldritch = biome(h, TCBiomes.ELDRITCH).value().getMobSettings();
        for (var mobs : List.of(forest, eerie)) {
            assertSpawn(h, mobs, MobCategory.MONSTER, EntityType.SPIDER, 100, 4, 4);
            assertSpawn(h, mobs, MobCategory.MONSTER, EntityType.ZOMBIE, 95, 4, 4);
            assertSpawn(h, mobs, MobCategory.MONSTER, EntityType.ZOMBIE_VILLAGER, 5, 1, 1);
            assertSpawn(h, mobs, MobCategory.MONSTER, EntityType.SKELETON, 100, 4, 4);
            assertSpawn(h, mobs, MobCategory.MONSTER, EntityType.CREEPER, 100, 4, 4);
            assertSpawn(h, mobs, MobCategory.MONSTER, EntityType.SLIME, 100, 4, 4);
            assertSpawn(h, mobs, MobCategory.MONSTER, EntityType.ENDERMAN, 10, 1, 4);
            assertSpawn(h, mobs, MobCategory.MONSTER, EntityType.WITCH, 5, 1, 1);
            assertSpawn(h, mobs, MobCategory.WATER_CREATURE, EntityType.SQUID, 10, 4, 4);
            assertSpawn(h, mobs, MobCategory.AMBIENT, EntityType.BAT, 10, 8, 8);
        }
        assertSpawn(h, forest, MobCategory.CREATURE, EntityType.SHEEP, 12, 4, 4);
        assertSpawn(h, forest, MobCategory.CREATURE, EntityType.PIG, 10, 4, 4);
        assertSpawn(h, forest, MobCategory.CREATURE, EntityType.CHICKEN, 10, 4, 4);
        assertSpawn(h, forest, MobCategory.CREATURE, EntityType.COW, 8, 4, 4);
        assertSpawn(h, forest, MobCategory.CREATURE, EntityType.WOLF, 2, 1, 3);
        assertSpawn(h, forest, MobCategory.CREATURE, EntityType.HORSE, 2, 1, 3);
        assertSpawn(h, forest, MobCategory.MONSTER, EntityType.WITCH, 3, 1, 1);
        assertSpawn(h, forest, MobCategory.MONSTER, EntityType.ENDERMAN, 3, 1, 1);
        assertSpawn(h, forest, MobCategory.MONSTER, EntityType.VEX, 1, 1, 1);
        assertSpawn(h, eerie, MobCategory.CREATURE, EntityType.BAT, 3, 1, 1);
        assertSpawn(h, eerie, MobCategory.MONSTER, EntityType.WITCH, 8, 1, 1);
        assertSpawn(h, eerie, MobCategory.MONSTER, EntityType.ENDERMAN, 4, 1, 1);
        h.assertTrue(eerie.getMobs(MobCategory.CREATURE).unwrap().stream().noneMatch(e -> e.type == EntityType.PIG),
            "eerie cleared its inherited farm animals");
        var originalEldritch = biome(h, TCBiomes.ELDRITCH).value().modifiableBiomeInfo()
            .getOriginalBiomeInfo().mobSpawnSettings();
        for (MobCategory category : MobCategory.values()) {
            h.assertTrue(originalEldritch.getMobs(category).isEmpty(), "outer lands bootstrap clears every inherited vanilla category");
            h.assertTrue(eldritch.getMobs(category).unwrap().stream()
                .noneMatch(entry -> EntityType.getKey(entry.type).getNamespace().equals("minecraft")),
                "source TC additions do not restore inherited or substitute vanilla mobs");
        }
        assertSpawn(h, eldritch, MobCategory.MONSTER, Mob8NaturalRegistries.INHABITED_ZOMBIE, 1, 1, 1);
        assertSpawn(h, eldritch, MobCategory.MONSTER, WarpEntities.ELDRITCH_GUARDIAN, 1, 1, 1);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void biomeTagsFeedLegacyAuraAndCrystalAspectRules(GameTestHelper h) {
        Holder<Biome> forest = biome(h, TCBiomes.MAGICAL_FOREST);
        Holder<Biome> eerie = biome(h, TCBiomes.EERIE);
        Holder<Biome> eldritch = biome(h, TCBiomes.ELDRITCH);
        for (var biome : List.of(forest, eerie, eldritch)) {
            h.assertTrue(biome.is(Tags.Biomes.IS_MAGICAL), "all TC biomes have the legacy MAGICAL type");
            h.assertTrue(BiomeHandler.getBiomeSupportsGreatwood(biome) == 1.0F, "MAGICAL permits greatwood with type chance 1");
        }
        h.assertTrue(forest.is(BiomeTags.IS_FOREST) && !forest.is(Tags.Biomes.IS_SPOOKY), "forest has MAGICAL+FOREST types");
        h.assertTrue(eerie.is(Tags.Biomes.IS_SPOOKY) && !eerie.is(BiomeTags.IS_FOREST), "eerie has MAGICAL+SPOOKY types");
        h.assertTrue(eldritch.is(Tags.Biomes.IS_SPOOKY) && eldritch.is(Tags.Biomes.IS_END)
            && !eldritch.is(BiomeTags.IS_END), "eldritch's END type does not opt into vanilla End structure tags");
        h.assertTrue(Math.abs(BiomeHandler.getBiomeAuraModifier(forest) - 0.625F) < 0.000001F, "forest aura averages 0.75 and 0.5");
        h.assertTrue(Math.abs(BiomeHandler.getBiomeAuraModifier(eerie) - 0.625F) < 0.000001F, "eerie aura averages 0.75 and 0.5");
        h.assertTrue(Math.abs(BiomeHandler.getBiomeAuraModifier(eldritch) - (0.75F + 0.5F + 0.125F) / 3.0F) < 0.000001F,
            "eldritch aura averages magical, spooky and end types");
        assertAspects(h, forest, 2, Set.of(Aspect.ORDER, Aspect.EARTH));
        assertAspects(h, eerie, 2, Set.of(Aspect.ORDER, Aspect.FIRE));
        assertAspects(h, eldritch, 3, Set.of(Aspect.ORDER, Aspect.FIRE, Aspect.AIR));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void biomeOwnedDecorationIsScopedToItsBiome(GameTestHelper h) {
        var placed = h.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        var forestFeature = placed.getOrThrow(TCBiomes.MAGICAL_FOREST_DECORATION);
        var eerieFeature = placed.getOrThrow(TCBiomes.EERIE_DECORATION);
        for (var biome : List.of(TCBiomes.MAGICAL_FOREST, TCBiomes.EERIE, TCBiomes.ELDRITCH, Biomes.FOREST)) {
            var settings = biome(h, biome).value().getGenerationSettings();
            h.assertTrue(settings.hasFeature(forestFeature) == biome.equals(TCBiomes.MAGICAL_FOREST), "forest decoration is biome-owned: " + biome.location());
            h.assertTrue(settings.hasFeature(eerieFeature) == biome.equals(TCBiomes.EERIE), "eerie decoration is biome-owned: " + biome.location());
        }
        for (var key : List.of(TCBiomes.MAGICAL_FOREST, TCBiomes.EERIE)) {
            var settings = biome(h, key).value().getGenerationSettings();
            var expected = key.equals(TCBiomes.MAGICAL_FOREST) ? forestFeature : eerieFeature;
            h.assertTrue(settings.features().get(GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()).stream()
                .anyMatch(holder -> holder.value() == expected), "owned decoration runs at vegetation stage");
        }
        var outerOriginal = biome(h, TCBiomes.ELDRITCH).value().modifiableBiomeInfo().getOriginalBiomeInfo().generationSettings();
        h.assertTrue(outerOriginal.features().isEmpty(), "outer lands bootstrap has no biome-owned decoration");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void terraBlenderReplacesOnlyTemperateForestCells(GameTestHelper h) {
        var region = new TCBiomeRegion(5);
        h.assertTrue(region.getType() == RegionType.OVERWORLD && region.getWeight() == 5, "region carries the configured weight and overworld type");
        List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> actual = new ArrayList<>();
        region.addBiomes(h.getLevel().registryAccess().registryOrThrow(Registries.BIOME), actual::add);
        var vanilla = new ModifiedVanillaOverworldBuilder().build();
        h.assertTrue(actual.size() == vanilla.size(), "all vanilla climate cells are retained, not a forest-only nearest-match source");
        int changed = 0;
        for (int i = 0; i < vanilla.size(); i++) {
            var original = vanilla.get(i);
            var replacement = actual.get(i);
            h.assertTrue(original.getFirst().equals(replacement.getFirst()), "region retains vanilla climate geometry at cell " + i);
            ResourceKey<Biome> expected = original.getSecond();
            if (expected.equals(Biomes.FOREST) || expected.equals(Biomes.BIRCH_FOREST)) {
                expected = TCBiomes.MAGICAL_FOREST;
                changed++;
            }
            h.assertTrue(replacement.getSecond().equals(expected), "only oak/birch forests become magical forest at cell " + i);
            h.assertTrue(!replacement.getSecond().equals(TCBiomes.EERIE) && !replacement.getSecond().equals(TCBiomes.ELDRITCH),
                "eerie and outer lands are never natural region candidates");
        }
        h.assertTrue(changed > 0, "forest has actual climate cells rather than a registry-only biome");
        h.assertTrue(TCBiomeRegion.shouldGenerateForest(true, 5), "enabled positive-weight forest is generated");
        h.assertFalse(TCBiomeRegion.shouldGenerateForest(false, 5), "disabled forest is not inserted");
        h.assertFalse(TCBiomeRegion.shouldGenerateForest(true, 0), "zero-weight forest is not inserted");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void biomeOwnedPlacementUsesSurfaceRatherThanCaveBiome(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var cave = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DEEP_DARK);
        for (var keys : List.of(Pair.of(TCBiomes.MAGICAL_FOREST, TCBiomes.MAGICAL_FOREST_DECORATION),
                              Pair.of(TCBiomes.EERIE, TCBiomes.EERIE_DECORATION))) {
            var surface = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(keys.getFirst());
            var placed = registries.registryOrThrow(Registries.PLACED_FEATURE).getOrThrow(keys.getSecond());
            List<BlockPos> sampled = new ArrayList<>();
            WorldGenLevel virtual = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMinBuildHeight" -> -64;
                    case "getMaxBuildHeight" -> 320;
                    case "getHeight" -> {
                        if (args == null || args.length == 0) {
                            yield 384;
                        }
                        h.assertTrue(args[0] == Heightmap.Types.WORLD_SURFACE_WG, "biome filter uses undecorated surface height");
                        yield 70;
                    }
                    case "getBiome" -> {
                        BlockPos pos = ((BlockPos) args[0]).immutable();
                        sampled.add(pos);
                        yield pos.getY() == 70 ? surface : cave;
                    }
                    default -> throw new AssertionError("Placement-only test tried unexpected terrain access: " + method);
                });
            var generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(surface),
                registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD));
            var context = new PlacementContext(virtual, generator, Optional.of(placed));
            var random = new LegacyRandomSource(0) {
                @Override
                public int nextInt(int bound) {
                    throw new AssertionError("Surface biome-filter placement must consume no random draw");
                }
            };
            BlockPos origin = new BlockPos(32, -64, -48); // ChunkGenerator's minimum-section origin.
            Stream<BlockPos> positions = Stream.of(origin);
            for (var modifier : placed.placement()) {
                positions = positions.flatMap(pos -> modifier.getPositions(context, random, pos));
            }
            var candidates = positions.toList();
            h.assertTrue(candidates.equals(List.of(new BlockPos(32, 70, -48))),
                "surface biome retains one whole-chunk decoration pass despite a different cave biome");
            h.assertTrue(sampled.equals(candidates), "BiomeFilter samples only the surface, not y=-64");
        }
        h.succeed();
    }

    private static Holder<Biome> biome(GameTestHelper h, ResourceKey<Biome> biome) {
        return h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
    }

    private static void assertSpawn(GameTestHelper h, MobSpawnSettings mobs, MobCategory category,
                                    EntityType<?> type, int weight, int min, int max) {
        h.assertTrue(mobs.getMobs(category).unwrap().stream().anyMatch(entry -> entry.type == type
                && entry.getWeight().asInt() == weight && entry.minCount == min && entry.maxCount == max),
            category + " has exact legacy entry " + EntityType.getKey(type) + " " + weight + " " + min + ".." + max);
    }

    private static void assertAspects(GameTestHelper h, Holder<Biome> biome, int count, Set<Aspect> expected) {
        Set<Aspect> actual = new HashSet<>();
        for (int index = 0; index < count; index++) {
            final int selected = index;
            actual.add(BiomeHandler.getRandomBiomeTag(biome, new LegacyRandomSource(0) {
                @Override
                public int nextInt(int bound) {
                    if (bound != count) {
                        throw new AssertionError("Expected " + count + " legacy aura type entries, got " + bound);
                    }
                    return selected;
                }
            }));
        }
        h.assertTrue(actual.equals(expected), "crystal biome bias uses exactly the legacy aspects: " + expected);
    }
}
