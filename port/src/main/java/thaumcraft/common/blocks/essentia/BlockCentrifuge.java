package thaumcraft.common.blocks.essentia;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.tiles.essentia.TileCentrifuge;

public class BlockCentrifuge extends BaseEntityBlock {
    public static final MapCodec<BlockCentrifuge> CODEC = simpleCodec(BlockCentrifuge::new);
    public BlockCentrifuge(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileCentrifuge(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return (world, pos, current, tile) -> { if (tile instanceof TileCentrifuge centrifuge) centrifuge.update(); }; }
}
