package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.crafting.BlockThaumatorium;

/** Source brain box has NO block entity and NO enabled property despite its old marker. */
public final class BlockBrainBox extends Block {
    public static final MapCodec<BlockBrainBox> CODEC = simpleCodec(BlockBrainBox::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public BlockBrainBox(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP)); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { BlockState state = defaultBlockState().setValue(FACING,context.getClickedFace().getOpposite()); return state.canSurvive(context.getLevel(),context.getClickedPos()) ? state : null; }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        BlockState target = level.getBlockState(pos.relative(state.getValue(FACING)));
        return (target.is(BlocksTC.thaumatorium) || target.is(BlocksTC.thaumatoriumTop)) && target.getValue(BlockThaumatorium.FACING) != state.getValue(FACING).getOpposite();
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        BlockState target = level.getBlockState(pos.relative(state.getValue(FACING)));
        // Source attached boxes survive target rotation, and fall only when both machine parts vanish.
        if (!level.isClientSide && !target.is(BlocksTC.thaumatorium) && !target.is(BlocksTC.thaumatoriumTop)) level.destroyBlock(pos,true);
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return box(3,3,3,13,13,13); }
}
