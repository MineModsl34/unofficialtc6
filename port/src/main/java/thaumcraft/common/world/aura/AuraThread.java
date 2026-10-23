package thaumcraft.common.world.aura;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Port of AuraThread. In TC6 this was a separate thread per dimension with a 1 s pause; here the same calculation
 * runs on the server tick every 20 ticks (see AuraEvents), with no races against the main thread.
 * Numbers and order of operations are kept.
 */
public class AuraThread {
    private static final float[] PHASE_TABLE = {0.25F, 0.15F, 0.1F, 0.05F, 0.0F, 0.05F, 0.1F, 0.15F};
    private static final float[] MAX_TABLE = {0.15F, 0.05F, 0.0F, -0.05F, -0.15F, -0.05F, 0.0F, 0.05F};
    private static final List<Direction> HORIZONTAL = List.of(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST);

    private final Random rand;
    private float phaseVis;
    private float phaseFlux;
    private float phaseMax;

    public AuraThread() {
        this(new Random());
    }

    AuraThread(Random random) {
        this.rand = random;
    }

    public void tick(ServerLevel level) {
        AuraHandler.AuraWorld auraWorld = AuraHandler.getAuraWorld(level.dimension());
        if (auraWorld == null) {
            return;
        }
        tick(auraWorld, level.getMoonPhase());
    }

    /** The same calculation without touching the level: handy for checking moon phases and transfer between chunks. */
    void tick(AuraHandler.AuraWorld auraWorld, int moon) {
        this.phaseVis = PHASE_TABLE[moon];
        this.phaseMax = 1.0F + MAX_TABLE[moon];
        this.phaseFlux = 0.25F - this.phaseVis;
        for (AuraChunk auraChunk : new ArrayList<>(auraWorld.auraChunks.values())) {
            this.processAuraChunk(auraWorld, auraChunk);
        }
    }

    private void processAuraChunk(AuraHandler.AuraWorld auraWorld, AuraChunk auraChunk) {
        List<Direction> directions = new ArrayList<>(HORIZONTAL);
        Collections.shuffle(directions, this.rand);
        int x = auraChunk.loc.x;
        int z = auraChunk.loc.z;
        float base = auraChunk.getBase() * this.phaseMax;
        boolean dirty = false;
        float currentVis = auraChunk.getVis();
        float currentFlux = auraChunk.getFlux();
        AuraChunk neighbourVisChunk = null;
        AuraChunk neighbourFluxChunk = null;
        float lowestVis = Float.MAX_VALUE;
        float lowestFlux = Float.MAX_VALUE;

        for (Direction dir : directions) {
            AuraChunk n = auraWorld.getAuraChunkAt(x + dir.getStepX(), z + dir.getStepZ());
            if (n != null) {
                if ((neighbourVisChunk == null || lowestVis > n.getVis()) && n.getVis() + n.getFlux() < n.getBase() * this.phaseMax) {
                    neighbourVisChunk = n;
                    lowestVis = n.getVis();
                }
                if (neighbourFluxChunk == null || lowestFlux > n.getFlux()) {
                    neighbourFluxChunk = n;
                    lowestFlux = n.getFlux();
                }
            }
        }

        if (neighbourVisChunk != null && lowestVis < currentVis && lowestVis / currentVis < 0.75) {
            float inc = Math.min(currentVis - lowestVis, 1.0F);
            currentVis -= inc;
            neighbourVisChunk.setVis(lowestVis + inc);
            dirty = true;
        }

        if (neighbourFluxChunk != null && currentFlux > Math.max(5.0F, auraChunk.getBase() / 10.0F) && lowestFlux < currentFlux / 1.75) {
            float inc = Math.min(currentFlux - lowestFlux, 1.0F);
            currentFlux -= inc;
            neighbourFluxChunk.setFlux(lowestFlux + inc);
            dirty = true;
        }

        if (currentVis + currentFlux < base) {
            float inc = Math.min(base - (currentVis + currentFlux), this.phaseVis);
            currentVis += inc;
            dirty = true;
        } else if (currentVis > base * 1.25 && this.rand.nextFloat() < 0.1) {
            currentFlux += this.phaseFlux;
            currentVis -= this.phaseFlux;
            dirty = true;
        } else if (currentVis <= base * 0.1 && currentVis >= currentFlux && this.rand.nextFloat() < 0.1) {
            currentFlux += this.phaseFlux;
            dirty = true;
        }

        if (dirty) {
            auraChunk.setVis(currentVis);
            auraChunk.setFlux(currentFlux);
        }

        if (currentFlux > base * 0.75 && this.rand.nextFloat() < currentFlux / 500.0F / 10.0F) {
            AuraHandler.riftTrigger.put(auraWorld.dim, new BlockPos(x * 16, 0, z * 16));
        }
    }
}
