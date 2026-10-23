package thaumcraft.common.world.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.event.EventHooks;
import thaumcraft.common.entities.CultEntities;
import thaumcraft.common.world.features.TCWorldgenBounds;
import thaumcraft.common.world.structures.TCSurfaceAttemptRules.Completion;

/** One entity stored in its owner's generation chunk, with a persisted one-shot outcome. */
public final class TCCultistPortalPiece extends StructurePiece {
    public static final StructurePieceType.ContextlessType TYPE = TCCultistPortalPiece::new;
    public static final TagKey<Block> SUPPORT = TagKey.create(Registries.BLOCK, ResourceLocation.parse(TCCultistPortalSupport.TAG_ID));
    private final int x, z;
    private final ChunkPos owner;
    private Completion completion = Completion.PENDING;

    public TCCultistPortalPiece(BlockPos sourceOrigin, ChunkPos owner, int minHeight, int maxHeight) {
        // One owner-column only. The late post-vegetation height must not be clipped to raw-noise Y.
        super(TYPE, 0, new BoundingBox(sourceOrigin.getX(), minHeight + 1, sourceOrigin.getZ(),
            sourceOrigin.getX(), maxHeight - 1, sourceOrigin.getZ()));
        x = sourceOrigin.getX(); z = sourceOrigin.getZ(); this.owner = owner;
        if ((x >> 4) != owner.x || (z >> 4) != owner.z) throw new IllegalArgumentException("Portal must belong to its start chunk");
    }

    public TCCultistPortalPiece(CompoundTag tag) {
        super(TYPE, tag);
        x = tag.getInt("TCX"); z = tag.getInt("TCZ");
        owner = new ChunkPos(tag.getInt("TCStartX"), tag.getInt("TCStartZ"));
        completion = Completion.fromSaved(tag.getInt("TCOutcome"));
        if ((x >> 4) != owner.x || (z >> 4) != owner.z) throw new IllegalArgumentException("Invalid saved portal owner chunk");
    }

    @Override protected synchronized void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("TCX", x); tag.putInt("TCZ", z);
        tag.putInt("TCStartX", owner.x); tag.putInt("TCStartZ", owner.z);
        tag.putInt("TCOutcome", completion.ordinal());
    }

    @Override public synchronized void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                                                   RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        if (completion.processed() || !owner.equals(chunk)) return;
        if (!TCMoundPiece.allowsPlacement(level, generator)) { reject(level); return; }
        // Source getPrecipitationHeight runs after ores/trees. Resolve actual blocking/fluid terrain at
        // FEATURES, not the raw-noise estimate used to decide both structures at STRUCTURE_STARTS.
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos support = new BlockPos(x, surface - 1, z);
        BlockPos spawn = support.above();
        if (!box.isInside(spawn) || !TCWorldgenBounds.canAccess(level, support) || !TCWorldgenBounds.canAccess(level, spawn)) {
            reject(level); return;
        }
        if (!level.getBlockState(support).is(SUPPORT)) { reject(level); return; }
        var portal = CultEntities.PORTAL.create(level.getLevel());
        if (portal == null) throw new IllegalStateException("Missing genuine cultist portal entity type");
        portal.setPos(x + .5D, surface, z + .5D);
        EventHooks.finalizeMobSpawn(portal, level, level.getCurrentDifficultyAt(spawn), MobSpawnType.STRUCTURE, null);
        // A null finalize return is legal: event cancellation only skips initialization. NeoForge's
        // separate setSpawnCancelled flag is what forbids spawning; never bypass it or load a moved chunk.
        if (!TCSurfaceAttemptRules.allowsPortalInsertion(portal.isSpawnCancelled(), portal.isRemoved(),
            portal.getBlockX() >> 4, portal.getBlockZ() >> 4, owner.x, owner.z, portal.getBlockY(),
            level.getMinBuildHeight(), level.getMaxBuildHeight())) { reject(level); return; }
        // WorldGenRegion.addFreshEntity serializes to this ProtoChunk, not the live server entity list.
        // Entity NBT and the completion flag consequently share the same owner chunk save lifecycle.
        completion = completion.resolve(level.addFreshEntity(portal));
        markOwnerUnsaved(level);
        if (completion == Completion.REJECTED) invalidateRejectedStart(level);
    }

    private void reject(WorldGenLevel level) {
        completion = completion.resolve(false);
        markOwnerUnsaved(level);
        invalidateRejectedStart(level);
    }

    private void markOwnerUnsaved(WorldGenLevel level) {
        level.getChunk(owner.x, owner.z, ChunkStatus.STRUCTURE_STARTS).setUnsaved(true);
    }

    private void invalidateRejectedStart(WorldGenLevel level) {
        ChunkAccess captured = level.getChunk(owner.x, owner.z, ChunkStatus.STRUCTURE_STARTS);
        var server = level.getLevel();
        // Match the mound's bounded, nonblocking cleanup: never load a FULL chunk or join a worker.
        server.getServer().execute(() -> {
            ChunkAccess current = server.getChunkSource().getChunkNow(owner.x, owner.z);
            if (current == null) current = captured;
            if (current instanceof ImposterProtoChunk imposter) current = imposter.getWrapped();
            for (var entry : current.getAllStarts().entrySet()) {
                if (!(entry.getKey() instanceof TCCultistPortalStructure)) continue;
                StructureStart start = current.getStartForStructure(entry.getKey());
                if (start != null && start.getPieces().stream().anyMatch(piece -> piece == this)) {
                    current.setStartForStructure(entry.getKey(), StructureStart.INVALID_START);
                    current.setUnsaved(true);
                    server.onStructureStartsAvailable(current);
                }
            }
        });
    }
}
