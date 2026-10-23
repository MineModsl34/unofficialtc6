package thaumcraft.common.blocks.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.tiles.crafting.InfusionRegistries;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;

/** Source invisible matrix model is rendered by its block-entity renderer. */
public final class BlockInfusionMatrix extends BaseEntityBlock {
    public static final MapCodec<BlockInfusionMatrix> CODEC = simpleCodec(BlockInfusionMatrix::new);
    public BlockInfusionMatrix(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BlockInfusionMatrix> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileInfusionMatrix(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, InfusionRegistries.INFUSION_MATRIX, (world, pos, block, tile) -> tile.update());
    }
}
