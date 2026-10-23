package thaumcraft.common.blocks.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import thaumcraft.common.tiles.crafting.*;

/** Source wand_workbench identity, nonopaque stone device with source full collision. */
public final class BlockFocalManipulator extends BaseEntityBlock {
    public static final MapCodec<BlockFocalManipulator> CODEC = simpleCodec(BlockFocalManipulator::new);
    public BlockFocalManipulator(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos) { return Shapes.empty(); }
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos) { return Shapes.empty(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new TileFocalManipulator(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createTickerHelper(type,FocalRegistries.TYPE,TileFocalManipulator::tick); }
    private static void open(Level level,BlockPos pos,Player player) { if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileFocalManipulator table) player.openMenu(table); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) { open(level,pos,player); return ItemInteractionResult.sidedSuccess(level.isClientSide); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) { open(level,pos,player); return InteractionResult.sidedSuccess(level.isClientSide); }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) { Containers.dropContentsOnDestroy(state,next,level,pos); super.onRemove(state,level,pos,next,moving); }
}
