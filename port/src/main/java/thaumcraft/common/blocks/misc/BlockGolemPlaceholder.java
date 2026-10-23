package thaumcraft.common.blocks.misc;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.common.blocks.crafting.BlockGolemBuilder;

/** Source invisible, non-BE rock shells. Real native drops are original bars/anvil/cauldron/table. */
public final class BlockGolemPlaceholder extends Block {
    public static final MapCodec<BlockGolemPlaceholder> CODEC = simpleCodec(BlockGolemPlaceholder::new);
    public BlockGolemPlaceholder(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    private static BlockPos core(Level level,BlockPos pos) {
        for (BlockPos other : BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1)))
            if (level.hasChunkAt(other) && level.getBlockState(other).getBlock() instanceof BlockGolemBuilder) return other.immutable();
        return null;
    }
    private static void open(Level level,BlockPos pos,Player player) { var core = core(level,pos); if (core != null) BlockGolemBuilder.open(level,core,player); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) { open(level,pos,player); return ItemInteractionResult.sidedSuccess(level.isClientSide); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) { open(level,pos,player); return InteractionResult.sidedSuccess(level.isClientSide); }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && !BlockGolemBuilder.ignore) { var core = core(level,pos); if (core != null) BlockGolemBuilder.destroy(level,core,level.getBlockState(core),pos); }
        super.onRemove(state,level,pos,next,moving);
    }
}
