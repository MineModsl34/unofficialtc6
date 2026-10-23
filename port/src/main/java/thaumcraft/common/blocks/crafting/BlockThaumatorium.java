package thaumcraft.common.blocks.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.crafting.TileThaumatorium;
import thaumcraft.common.tiles.crafting.TileThaumatoriumTop;
import thaumcraft.common.tiles.devices.ProcessingRegistries;

public final class BlockThaumatorium extends BaseEntityBlock {
    public static final MapCodec<BlockThaumatorium> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
        com.mojang.serialization.Codec.BOOL.fieldOf("top").forGetter(block -> block.top),propertiesCodec()).apply(instance,BlockThaumatorium::new));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final boolean top;
    public BlockThaumatorium(boolean top,Properties properties) { super(properties); this.top = top; registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return top ? RenderShape.INVISIBLE : RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return top ? new TileThaumatoriumTop(pos,state) : new TileThaumatorium(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return top ? null : createTickerHelper(type,ProcessingRegistries.THAUMATORIUM,TileThaumatorium::tick); }
    private void open(Level level,BlockPos pos,Player player) { if (!level.isClientSide && !player.isShiftKeyDown() && level.getBlockEntity(top ? pos.below() : pos) instanceof TileThaumatorium tile) player.openMenu(tile); }
    @Override protected ItemInteractionResult useItemOn(ItemStack held,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) { open(level,pos,player); return ItemInteractionResult.sidedSuccess(level.isClientSide); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) { open(level,pos,player); return InteractionResult.sidedSuccess(level.isClientSide); }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide) {
            // Only the bottom owns catalyst items. Dropping the forwarding top would duplicate them.
            if (!top) net.minecraft.world.Containers.dropContentsOnDestroy(state,next,level,pos);
            BlockPos other = top ? pos.below() : pos.above();
            if (level.getBlockState(other).is(top ? BlocksTC.thaumatorium : BlocksTC.thaumatoriumTop)) level.setBlock(other,BlocksTC.metalAlchemical.defaultBlockState(),3);
        }
        super.onRemove(state,level,pos,next,moving);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        if (!level.isClientSide && !top && !level.getBlockState(pos.below()).is(BlocksTC.crucible)) level.setBlock(pos,BlocksTC.metalAlchemical.defaultBlockState(),3);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return !top; }
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos) { return top ? 0 : net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos)); }
}
