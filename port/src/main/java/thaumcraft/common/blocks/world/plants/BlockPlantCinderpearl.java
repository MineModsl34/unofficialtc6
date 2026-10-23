package thaumcraft.common.blocks.world.plants;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.lib.TCTags;

/** Cinderpearl: sand, dirt, terracotta (PlantType.Desert + canSustainBush in 1.12), the thaumcraft:cinderpearl_soil tag. */
public class BlockPlantCinderpearl extends BushBlock {
    public static final MapCodec<BlockPlantCinderpearl> CODEC = simpleCodec(BlockPlantCinderpearl::new);

    public BlockPlantCinderpearl(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(TCTags.Blocks.CINDERPEARL_SOIL);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        if (rand.nextBoolean()) {
            double xr = pos.getX() + 0.5F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
            double yr = pos.getY() + 0.6F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
            double zr = pos.getZ() + 0.5F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
            level.addParticle(ParticleTypes.SMOKE, xr, yr, zr, 0.0, 0.0, 0.0);
            level.addParticle(ParticleTypes.FLAME, xr, yr, zr, 0.0, 0.0, 0.0);
        }
    }
}
