package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.IInfusionStabiliserExt;
import thaumcraft.common.tiles.crafting.TilePedestal;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.lib.fx.SourceFX;

public final class BlockPedestal extends BaseEntityBlock implements IInfusionStabiliserExt {
    public static final MapCodec<BlockPedestal> CODEC = simpleCodec(BlockPedestal::new);
    public BlockPedestal(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(BlockInlay.CHARGE, 0)); }
    @Override protected MapCodec<? extends BlockPedestal> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BlockInlay.CHARGE); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TilePedestal(pos, state); }
    private static boolean activate(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (level.isClientSide) return true;
        if (!(level.getBlockEntity(pos) instanceof TilePedestal tile)) return false;
        if (tile.isEmpty() && !player.getMainHandItem().isEmpty() && player.getMainHandItem().getCount() > 0) {
            tile.setItem(0, player.getItemInHand(hand).copyWithCount(1)); player.getItemInHand(hand).shrink(1);
            player.getInventory().setChanged();
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, .2F, ((level.random.nextFloat() - level.random.nextFloat()) * .7F + 1F) * 1.6F);
            return true;
        }
        if (!tile.isEmpty()) {
            ItemStack output = tile.removeItem(0, 1);
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, player.getX(), player.getY() + player.getEyeHeight() / 2F, player.getZ(), output));
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, .2F, ((level.random.nextFloat() - level.random.nextFloat()) * .7F + 1F) * 1.5F);
            return true;
        }
        return false;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return activate(level, pos, player, hand) ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return activate(level, pos, player, InteractionHand.MAIN_HAND) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) { if (!state.is(previous.getBlock())) BlockInlay.changed(level, pos); }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block changed, BlockPos from, boolean moving) { BlockInlay.changed(level, pos); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        Containers.dropContentsOnDestroy(state, next, level, pos); super.onRemove(state, level, pos, next, moving);
        if (!state.is(next.getBlock())) BlockInlay.changed(level, pos);
    }
    @Override public boolean canStabaliseInfusion(Level level, BlockPos pos) { return true; }
    @Override public float getStabilizationAmount(Level level, BlockPos pos) { return level.getBlockState(pos).is(BlocksTC.pedestalEldritch) ? .1F : 0; }
    @Override public boolean hasSymmetryPenalty(Level level, BlockPos first, BlockPos second) {
        return level.getBlockEntity(first) instanceof TilePedestal a && level.getBlockEntity(second) instanceof TilePedestal b && a.isEmpty() != b.isEmpty();
    }
    @Override public float getSymmetryPenalty(Level level, BlockPos pos) { return .1F; }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){if(state.getValue(BlockInlay.CHARGE)>0)SourceFX.local(level,"pedestal_charge",new Vec3(pos.getX(),pos.getY()-.375,pos.getZ()),Vec3.ZERO,0,0);}
}
