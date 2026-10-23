package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.ore.BlockCrystal;
import thaumcraft.common.lib.TCAttachments;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.aura.AuraHandler;

/** Stage 3a: aura attachment, API, crystals and leaves. The aura state is restored after each test. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class AuraGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private static void equal(GameTestHelper h, float actual, float expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < 0.0001F, message + ": " + actual + " != " + expected);
    }

    private static BlockState crystal(GameTestHelper h, Block block, int size, int generation) {
        h.setBlock(POS.below(), Blocks.STONE);
        BlockState state = BlockCrystal.withConnections(block.defaultBlockState(), h.getLevel(), h.absolutePos(POS))
            .setValue(BlockCrystal.SIZE, size).setValue(BlockCrystal.GENERATION, generation);
        h.setBlock(POS, state);
        return state;
    }

    private static Rolls tick(GameTestHelper h, int... rolls) {
        Rolls random = new Rolls(rolls);
        BlockPos pos = h.absolutePos(POS);
        h.getLevel().getBlockState(pos).randomTick(h.getLevel(), pos, random);
        h.assertTrue(random.index == rolls.length, "all scripted random draws consumed");
        return random;
    }

    @GameTest(template = "empty")
    public static void auraApiAndDirtyChunk(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.aura.setVis(12.5F);
            f.aura.setFlux(7.5F);
            f.chunk.setUnsaved(false);
            equal(h, AuraHelper.drainVis(h.getLevel(), f.pos, 100, true), 12.5F, "simulated vis drain is limited");
            equal(h, AuraHelper.drainFlux(h.getLevel(), f.pos, 100, true), 7.5F, "simulated flux drain is limited");
            equal(h, f.aura.getVis(), 12.5F, "simulate leaves vis intact");
            equal(h, f.aura.getFlux(), 7.5F, "simulate leaves flux intact");
            h.assertFalse(f.chunk.isUnsaved(), "simulate does not dirty chunk");
            equal(h, AuraHelper.drainVis(h.getLevel(), f.pos, 100, false), 12.5F, "real vis drain is limited");
            equal(h, f.aura.getVis(), 0, "vis cannot go negative");
            h.assertTrue(f.chunk.isUnsaved(), "real drain dirties chunk");
            equal(h, AuraHelper.drainFlux(h.getLevel(), f.pos, 2, false), 2, "real flux drain");
            AuraHelper.addVis(h.getLevel(), f.pos, 5);
            AuraHelper.polluteAura(h.getLevel(), f.pos, 3, false);
            AuraHelper.addVis(h.getLevel(), f.pos, -10);
            AuraHelper.polluteAura(h.getLevel(), f.pos, -10, false);
            equal(h, AuraHelper.getVis(h.getLevel(), f.pos), 5, "negative vis addition ignored");
            equal(h, AuraHelper.getFlux(h.getLevel(), f.pos), 8.5F, "negative flux addition ignored");
            equal(h, AuraHandler.getTotalAura(h.getLevel(), f.pos), 13.5F, "total aura");
            h.assertTrue(AuraHelper.getAuraBase(h.getLevel(), f.pos) == 100, "base through API");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void attachmentSerializesAndCacheReloadDoesNotRegenerate(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.aura.setVis(12.25F);
            f.aura.setFlux(7.5F);
            CompoundTag saved = f.chunk.writeAttachmentsToNBT(h.getLevel().registryAccess());
            h.assertTrue(saved != null && saved.contains("thaumcraft:aura"), "registered chunk attachment saved");
            AuraChunk decoded = AuraChunk.CODEC.parse(NbtOps.INSTANCE, saved.get("thaumcraft:aura")).getOrThrow();
            h.assertTrue(decoded.isGenerated() && decoded.getBase() == 100, "saved base is generated");
            equal(h, decoded.getVis(), 12.25F, "saved vis");
            equal(h, decoded.getFlux(), 7.5F, "saved flux");
            AuraHandler.removeAuraChunk(h.getLevel(), f.chunk.getPos().x, f.chunk.getPos().z);
            h.assertTrue(AuraHandler.getAuraChunk(h.getLevel(), f.chunk.getPos().x, f.chunk.getPos().z) == null,
                "unload removes index, lookup does not load chunk");
            f.chunk.readAttachmentsFromNBT(h.getLevel().registryAccess(), saved);
            AuraHandler.loadAuraChunk(h.getLevel(), f.chunk);
            AuraChunk restored = f.chunk.getData(TCAttachments.AURA);
            h.assertTrue(AuraHandler.getAuraChunk(h.getLevel(), f.chunk.getPos().x, f.chunk.getPos().z) == restored,
                "reload indexes deserialized attachment");
            equal(h, restored.getVis(), 12.25F, "reload does not regenerate vis");
            equal(h, restored.getFlux(), 7.5F, "reload does not regenerate flux");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void crystalVisGrowthAndThresholds(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            crystal(h, BlocksTC.crystalAir, 0, 1);
            f.aura.setVis(110);
            tick(h, 0);
            h.assertBlockProperty(POS, BlockCrystal.SIZE, 0);
            f.aura.setVis(111);
            Rolls skipped = tick(h, 1);
            h.assertTrue(skipped.bounds.equals(List.of(4)), "generation 1 tick chance 1/4");
            h.assertBlockProperty(POS, BlockCrystal.SIZE, 0);
            f.aura.setFlux(222);
            tick(h, 0);
            h.assertBlockProperty(POS, BlockCrystal.SIZE, 1);
            equal(h, f.aura.getVis(), 101, "growth spends 10 vis");
            equal(h, f.aura.getFlux(), 222, "normal crystal does not spend flux");
            f.aura.setVis(10);
            tick(h, 0);
            h.assertBlockProperty(POS, BlockCrystal.SIZE, 0);
            equal(h, f.aura.getVis(), 20, "decay returns 10 vis at <= 10");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fluxCrystalUsesFluxInsteadOfVis(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            crystal(h, BlocksTC.crystalTaint, 0, 1);
            f.aura.setVis(222);
            f.aura.setFlux(110);
            tick(h, 0);
            h.assertBlockProperty(POS, BlockCrystal.SIZE, 0);
            f.aura.setFlux(111);
            tick(h, 0);
            h.assertBlockProperty(POS, BlockCrystal.SIZE, 1);
            equal(h, f.aura.getFlux(), 101, "flux crystal spends 10 flux");
            equal(h, f.aura.getVis(), 222, "flux crystal does not spend vis");
            f.aura.setFlux(10);
            tick(h, 0);
            h.assertBlockProperty(POS, BlockCrystal.SIZE, 0);
            equal(h, f.aura.getFlux(), 20, "decay returns 10 flux");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lastCrystalSurvivesButSameKindDuplicateDissolves(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            crystal(h, BlocksTC.crystalAir, 0, 1);
            f.aura.setVis(0);
            tick(h, 0);
            h.assertBlockPresent(BlocksTC.crystalAir, POS);
            equal(h, f.aura.getVis(), 0, "last crystal does not dissolve");
            h.setBlock(POS.east().below(), Blocks.STONE);
            h.setBlock(POS.east(), BlockCrystal.withConnections(BlocksTC.crystalFire.defaultBlockState(), h.getLevel(), h.absolutePos(POS.east())));
            tick(h, 0);
            h.assertBlockPresent(BlocksTC.crystalAir, POS);
            h.setBlock(POS.east(), BlockCrystal.withConnections(BlocksTC.crystalAir.defaultBlockState(), h.getLevel(), h.absolutePos(POS.east())));
            tick(h, 0);
            h.assertBlockPresent(Blocks.AIR, POS);
            equal(h, f.aura.getVis(), 10, "same kind duplicate returns 10 vis");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void spreadPreservesGenerationChancesAndStopsAtFour(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            crystal(h, BlocksTC.crystalAir, 3, 3);
            h.setBlock(POS.east().below(), Blocks.STONE);
            f.aura.setVis(150);
            Rolls spread = tick(h, 0, 2, 1, 1, 0, 1); // east, 1/16 succeeds, generation downgrade does not
            h.assertTrue(spread.bounds.equals(List.of(6, 3, 3, 3, 16, 6)), "original random bounds");
            h.assertBlockPresent(BlocksTC.crystalAir, POS.east());
            h.assertBlockProperty(POS.east(), BlockCrystal.GENERATION, 4);
            h.assertBlockProperty(POS.east(), BlockCrystal.SIZE, 0);
            h.assertBlockProperty(POS.east(), BlockCrystal.DOWN, true);
            equal(h, f.aura.getVis(), 140, "spreading spends 10 vis");
            h.setBlock(POS.east(), Blocks.AIR);
            tick(h, 0, 2, 1, 1, 0, 0); // rare 1/6 branch preserves parent's generation
            h.assertBlockProperty(POS.east(), BlockCrystal.GENERATION, 3);
            h.setBlock(POS.east(), Blocks.AIR);
            crystal(h, BlocksTC.crystalAir, 3, 4);
            Rolls last = tick(h, 0);
            h.assertTrue(last.bounds.equals(List.of(7)), "generation 4 tick chance 1/7");
            h.assertBlockPresent(Blocks.AIR, POS.east());
            equal(h, f.aura.getVis(), 130, "generation 4 never spreads");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void spreadRejectsWaterNoSupportOccupiedOriginAndFailedChance(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            crystal(h, BlocksTC.crystalTaint, 3, 1);
            f.aura.setFlux(150);
            h.setBlock(POS.east().below(), Blocks.STONE);
            h.setBlock(POS.east(), Blocks.WATER);
            tick(h, 0, 2, 1, 1);
            h.assertBlockPresent(Blocks.WATER, POS.east());
            h.setBlock(POS.east(), Blocks.STONE);
            tick(h, 0, 2, 1, 1);
            h.setBlock(POS.east(), Blocks.AIR);
            h.setBlock(POS.east().below(), Blocks.AIR);
            tick(h, 0, 2, 1, 1, 0);
            h.assertBlockPresent(Blocks.AIR, POS.east());
            h.setBlock(POS.east().below(), Blocks.STONE);
            tick(h, 0, 1, 1, 1); // same coordinate as parent
            tick(h, 0, 2, 1, 1, 1); // 1/16 roll misses
            h.assertBlockPresent(Blocks.AIR, POS.east());
            equal(h, f.aura.getFlux(), 150, "failed spread never drains aura");
            tick(h, 0, 2, 1, 1, 0, 1);
            h.assertBlockPresent(BlocksTC.crystalTaint, POS.east());
            equal(h, f.aura.getFlux(), 140, "successful flux spread drains flux");
            equal(h, f.aura.getVis(), 0, "flux spread never drains vis");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void growthLimitUsesLegacySignedPositionPacking(GameTestHelper h) {
        h.assertTrue(BlockCrystal.getGrowthLimit(new BlockPos(0, 1, 2), 4) == 1, "1.12 y packing");
        h.assertTrue(BlockCrystal.getGrowthLimit(new BlockPos(0, 1, 0), 4) == 2, "positive packed remainder");
        h.assertTrue(BlockCrystal.getGrowthLimit(new BlockPos(-1, 0, 0), 4) == 0, "negative remainder is signed");
        h.assertTrue(BlockCrystal.getGrowthLimit(new BlockPos(0, -1, 0), 4) == 1, "old 12-bit y mask");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyNaturalSilverwoodLeavesRegenerateBelowBase(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            BlockState natural = BlocksTC.leafSilverwood.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1);
            h.setBlock(POS, natural);
            f.aura.setVis(50);
            tick(h);
            equal(h, f.aura.getVis(), 50.01F, "natural silverwood adds 0.01 vis");
            f.aura.setVis(100);
            tick(h);
            equal(h, f.aura.getVis(), 100, "leaves do not regenerate at base");
            f.aura.setVis(101);
            tick(h);
            equal(h, f.aura.getVis(), 101, "leaves do not regenerate above base");
            f.aura.setVis(50);
            h.setBlock(POS, natural.setValue(LeavesBlock.PERSISTENT, true));
            tick(h);
            equal(h, f.aura.getVis(), 50, "player-placed leaves do not regenerate");
            h.setBlock(POS, BlocksTC.leafGreatwood.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
            tick(h);
            equal(h, f.aura.getVis(), 50, "greatwood does not regenerate");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void silverwoodAuraDoesNotDisableLeafDecay(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            h.setBlock(POS, BlocksTC.leafSilverwood.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 7));
            f.aura.setVis(50);
            tick(h);
            h.assertBlockPresent(Blocks.AIR, POS);
            equal(h, f.aura.getVis(), 50.01F, "regen happens before vanilla decay, as in TC6");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void auraRegeneratesThroughServerLevelTick(GameTestHelper h) {
        Fixture fixture = new Fixture(h);
        long oldDayTime = h.getLevel().getDayTime();
        h.getLevel().setDayTime(0); // full moon, positive regeneration
        h.runAfterDelay(40, () -> {
            try (fixture) {
                h.assertTrue(AuraHelper.getVis(h.getLevel(), fixture.pos) > 0, "LevelTickEvent regenerates aura without manual tick");
            } finally {
                h.getLevel().setDayTime(oldDayTime);
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void auraDebugCommandReadsCurrentAndExplicitChunk(GameTestHelper h) throws Exception {
        try (Fixture f = new Fixture(h)) {
            var server = h.getLevel().getServer();
            var source = server.createCommandSourceStack().withLevel(h.getLevel()).withPosition(Vec3.atCenterOf(f.pos));
            var dispatcher = server.getCommands().getDispatcher();
            h.assertTrue(dispatcher.execute("tc aura", source) == 1, "current chunk command");
            h.assertTrue(dispatcher.execute("thaumcraft aura " + f.pos.getX() + " " + f.pos.getY() + " " + f.pos.getZ(), source) == 1,
                "explicit position command");
            h.assertTrue(dispatcher.execute("tc aura 29999900 64 29999900", source) == 0, "missing chunk is not loaded");
        }
        h.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos pos;
        final LevelChunk chunk;
        final AuraChunk aura;
        final short oldBase;
        final float oldVis;
        final float oldFlux;

        Fixture(GameTestHelper h) {
            helper = h;
            pos = h.absolutePos(POS);
            chunk = h.getLevel().getChunkAt(pos);
            aura = AuraHandler.getAuraChunk(h.getLevel(), chunk.getPos().x, chunk.getPos().z);
            h.assertTrue(aura != null && aura.isGenerated(), "chunk load generated aura");
            h.assertTrue(aura == chunk.getData(TCAttachments.AURA), "cache indexes actual attachment");
            h.assertTrue(aura.getBase() >= 0 && aura.getBase() <= 500, "generated base in TC6 range");
            oldBase = aura.getBase();
            oldVis = aura.getVis();
            oldFlux = aura.getFlux();
            aura.setBase((short) 100);
            aura.setVis(0);
            aura.setFlux(0);
        }

        @Override
        public void close() {
            chunk.setData(TCAttachments.AURA, aura);
            AuraHandler.loadAuraChunk(helper.getLevel(), chunk);
            aura.setBase(oldBase);
            aura.setVis(oldVis);
            aura.setFlux(oldFlux);
        }
    }

    private static final class Rolls extends LegacyRandomSource {
        final int[] values;
        final List<Integer> bounds = new ArrayList<>();
        int index;

        Rolls(int... values) {
            super(0);
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            if (index >= values.length || values[index] < 0 || values[index] >= bound) {
                throw new AssertionError("Unexpected random draw with bound " + bound);
            }
            bounds.add(bound);
            return values[index++];
        }
    }
}
