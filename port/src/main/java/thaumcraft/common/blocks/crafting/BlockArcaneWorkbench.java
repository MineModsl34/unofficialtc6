package thaumcraft.common.blocks.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;

/** Original nonopaque wood device, full cube collision, no facing property. */
public final class BlockArcaneWorkbench extends BaseEntityBlock {
    public static final MapCodec<BlockArcaneWorkbench> CODEC = simpleCodec(BlockArcaneWorkbench::new);
    public BlockArcaneWorkbench(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BlockArcaneWorkbench> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileArcaneWorkbench(pos, state); }
    private static void open(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileArcaneWorkbench table) player.openMenu(table);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
        Player player, InteractionHand hand, BlockHitResult hit) { open(level, pos, player); return ItemInteractionResult.sidedSuccess(level.isClientSide); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        open(level, pos, player); return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        Containers.dropContentsOnDestroy(state, replacement, level, pos); super.onRemove(state, level, pos, replacement, moving);
    }
}
