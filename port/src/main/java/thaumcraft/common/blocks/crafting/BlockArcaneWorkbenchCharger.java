package thaumcraft.common.blocks.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.blocks.BlocksTC;

/** Original charger is a plain block, not a ticking/block-entity machine. The table
 * below discovers it when pooling nine aura chunks. Source full-cube collision retained. */
public final class BlockArcaneWorkbenchCharger extends Block {
    public static final MapCodec<BlockArcaneWorkbenchCharger> CODEC = simpleCodec(BlockArcaneWorkbenchCharger::new);
    public BlockArcaneWorkbenchCharger(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BlockArcaneWorkbenchCharger> codec() { return CODEC; }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Block support = level.getBlockState(pos.below()).getBlock();
        return support == BlocksTC.arcaneWorkbench || support == BlocksTC.wandWorkbench;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        if (!state.canSurvive(context.getLevel(), context.getClickedPos())) return null;
        BlockPos below = context.getClickedPos().below();
        BlockState support = context.getLevel().getBlockState(below);
        context.getLevel().sendBlockUpdated(below, support, support, Block.UPDATE_CLIENTS);
        return state;
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos changedPos, boolean moving) {
        if (!level.isClientSide && !state.canSurvive(level, pos)) {
            dropResources(state, level, pos); level.removeBlock(pos, false);
        }
    }
    private static void open(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos.below()) instanceof MenuProvider menu) player.openMenu(menu);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        open(level, pos, player); return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        open(level, pos, player); return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
