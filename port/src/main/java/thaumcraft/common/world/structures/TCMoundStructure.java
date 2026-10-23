package thaumcraft.common.world.structures;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import thaumcraft.common.config.ModConfig;

/** TC6 every-chunk surface attempt adapted to 1.21's start-before-terrain structure lifecycle. */
public class TCMoundStructure extends Structure {
    public static final MapCodec<TCMoundStructure> CODEC = simpleCodec(TCMoundStructure::new);
    public static final StructureType<TCMoundStructure> TYPE = () -> CODEC;

    public TCMoundStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!ModConfig.CONFIG_WORLD.generateStructure || context.chunkGenerator() instanceof FlatLevelSource) {
            return Optional.empty();
        }
        // GenerationContext contains a ChunkAccess, not a dimension/Level. Dimension and blacklist
        // checks therefore happen again, authoritatively, in FEATURES before any piece terrain read/write.
        return candidate(context.random(), context.chunkPos(), (x, z) -> context.chunkGenerator()
            .getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()),
            context.heightAccessor().getMaxBuildHeight()).flatMap(origin -> {
                // Starts precede built surface terrain. Reject impossible raw-noise slopes/water here,
                // so obvious invalid candidates do not become empty /locate destinations. The same
                // five guards on actual biome-built terrain remain authoritative in FEATURES.
                Map<Long, NoiseColumn> columns = new HashMap<>();
                if (!TCMoundPiece.validRawLocation(origin, pos -> columns.computeIfAbsent(
                    ChunkPos.asLong(pos.getX(), pos.getZ()), key -> context.chunkGenerator().getBaseColumn(
                        pos.getX(), pos.getZ(), context.heightAccessor(), context.randomState())).getBlock(pos.getY()))) {
                    return Optional.empty();
                }
                return Optional.of(new GenerationStub(origin, pieces -> pieces.addPiece(
                    new TCMoundPiece(context.structureTemplateManager(), origin, context.seed(), context.chunkPos()))));
            });
    }

    /** Original order: two nextInt(9) offsets, precipitation-height -9, then 1/100 mound or 1/500 portal. */
    public static Optional<BlockPos> candidate(RandomSource random, ChunkPos chunk, IntBinaryOperator height, int maxHeight) {
        TCSurfaceAttemptRules.Attempt attempt = TCSurfaceAttemptRules.roll(random::nextInt, chunk.x, chunk.z, height, maxHeight);
        // Keep this public mound-only API, its RNG consumption and every existing mound guard unchanged.
        // The separate portal Structure gets the same GenerationContext large-feature seed and roll.
        return attempt.kind() == TCSurfaceAttemptRules.Kind.MOUND
            ? Optional.of(new BlockPos(attempt.x(), attempt.originY(), attempt.z())) : Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return TYPE;
    }
}
