package thaumcraft.gametest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.world.features.TCGreatwoodTreeFeature;
import thaumcraft.common.world.features.TCSilverwoodTreeFeature;
import thaumcraft.common.world.features.TCTreeSupport;
import thaumcraft.common.world.features.TCVegetationFeature;
import thaumcraft.common.world.features.TCWorldgen;

/** TC6 tree geometry and random traces on virtual terrain, never on real server chunks. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class TreeGameTests {
    private static final BlockPos ORIGIN = new BlockPos(8, 70, 8);

    @GameTest(template = "empty")
    public static void saplingAndNaturalConfiguredTreesHaveDistinctSettings(GameTestHelper h) {
        var great = feature(h, TCWorldgen.GREATWOOD);
        var greatNatural = feature(h, TCWorldgen.GREATWOOD_NATURAL);
        var spider = feature(h, TCWorldgen.GREATWOOD_SPIDER);
        var silver = feature(h, TCWorldgen.SILVERWOOD);
        var silverNatural = feature(h, TCWorldgen.SILVERWOOD_NATURAL);
        h.assertTrue(great.feature() instanceof TCGreatwoodTreeFeature
            && greatNatural.feature() == great.feature() && spider.feature() == great.feature(), "one stateless greatwood feature serves all configs");
        h.assertTrue(great.config().equals(new TCGreatwoodTreeFeature.Configuration(false, false)), "sapling greatwood has neighbor updates and no spider nest");
        h.assertTrue(greatNatural.config().equals(new TCGreatwoodTreeFeature.Configuration(true, false)), "ordinary natural greatwood config");
        h.assertTrue(spider.config().equals(new TCGreatwoodTreeFeature.Configuration(true, true)), "natural spider greatwood config");
        h.assertTrue(silver.feature() instanceof TCSilverwoodTreeFeature && silverNatural.feature() == silver.feature(), "one silverwood feature serves both configs");
        h.assertTrue(silver.config().equals(new TCSilverwoodTreeFeature.Configuration(7, 4, false)), "sapling silverwood: height 7..10, no shimmerleaf");
        h.assertTrue(silverNatural.config().equals(new TCSilverwoodTreeFeature.Configuration(7, 4, true)), "natural silverwood includes shimmerleaf");
        h.assertTrue(feature(h, TCWorldgen.VEGETATION).feature() instanceof TCVegetationFeature,
            "a single vegetation wrapper preserves the tree/flower order");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void silverwoodRetainsHeightRootsAxesAndInitializedLeaves(GameTestHelper h) {
        for (int heightRoll = 0; heightRoll < 4; heightRoll++) {
            MemoryLevel memory = ordinaryTerrain(h, Biomes.FOREST);
            SilverDraws random = new SilverDraws(heightRoll, false);
            h.assertTrue(feature(h, TCWorldgen.SILVERWOOD).place(memory.level, generator(h), random, ORIGIN), "silverwood succeeds on legacy soil");
            random.assertComplete(h);
            h.assertTrue(memory.state(ORIGIN.below()).is(Blocks.DIRT), "onPlantGrow converts only central grass to dirt");
            assertLog(h, memory, ORIGIN, BlocksTC.logSilverwood, Direction.Axis.Y);
            assertLog(h, memory, ORIGIN.above(7 + heightRoll), BlocksTC.logSilverwood, Direction.Axis.Y);
            assertLog(h, memory, ORIGIN.west(2), BlocksTC.logSilverwood, Direction.Axis.X);
            assertLog(h, memory, ORIGIN.east(2), BlocksTC.logSilverwood, Direction.Axis.X);
            assertLog(h, memory, ORIGIN.north(2), BlocksTC.logSilverwood, Direction.Axis.Z);
            assertLog(h, memory, ORIGIN.south(2), BlocksTC.logSilverwood, Direction.Axis.Z);
            h.assertTrue(memory.state(ORIGIN.west(2).below()).is(Blocks.GRASS_BLOCK), "guarded roots never carve solid soil");
            h.assertTrue(memory.blocks.values().stream().noneMatch(state -> state.is(BlocksTC.shimmerleaf)), "sapling config adds no flowers");
            assertLeafDistances(h, memory, BlocksTC.logSilverwood, BlocksTC.leafSilverwood);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void naturalSilverwoodAddsExactlyEighteenShimmerleafAttemptsAfterSuccess(GameTestHelper h) {
        MemoryLevel memory = ordinaryTerrain(h, Biomes.FOREST);
        SilverDraws random = new SilverDraws(0, true);
        h.assertTrue(feature(h, TCWorldgen.SILVERWOOD_NATURAL).place(memory.level, generator(h), random, ORIGIN), "natural silverwood succeeds");
        random.assertComplete(h);
        h.assertTrue(random.flowerIndex == 108, "18 flower attempts consume 6 offset draws each");
        h.assertTrue(memory.state(ORIGIN.offset(3, 0, 3)).is(BlocksTC.shimmerleaf), "natural shimmerleaf appears on free grass outside the roots");
        h.assertTrue(memory.blocks.values().stream().filter(state -> state.is(BlocksTC.shimmerleaf)).count() == 1,
            "18 identical candidate offsets only place one shimmerleaf");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void silverwoodRejectsObstaclesSoilAndStrictUpperBoundaryBeforeWrites(GameTestHelper h) {
        for (int failure = 0; failure < 3; failure++) {
            MemoryLevel memory = ordinaryTerrain(h, Biomes.FOREST);
            BlockPos origin = ORIGIN;
            if (failure == 0) memory.blocks.put(ORIGIN.above(), Blocks.STONE.defaultBlockState());
            if (failure == 1) memory.blocks.put(ORIGIN.below(), Blocks.STONE.defaultBlockState());
            if (failure == 2) {
                origin = new BlockPos(8, h.getLevel().getMaxBuildHeight() - 8, 8); // initial <= passes, strict second '<' fails
                memory.blocks.put(origin.below(), Blocks.GRASS_BLOCK.defaultBlockState());
            }
            HeightOnly random = new HeightOnly();
            h.assertFalse(feature(h, TCWorldgen.SILVERWOOD_NATURAL).place(memory.level, generator(h), random, origin), "invalid silverwood site " + failure);
            h.assertTrue(random.draws == 1 && memory.writes.isEmpty(), "failure consumes only height, places neither tree nor shimmerleaf");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void silverwoodToleratesOccupiedOriginButNeverOverwritesIt(GameTestHelper h) {
        MemoryLevel memory = ordinaryTerrain(h, Biomes.FOREST);
        memory.blocks.put(ORIGIN, Blocks.STONE.defaultBlockState());
        memory.blocks.put(ORIGIN.west(2), Blocks.STONE.defaultBlockState());
        SilverDraws random = new SilverDraws(0, false);
        h.assertTrue(feature(h, TCWorldgen.SILVERWOOD).place(memory.level, generator(h), random, ORIGIN),
            "TC6 clearance ignores occupied origin and radial base roots");
        random.assertComplete(h);
        h.assertTrue(memory.state(ORIGIN).is(Blocks.STONE) && memory.state(ORIGIN.west(2)).is(Blocks.STONE), "write guards preserve both solid cells");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void leafDistanceInitializationUsesExistingCrownsAndBoundaryLogs(GameTestHelper h) {
        MemoryLevel memory = ordinaryTerrain(h, Biomes.FOREST);
        BlockState fresh = BlocksTC.leafSilverwood.defaultBlockState().setValue(LeavesBlock.DISTANCE, 7);
        BlockPos oldLeaf = ORIGIN;
        BlockPos a = ORIGIN.east();
        BlockPos b = ORIGIN.east(2);
        BlockPos c = ORIGIN.east(3);
        BlockPos existingLog = ORIGIN.east(4);
        BlockPos isolatedSupported = ORIGIN.south(4);
        memory.blocks.put(oldLeaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 2));
        memory.blocks.put(a, fresh);
        memory.blocks.put(b, fresh);
        memory.blocks.put(c, fresh);
        memory.blocks.put(existingLog, Blocks.OAK_LOG.defaultBlockState());
        memory.blocks.put(isolatedSupported, fresh);
        memory.blocks.put(isolatedSupported.west(), Blocks.OAK_LOG.defaultBlockState());
        TCTreeSupport.updateLeafDistances(memory.level, Set.of(), Set.of(a, b, c, isolatedSupported), Block.UPDATE_CLIENTS);
        h.assertTrue(memory.state(a).getValue(LeavesBlock.DISTANCE) == 3, "old crown distance2 supports new boundary leaf at distance3");
        h.assertTrue(memory.state(b).getValue(LeavesBlock.DISTANCE) == 2 && memory.state(c).getValue(LeavesBlock.DISTANCE) == 1,
            "relaxation retains the shorter route from an existing log outside this generation's log set");
        h.assertTrue(memory.state(isolatedSupported).getValue(LeavesBlock.DISTANCE) == 1, "isolated new leaf sees preexisting adjacent log");
        h.assertTrue(memory.state(oldLeaf).getValue(LeavesBlock.DISTANCE) == 2, "old leaf itself is not rewritten");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void greatwoodUsesSingleSharedSeedAndFreshTwoCrownGenerator(GameTestHelper h) {
        MemoryLevel first = ordinaryTerrain(h, Biomes.FOREST);
        MemoryLevel second = ordinaryTerrain(h, Biomes.FOREST);
        for (MemoryLevel memory : List.of(first, second)) {
            GreatDraws random = new GreatDraws(false);
            h.assertTrue(feature(h, TCWorldgen.GREATWOOD_NATURAL).place(memory.level, generator(h), random, ORIGIN), "natural greatwood succeeds");
            h.assertTrue(random.seedDraws == 1 && random.webIndex == 0, "non-spider tree consumes only one nextLong from shared RNG");
            for (int x = 0; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) assertLog(h, memory, ORIGIN.offset(x, 0, z), BlocksTC.logGreatwood, Direction.Axis.Y);
            }
            int highestLog = memory.blocks.entrySet().stream().filter(entry -> entry.getValue().is(BlocksTC.logGreatwood))
                .mapToInt(entry -> entry.getKey().getY()).max().orElseThrow();
            h.assertTrue(highestLog >= ORIGIN.getY() + 12, "second trunk begins at the first crown's trunk top, not a single vanilla tree");
            h.assertTrue(memory.blocks.values().stream().anyMatch(state -> state.is(BlocksTC.logGreatwood)
                    && state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y), "greatwood branches carry horizontal axes");
            assertLeafDistances(h, memory, BlocksTC.logGreatwood, BlocksTC.leafGreatwood);
        }
        h.assertTrue(first.blocks.equals(second.blocks), "configured feature is stateless: identical seed and terrain yield identical fresh geometry");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void greatwoodSpiderNestUsesFiftyWebAttemptsAndSeededDungeonChest(GameTestHelper h) {
        MemoryLevel memory = ordinaryTerrain(h, Biomes.FOREST);
        GreatDraws random = new GreatDraws(true);
        h.assertTrue(feature(h, TCWorldgen.GREATWOOD_SPIDER).place(memory.level, generator(h), random, ORIGIN), "spider greatwood succeeds");
        h.assertTrue(random.seedDraws == 1 && random.webIndex == 150, "one seed plus fifty X/Y/Z web attempts, not shared crown RNG");
        h.assertTrue(memory.state(ORIGIN.below()).is(Blocks.SPAWNER), "spawner sits directly under the trunk");
        h.assertTrue(memory.state(ORIGIN.below(2)).is(Blocks.CHEST), "chest sits two blocks under the trunk");
        SpawnerBlockEntity spawner = (SpawnerBlockEntity) memory.entities.get(ORIGIN.below());
        var tag = spawner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(tag.getCompound("SpawnData").getCompound("entity").getString("id").equals("minecraft:cave_spider"), "nest spawns cave spiders");
        ChestBlockEntity chest = (ChestBlockEntity) memory.entities.get(ORIGIN.below(2));
        h.assertTrue(BuiltInLootTables.SIMPLE_DUNGEON.equals(chest.getLootTable()), "TC6 simple-dungeon loot table retained");
        h.assertTrue(chest.getLootTableSeed() != 0, "chest receives the internal tree RNG's loot seed");
        h.assertTrue(memory.state(ORIGIN.offset(-1, 1, 0)).is(Blocks.COBWEB), "air adjacent to the trunk accepts a web");
        h.assertTrue(memory.blocks.values().stream().filter(state -> state.is(Blocks.COBWEB)).count() == 1, "fifty identical web candidates place only once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vegetationPreservesSkippedTreeRollsBeforeCinderpearlAndRejectsFlatOrDisabled(GameTestHelper h) {
        boolean trees = ModConfig.CONFIG_WORLD.generateTrees;
        try {
            Holder<Biome> desert = biome(h, Biomes.DESERT);
            ChunkGenerator noise = new NoiseBasedChunkGenerator(new FixedBiomeSource(desert),
                h.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD));
            ChunkGenerator flat = new FlatLevelSource(new FlatLevelGeneratorSettings(Optional.empty(), desert, List.of()));
            ConfiguredFeature<?, ?> vegetation = feature(h, TCWorldgen.VEGETATION);
            MemoryLevel disabled = sandTerrain(h);
            ModConfig.CONFIG_WORLD.generateTrees = false;
            h.assertFalse(vegetation.place(disabled.level, noise, new NoDraws(), ORIGIN), "config gate precedes vegetation RNG/terrain");
            h.assertTrue(disabled.terrainQueries == 0 && disabled.writes.isEmpty(), "disabled trees touch no virtual terrain");
            ModConfig.CONFIG_WORLD.generateTrees = true;
            MemoryLevel flatTerrain = sandTerrain(h);
            h.assertFalse(vegetation.place(flatTerrain.level, flat, new NoDraws(), ORIGIN), "flat vegetation touches no RNG");
            h.assertTrue(flatTerrain.terrainQueries == 0 && flatTerrain.writes.isEmpty(), "flat vegetation touches no terrain");
            for (boolean success : new boolean[]{false, true}) {
                MemoryLevel memory = sandTerrain(h);
                VegetationDraws random = new VegetationDraws(success);
                h.assertTrue(vegetation.place(memory.level, noise, random, ORIGIN) == success, "skipped trees then cinderpearl outcome");
                h.assertTrue(random.index == (success ? 111 : 3), "exact trace 80 -> 25 -> 30 then 18 six-draw flower attempts");
                h.assertTrue(memory.state(ORIGIN).is(success ? BlocksTC.cinderpearl : Blocks.AIR), "flower remains at chunk center");
            }
        } finally {
            ModConfig.CONFIG_WORLD.generateTrees = trees;
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void forcedVegetationTreeAttemptsPreserveConfiguredCallsRollOrderAndLiveHeightQueries(GameTestHelper h) {
        boolean trees = ModConfig.CONFIG_WORLD.generateTrees;
        try {
            ModConfig.CONFIG_WORLD.generateTrees = true;
            Holder<Biome> forest = biome(h, Biomes.FOREST);
            ChunkGenerator noise = new NoiseBasedChunkGenerator(new FixedBiomeSource(forest),
                h.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD));
            MemoryLevel attempts = new MemoryLevel(h, Biomes.FOREST,
                pos -> (pos.getY() == 69 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            TypedTrace trace = new TypedTrace()
                .integer(80, 3).integer(9, 0).integer(9, 8).floating(0).integer(4, 2)
                .integer(25, 7).integer(9, 8).integer(9, 0).floating(0).integer(8, 1).longValue(123456789L);
            h.assertFalse(feature(h, TCWorldgen.VEGETATION).place(attempts.level, noise, trace, ORIGIN),
                "forest admits both tree rolls, but silverwood and greatwood reject stone soil");
            trace.assertComplete(h);
            h.assertTrue(attempts.writes.isEmpty(), "both failed configured trees leave terrain unchanged");
            h.assertTrue(attempts.heights.equals(List.of(
                new HeightQuery(Heightmap.Types.MOTION_BLOCKING, 4, 12),
                new HeightQuery(Heightmap.Types.MOTION_BLOCKING, 12, 4),
                new HeightQuery(Heightmap.Types.WORLD_SURFACE, 8, 8))),
                "tree offsets are drawn X then Z and cinderpearl queries live surface at the chunk center");

            MemoryLevel unsupportedRoll = new MemoryLevel(h, Biomes.PLAINS,
                pos -> (pos.getY() == 69 ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState());
            TypedTrace rejected = new TypedTrace()
                .integer(80, 3).integer(9, 4).integer(9, 4).floating(0.99F)
                .integer(25, 7).integer(9, 4).integer(9, 4).floating(0.99F);
            h.assertFalse(feature(h, TCWorldgen.VEGETATION).place(unsupportedRoll.level, noise, rejected, ORIGIN),
                "plains support probabilities reject both high floats before tree height, spider chance, or seed draws");
            rejected.assertComplete(h);
            h.assertTrue(unsupportedRoll.writes.isEmpty(), "biome probability rejection does not invoke a tree placement");
        } finally {
            ModConfig.CONFIG_WORLD.generateTrees = trees;
        }
        h.succeed();
    }

    private static void assertLog(GameTestHelper h, MemoryLevel memory, BlockPos pos, Block log, Direction.Axis axis) {
        BlockState state = memory.state(pos);
        h.assertTrue(state.is(log) && state.getValue(RotatedPillarBlock.AXIS) == axis, "log/axis at " + pos + ": " + state);
    }

    private static void assertLeafDistances(GameTestHelper h, MemoryLevel memory, Block log, Block leaf) {
        Map<BlockPos, Integer> distances = new HashMap<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        memory.blocks.forEach((pos, state) -> {
            if (state.is(log)) { distances.put(pos, 0); pending.add(pos); }
        });
        while (!pending.isEmpty()) {
            BlockPos pos = pending.remove();
            int distance = distances.get(pos) + 1;
            if (distance >= 7) continue;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (!distances.containsKey(neighbor) && memory.state(neighbor).is(leaf)) {
                    distances.put(neighbor, distance);
                    pending.add(neighbor);
                }
            }
        }
        int count = 0;
        boolean connected = false;
        for (var entry : memory.blocks.entrySet()) {
            if (!entry.getValue().is(leaf)) continue;
            count++;
            int expected = distances.getOrDefault(entry.getKey(), 7);
            h.assertTrue(entry.getValue().getValue(LeavesBlock.DISTANCE) == expected, "leaf initialized with its actual log-path distance at " + entry.getKey());
            h.assertFalse(entry.getValue().getValue(LeavesBlock.PERSISTENT), "generated leaves are natural, not persistent player leaves");
            connected |= expected < 7;
        }
        h.assertTrue(count > 0 && connected, "tree generated foliage with connected initialized distances");
    }

    private static MemoryLevel ordinaryTerrain(GameTestHelper h, ResourceKey<Biome> biome) {
        return new MemoryLevel(h, biome, pos -> (pos.getY() == 69 ? Blocks.GRASS_BLOCK : pos.getY() < 69 ? Blocks.DIRT : Blocks.AIR).defaultBlockState());
    }

    private static MemoryLevel sandTerrain(GameTestHelper h) {
        return new MemoryLevel(h, Biomes.DESERT, pos -> (pos.getY() == 69 ? Blocks.SAND : Blocks.AIR).defaultBlockState());
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

    private record Write(BlockPos pos, BlockState state, int flags) { }
    private record HeightQuery(Heightmap.Types type, int x, int z) { }

    /** No method falls through to actual terrain. Only read-only server metadata is shared. */
    private static final class MemoryLevel implements InvocationHandler {
        final GameTestHelper helper;
        final Holder<Biome> biome;
        final Function<BlockPos, BlockState> fallback;
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final Map<BlockPos, BlockEntity> entities = new HashMap<>();
        final List<Write> writes = new ArrayList<>();
        final List<HeightQuery> heights = new ArrayList<>();
        final WorldGenLevel level;
        int terrainQueries;

        MemoryLevel(GameTestHelper helper, ResourceKey<Biome> key, Function<BlockPos, BlockState> fallback) {
            this.helper = helper;
            this.biome = biome(helper, key);
            this.fallback = fallback;
            level = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, this);
        }

        BlockState state(BlockPos pos) {
            terrainQueries++;
            return blocks.getOrDefault(pos, fallback.apply(pos));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getLevel" -> helper.getLevel();
                case "registryAccess" -> helper.getLevel().registryAccess();
                case "getMinBuildHeight" -> helper.getLevel().getMinBuildHeight();
                case "getMaxBuildHeight" -> helper.getLevel().getMaxBuildHeight();
                case "getHeight" -> {
                    if (args == null || args.length == 0) yield helper.getLevel().getHeight();
                    if (args.length != 3 || !(args[0] instanceof Heightmap.Types)) throw new AssertionError("Unexpected height query: " + method);
                    terrainQueries++;
                    heights.add(new HeightQuery((Heightmap.Types) args[0], (Integer) args[1], (Integer) args[2]));
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
                case "isStateAtPosition" -> ((Predicate<BlockState>) args[1]).test(state((BlockPos) args[0]));
                case "getBiome" -> { terrainQueries++; yield biome; }
                case "getBlockEntity" -> entities.get((BlockPos) args[0]);
                case "setBlock" -> {
                    BlockPos pos = ((BlockPos) args[0]).immutable();
                    BlockState state = (BlockState) args[1];
                    if (state(pos).equals(state)) yield false;
                    blocks.put(pos, state);
                    writes.add(new Write(pos, state, (Integer) args[2]));
                    if (state.is(Blocks.SPAWNER)) entities.put(pos, new SpawnerBlockEntity(pos, state));
                    else if (state.is(Blocks.CHEST)) entities.put(pos, new ChestBlockEntity(pos, state));
                    else entities.remove(pos);
                    yield true;
                }
                case "toString" -> "Virtual tree GameTest WorldGenLevel";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Tree attempted unexpected world access, cannot use real terrain: " + method);
            };
        }
    }

    private static class NoDraws extends LegacyRandomSource {
        NoDraws() { super(0); }
        @Override public int next(int bits) { throw new AssertionError("Unexpected non-scripted tree RNG bits: " + bits); }
        @Override public int nextInt(int bound) { throw new AssertionError("Unexpected non-scripted tree RNG bound: " + bound); }
        @Override public long nextLong() { throw new AssertionError("Unexpected non-scripted tree RNG long"); }
        @Override public float nextFloat() { throw new AssertionError("Unexpected non-scripted tree RNG float"); }
    }

    private static final class HeightOnly extends NoDraws {
        int draws;
        @Override public int nextInt(int bound) {
            if (++draws != 1 || bound != 4) throw new AssertionError("Rejected silverwood must only draw nextInt(4) height");
            return 0;
        }
    }

    /** Crown/end/corner trace exactly follows TC6. Flower offsets repeat (+3,0,+3). */
    private static final class SilverDraws extends NoDraws {
        final int heightRoll;
        final boolean natural;
        int index;
        int crownDraws;
        int threes;
        int flowerIndex;
        SilverDraws(int heightRoll, boolean natural) { this.heightRoll = heightRoll; this.natural = natural; }

        @Override public int nextInt(int bound) {
            if (index++ == 0) {
                if (bound != 4) throw new AssertionError("Silverwood first draw must be nextInt(4)");
                return heightRoll;
            }
            if (threes == 9) {
                if (!natural || flowerIndex >= 108) throw new AssertionError("Unexpected extra flower/crown draw");
                int[] expected = {8, 8, 4, 4, 8, 8};
                int[] values = {3, 0, 0, 0, 3, 0};
                if (bound != expected[flowerIndex % 6]) throw new AssertionError("Flower offset draw order mismatch");
                return values[flowerIndex++ % 6];
            }
            if (bound == 3) {
                if (threes == 0 && crownDraws != 0 || threes > 0 && crownDraws != 9 * 121) throw new AssertionError("Crown/end/corner RNG order mismatch");
                threes++;
                return 0;
            }
            if (bound != 8 || threes != 1 || ++crownDraws > 9 * 121) throw new AssertionError("Unexpected silverwood crown RNG trace");
            return 0;
        }

        void assertComplete(GameTestHelper h) {
            h.assertTrue(crownDraws == 1089 && threes == 9 && flowerIndex == (natural ? 108 : 0)
                && index == (natural ? 1207 : 1099), "silverwood consumes every crown cell then eight corners and optional 108 flower draws");
        }
    }

    private static final class GreatDraws extends NoDraws {
        final boolean spiders;
        int seedDraws;
        int webIndex;
        GreatDraws(boolean spiders) { this.spiders = spiders; }
        @Override public long nextLong() {
            if (++seedDraws != 1) throw new AssertionError("Greatwood crown/loot must use its own java.util.Random");
            return 123456789L;
        }
        @Override public int nextInt(int bound) {
            if (!spiders || seedDraws != 1 || webIndex >= 150) throw new AssertionError("Unexpected greatwood shared RNG bounded draw");
            int[] expected = {14, 10, 14};
            int[] values = {6, 1, 7};
            if (bound != expected[webIndex % 3]) throw new AssertionError("Spider web draws must be X14,Y10,Z14");
            return values[webIndex++ % 3];
        }
    }

    private static final class VegetationDraws extends NoDraws {
        final boolean success;
        int index;
        VegetationDraws(boolean success) { this.success = success; }
        @Override public int nextInt(int bound) {
            if (index < 3) {
                int[] expected = {80, 25, 30};
                if (bound != expected[index]) throw new AssertionError("Vegetation order must be silverwood80,greatwood25,cinderpearl30");
                return index++ == 2 && !success ? 1 : 0;
            }
            if (!success || index >= 111) throw new AssertionError("Unexpected vegetation draw after rejected/completed flower patch");
            int[] expected = {8, 8, 4, 4, 8, 8};
            if (bound != expected[(index++ - 3) % 6]) throw new AssertionError("Vegetation flower RNG mismatch");
            return 0;
        }
    }

    private record TraceDraw(char type, int bound, Number value) { }

    private static final class TypedTrace extends NoDraws {
        final List<TraceDraw> draws = new ArrayList<>();
        int index;
        TypedTrace integer(int bound, int value) { draws.add(new TraceDraw('i', bound, value)); return this; }
        TypedTrace floating(float value) { draws.add(new TraceDraw('f', 0, value)); return this; }
        TypedTrace longValue(long value) { draws.add(new TraceDraw('l', 0, value)); return this; }
        private Number draw(char type, int bound) {
            if (index >= draws.size()) throw new AssertionError("Unexpected extra vegetation RNG draw " + type + "/" + bound);
            TraceDraw expected = draws.get(index++);
            if (expected.type != type || expected.bound != bound) throw new AssertionError("Vegetation typed draw order mismatch at " + (index - 1) + ": " + expected + " vs " + type + "/" + bound);
            return expected.value;
        }
        @Override public int nextInt(int bound) { return draw('i', bound).intValue(); }
        @Override public float nextFloat() { return draw('f', 0).floatValue(); }
        @Override public long nextLong() { return draw('l', 0).longValue(); }
        void assertComplete(GameTestHelper h) { h.assertTrue(index == draws.size(), "all " + draws.size() + " typed vegetation draws consumed"); }
    }
}
