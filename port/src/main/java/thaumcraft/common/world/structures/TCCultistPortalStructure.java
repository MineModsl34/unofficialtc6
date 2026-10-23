package thaumcraft.common.world.structures;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import thaumcraft.common.config.ModConfig;

/** The other source generateSurface branch, not an Outer Lands portal or a mound destination. */
public final class TCCultistPortalStructure extends Structure {
    public static final MapCodec<TCCultistPortalStructure> CODEC = simpleCodec(TCCultistPortalStructure::new);
    public static final StructureType<TCCultistPortalStructure> TYPE = () -> CODEC;

    public TCCultistPortalStructure(StructureSettings settings) { super(settings); }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!ModConfig.CONFIG_WORLD.generateStructure || context.chunkGenerator() instanceof FlatLevelSource) return Optional.empty();
        // Structure.GenerationContext.makeRandom uses ONLY worldSeed/chunkX/chunkZ, not a structure ID.
        // Using the same height query as TCMoundStructure makes the 1/100 and else-if 1/500 exclusive.
        TCSurfaceAttemptRules.Attempt attempt = TCSurfaceAttemptRules.roll(context.random()::nextInt,
            context.chunkPos().x, context.chunkPos().z, (x, z) -> context.chunkGenerator().getFirstFreeHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()),
            context.heightAccessor().getMaxBuildHeight());
        if (attempt.kind() != TCSurfaceAttemptRules.Kind.PORTAL) return Optional.empty();
        BlockPos origin = new BlockPos(attempt.x(), attempt.originY(), attempt.z());
        // Never apply WorldGenMound's five ground/air/footprint checks to the entity branch.
        return Optional.of(new GenerationStub(origin, pieces -> pieces.addPiece(new TCCultistPortalPiece(
            origin, context.chunkPos(), context.heightAccessor().getMinBuildHeight(), context.heightAccessor().getMaxBuildHeight()))));
    }

    @Override public StructureType<?> type() { return TYPE; }
}
