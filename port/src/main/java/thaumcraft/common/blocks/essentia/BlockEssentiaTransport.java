package thaumcraft.common.blocks.essentia;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.tiles.essentia.*;
import thaumcraft.api.aura.AuraHelper;

public class BlockEssentiaTransport extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final MapCodec<BlockEssentiaTransport> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(propertiesCodec(),
        Codec.BOOL.fieldOf("output").forGetter(block -> block.output)).apply(instance, BlockEssentiaTransport::new));
    private final boolean output;
    public BlockEssentiaTransport(Properties properties, boolean output) { super(properties); this.output = output; registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getClickedFace()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return output ? new TileEssentiaOutput(pos, state) : new TileEssentiaInput(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return (world, pos, current, tile) -> { if (tile instanceof TransportTile transport) transport.update(); }; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return switch (state.getValue(FACING)) {
        case UP -> box(4, 0, 4, 12, 8, 12); case NORTH -> box(4, 4, 8, 12, 12, 16); case SOUTH -> box(4, 4, 0, 12, 12, 8);
        case WEST -> box(8, 4, 4, 16, 12, 12); case EAST -> box(0, 4, 4, 8, 12, 12); case DOWN -> box(4, 8, 4, 12, 16, 12);
    }; }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) { return Shapes.empty(); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        // An interrupted escrow remains real matter: breaking its input releases one flux,
        // exactly the native pipe destruction sink, rather than creating an unaccounted drop.
        if (state.getBlock() != replacement.getBlock() && !level.isClientSide && level.getBlockEntity(pos) instanceof TileEssentiaInput input && input.getEssentiaAmount(null) > 0)
            AuraHelper.polluteAura(level, pos, input.getEssentiaAmount(null), true);
        super.onRemove(state, level, pos, replacement, moved);
    }
}
