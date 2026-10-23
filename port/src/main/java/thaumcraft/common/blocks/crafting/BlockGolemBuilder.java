package thaumcraft.common.blocks.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.crafting.*;

/** Genuine source press core; hidden ItemBlock exists for the original multiblock blueprint. */
public final class BlockGolemBuilder extends BaseEntityBlock {
    public static final MapCodec<BlockGolemBuilder> CODEC = simpleCodec(BlockGolemBuilder::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static boolean ignore;
    public BlockGolemBuilder(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new TileGolemBuilder(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createTickerHelper(type,Golem9BuilderRegistries.TYPE,TileGolemBuilder::tick); }
    public static void open(Level level,BlockPos pos,Player player) { if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileGolemBuilder tile) player.openMenu(tile); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) { open(level,pos,player); return ItemInteractionResult.sidedSuccess(level.isClientSide); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) { open(level,pos,player); return InteractionResult.sidedSuccess(level.isClientSide); }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!state.is(next.getBlock())) { destroy(level,pos,state,pos); Containers.dropContentsOnDestroy(state,next,level,pos); }
        super.onRemove(state,level,pos,next,moving);
    }
    public static void destroy(Level level,BlockPos pos,BlockState state,BlockPos start) {
        if (ignore || level.isClientSide) return; ignore = true;
        try {
            for (BlockPos other : BlockPos.betweenClosed(pos.offset(-1,0,-1),pos.offset(1,1,1))) {
                if (other.equals(start) || !level.hasChunkAt(other)) continue; var old = level.getBlockState(other);
                Block replacement = old.is(BlocksTC.placeholderBars) ? Blocks.IRON_BARS : old.is(BlocksTC.placeholderAnvil) ? Blocks.ANVIL
                    : old.is(BlocksTC.placeholderCauldron) ? Blocks.CAULDRON : old.is(BlocksTC.placeholderTable) ? BlocksTC.tableStone : null;
                if (replacement != null) level.setBlock(other,replacement.defaultBlockState(),3);
            }
            if (!pos.equals(start)) level.setBlock(pos,Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP),3);
        } finally { ignore = false; }
    }
}
