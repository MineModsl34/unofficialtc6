package thaumcraft.common.blocks.essentia;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.ILabelable;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.tiles.essentia.TileAlembic;

public class BlockAlembic extends BaseEntityBlock implements ILabelable {
    public static final MapCodec<BlockAlembic> CODEC = simpleCodec(BlockAlembic::new);
    private static final VoxelShape SHAPE = box(2,0,2,14,16,14);
    public BlockAlembic(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileAlembic(pos,state); }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) { return level.getBlockEntity(pos) instanceof TileAlembic alembic ? (int)(alembic.amount / (float)alembic.maxAmount * 14F) + (alembic.amount > 0 ? 1 : 0) : 0; }
    private static boolean activate(Level level, BlockPos pos, Player player, Direction side) {
        if (!(level.getBlockEntity(pos) instanceof TileAlembic alembic)) return false;
        if (level.isClientSide) return true;
        if (player.isShiftKeyDown() && alembic.aspectFilter != null && side.get3DDataValue() == alembic.facing) { alembic.aspectFilter = null; alembic.facing = 0; popResource(level,pos,new ItemStack(ItemsTC.label)); alembic.changedAndSync(); level.playSound(null,pos,thaumcraft.common.lib.SoundsTC.page,net.minecraft.sounds.SoundSource.BLOCKS,1F,1F); }
        else if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && (alembic.aspectFilter == null || side.get3DDataValue() != alembic.facing)) { AuraHelper.polluteAura(level,pos,alembic.amount,true); alembic.amount = 0; alembic.aspect = null; alembic.changedAndSync(); level.playSound(null,pos,thaumcraft.common.lib.SoundsTC.jar,net.minecraft.sounds.SoundSource.BLOCKS,.4F,1F); }
        return true;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) { return activate(level,pos,player,hit.getDirection()) ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) { return activate(level,pos,player,hit.getDirection()) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS; }
    @Override public boolean applyLabel(Player player, BlockPos pos, Direction side, ItemStack label) {
        if (side.getAxis().isVertical() || !(player.level().getBlockEntity(pos) instanceof TileAlembic alembic) || alembic.aspectFilter != null) return false;
        var tags = label.getItem() instanceof IEssentiaContainerItem container ? container.getAspects(label) : null;
        Aspect tag = alembic.amount > 0 ? alembic.aspect : tags == null || tags.size() == 0 ? null : tags.getAspects()[0];
        if (tag == null) return false;
        alembic.aspectFilter = tag; alembic.facing = side.get3DDataValue(); alembic.changedAndSync(); player.level().playSound(null,pos,thaumcraft.common.lib.SoundsTC.page,net.minecraft.sounds.SoundSource.BLOCKS,1F,1F); return true;
    }
}
