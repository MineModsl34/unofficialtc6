package thaumcraft.common.blocks.world.plants;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Shimmerleaf: grows on dirt/grass (PlantType.Plains in 1.12), the XZ offset is set in Properties. */
public class BlockPlantShimmerleaf extends BushBlock {
    public static final MapCodec<BlockPlantShimmerleaf> CODEC = simpleCodec(BlockPlantShimmerleaf::new);

    public BlockPlantShimmerleaf(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.getBlock() instanceof net.minecraft.world.level.block.FarmBlock;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        if(rand.nextInt(3)==0) {
            var point=new net.minecraft.world.phys.Vec3((float)(pos.getX()+.5F+rand.nextGaussian()*.1),(float)(pos.getY()+.4F+rand.nextGaussian()*.1),(float)(pos.getZ()+.5F+rand.nextGaussian()*.1));
            var motion=new net.minecraft.world.phys.Vec3(rand.nextGaussian()*.01,rand.nextGaussian()*.01,rand.nextGaussian()*.01);
            thaumcraft.common.lib.fx.SourceFX.local(level,"shimmerleaf",point,motion,0,0);
        }
    }
}
