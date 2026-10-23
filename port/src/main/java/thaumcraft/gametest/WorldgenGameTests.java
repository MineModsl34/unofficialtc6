package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.ore.BlockCrystal;
import thaumcraft.common.world.features.TCFlowersFeature;
import thaumcraft.common.world.features.TCCinderpearlFeature;
import thaumcraft.common.world.features.TCUndergroundFeature;
import thaumcraft.common.world.features.TCWorldgen;

/** Phase 3b: TC6 placement rules and loaded feature/biome-modifier wiring. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class WorldgenGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static void clearCrystalSite(GameTestHelper h) {
        h.setBlock(POS, Blocks.AIR);
        for (Direction side : Direction.values()) {
            h.setBlock(POS.relative(side), Blocks.AIR);
        }
    }

    @GameTest(template = "empty")
    public static void oresReplaceOnlyOldStoneVariants(GameTestHelper h) {
        BlockPos absolute = h.absolutePos(POS);
        for (Block host : new Block[]{Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE}) {
            h.setBlock(POS, host);
            h.assertTrue(TCUndergroundFeature.tryPlaceOre(h.getLevel(), absolute, BlocksTC.oreCinnabar.defaultBlockState()),
                "old BlockStone variant is an ore host: " + host);
            h.assertBlockPresent(BlocksTC.oreCinnabar, POS);
        }
        for (Block nonHost : new Block[]{Blocks.DEEPSLATE, Blocks.DIRT, Blocks.NETHERRACK, Blocks.AIR, BlocksTC.oreCinnabar}) {
            h.setBlock(POS, nonHost);
            h.assertFalse(TCUndergroundFeature.tryPlaceOre(h.getLevel(), absolute, BlocksTC.oreAmber.defaultBlockState()),
                "ore generation must not replace non-host: " + nonHost);
            h.assertBlockPresent(nonHost, POS);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void crystalsAttachAndRetainSeedState(GameTestHelper h) {
        BlockPos absolute = h.absolutePos(POS);
        for (int size = 1; size <= 3; size++) {
            clearCrystalSite(h);
            h.setBlock(POS.below(), Blocks.STONE);
            BlockState seed = BlocksTC.crystalAir.defaultBlockState()
                .setValue(BlockCrystal.SIZE, size).setValue(BlockCrystal.GENERATION, 1);
            h.assertTrue(TCUndergroundFeature.tryPlaceCrystal(h.getLevel(), absolute, seed), "dry supported air accepts crystal");
            h.assertBlockPresent(BlocksTC.crystalAir, POS);
            h.assertBlockProperty(POS, BlockCrystal.SIZE, size);
            h.assertBlockProperty(POS, BlockCrystal.GENERATION, 1);
            h.assertBlockProperty(POS, BlockCrystal.DOWN, true);
            h.assertBlockProperty(POS, BlockCrystal.UP, false);
            h.assertBlockProperty(POS, BlockCrystal.NORTH, false);
            h.assertBlockProperty(POS, BlockCrystal.SOUTH, false);
            h.assertBlockProperty(POS, BlockCrystal.EAST, false);
            h.assertBlockProperty(POS, BlockCrystal.WEST, false);
        }

        clearCrystalSite(h);
        h.setBlock(POS.below(), Blocks.SAND);
        h.setBlock(POS.east(), Blocks.STONE);
        h.setBlock(POS, Blocks.DEAD_BUSH);
        h.assertTrue(TCUndergroundFeature.tryPlaceCrystal(h.getLevel(), absolute, BlocksTC.crystalFire.defaultBlockState()
                .setValue(BlockCrystal.SIZE, 2)),
            "dry replaceable bush accepts crystal when a solid rock face is adjacent");
        h.assertBlockProperty(POS, BlockCrystal.EAST, true);
        h.assertBlockProperty(POS, BlockCrystal.DOWN, false);
        h.assertBlockProperty(POS, BlockCrystal.SIZE, 2);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void crystalsRejectWetSolidAndUnsupportedSites(GameTestHelper h) {
        BlockPos absolute = h.absolutePos(POS);
        BlockState seed = BlocksTC.crystalOrder.defaultBlockState().setValue(BlockCrystal.SIZE, 3);
        clearCrystalSite(h);
        h.assertFalse(TCUndergroundFeature.tryPlaceCrystal(h.getLevel(), absolute, seed), "unsupported air is rejected");
        h.setBlock(POS.below(), Blocks.DIRT);
        h.assertFalse(TCUndergroundFeature.tryPlaceCrystal(h.getLevel(), absolute, seed), "soil is not rock support");
        h.setBlock(POS.below(), Blocks.STONE_SLAB);
        h.assertFalse(TCUndergroundFeature.tryPlaceCrystal(h.getLevel(), absolute, seed), "partial untagged support is rejected");
        h.setBlock(POS.below(), Blocks.STONE);
        for (Block occupied : new Block[]{Blocks.WATER, Blocks.LAVA, Blocks.SEAGRASS, Blocks.STONE}) {
            h.setBlock(POS, occupied);
            h.assertFalse(TCUndergroundFeature.tryPlaceCrystal(h.getLevel(), absolute, seed),
                "wet or non-replaceable candidate is rejected: " + occupied);
            h.assertBlockPresent(occupied, POS);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void flowerPatchUsesOnlyOldGrassAndSand(GameTestHelper h) {
        BlockPos absolute = h.absolutePos(POS);
        for (Block soil : new Block[]{Blocks.GRASS_BLOCK, Blocks.SAND, Blocks.RED_SAND, Blocks.DIRT, Blocks.TERRACOTTA, Blocks.STONE}) {
            h.setBlock(POS, Blocks.AIR);
            h.setBlock(POS.below(), soil);
            FlowerRolls random = new FlowerRolls();
            int count = TCFlowersFeature.placePatch(h.getLevel(), random, absolute, BlocksTC.cinderpearl.defaultBlockState());
            boolean valid = soil == Blocks.GRASS_BLOCK || soil == Blocks.SAND || soil == Blocks.RED_SAND;
            h.assertTrue(count == (valid ? 1 : 0), "eighteen identical candidates place at most once on " + soil + ": " + count);
            h.assertBlockPresent(valid ? BlocksTC.cinderpearl : Blocks.AIR, POS);
            assertFlowerDraws(h, random);
        }
        h.setBlock(POS.below(), Blocks.GRASS_BLOCK);
        h.setBlock(POS, Blocks.SHORT_GRASS);
        FlowerRolls occupied = new FlowerRolls();
        h.assertTrue(TCFlowersFeature.placePatch(h.getLevel(), occupied, absolute, BlocksTC.cinderpearl.defaultBlockState()) == 0,
            "flower patch needs air; it does not replace even ordinary short grass");
        h.assertBlockPresent(Blocks.SHORT_GRASS, POS);
        assertFlowerDraws(h, occupied);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void flowerPatchPreservesDrawOrderAndEighteenAttempts(GameTestHelper h) {
        // All positions remain inside the existing 3x3x3 template. First offset is (+1,+1,-1).
        BlockPos target = POS.offset(1, 1, -1);
        h.setBlock(POS, Blocks.AIR);
        h.setBlock(POS.below(), Blocks.STONE);
        h.setBlock(target, Blocks.AIR);
        h.setBlock(target.below(), Blocks.GRASS_BLOCK);
        FlowerRolls random = new FlowerRolls(1, 0, 1, 0, 0, 1);
        h.assertTrue(TCFlowersFeature.placePatch(h.getLevel(), random, h.absolutePos(POS), BlocksTC.cinderpearl.defaultBlockState()) == 1,
            "only first scripted candidate succeeds");
        h.assertBlockPresent(BlocksTC.cinderpearl, target);
        h.assertBlockPresent(Blocks.AIR, POS);
        assertFlowerDraws(h, random);
        h.succeed();
    }

    private static void assertFlowerDraws(GameTestHelper h, FlowerRolls random) {
        h.assertTrue(random.bounds.size() == 18 * 6, "all eighteen flower attempts make six random draws, including failed candidates");
        int[] expected = {8, 8, 4, 4, 8, 8};
        for (int i = 0; i < random.bounds.size(); i++) {
            h.assertTrue(random.bounds.get(i) == expected[i % 6], "flower X,X,Y,Y,Z,Z draw bounds at index " + i);
        }
    }

    @GameTest(template = "empty")
    public static void registeredFeaturesReachBiomeDecorationSettings(GameTestHelper h) {
        var configured = h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        var placed = h.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        var underground = configured.getOrThrow(TCWorldgen.UNDERGROUND);
        var cinderpearl = configured.getOrThrow(TCWorldgen.CINDERPEARL);
        var shimmerleaf = configured.getOrThrow(TCWorldgen.SHIMMERLEAF_PATCH);
        h.assertTrue(underground.feature() instanceof TCUndergroundFeature, "underground configured feature uses ported aggregate generator");
        h.assertTrue(cinderpearl.feature() instanceof TCCinderpearlFeature, "cinderpearl configured feature uses climate/config-gated decoration");
        h.assertTrue(shimmerleaf.feature() instanceof TCFlowersFeature, "silverwood patch uses TC6 flower patch");
        h.assertTrue(cinderpearl.config() instanceof NoneFeatureConfiguration, "cinderpearl generator owns its fixed flower state");
        h.assertTrue(shimmerleaf.config() instanceof BlockStateConfiguration flowers && flowers.state.is(BlocksTC.shimmerleaf),
            "shimmerleaf configured state is registered");
        PlacedFeature undergroundPlaced = placed.getOrThrow(TCWorldgen.UNDERGROUND_PLACED);
        PlacedFeature vegetationPlaced = placed.getOrThrow(TCWorldgen.VEGETATION_PLACED);
        h.assertTrue(undergroundPlaced.feature().value() == underground, "underground placed feature resolves configured holder");
        h.assertTrue(vegetationPlaced.feature().value() == configured.getOrThrow(TCWorldgen.VEGETATION),
            "vegetation placed feature resolves the shared tree/flower pass");
        for (PlacedFeature feature : List.of(undergroundPlaced, vegetationPlaced)) {
            h.assertTrue(feature.placement().size() == 1 && feature.placement().get(0) instanceof BiomeFilter,
                "aggregate chunk generators use only biome filtering, with no extra count/in-square random draws");
        }
        h.assertFalse(placed.containsKey(TCWorldgen.SHIMMERLEAF_PATCH.location()), "shimmerleaf is not independently attached as a placed feature");
        h.assertFalse(placed.containsKey(TCWorldgen.CINDERPEARL.location()),
            "the old independent cinderpearl placement is removed, so vegetation cannot generate its flowers twice");
        // TC6 permits ordinary generation in other dimensions; runtime feature checks, not the holder set, gate it.
        for (ResourceKey<Biome> biome : List.of(Biomes.PLAINS, Biomes.DESERT, Biomes.NETHER_WASTES, Biomes.THE_END)) {
            assertDecoration(h, biome, GenerationStep.Decoration.UNDERGROUND_ORES, undergroundPlaced, true);
            assertDecoration(h, biome, GenerationStep.Decoration.VEGETAL_DECORATION, vegetationPlaced, true);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void undergroundRejectsExistingNetherBeforeRandomOrChunkAccess(GameTestHelper h) {
        // Query an already-created dimension; never create/load a world or request a Nether chunk.
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "test server has its existing Nether level");
        var configured = h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(TCWorldgen.UNDERGROUND);
        var noRandom = new LegacyRandomSource(0) {
            @Override
            public int nextInt(int bound) {
                throw new AssertionError("Nether rejection must precede all underground random draws");
            }
        };
        h.assertFalse(configured.place(nether, nether.getChunkSource().getGenerator(), noRandom, h.absolutePos(POS)),
            "Nether never runs ordinary ore/crystal generation");
        h.succeed();
    }

    private static void assertDecoration(GameTestHelper h, ResourceKey<Biome> biome, GenerationStep.Decoration step,
                                         PlacedFeature feature, boolean expected) {
        var settings = h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(biome).getGenerationSettings();
        var steps = settings.features();
        boolean present = steps.size() > step.ordinal()
            && steps.get(step.ordinal()).stream().anyMatch(holder -> holder.value() == feature);
        h.assertTrue(present == expected, biome.location() + " has expected " + step + " attachment: " + expected);
        h.assertTrue(settings.hasFeature(feature) == expected, biome.location() + " feature membership agrees with decoration step");
    }

    private static final class FlowerRolls extends LegacyRandomSource {
        final int[] firstValues;
        final List<Integer> bounds = new ArrayList<>();

        FlowerRolls(int... firstValues) {
            super(0);
            this.firstValues = firstValues;
        }

        @Override
        public int nextInt(int bound) {
            int index = bounds.size();
            if (index >= 18 * 6) {
                throw new AssertionError("Flower patch made more than eighteen attempts");
            }
            int value = index < firstValues.length ? firstValues[index] : 0;
            if (value < 0 || value >= bound) {
                throw new AssertionError("Scripted flower draw " + value + " exceeds bound " + bound);
            }
            bounds.add(bound);
            return value;
        }
    }
}
