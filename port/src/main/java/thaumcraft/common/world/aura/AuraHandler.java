package thaumcraft.common.world.aura;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import thaumcraft.Thaumcraft;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.TCAttachments;
import thaumcraft.common.world.biomes.BiomeHandler;

/**
 * Port of AuraHandler. Differences from TC6:
 * - indexed by the dimension's ResourceKey instead of int dim;
 * - server thread only (AuraThread is replaced by the level tick, see AuraEvents), so no Concurrent collections;
 * - chunk data lives in the thaumcraft:aura attachment, saved through chunk.setUnsaved (no dirtyChunks needed).
 */
public class AuraHandler {
    public static final int AURA_CEILING = 500;
    static final Map<ResourceKey<Level>, AuraWorld> auras = new HashMap<>();
    /** Chunk where a flux rift should appear (handled on the level tick). */
    public static final Map<ResourceKey<Level>, BlockPos> riftTrigger = new HashMap<>();

    public static class AuraWorld {
        final ResourceKey<Level> dim;
        final Long2ObjectOpenHashMap<AuraChunk> auraChunks = new Long2ObjectOpenHashMap<>();

        AuraWorld(ResourceKey<Level> dim) {
            this.dim = dim;
        }

        public AuraChunk getAuraChunkAt(int x, int z) {
            return this.auraChunks.get(ChunkPos.asLong(x, z));
        }

        public Iterable<AuraChunk> getAuraChunks() {
            return this.auraChunks.values();
        }
    }

    public static AuraWorld getAuraWorld(ResourceKey<Level> dim) {
        return auras.get(dim);
    }

    public static AuraChunk getAuraChunk(Level level, int x, int z) {
        if (level == null || level.isClientSide) {
            return null;
        }
        AuraWorld aw = auras.get(level.dimension());
        return aw == null ? null : aw.getAuraChunkAt(x, z);
    }

    private static AuraChunk at(Level level, BlockPos pos) {
        return getAuraChunk(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static void addAuraWorld(ResourceKey<Level> dim) {
        if (!auras.containsKey(dim)) {
            auras.put(dim, new AuraWorld(dim));
            Thaumcraft.log.info("Creating aura cache for world {}", dim.location());
        }
    }

    public static void removeAuraWorld(ResourceKey<Level> dim) {
        auras.remove(dim);
        riftTrigger.remove(dim);
        Thaumcraft.log.info("Removing aura cache for world {}", dim.location());
    }

    /** Chunk loaded: take its attachment, generate the aura if there is none (ChunkEvents.chunkLoad in TC6). */
    public static void loadAuraChunk(Level level, LevelChunk chunk) {
        AuraChunk ac = chunk.getData(TCAttachments.AURA);
        ac.chunk = chunk;
        ac.loc = chunk.getPos();
        if (!ac.isGenerated()) {
            generateAura(level, chunk, ac, level.random);
        }
        if (ac.isGenerated()) {
            addAuraWorld(level.dimension());
            auras.get(level.dimension()).auraChunks.put(chunk.getPos().toLong(), ac);
        }
    }

    public static void removeAuraChunk(Level level, int x, int z) {
        AuraWorld aw = auras.get(level.dimension());
        if (aw != null) {
            aw.auraChunks.remove(ChunkPos.asLong(x, z));
        }
    }

    public static float getTotalAura(Level level, BlockPos pos) {
        AuraChunk ac = at(level, pos);
        return ac != null ? ac.getVis() + ac.getFlux() : 0.0F;
    }

    public static float getFluxSaturation(Level level, BlockPos pos) {
        AuraChunk ac = at(level, pos);
        return ac != null && ac.getBase() > 0 ? ac.getFlux() / ac.getBase() : 0.0F;
    }

    public static float getVis(Level level, BlockPos pos) {
        AuraChunk ac = at(level, pos);
        return ac != null ? ac.getVis() : 0.0F;
    }

    public static float getFlux(Level level, BlockPos pos) {
        AuraChunk ac = at(level, pos);
        return ac != null ? ac.getFlux() : 0.0F;
    }

    public static int getAuraBase(Level level, BlockPos pos) {
        AuraChunk ac = at(level, pos);
        return ac != null ? ac.getBase() : 0;
    }

    public static boolean shouldPreserveAura(Level level, Player player, BlockPos pos) {
        boolean researched = player == null || thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player).isResearchComplete("AURAPRESERVE");
        return researched && getVis(level, pos) / getAuraBase(level, pos) < 0.1;
    }

    public static void addVis(Level level, BlockPos pos, float amount) {
        if (amount >= 0.0F) {
            modifyVisInChunk(at(level, pos), amount, true);
        }
    }

    public static void addFlux(Level level, BlockPos pos, float amount) {
        if (amount >= 0.0F) {
            modifyFluxInChunk(at(level, pos), amount, true);
        }
    }

    public static float drainVis(Level level, BlockPos pos, float amount, boolean simulate) {
        AuraChunk ac = at(level, pos);
        if (ac == null) {
            return 0.0F;
        }
        if (amount > ac.getVis()) {
            amount = ac.getVis();
        }
        modifyVisInChunk(ac, -amount, !simulate);
        return amount;
    }

    public static float drainFlux(Level level, BlockPos pos, float amount, boolean simulate) {
        AuraChunk ac = at(level, pos);
        if (ac == null) {
            return 0.0F;
        }
        if (amount > ac.getFlux()) {
            amount = ac.getFlux();
        }
        modifyFluxInChunk(ac, -amount, !simulate);
        return amount;
    }

    public static boolean modifyVisInChunk(AuraChunk ac, float amount, boolean doit) {
        if (ac == null) {
            return false;
        }
        if (doit) {
            ac.setVis(Math.max(0.0F, ac.getVis() + amount));
        }
        return true;
    }

    private static boolean modifyFluxInChunk(AuraChunk ac, float amount, boolean doit) {
        if (ac == null) {
            return false;
        }
        if (doit) {
            ac.setFlux(Math.max(0.0F, ac.getFlux() + amount));
        }
        return true;
    }

    /**
     * Aura base = average of the biome modifiers of the chunk and its 4 neighbours * 500 * N(1, 0.1).
     * Biomes are read without loading neighbouring chunks (getUncachedNoiseBiome).
     */
    public static void generateAura(Level level, LevelChunk chunk, AuraChunk ac, RandomSource rand) {
        if (!ModConfig.CONFIG_WORLD.generateAura) {
            return;
        }
        ChunkPos cp = chunk.getPos();
        Holder<Biome> center = biomeAt(level, cp.x, cp.z);
        if (!ModConfig.CONFIG_WORLD.worldgenPolicy.allowsAura(BiomeHandler.biomeKey(center))) {
            return;
        }
        float life = BiomeHandler.getBiomeAuraModifier(center);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            life += BiomeHandler.getBiomeAuraModifier(biomeAt(level, cp.x + dir.getStepX(), cp.z + dir.getStepZ()));
        }
        life /= 5.0F;
        float noise = (float) (1.0 + rand.nextGaussian() * 0.1F);
        short base = (short) Mth.clamp((int) (life * 500.0F * noise), 0, AURA_CEILING);
        ac.set(base, base, 0.0F);
    }

    private static Holder<Biome> biomeAt(Level level, int chunkX, int chunkZ) {
        // TC6 took the point (x*16+8, 50, z*16+8); here it is the quart coordinates of the biome noise
        return level.getUncachedNoiseBiome((chunkX << 2) + 2, 50 >> 2, (chunkZ << 2) + 2);
    }
}
