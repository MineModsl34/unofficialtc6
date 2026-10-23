package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.crafting.IInfusionStabiliserExt;
import thaumcraft.common.tiles.crafting.InfusionRegistries;
import thaumcraft.common.tiles.devices.TileStabilizer;

public class BlockStabilizer extends BaseEntityBlock implements IInfusionStabiliserExt {
    public static final MapCodec<BlockStabilizer> CODEC = simpleCodec(BlockStabilizer::new);
    public BlockStabilizer(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileStabilizer(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, InfusionRegistries.STABILIZER, (world, pos, block, tile) -> tile.update());
    }
    @Override public boolean canStabaliseInfusion(Level world, BlockPos pos) { return true; }
    @Override public float getStabilizationAmount(Level world, BlockPos pos) { return .25F; }
}
