package thaumcraft.common.blocks.essentia;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Source non-BE attachment; facing points IN to the smelter. Front face may not be occupied. */
public class BlockSmelterAux extends Block {
    public static final MapCodec<BlockSmelterAux> CODEC = simpleCodec(BlockSmelterAux::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public BlockSmelterAux(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH)); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction side = context.getClickedFace();
        if (!side.getAxis().isHorizontal()) return null;
        BlockState state = defaultBlockState().setValue(FACING,side.getOpposite());
        return state.canSurvive(context.getLevel(),context.getClickedPos()) ? state : null;
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockState source = level.getBlockState(pos.relative(facing));
        return source.getBlock() instanceof BlockSmelter && source.getValue(BlockSmelter.FACING) != facing.getOpposite();
    }
}
