package thaumcraft.common.world.aura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class AuraTest {
    private static AuraChunk add(AuraHandler.AuraWorld world, int x, int z, int base, float vis, float flux) {
        AuraChunk chunk = new AuraChunk((short) base, vis, flux);
        chunk.loc = new ChunkPos(x, z);
        world.auraChunks.put(chunk.loc.toLong(), chunk);
        return chunk;
    }

    @Test
    void codecRoundTripRetainsGeneratedZeroAndFractionalValues() {
        for (AuraChunk original : new AuraChunk[]{new AuraChunk((short) 0, 0, 0), new AuraChunk((short) 250, 12.25F, 7.5F)}) {
            var nbt = AuraChunk.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
            AuraChunk decoded = AuraChunk.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
            assertTrue(decoded.isGenerated());
            assertEquals(original.getBase(), decoded.getBase());
            assertEquals(original.getVis(), decoded.getVis());
            assertEquals(original.getFlux(), decoded.getFlux());
        }
        assertFalse(new AuraChunk().isGenerated());
    }

    @Test
    void settersKeepThaumcraft6Bounds() {
        AuraChunk chunk = new AuraChunk((short) 100, 0, 0);
        chunk.setVis(-1);
        chunk.setFlux(-10);
        assertEquals(0, chunk.getVis());
        assertEquals(0, chunk.getFlux());
        chunk.setVis(40000);
        chunk.setFlux(40000);
        assertEquals(32766, chunk.getVis());
        assertEquals(32766, chunk.getFlux());
    }

    @Test
    void regenerationMatchesEveryMoonPhase() {
        float[] expected = {0.25F, 0.15F, 0.1F, 0.05F, 0.0F, 0.05F, 0.1F, 0.15F};
        for (int moon = 0; moon < expected.length; moon++) {
            AuraHandler.AuraWorld world = new AuraHandler.AuraWorld(null);
            AuraChunk chunk = add(world, 0, 0, 100, 10, 0);
            new AuraThread(new Random(1)).tick(world, moon);
            assertEquals(10 + expected[moon], chunk.getVis(), 0.00001F, "phase " + moon);
            assertEquals(0, chunk.getFlux());
        }
    }

    @Test
    void regenerationStopsAtMoonAdjustedTotal() {
        AuraHandler.AuraWorld world = new AuraHandler.AuraWorld(null);
        AuraChunk chunk = add(world, 0, 0, 100, 104.95F, 0);
        new AuraThread(new Random(1)).tick(world, 1); // max = 1.05, regen = 0.15
        assertEquals(105, chunk.getVis(), 0.0001F);
        chunk.setVis(100);
        chunk.setFlux(5);
        new AuraThread(new Random(1)).tick(world, 1);
        assertEquals(100, chunk.getVis()); // flux takes up capacity too
        assertEquals(5, chunk.getFlux());
    }

    @Test
    void visAndFluxMoveAtMostOneUnitAndAreConservedWithoutRegeneration() {
        AuraHandler.AuraWorld world = new AuraHandler.AuraWorld(null);
        AuraChunk source = add(world, -1, 0, 100, 70, 20);
        AuraChunk destination = add(world, 0, 0, 100, 10, 0);
        new AuraThread(new Random(1)).tick(world, 4); // new moon: no vis regeneration
        assertEquals(69, source.getVis());
        assertEquals(11, destination.getVis());
        assertEquals(19, source.getFlux());
        assertEquals(1, destination.getFlux());
        assertEquals(80, source.getVis() + destination.getVis());
        assertEquals(20, source.getFlux() + destination.getFlux());
    }

    @Test
    void neighbourVisRatioAndFluxThresholdAreStrict() {
        AuraHandler.AuraWorld world = new AuraHandler.AuraWorld(null);
        AuraChunk source = add(world, 0, 0, 100, 40, 10);
        AuraChunk destination = add(world, 1, 0, 100, 30, 0);
        new AuraThread(new Random(1)).tick(world, 4);
        assertEquals(40, source.getVis()); // ratio exactly 0.75 does not transfer
        assertEquals(30, destination.getVis());
        assertEquals(10, source.getFlux()); // exactly base / 10 does not transfer
        assertEquals(0, destination.getFlux());
    }

    @Test
    void excessVisConvertsToFluxAtOriginalRate() {
        AuraHandler.AuraWorld world = new AuraHandler.AuraWorld(null);
        AuraChunk chunk = add(world, 0, 0, 100, 150, 0);
        new AuraThread(alwaysZeroFloat()).tick(world, 4);
        assertEquals(149.75F, chunk.getVis());
        assertEquals(0.25F, chunk.getFlux());
        assertEquals(150, chunk.getVis() + chunk.getFlux());
    }

    @Test
    void riftTriggerUsesMoonAdjustedThresholdAndChunkOrigin() {
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("thaumcraft", "aura_test"));
        AuraHandler.AuraWorld world = new AuraHandler.AuraWorld(dimension);
        AuraChunk chunk = add(world, -3, 2, 100, 25, 60);
        try {
            new AuraThread(alwaysZeroFloat()).tick(world, 4);
            assertFalse(AuraHandler.riftTrigger.containsKey(dimension));
            chunk.setFlux(70);
            new AuraThread(alwaysZeroFloat()).tick(world, 4);
            assertEquals(new BlockPos(-48, 0, 32), AuraHandler.riftTrigger.get(dimension));
        } finally {
            AuraHandler.riftTrigger.remove(dimension);
        }
    }

    private static Random alwaysZeroFloat() {
        return new Random(1) {
            @Override
            public float nextFloat() {
                return 0;
            }
        };
    }
}
