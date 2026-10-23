package thaumcraft.gametest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.ore.BlockCrystal;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.world.features.TCWorldgen;

/** Exact scripted passes on an in-memory WorldGenLevel, never on server terrain. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class WorldgenPassGameTests {
    private static final BlockPos ORIGIN = new BlockPos(13, 1, 11);

    @GameTest(template = "empty")
    public static void undergroundPassPreservesOrderCountsHeightsAndChunkAlignment(GameTestHelper h) {
        try (ConfigFixture config = new ConfigFixture()) {
            config.underground(true, true, true, true);
            MemoryLevel memory = new MemoryLevel(h, Biomes.PLAINS, pos -> Blocks.STONE.defaultBlockState());
            ScriptedRandom random = new ScriptedRandom();
            for (int i = 0; i < 18; i++) {
                random.add(13, 6).add(51, 10).add(13, 6); // cinnabar: X,Y,Z
            }
            for (int i = 0; i < 18; i++) {
                random.add(13, 6).add(64, 20).add(13, 6); // quartz: X,Y,Z
            }
            for (int i = 0; i < 20; i++) {
                random.add(13, 6).add(13, 6).add(25, 10); // amber: X,Z,surface offset
            }
            for (int i = 0; i < 8; i++) {
                random.add(13, 6).add(13, 6).add(65, 10).add(6, 0).add(3, 1);
                for (int candidate = 0; candidate < 27; candidate++) {
                    random.add(3, 1); // every candidate is occupied stone, so no SIZE draws
                }
            }
            BlockPos origin = h.absolutePos(ORIGIN);
            h.assertTrue(feature(h, TCWorldgen.UNDERGROUND).place(memory.level, generator(h), random, origin),
                "aggregate pass placed ordinary ores");
            random.assertConsumed(h, 424);
            h.assertTrue(memory.writes.size() == 3, "identical ore targets succeed once each, not once per attempt");
            int x = (origin.getX() & ~15) + 8;
            int z = (origin.getZ() & ~15) + 8;
            assertWrite(h, memory.writes.get(0), new BlockPos(x, 10, z), BlocksTC.oreCinnabar.defaultBlockState(), "cinnabar first");
            assertWrite(h, memory.writes.get(1), new BlockPos(x, 20, z), BlocksTC.oreQuartz.defaultBlockState(), "quartz second");
            assertWrite(h, memory.writes.get(2), new BlockPos(x, 60, z), BlocksTC.oreAmber.defaultBlockState(), "amber third");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void crystalBudgetStopsOnlyAfterWholeCubeAndSumsSize(GameTestHelper h) {
        try (ConfigFixture config = new ConfigFixture()) {
            config.underground(false, false, false, true);
            MemoryLevel memory = new MemoryLevel(h, Biomes.PLAINS,
                pos -> (pos.getY() <= 9 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            ScriptedRandom random = new ScriptedRandom();
            for (int seed = 0; seed < 3; seed++) {
                random.add(13, seed * 3).add(13, 6).add(65, 10).add(6, 0).add(3, 1);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            random.add(3, 1);
                            if (dy == 0) {
                                random.add(3, 2); // air above the floor receives SIZE=3
                            }
                        }
                    }
                }
            }
            BlockPos origin = h.absolutePos(ORIGIN);
            h.assertTrue(feature(h, TCWorldgen.UNDERGROUND).place(memory.level, generator(h), random, origin),
                "crystal pass placed supported crystals");
            random.assertConsumed(h, 123);
            h.assertTrue(memory.writes.size() == 27, "three whole cubes place nine floor crystals each");
            int totalSize = 0;
            int centerX = (origin.getX() & ~15) + 8;
            int centerZ = (origin.getZ() & ~15) + 8;
            for (int i = 0; i < memory.writes.size(); i++) {
                Write write = memory.writes.get(i);
                int seed = i / 9;
                int patchIndex = i % 9;
                BlockPos expected = new BlockPos(centerX - 6 + seed * 3 + patchIndex / 3 - 1, 10,
                    centerZ + patchIndex % 3 - 1);
                h.assertTrue(write.pos.equals(expected), "cube visits X,Y,Z in the original nesting order at " + i);
                h.assertTrue(write.state.is(BlocksTC.crystalAir), "initial meta=0 selects AIR crystal");
                h.assertTrue(write.state.getValue(BlockCrystal.SIZE) == 3, "successful candidate retains SIZE=3");
                h.assertTrue(write.state.getValue(BlockCrystal.GENERATION) == 1, "seed starts in generation one");
                h.assertTrue(write.state.getValue(BlockCrystal.DOWN), "floor connection is populated");
                h.assertFalse(write.state.getValue(BlockCrystal.UP), "no rock face above the crystal");
                totalSize += write.state.getValue(BlockCrystal.SIZE);
            }
            h.assertTrue(totalSize == 81 && totalSize > 64, "budget sums SIZE and allows the last whole cube to overshoot to 81");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cinderpearlPassHonorsConfigFlatClimateSoilAndChanceGates(GameTestHelper h) {
        try (ConfigFixture config = new ConfigFixture()) {
            ConfiguredFeature<?, ?> cinderpearl = feature(h, TCWorldgen.CINDERPEARL);
            Holder<Biome> desert = biome(h, Biomes.DESERT);
            ChunkGenerator noise = new NoiseBasedChunkGenerator(new FixedBiomeSource(desert),
                h.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD));
            ChunkGenerator flat = new FlatLevelSource(new FlatLevelGeneratorSettings(Optional.empty(), desert, List.of()));
            BlockPos origin = h.absolutePos(ORIGIN);

            ModConfig.CONFIG_WORLD.generateTrees = false;
            MemoryLevel disabled = flowerTerrain(h, Biomes.DESERT, Blocks.SAND.defaultBlockState());
            assertRejected(h, cinderpearl, disabled, noise, origin, new ScriptedRandom(), "generateTrees=false");
            h.assertTrue(disabled.terrainQueries == 0, "disabled vegetation does not query terrain");
            ModConfig.CONFIG_WORLD.generateTrees = true;

            MemoryLevel flatTerrain = flowerTerrain(h, Biomes.DESERT, Blocks.SAND.defaultBlockState());
            assertRejected(h, cinderpearl, flatTerrain, flat, origin, new ScriptedRandom(), "flat generator");
            h.assertTrue(flatTerrain.terrainQueries == 0, "flat vegetation does not query terrain");
            assertRejected(h, cinderpearl, flowerTerrain(h, Biomes.SAVANNA, Blocks.SAND.defaultBlockState()), noise, origin,
                new ScriptedRandom(), "hot but non-sandy biome");
            assertRejected(h, cinderpearl, flowerTerrain(h, Biomes.BEACH, Blocks.SAND.defaultBlockState()), noise, origin,
                new ScriptedRandom(), "sandy but temperature <= 1 biome");
            assertRejected(h, cinderpearl, flowerTerrain(h, Biomes.DESERT, Blocks.STONE.defaultBlockState()), noise, origin,
                new ScriptedRandom(), "hot sandy biome with wrong actual center soil");
            assertRejected(h, cinderpearl, flowerTerrain(h, Biomes.DESERT, Blocks.SAND.defaultBlockState()), noise, origin,
                new ScriptedRandom().add(30, 1), "failed one-in-thirty chance");

            for (ResourceKey<Biome> key : List.of(Biomes.DESERT, Biomes.BADLANDS)) {
                BlockState sand = (key.equals(Biomes.BADLANDS) ? Blocks.RED_SAND : Blocks.SAND).defaultBlockState();
                MemoryLevel memory = flowerTerrain(h, key, sand);
                ScriptedRandom random = new ScriptedRandom().add(30, 0);
                for (int attempt = 0; attempt < 18; attempt++) {
                    random.add(8, 0).add(8, 0).add(4, 0).add(4, 0).add(8, 0).add(8, 0);
                }
                h.assertTrue(cinderpearl.place(memory.level, noise, random, origin), "successful hot sand patch in " + key.location());
                random.assertConsumed(h, 109);
                h.assertTrue(memory.writes.size() == 1, "eighteen identical flower candidates place only once");
                assertWrite(h, memory.writes.get(0), new BlockPos((origin.getX() & ~15) + 8, 70, (origin.getZ() & ~15) + 8),
                    BlocksTC.cinderpearl.defaultBlockState(), "flower origin is the chunk center above sand");
            }
        }
        h.succeed();
    }

    private static void assertRejected(GameTestHelper h, ConfiguredFeature<?, ?> feature, MemoryLevel memory,
                                       ChunkGenerator generator, BlockPos origin, ScriptedRandom random, String reason) {
        h.assertFalse(feature.place(memory.level, generator, random, origin), "cinderpearl rejects " + reason);
        random.assertConsumed(h, random.draws.size());
        h.assertTrue(memory.writes.isEmpty(), "rejected cinderpearl pass writes nothing: " + reason);
    }

    private static MemoryLevel flowerTerrain(GameTestHelper h, ResourceKey<Biome> biome, BlockState soil) {
        return new MemoryLevel(h, biome, pos -> pos.getY() == 69 ? soil : Blocks.AIR.defaultBlockState());
    }

    private static Holder<Biome> biome(GameTestHelper h, ResourceKey<Biome> key) {
        return h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
    }

    private static ConfiguredFeature<?, ?> feature(GameTestHelper h, ResourceKey<ConfiguredFeature<?, ?>> key) {
        return h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(key);
    }

    private static ChunkGenerator generator(GameTestHelper h) {
        return h.getLevel().getChunkSource().getGenerator();
    }

    private static void assertWrite(GameTestHelper h, Write write, BlockPos pos, BlockState state, String message) {
        h.assertTrue(write.pos.equals(pos) && write.state.equals(state), message + ": " + write);
    }

    private record Write(BlockPos pos, BlockState state) {
    }

    /** Fail closed: unexpected world access must not fall through to real chunks. */
    private static final class MemoryLevel implements InvocationHandler {
        final GameTestHelper helper;
        final Holder<Biome> biome;
        final Function<BlockPos, BlockState> fallback;
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final List<Write> writes = new ArrayList<>();
        final WorldGenLevel level;
        int terrainQueries;

        MemoryLevel(GameTestHelper helper, ResourceKey<Biome> biome, Function<BlockPos, BlockState> fallback) {
            this.helper = helper;
            this.biome = biome(helper, biome);
            this.fallback = fallback;
            level = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, this);
        }

        private BlockState state(BlockPos pos) {
            terrainQueries++;
            BlockState explicit = blocks.get(pos);
            return explicit == null ? fallback.apply(pos) : explicit;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getLevel" -> helper.getLevel();
                case "getMinBuildHeight" -> helper.getLevel().getMinBuildHeight();
                case "getMaxBuildHeight" -> helper.getLevel().getMaxBuildHeight();
                case "getHeight" -> {
                    if (args == null || args.length == 0) {
                        yield helper.getLevel().getHeight();
                    }
                    if (args.length != 3 || !(args[0] instanceof Heightmap.Types)) {
                        throw new AssertionError("Unexpected height query: " + method);
                    }
                    terrainQueries++;
                    yield 70;
                }
                case "isOutsideBuildHeight" -> {
                    int y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer) args[0];
                    yield y < helper.getLevel().getMinBuildHeight() || y >= helper.getLevel().getMaxBuildHeight();
                }
                case "ensureCanWrite" -> true;
                case "getBlockState" -> state((BlockPos) args[0]);
                case "getFluidState" -> state((BlockPos) args[0]).getFluidState();
                case "isEmptyBlock" -> state((BlockPos) args[0]).isAir();
                case "getBiome" -> {
                    terrainQueries++;
                    yield biome;
                }
                case "setBlock" -> {
                    BlockPos pos = ((BlockPos) args[0]).immutable();
                    BlockState next = (BlockState) args[1];
                    if (state(pos).equals(next)) {
                        yield false;
                    }
                    blocks.put(pos, next);
                    writes.add(new Write(pos, next));
                    yield true;
                }
                case "toString" -> "In-memory GameTest WorldGenLevel";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Synthetic generation attempted unexpected world access: " + method);
            };
        }
    }

    private static final class ConfigFixture implements AutoCloseable {
        final int density = ModConfig.CONFIG_WORLD.oreDensity;
        final boolean cinnabar = ModConfig.CONFIG_WORLD.generateCinnabar;
        final boolean quartz = ModConfig.CONFIG_WORLD.generateQuartz;
        final boolean amber = ModConfig.CONFIG_WORLD.generateAmber;
        final boolean crystals = ModConfig.CONFIG_WORLD.generateCrystals;
        final boolean trees = ModConfig.CONFIG_WORLD.generateTrees;

        void underground(boolean cinnabar, boolean quartz, boolean amber, boolean crystals) {
            ModConfig.CONFIG_WORLD.oreDensity = 100;
            ModConfig.CONFIG_WORLD.generateCinnabar = cinnabar;
            ModConfig.CONFIG_WORLD.generateQuartz = quartz;
            ModConfig.CONFIG_WORLD.generateAmber = amber;
            ModConfig.CONFIG_WORLD.generateCrystals = crystals;
        }

        @Override
        public void close() {
            ModConfig.CONFIG_WORLD.oreDensity = density;
            ModConfig.CONFIG_WORLD.generateCinnabar = cinnabar;
            ModConfig.CONFIG_WORLD.generateQuartz = quartz;
            ModConfig.CONFIG_WORLD.generateAmber = amber;
            ModConfig.CONFIG_WORLD.generateCrystals = crystals;
            ModConfig.CONFIG_WORLD.generateTrees = trees;
        }
    }

    private record Draw(int bound, int value) {
    }

    private static final class ScriptedRandom extends LegacyRandomSource {
        final List<Draw> draws = new ArrayList<>();
        int index;

        ScriptedRandom() {
            super(0);
        }

        ScriptedRandom add(int bound, int value) {
            if (value < 0 || value >= bound) {
                throw new IllegalArgumentException("Invalid scripted draw " + value + "/" + bound);
            }
            draws.add(new Draw(bound, value));
            return this;
        }

        @Override
        public int nextInt(int bound) {
            if (index >= draws.size()) {
                throw new AssertionError("Unexpected extra random draw " + index + " with bound " + bound);
            }
            Draw draw = draws.get(index++);
            if (draw.bound != bound) {
                throw new AssertionError("Random bound at draw " + (index - 1) + ": " + bound + " != " + draw.bound);
            }
            return draw.value;
        }

        @Override
        public int next(int bits) {
            throw new AssertionError("Unexpected non-nextInt random draw, bits=" + bits);
        }

        void assertConsumed(GameTestHelper h, int expected) {
            h.assertTrue(draws.size() == expected && index == expected,
                "complete scripted random trace: consumed " + index + " of " + draws.size() + ", expected " + expected);
        }
    }
}
