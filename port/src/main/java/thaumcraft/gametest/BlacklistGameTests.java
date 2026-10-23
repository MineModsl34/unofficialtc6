package thaumcraft.gametest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.world.WorldgenPolicy;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.aura.AuraHandler;
import thaumcraft.common.world.biomes.BiomeHandler;
import thaumcraft.common.world.features.TCWorldgen;

/** Full blacklist entry-point checks; virtual terrain and detached aura leave real chunk contents untouched. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class BlacklistGameTests {
    private static final int[] MODES = {0, 1, 2, -1, 7};
    private static final BlockPos ORIGIN = new BlockPos(13, 1, 11);

    @GameTest(template = "empty")
    public static void undergroundModesCheckChunkCenterBeforeRandomOrWrites(GameTestHelper h) {
        try (ConfigFixture config = new ConfigFixture()) {
            ModConfig.CONFIG_WORLD.oreDensity = 100;
            ModConfig.CONFIG_WORLD.generateCinnabar = true;
            ModConfig.CONFIG_WORLD.generateQuartz = false;
            ModConfig.CONFIG_WORLD.generateAmber = false;
            ModConfig.CONFIG_WORLD.generateCrystals = false;
            var feature = feature(h, TCWorldgen.UNDERGROUND);
            BlockPos origin = h.absolutePos(ORIGIN);
            ChunkGenerator generator = generator(h);
            for (boolean dimension : new boolean[]{false, true}) {
                for (int mode : MODES) {
                    policy(h, dimension, mode);
                    MemoryLevel memory = new MemoryLevel(h, true);
                    Rolls random = new Rolls();
                    boolean allowed = mode != 0 && mode != 2;
                    h.assertTrue(feature.place(memory.level, generator, random, origin) == allowed,
                        "ore mode " + mode + " dimension=" + dimension + " allows=" + allowed);
                    assertFirstCenter(h, memory, origin);
                    h.assertTrue(memory.biomeQueries.size() == 1, "ore policy reads only its chunk-center biome when crystals are off");
                    h.assertTrue(random.bounds.size() == (allowed ? 18 * 3 : 0), "ore policy gates all X/Y/Z random draws");
                    h.assertTrue(memory.writes.size() == (allowed ? 1 : 0), "ore policy gates actual ore writes");
                    if (!allowed) {
                        h.assertTrue(memory.terrainQueries == 0, "blocked ores do not read block or height terrain");
                    } else {
                        h.assertTrue(memory.writes.get(0).state.is(BlocksTC.oreCinnabar), "allowed ore branch places cinnabar");
                    }
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vegetationModesGateBeforeTreeAndFlowerRandom(GameTestHelper h) {
        try (ConfigFixture config = new ConfigFixture()) {
            ModConfig.CONFIG_WORLD.generateTrees = true;
            BlockPos origin = h.absolutePos(ORIGIN);
            ChunkGenerator generator = generator(h);
            for (var key : List.of(TCWorldgen.VEGETATION, TCWorldgen.CINDERPEARL)) {
                for (boolean dimension : new boolean[]{false, true}) {
                    for (int mode : MODES) {
                        policy(h, dimension, mode);
                        MemoryLevel memory = new MemoryLevel(h, false);
                        Rolls random = new Rolls();
                        boolean allowed = mode == -1;
                        h.assertTrue(feature(h, key).place(memory.level, generator, random, origin) == allowed,
                            "vegetation " + key.location() + " mode " + mode + " dimension=" + dimension);
                        assertFirstCenter(h, memory, origin);
                        h.assertTrue(random.bounds.size() == (allowed ? (key.equals(TCWorldgen.VEGETATION) ? 111 : 109) : 0),
                            "blacklist gates before tree and patch draws");
                        h.assertTrue(memory.writes.size() == (allowed ? 1 : 0), "only allowed vegetation writes its flower");
                        if (!allowed) {
                            h.assertTrue(memory.biomeQueries.size() == 1 && memory.terrainQueries == 0,
                                "blocked vegetation only samples the biome at y50, never surface or soil");
                        } else {
                            h.assertTrue(memory.biomeQueries.get(memory.biomeQueries.size() - 1).getY() == 70,
                                "allowed cinderpearl uses its distinct surface biome after the center policy gate");
                            h.assertTrue(memory.writes.get(0).state.is(BlocksTC.cinderpearl), "allowed pass keeps old hot-sand flower generation");
                        }
                    }
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void auraChecksOnlyBiomeBlacklistAndDoesNotGenerateWhenBlocked(GameTestHelper h) {
        try (ConfigFixture config = new ConfigFixture()) {
            ModConfig.CONFIG_WORLD.generateAura = true;
            BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
            var chunk = h.getLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            h.assertTrue(chunk != null, "uses an already-loaded test chunk, never creates or loads terrain");
            var cp = chunk.getPos();
            var center = h.getLevel().getUncachedNoiseBiome((cp.x << 2) + 2, 50 >> 2, (cp.z << 2) + 2);
            var biomeKey = BiomeHandler.biomeKey(center);
            h.assertTrue(biomeKey != null, "test chunk center resolves a registry biome key");
            String dimEntry = h.getLevel().dimension().location().toString();
            for (int mode : MODES) {
                // Dimension blacklist does not participate in TC6's actual AuraHandler.generateAura path.
                ModConfig.CONFIG_WORLD.worldgenPolicy = WorldgenPolicy.fromEntries(List.of(dimEntry + "=" + mode), List.of());
                AuraChunk detached = new AuraChunk();
                GaussianRolls random = new GaussianRolls();
                AuraHandler.generateAura(h.getLevel(), chunk, detached, random);
                h.assertTrue(detached.isGenerated() && random.draws == 1, "dimension mode " + mode + " is ignored by aura generation");

                ModConfig.CONFIG_WORLD.worldgenPolicy = WorldgenPolicy.fromEntries(List.of(), List.of(biomeKey + "=" + mode));
                detached = new AuraChunk();
                random = new GaussianRolls();
                AuraHandler.generateAura(h.getLevel(), chunk, detached, random);
                boolean allowed = mode == -1;
                h.assertTrue(detached.isGenerated() == allowed, "biome mode " + mode + " determines whether aura is marked generated");
                h.assertTrue(random.draws == (allowed ? 1 : 0), "blocked biome consumes no Gaussian draw");
                if (!allowed) {
                    h.assertTrue(detached.getBase() == 0 && detached.getVis() == 0 && detached.getFlux() == 0,
                        "blocked biome leaves the fresh aura entirely unset");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void disabledAuraLeavesDetachedDataUnsetWithoutNoise(GameTestHelper h) {
        try (ConfigFixture config = new ConfigFixture()) {
            ModConfig.CONFIG_WORLD.generateAura = false;
            ModConfig.CONFIG_WORLD.worldgenPolicy = WorldgenPolicy.EMPTY;
            BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
            var chunk = h.getLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            h.assertTrue(chunk != null, "existing test chunk is loaded");
            AuraChunk detached = new AuraChunk();
            GaussianRolls random = new GaussianRolls();
            AuraHandler.generateAura(h.getLevel(), chunk, detached, random);
            h.assertFalse(detached.isGenerated(), "generateAura=false never marks data generated");
            h.assertTrue(random.draws == 0 && detached.getBase() == 0 && detached.getVis() == 0 && detached.getFlux() == 0,
                "disabled aura does not consume noise or change detached data");
        }
        h.succeed();
    }

    private static void policy(GameTestHelper h, boolean dimension, int mode) {
        ModConfig.CONFIG_WORLD.worldgenPolicy = WorldgenPolicy.fromEntries(
            dimension ? List.of(h.getLevel().dimension().location() + "=" + mode) : List.of(),
            dimension ? List.of() : List.of(Biomes.PLAINS.location() + "=" + mode));
    }

    private static ConfiguredFeature<?, ?> feature(GameTestHelper h, ResourceKey<ConfiguredFeature<?, ?>> key) {
        return h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(key);
    }

    private static ChunkGenerator generator(GameTestHelper h) {
        var plains = h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        return new NoiseBasedChunkGenerator(new FixedBiomeSource(plains),
            h.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD));
    }

    private static void assertFirstCenter(GameTestHelper h, MemoryLevel memory, BlockPos origin) {
        var expected = new BlockPos((origin.getX() & ~15) + 8, 50, (origin.getZ() & ~15) + 8);
        h.assertTrue(!memory.biomeQueries.isEmpty() && memory.biomeQueries.get(0).equals(expected),
            "policy samples original TC6 chunk center at y50, independently of origin or surface elevation");
    }

    private record Write(BlockPos pos, BlockState state) {
    }

    /** Same virtual terrain pattern as WorldgenPassGameTests, with biome coordinates recorded. */
    private static final class MemoryLevel implements InvocationHandler {
        final GameTestHelper helper;
        final boolean stone;
        final Holder<Biome> center;
        final Holder<Biome> surface;
        final WorldGenLevel level;
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final List<BlockPos> biomeQueries = new ArrayList<>();
        final List<Write> writes = new ArrayList<>();
        int terrainQueries;

        MemoryLevel(GameTestHelper h, boolean stone) {
            helper = h;
            this.stone = stone;
            center = h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
            surface = h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
            level = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, this);
        }

        BlockState state(BlockPos pos) {
            terrainQueries++;
            return blocks.getOrDefault(pos, (stone ? Blocks.STONE : pos.getY() == 69 ? Blocks.SAND : Blocks.AIR).defaultBlockState());
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
                    terrainQueries++;
                    yield 70;
                }
                case "isOutsideBuildHeight" -> {
                    int y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer) args[0];
                    yield y < helper.getLevel().getMinBuildHeight() || y >= helper.getLevel().getMaxBuildHeight();
                }
                case "ensureCanWrite" -> true;
                case "getBiome" -> {
                    BlockPos pos = ((BlockPos) args[0]).immutable();
                    biomeQueries.add(pos);
                    yield pos.getY() == 50 ? center : surface;
                }
                case "getBlockState" -> state((BlockPos) args[0]);
                case "getFluidState" -> state((BlockPos) args[0]).getFluidState();
                case "isEmptyBlock" -> state((BlockPos) args[0]).isAir();
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
                case "toString" -> "In-memory blacklist GameTest terrain";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Blacklist test attempted unexpected real-world access: " + method);
            };
        }
    }

    private static final class Rolls extends LegacyRandomSource {
        final List<Integer> bounds = new ArrayList<>();

        Rolls() {
            super(0);
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return switch (bound) {
                case 13 -> 6;
                case 51 -> 10;
                case 80, 25, 30, 8, 4 -> 0;
                default -> throw new AssertionError("Unexpected random bound " + bound);
            };
        }

        @Override
        public float nextFloat() {
            throw new AssertionError("Skipped tree rolls must not draw a tree probability");
        }
    }

    private static final class GaussianRolls extends LegacyRandomSource {
        int draws;

        GaussianRolls() {
            super(0);
        }

        @Override
        public double nextGaussian() {
            draws++;
            return 0;
        }
    }

    private static final class ConfigFixture implements AutoCloseable {
        final WorldgenPolicy policy = ModConfig.CONFIG_WORLD.worldgenPolicy;
        final int density = ModConfig.CONFIG_WORLD.oreDensity;
        final boolean cinnabar = ModConfig.CONFIG_WORLD.generateCinnabar;
        final boolean quartz = ModConfig.CONFIG_WORLD.generateQuartz;
        final boolean amber = ModConfig.CONFIG_WORLD.generateAmber;
        final boolean crystals = ModConfig.CONFIG_WORLD.generateCrystals;
        final boolean trees = ModConfig.CONFIG_WORLD.generateTrees;
        final boolean aura = ModConfig.CONFIG_WORLD.generateAura;

        @Override
        public void close() {
            ModConfig.CONFIG_WORLD.worldgenPolicy = policy;
            ModConfig.CONFIG_WORLD.oreDensity = density;
            ModConfig.CONFIG_WORLD.generateCinnabar = cinnabar;
            ModConfig.CONFIG_WORLD.generateQuartz = quartz;
            ModConfig.CONFIG_WORLD.generateAmber = amber;
            ModConfig.CONFIG_WORLD.generateCrystals = crystals;
            ModConfig.CONFIG_WORLD.generateTrees = trees;
            ModConfig.CONFIG_WORLD.generateAura = aura;
        }
    }
}
