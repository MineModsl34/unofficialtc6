package thaumcraft.common.blocks.misc;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.blocks.devices.BlockInfernalFurnace;

/** Genuine invisible non-BE multiblock shells. They drop the original brick/obsidian. */
public final class BlockInfernalPlaceholder extends Block {
    public static final MapCodec<BlockInfernalPlaceholder> CODEC = simpleCodec(BlockInfernalPlaceholder::new);
    public BlockInfernalPlaceholder(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && !BlockInfernalFurnace.ignore) {
            for (BlockPos neighbor : BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1))) {
                BlockState core = level.getBlockState(neighbor);
                if (core.getBlock() instanceof BlockInfernalFurnace) { BlockInfernalFurnace.destroyFurnace(level,neighbor.immutable(),core,pos); break; }
            }
        }
        super.onRemove(state,level,pos,next,moving);
    }
}
