package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import thaumcraft.common.tiles.devices.ProcessingRegistries;
import thaumcraft.common.tiles.devices.TileBellows;

public final class BlockBellows extends BaseEntityBlock {
    public static final MapCodec<BlockBellows> CODEC = simpleCodec(BlockBellows::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");
    public BlockBellows(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP).setValue(ENABLED,true)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,ENABLED); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getClickedFace().getOpposite()).setValue(ENABLED,!context.getLevel().hasNeighborSignal(context.getClickedPos())); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new TileBellows(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createTickerHelper(type,ProcessingRegistries.BELLOWS,TileBellows::tick); }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        if (!level.isClientSide) { boolean enabled = !level.hasNeighborSignal(pos); if (enabled != state.getValue(ENABLED)) level.setBlock(pos,state.setValue(ENABLED,enabled),3); }
    }
}
