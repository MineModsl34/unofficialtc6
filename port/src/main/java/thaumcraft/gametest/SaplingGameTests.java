package thaumcraft.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.plants.BlockSaplingTC;

/** TC6 sapling transitions and modern growth hooks. All writes stay inside the empty template. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class SaplingGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final BlockPos SQUARE = new BlockPos(0, 1, 0);

    @GameTest(template = "empty")
    public static void firstGrowthOnlyAdvancesStageWithoutRandomDraws(GameTestHelper h) {
        for (Block sapling : new Block[]{BlocksTC.saplingGreatwood, BlocksTC.saplingSilverwood}) {
            h.setBlock(POS.below(), Blocks.GRASS_BLOCK);
            BlockState state = sapling.defaultBlockState();
            h.setBlock(POS, state);
            ((BlockSaplingTC) sapling).advanceTree(h.getLevel(), h.absolutePos(POS), state, new NoDraws());
            h.assertBlockPresent(sapling, POS);
            h.assertBlockProperty(POS, SaplingBlock.STAGE, 1);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void singleGreatwoodCannotGrowOrConsumeTreeRandomness(GameTestHelper h) {
        h.setBlock(POS.below(), Blocks.GRASS_BLOCK);
        BlockState state = mature(BlocksTC.saplingGreatwood);
        h.setBlock(POS, state);
        ((BlockSaplingTC) BlocksTC.saplingGreatwood).advanceTree(h.getLevel(), h.absolutePos(POS), state, new NoDraws());
        h.assertBlockPresent(BlocksTC.saplingGreatwood, POS);
        h.assertBlockProperty(POS, SaplingBlock.STAGE, 1);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedSilverwoodRestoresSaplingWithoutVanillaExtraFloat(GameTestHelper h) {
        h.setBlock(POS.below(), Blocks.GRASS_BLOCK);
        h.setBlock(POS.above(), Blocks.STONE);
        BlockState state = mature(BlocksTC.saplingSilverwood);
        h.setBlock(POS, state);
        HeightDraw random = new HeightDraw();
        ((BlockSaplingTC) BlocksTC.saplingSilverwood).advanceTree(h.getLevel(), h.absolutePos(POS), state, random);
        h.assertTrue(random.draws == 1, "silverwood's only outer draw before obstruction is nextInt(4) for height");
        h.assertBlockPresent(BlocksTC.saplingSilverwood, POS);
        h.assertBlockProperty(POS, SaplingBlock.STAGE, 1);
        h.assertBlockPresent(Blocks.STONE, POS.above());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void failedGreatwoodFindsSouthEastTriggerAndRestoresAllStages(GameTestHelper h) {
        // Trigger at southeast corner exercises the final (-1,-1) origin candidate.
        plantSquare(h, false);
        h.setBlock(SQUARE.above(), Blocks.STONE);
        LongDraw random = new LongDraw();
        BlockState state = mature(BlocksTC.saplingGreatwood);
        ((BlockSaplingTC) BlocksTC.saplingGreatwood).advanceTree(h.getLevel(), h.absolutePos(POS), state, random);
        h.assertTrue(random.draws == 1, "greatwood consumes one outer seed, then uses its own legacy RNG");
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                BlockPos p = SQUARE.offset(x, 0, z);
                h.assertBlockPresent(BlocksTC.saplingGreatwood, p);
                h.assertBlockProperty(p, SaplingBlock.STAGE, 1);
            }
        }
        h.assertBlockPresent(Blocks.STONE, SQUARE.above());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledAndNullGrowthEventsKeepSaplingsAndConsumeNoRandom(GameTestHelper h) {
        for (EventMode mode : new EventMode[]{EventMode.CANCEL, EventMode.NULL}) {
            for (Block sapling : new Block[]{BlocksTC.saplingGreatwood, BlocksTC.saplingSilverwood}) {
                if (sapling == BlocksTC.saplingGreatwood) {
                    plantSquare(h, true);
                } else {
                    h.setBlock(POS.below(), Blocks.GRASS_BLOCK);
                    h.setBlock(POS, mature(sapling));
                }
                GrowthProbe listener = new GrowthProbe(h, mode);
                NeoForge.EVENT_BUS.register(listener);
                try {
                    ((BlockSaplingTC) sapling).advanceTree(h.getLevel(), h.absolutePos(POS), mature(sapling), new NoDraws());
                } finally {
                    NeoForge.EVENT_BUS.unregister(listener);
                }
                h.assertTrue(listener.calls == 1 && listener.originalFeature != null,
                    "one event carries the registered configured feature before cancellation/null replacement");
                h.assertBlockPresent(sapling, POS);
                h.assertBlockProperty(POS, SaplingBlock.STAGE, 1);
                if (sapling == BlocksTC.saplingGreatwood) {
                    for (int x = 0; x <= 1; x++) {
                        for (int z = 0; z <= 1; z++) {
                            h.assertBlockPresent(sapling, SQUARE.offset(x, 0, z));
                        }
                    }
                }
                clearSquare(h);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void growthEventCanReplaceFeatureWithoutExtraTreeRandomness(GameTestHelper h) {
        h.setBlock(POS.below(), Blocks.GRASS_BLOCK);
        BlockState state = mature(BlocksTC.saplingSilverwood);
        h.setBlock(POS, state);
        GrowthProbe listener = new GrowthProbe(h, EventMode.REPLACE);
        NeoForge.EVENT_BUS.register(listener);
        try {
            ((BlockSaplingTC) BlocksTC.saplingSilverwood).advanceTree(h.getLevel(), h.absolutePos(POS), state, new NoDraws());
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        h.assertTrue(listener.calls == 1, "replacement feature event fired once");
        h.assertBlockPresent(Blocks.AIR, POS); // successful NO_OP leaves the cleared sapling position empty
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void saplingShapeRetainsTC6Bounds(GameTestHelper h) {
        for (Block sapling : new Block[]{BlocksTC.saplingGreatwood, BlocksTC.saplingSilverwood}) {
            var bounds = sapling.defaultBlockState().getShape(h.getLevel(), h.absolutePos(POS), CollisionContext.empty()).bounds();
            h.assertTrue(Math.abs(bounds.minX - 0.1) < 0.000001 && Math.abs(bounds.minZ - 0.1) < 0.000001
                    && bounds.minY == 0 && Math.abs(bounds.maxX - 0.9) < 0.000001
                    && Math.abs(bounds.maxZ - 0.9) < 0.000001 && Math.abs(bounds.maxY - 0.8) < 0.000001,
                "sapling outline matches original TC6 rather than the smaller vanilla shape");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void saplingsRetainLegacySoilsInsteadOfModernDirtTagExpansion(GameTestHelper h) {
        for (Block soil : new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL,
                Blocks.FARMLAND, Blocks.ROOTED_DIRT, Blocks.MOSS_BLOCK, Blocks.MYCELIUM, Blocks.SAND, Blocks.STONE}) {
            h.setBlock(POS.below(), soil);
            boolean allowed = soil == Blocks.GRASS_BLOCK || soil == Blocks.DIRT || soil == Blocks.COARSE_DIRT
                || soil == Blocks.PODZOL || soil == Blocks.FARMLAND;
            for (Block sapling : new Block[]{BlocksTC.saplingGreatwood, BlocksTC.saplingSilverwood}) {
                h.assertTrue(sapling.defaultBlockState().canSurvive(h.getLevel(), h.absolutePos(POS)) == allowed,
                    "legacy sapling soil acceptance for " + soil);
            }
        }
        h.succeed();
    }

    private static BlockState mature(Block sapling) {
        return sapling.defaultBlockState().setValue(SaplingBlock.STAGE, 1);
    }

    private static void plantSquare(GameTestHelper h, boolean allMature) {
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                BlockPos p = SQUARE.offset(x, 0, z);
                h.setBlock(p.below(), Blocks.GRASS_BLOCK);
                h.setBlock(p, BlocksTC.saplingGreatwood.defaultBlockState()
                    .setValue(SaplingBlock.STAGE, allMature || x == 1 && z == 1 ? 1 : 0));
            }
        }
    }

    private static void clearSquare(GameTestHelper h) {
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                h.setBlock(SQUARE.offset(x, 0, z), Blocks.AIR);
            }
        }
    }

    private enum EventMode { CANCEL, NULL, REPLACE }

    public static final class GrowthProbe {
        private final GameTestHelper helper;
        private final EventMode mode;
        int calls;
        Holder<ConfiguredFeature<?, ?>> originalFeature;

        GrowthProbe(GameTestHelper helper, EventMode mode) {
            this.helper = helper;
            this.mode = mode;
        }

        @SubscribeEvent
        public void onGrowth(BlockGrowFeatureEvent event) {
            if (event.getLevel() != helper.getLevel() || !event.getPos().equals(helper.absolutePos(POS))) return;
            calls++;
            originalFeature = event.getFeature();
            switch (mode) {
                case CANCEL -> event.setCanceled(true);
                case NULL -> event.setFeature((Holder<ConfiguredFeature<?, ?>>) null);
                case REPLACE -> event.setFeature(Holder.direct(new ConfiguredFeature<>(Feature.NO_OP, NoneFeatureConfiguration.INSTANCE)));
            }
        }
    }

    private static class NoDraws extends LegacyRandomSource {
        NoDraws() { super(0); }
        @Override public int nextInt(int bound) { throw new AssertionError("Unexpected bounded sapling RNG draw: " + bound); }
        @Override public int next(int bits) { throw new AssertionError("Unexpected sapling RNG bits: " + bits); }
        @Override public float nextFloat() { throw new AssertionError("Saplings must not consume vanilla TreeGrower's secondary-choice float"); }
        @Override public long nextLong() { throw new AssertionError("Unexpected sapling RNG long"); }
    }

    private static final class HeightDraw extends NoDraws {
        int draws;
        @Override public int nextInt(int bound) {
            if (bound != 4 || ++draws != 1) throw new AssertionError("Expected only one silverwood height draw nextInt(4)");
            return 0;
        }
    }

    private static final class LongDraw extends NoDraws {
        int draws;
        @Override public long nextLong() {
            if (++draws != 1) throw new AssertionError("Expected one greatwood seed nextLong()");
            return 123456789L;
        }
    }
}
