package thaumcraft.gametest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.world.features.TCBigMagicTreeFeature;
import thaumcraft.common.world.features.TCLegacyBiomeDecoration;
import thaumcraft.common.world.features.TCMagicalForestFeature;

/** Biome-owned TC6 decoration on virtual terrain; no actual server blocks/chunks are touched. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class BiomeDecorationGameTests {
    private static final BlockPos ORIGIN = new BlockPos(8, 70, 8);

    @GameTest(template = "empty")
    public static void magicalOakRetainsFreshHeightWideCrownAxesAndOneSharedSeed(GameTestHelper h) {
        long seed = 123456789L;
        MemoryLevel first = terrain(h, Blocks.GRASS_BLOCK);
        MemoryLevel second = terrain(h, Blocks.GRASS_BLOCK);
        for (MemoryLevel memory : List.of(first, second)) {
            SeedOnly random = new SeedOnly(seed);
            h.assertTrue(TCBigMagicTreeFeature.placeTree(memory.level, random, ORIGIN), "TC magical oak succeeds on grass");
            h.assertTrue(random.draws == 1, "only one nextLong from shared world RNG");
            int heightLimit = 11 + new Random(seed).nextInt(11);
            int highestLeaf = memory.blocks.entrySet().stream().filter(entry -> entry.getValue().is(Blocks.OAK_LEAVES))
                .mapToInt(entry -> entry.getKey().getY()).max().orElseThrow();
            h.assertTrue(highestLeaf == ORIGIN.getY() + heightLimit - 1, "crown retains height11+nextInt11 and four leaf layers");
            h.assertTrue(memory.state(ORIGIN).is(Blocks.OAK_LOG), "single oak trunk, not TC greatwood");
            h.assertTrue(memory.state(ORIGIN.below()).is(Blocks.GRASS_BLOCK), "original magic tree does not call onPlantGrow");
            h.assertTrue(memory.blocks.values().stream().anyMatch(state -> state.is(Blocks.OAK_LOG)
                && state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y), "angled oak branches retain horizontal axes");
            h.assertTrue(memory.blocks.values().stream().filter(state -> state.is(Blocks.OAK_LEAVES))
                .allMatch(state -> !state.getValue(LeavesBlock.PERSISTENT)), "natural oak leaves are not persistent");
            h.assertTrue(memory.blocks.values().stream().anyMatch(state -> state.is(Blocks.OAK_LEAVES)
                && state.getValue(LeavesBlock.DISTANCE) < 7), "modern leaf support distances initialized");
            h.assertTrue(!memory.blocks.containsKey(BlockPos.ZERO), "degenerate branch must not write through NaN at world origin");
        }
        h.assertTrue(first.blocks.equals(second.blocks), "no shared mutable height or random state leaks between placements");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void magicalOakRejectsEarlyObstacleButTruncatesAtSix(GameTestHelper h) {
        MemoryLevel badSoil = terrain(h, Blocks.STONE);
        h.assertFalse(TCBigMagicTreeFeature.placeTree(badSoil.level, new SeedOnly(123456789L), ORIGIN), "bad soil rejected");
        h.assertTrue(badSoil.writes.isEmpty(), "bad soil makes no writes");
        MemoryLevel early = terrain(h, Blocks.GRASS_BLOCK);
        early.blocks.put(ORIGIN.above(5), Blocks.STONE.defaultBlockState());
        h.assertFalse(TCBigMagicTreeFeature.placeTree(early.level, new SeedOnly(123456789L), ORIGIN), "obstacle before six rejects entire tree");
        h.assertTrue(early.writes.isEmpty(), "early obstruction makes no writes");
        MemoryLevel truncated = terrain(h, Blocks.GRASS_BLOCK);
        truncated.blocks.put(ORIGIN.above(6), Blocks.STONE.defaultBlockState());
        h.assertTrue(TCBigMagicTreeFeature.placeTree(truncated.level, new SeedOnly(123456789L), ORIGIN), "obstacle at six shortens tree instead of rejection");
        h.assertTrue(truncated.state(ORIGIN.above(6)).is(Blocks.STONE), "obstructing cell remains untouched");
        h.assertTrue(truncated.writes.stream().allMatch(write -> write.pos().getY() < ORIGIN.getY() + 6), "shortened tree does not cross its new upper limit");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void forestOakRetainsFailedHeightAndTruncationWithoutAnotherHeightDraw(GameTestHelper h) {
        TCBigMagicTreeFeature.LegacyState state = new TCBigMagicTreeFeature.LegacyState();
        long firstSeed = 123456789L;
        MemoryLevel badSoil = terrain(h, Blocks.STONE);
        h.assertFalse(state.placeTree(badSoil.level, new SeedOnly(firstSeed), ORIGIN), "first invalid soil attempt fails");
        h.assertTrue(state.heightLimit() == 11 + new Random(firstSeed).nextInt(11), "first height is initialized even on failed soil");
        MemoryLevel obstructed = terrain(h, Blocks.GRASS_BLOCK);
        obstructed.blocks.put(ORIGIN.above(6), Blocks.STONE.defaultBlockState());
        h.assertTrue(state.placeTree(obstructed.level, new SeedOnly(1L), ORIGIN), "later valid attempt truncates retained height to six");
        h.assertTrue(state.heightLimit() == 6, "truncation persists in biome-owned state");

        // At height6 there is exactly one candidate besides the initial node. Its two floats are
        // the first values of the new java Random: no nextInt(11) may precede them anymore.
        long nextSeed = 0L;
        BlockPos expectedNode;
        while (true) {
            expectedNode = sixHighCandidate(new Random(nextSeed));
            Random wronglyRerolled = new Random(nextSeed);
            wronglyRerolled.nextInt(11);
            if (!expectedNode.equals(sixHighCandidate(wronglyRerolled))) break;
            nextSeed++;
        }
        MemoryLevel clear = terrain(h, Blocks.GRASS_BLOCK);
        h.assertTrue(state.placeTree(clear.level, new SeedOnly(nextSeed), ORIGIN), "unobstructed placement still uses retained six");
        h.assertTrue(state.heightLimit() == 6, "clear terrain never restores untruncated height");
        Set<BlockPos> expectedLeaves = new HashSet<>();
        expectedLeaves.add(expectedNode);
        expectedLeaves.add(expectedNode.north());
        expectedLeaves.add(expectedNode.south());
        expectedLeaves.add(expectedNode.east());
        expectedLeaves.add(expectedNode.west());
        expectedLeaves.remove(ORIGIN.above()); // The vertical trunk overwrites this possible leaf.
        Set<BlockPos> actualLeaves = new HashSet<>();
        clear.blocks.forEach((pos, block) -> {
            if (pos.getY() == ORIGIN.getY() + 1 && block.is(Blocks.OAK_LEAVES)) actualLeaves.add(pos);
        });
        h.assertTrue(actualLeaves.equals(expectedLeaves), "candidate's lower cross proves internal RNG skipped the first-placement height draw");
        h.succeed();
    }

    private static BlockPos sixHighCandidate(Random random) {
        float halfHeight = 3.0F;
        float offset = halfHeight - 2;
        float radius = (float)Math.sqrt(halfHeight * halfHeight - offset * offset) * 0.5F;
        double distance = 1.25 * radius * (random.nextFloat() + 0.328);
        double angle = random.nextFloat() * 2.0F * Math.PI;
        return BlockPos.containing(ORIGIN.getX() + distance * Math.sin(angle) + 0.5,
            ORIGIN.getY() + 1, ORIGIN.getZ() + distance * Math.cos(angle) + 0.5);
    }

    @GameTest(template = "empty")
    public static void originalGiantMushroomsRetainHeightRoundedCapsAndTextureFaces(GameTestHelper h) {
        for (boolean brown : new boolean[]{true, false}) {
            MemoryLevel memory = terrain(h, Blocks.GRASS_BLOCK);
            MushroomDraws random = new MushroomDraws(brown);
            h.assertTrue(TCLegacyBiomeDecoration.giantMushroom(memory.level, random, ORIGIN), "original giant mushroom succeeds");
            h.assertTrue(random.draws == 3, "type, height, rare-double-height draws only");
            Block cap = brown ? Blocks.BROWN_MUSHROOM_BLOCK : Blocks.RED_MUSHROOM_BLOCK;
            long caps = memory.blocks.values().stream().filter(state -> state.is(cap)).count();
            long stems = memory.blocks.values().stream().filter(state -> state.is(Blocks.MUSHROOM_STEM)).count();
            h.assertTrue(caps == 45 && stems == 4, "height4 shape has45 capcells and4 stemcells for either type");
            h.assertTrue(memory.state(ORIGIN.above(4)).is(cap), "cap top at chosen height");
            h.assertTrue(!memory.state(ORIGIN.above(4)).getValue(HugeMushroomBlock.DOWN), "metadata underside maps to inside face");
            if (brown) {
                BlockState edge = memory.state(ORIGIN.offset(-2, 4, -3));
                h.assertTrue(edge.getValue(HugeMushroomBlock.NORTH) && edge.getValue(HugeMushroomBlock.WEST)
                    && !edge.getValue(HugeMushroomBlock.SOUTH) && !edge.getValue(HugeMushroomBlock.EAST), "rounded northwest cap edge carries both exposed directions");
                h.assertTrue(memory.state(ORIGIN.offset(-3, 4, -3)).isAir(), "brown cap skips bounding-square corners");
            } else {
                h.assertTrue(memory.state(ORIGIN.offset(1, 4, 1)).is(cap), "red top square retains its corners");
                h.assertTrue(memory.state(ORIGIN.offset(1, 1, 0)).isAir(), "red lower cap layers are hollow");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void giantMushroomRejectsOccupiedClearanceBeforeWrites(GameTestHelper h) {
        MemoryLevel memory = terrain(h, Blocks.GRASS_BLOCK);
        memory.blocks.put(ORIGIN.above(), Blocks.STONE.defaultBlockState());
        MushroomDraws random = new MushroomDraws(true);
        h.assertFalse(TCLegacyBiomeDecoration.giantMushroom(memory.level, random, ORIGIN), "occupied stem line rejects mushroom");
        h.assertTrue(random.draws == 3 && memory.writes.isEmpty(), "rejection consumes type/height/double chance only");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mossyBlobRetainsThreePartsAndEighteenSizeAndDriftDraws(GameTestHelper h) {
        MemoryLevel memory = terrain(h, Blocks.GRASS_BLOCK);
        RepeatedInt random = new RepeatedInt(2, 0);
        h.assertTrue(TCLegacyBiomeDecoration.mossyBlob(memory.level, random, ORIGIN), "blob finds grass support");
        h.assertTrue(random.draws == 18 && memory.writes.size() == 3, "three tiny parts, six bound2 draws each");
        for (int part = 0; part < 3; part++) {
            h.assertTrue(memory.state(ORIGIN.offset(-part, 0, -part)).is(Blocks.MOSSY_COBBLESTONE), "original negative X/Z drift retained");
        }
        h.assertTrue(memory.writes.stream().allMatch(write -> write.flags() == Block.UPDATE_INVISIBLE), "legacy blob flags4 retained");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ambientGrassMakesAtMostThreeSearchesAndStopsAfterFirstGrass(GameTestHelper h) {
        MemoryLevel accepted = terrain(h, Blocks.GRASS_BLOCK);
        RepeatedInt first = new RepeatedInt(8, 0);
        h.assertTrue(TCMagicalForestFeature.placeAmbientGrass(accepted.level, first, BlockPos.ZERO), "first grass column succeeds");
        h.assertTrue(first.draws == 2 && accepted.writes.size() == 1, "successful first attempt skips remaining two searches");
        h.assertTrue(accepted.state(new BlockPos(4, 69, 4)).is(BlocksTC.grassAmbient), "center offsets start4 and find terrain's grass below surface");
        MemoryLevel denied = terrain(h, Blocks.STONE);
        RepeatedInt all = new RepeatedInt(8, 0);
        h.assertFalse(TCMagicalForestFeature.placeAmbientGrass(denied.level, all, BlockPos.ZERO), "no grass beforeY30 is rejected");
        h.assertTrue(all.draws == 6 && denied.writes.isEmpty(), "three failed searches consume six X/Z draws");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vishroomsRequireGrassReplaceableSpaceAndAnyOfTwentySixLogNeighbors(GameTestHelper h) {
        for (int condition = 0; condition < 3; condition++) {
            MemoryLevel memory = terrain(h, Blocks.GRASS_BLOCK);
            BlockPos target = new BlockPos(0, 70, 0);
            memory.blocks.put(condition == 1 ? target.east(2) : target.offset(1, 1, 1), Blocks.OAK_LOG.defaultBlockState());
            if (condition == 2) memory.blocks.put(target, Blocks.STONE.defaultBlockState());
            RepeatedInt random = new RepeatedInt(16, 0);
            boolean placed = TCMagicalForestFeature.placeVishrooms(memory.level, random, BlockPos.ZERO);
            h.assertTrue(placed == (condition == 0), "diagonal log qualifies, out-of-range log or occupied spot does not");
            h.assertTrue(random.draws == 16, "always eight X/Z attempts, even repeated candidates");
            h.assertTrue(memory.state(target).is(BlocksTC.vishroom) == (condition == 0), "vishroom placement condition " + condition);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wholeForestPassRetainsOriginalCountsAndSharedRandomOrder(GameTestHelper h) {
        MemoryLevel memory = terrain(h, Blocks.STONE);
        Tape random = new Tape();
        random.ints(8, 6); // Three failed ambient-grass attempts, X/Z.
        random.integer(3, 0); // No blobs.
        for (int cell = 0; cell < 16; cell++) random.integer(40, 1); // No giant mushrooms.
        random.floating(0.9F); // Two trees; no extra third tree.
        for (int tree = 0; tree < 2; tree++) {
            random.ints(16, 2);
            random.integer(18, 1);
            random.integer(12, 1);
            random.seed(123456789L); // Two magical oaks fail soil, own java Random is isolated.
        }
        for (int patch = 0; patch < 10; patch++) {
            random.ints(16, 2);
            random.integer(102, 0);
            random.triangular(64);
        }
        for (int patch = 0; patch < 12; patch++) {
            random.ints(16, 2);
            random.integer(140, 0);
            random.integer(4, 1); // Grass rather than fern.
            random.triangular(128);
        }
        for (int patch = 0; patch < 6; patch++) {
            random.ints(16, 2);
            random.integer(140, 0);
            random.triangular(10);
        }
        for (int patch = 0; patch < 6; patch++) {
            random.integer(4, 1);
            random.integer(8, 1);
        }
        random.integer(4, 1);
        random.integer(8, 1);
        for (int patch = 0; patch < 10; patch++) {
            random.ints(16, 2);
            random.integer(140, 0);
            random.ints(4, 80); // Twenty reed candidates consume four offsets each.
        }
        random.integer(32, 1); // No pumpkin patch.
        random.ints(16, 16); // Eight vishroom X/Z attempts.
        int expected = random.entries.size();
        var context = new FeaturePlaceContext<>(Optional.empty(), memory.level,
            h.getLevel().getChunkSource().getGenerator(), random, ORIGIN, NoneFeatureConfiguration.INSTANCE);
        h.assertFalse(new TCMagicalForestFeature().place(context), "unsuitable stone terrain makes no decoration writes");
        h.assertTrue(random.index == expected, "entire scripted forest trace consumed without omitted/extra stages");
        h.assertTrue(memory.writes.isEmpty(), "scripted pass only tests virtual unsuitable terrain");
        h.succeed();
    }

    private static MemoryLevel terrain(GameTestHelper h, Block soil) {
        return new MemoryLevel(h, pos -> pos.getY() < 70 ? soil.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }

    private record Write(BlockPos pos, BlockState state, int flags) {
    }

    private static final class MemoryLevel implements InvocationHandler {
        private final GameTestHelper helper;
        private final Function<BlockPos, BlockState> fallback;
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final List<Write> writes = new ArrayList<>();
        private final WorldGenLevel level;

        private MemoryLevel(GameTestHelper helper, Function<BlockPos, BlockState> fallback) {
            this.helper = helper;
            this.fallback = fallback;
            this.level = (WorldGenLevel)Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class}, this);
        }

        private BlockState state(BlockPos pos) {
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
                case "getHeight" -> args == null || args.length == 0 ? helper.getLevel().getHeight() : 70;
                case "isOutsideBuildHeight" -> {
                    int y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer)args[0];
                    yield y < helper.getLevel().getMinBuildHeight() || y >= helper.getLevel().getMaxBuildHeight();
                }
                case "ensureCanWrite" -> true;
                case "getBlockState" -> state((BlockPos)args[0]);
                case "getFluidState" -> state((BlockPos)args[0]).getFluidState();
                case "isEmptyBlock" -> state((BlockPos)args[0]).isAir();
                case "isStateAtPosition" -> ((Predicate<BlockState>)args[1]).test(state((BlockPos)args[0]));
                case "getRawBrightness" -> 15;
                case "canSeeSky" -> true;
                case "setBlock" -> {
                    BlockPos pos = ((BlockPos)args[0]).immutable();
                    BlockState state = (BlockState)args[1];
                    if (state(pos).equals(state)) yield false;
                    blocks.put(pos, state);
                    writes.add(new Write(pos, state, (Integer)args[2]));
                    yield true;
                }
                case "toString" -> "Virtual biome-decoration terrain";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Unexpected biome decoration terrain access: " + method);
            };
        }
    }

    private static class NoDraws extends LegacyRandomSource {
        private NoDraws() { super(0); }
        @Override public int next(int bits) { throw new AssertionError("Unexpected random bits: " + bits); }
        @Override public int nextInt(int bound) { throw new AssertionError("Unexpected random bound: " + bound); }
        @Override public long nextLong() { throw new AssertionError("Unexpected random long"); }
        @Override public boolean nextBoolean() { throw new AssertionError("Unexpected random boolean"); }
        @Override public float nextFloat() { throw new AssertionError("Unexpected random float"); }
    }

    private static final class SeedOnly extends NoDraws {
        private final long seed;
        private int draws;
        private SeedOnly(long seed) { this.seed = seed; }
        @Override public long nextLong() {
            if (++draws != 1) throw new AssertionError("Only one shared seed is allowed");
            return seed;
        }
    }

    private static final class RepeatedInt extends NoDraws {
        private final int bound;
        private final int value;
        private int draws;
        private RepeatedInt(int bound, int value) { this.bound = bound; this.value = value; }
        @Override public int nextInt(int candidate) {
            if (candidate != bound) throw new AssertionError("Expected bound " + bound + ", got " + candidate);
            draws++;
            return value;
        }
    }

    private static final class MushroomDraws extends NoDraws {
        private final boolean brown;
        private int draws;
        private MushroomDraws(boolean brown) { this.brown = brown; }
        @Override public boolean nextBoolean() {
            if (draws++ != 0) throw new AssertionError("Mushroom type must be first");
            return brown;
        }
        @Override public int nextInt(int bound) {
            if (draws++ == 1 && bound == 3) return 0;
            if (draws == 3 && bound == 12) return 1;
            throw new AssertionError("Unexpected giant mushroom RNG trace");
        }
    }

    private record Draw(String type, int bound, Number value) {
    }

    private static final class Tape extends NoDraws {
        private final List<Draw> entries = new ArrayList<>();
        private int index;
        private void integer(int bound, int value) { entries.add(new Draw("integer", bound, value)); }
        private void ints(int bound, int count) { for (int draw = 0; draw < count; draw++) integer(bound, 0); }
        private void floating(float value) { entries.add(new Draw("float", 0, value)); }
        private void seed(long value) { entries.add(new Draw("long", 0, value)); }
        private void triangular(int attempts) {
            for (int attempt = 0; attempt < attempts; attempt++) {
                ints(8, 2); ints(4, 2); ints(8, 2);
            }
        }
        private Number draw(String type, int bound) {
            if (index >= entries.size()) throw new AssertionError("Extra forest RNG draw: " + type + "/" + bound);
            Draw expected = entries.get(index++);
            if (!expected.type().equals(type) || expected.bound() != bound) {
                throw new AssertionError("Forest RNG draw " + (index - 1) + ": expected " + expected + ", got " + type + "/" + bound);
            }
            return expected.value();
        }
        @Override public int nextInt(int bound) { return draw("integer", bound).intValue(); }
        @Override public float nextFloat() { return draw("float", 0).floatValue(); }
        @Override public long nextLong() { return draw("long", 0).longValue(); }
    }
}
