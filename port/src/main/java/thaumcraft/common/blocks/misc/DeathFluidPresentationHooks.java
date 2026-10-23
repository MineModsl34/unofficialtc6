package thaumcraft.common.blocks.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/** Dist-safe source particle hook; client construction never enters fluid/server classes. */
public final class DeathFluidPresentationHooks {
    public interface Presentation { void bubble(Level level, BlockPos pos, int metadata, RandomSource random); }
    private static Presentation client = (level, pos, metadata, random) -> {};
    private DeathFluidPresentationHooks() {}
    public static void install(Presentation presentation) { client = java.util.Objects.requireNonNull(presentation); }
    public static void bubble(Level level, BlockPos pos, int metadata, RandomSource random) { client.bubble(level, pos, metadata, random); }
}
