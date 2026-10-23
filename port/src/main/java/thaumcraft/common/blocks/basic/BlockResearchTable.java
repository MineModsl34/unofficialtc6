package thaumcraft.common.blocks.basic;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/** Source full-cube collision/nonopaque device, with NO solid support faces. Its physical
 * two-slot theorycraft menu, source cards and dynamic scroll/inkwell/quill renderer are native. */
public final class BlockResearchTable extends BaseEntityBlock {
    public static final MapCodec<BlockResearchTable> CODEC = simpleCodec(BlockResearchTable::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BlockResearchTable(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BlockResearchTable> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Source SAME player facing, unlike BlockTCDevice's usual opposite/sneak behavior.
        Direction facing = context.getPlayer() == null ? context.getHorizontalDirection() : legacyFacing(context.getPlayer().getYRot());
        return defaultBlockState().setValue(FACING, facing);
    }

    public static Direction legacyFacing(float yaw) {
        return Direction.from2DDataValue(Mth.floor((double) (yaw * 4.0F / 360.0F) + .5D) & 3);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }

    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileResearchTable(pos, state); }

    private static void open(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileResearchTable table) player.openMenu(table);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hit) {
        open(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        open(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        // Changing FACING keeps the BE/content; actual replacement spills both slots once.
        Containers.dropContentsOnDestroy(state, replacement, level, pos);
        super.onRemove(state, level, pos, replacement, moving);
    }
}
