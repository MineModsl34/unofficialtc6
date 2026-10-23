package thaumcraft.common.blocks.basic;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.crafting.IInfusionStabiliserExt;

/** Tallow candle: stands on a full top face, stabilises infusion (0.1). The colour is a tint by dye (ColorHandler). */
public class BlockCandle extends Block implements IInfusionStabiliserExt {
    public static final MapCodec<BlockCandle> CODEC = simpleCodec(p -> new BlockCandle(DyeColor.WHITE, p));
    private static final VoxelShape SHAPE = box(6, 0, 6, 10, 8, 10);
    public final DyeColor dye;

    public BlockCandle(DyeColor dye, Properties properties) {
        super(properties);
        this.dye = dye;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.7;
        double z = pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
    }

    @Override
    public boolean canStabaliseInfusion(Level level, BlockPos pos) {
        return true;
    }

    @Override
    public float getStabilizationAmount(Level level, BlockPos pos) {
        return 0.1F;
    }
}
