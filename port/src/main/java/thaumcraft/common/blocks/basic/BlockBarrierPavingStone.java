package thaumcraft.common.blocks.basic;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.tiles.misc.BarrierRegistries;
import thaumcraft.common.tiles.misc.TileBarrierStone;

/** Only barrier paving owns a block entity; ordinary travel paving remains portable. */
public final class BlockBarrierPavingStone extends BlockPavingStone implements EntityBlock {
    public static final MapCodec<BlockBarrierPavingStone> CODEC = simpleCodec(BlockBarrierPavingStone::new);
    public BlockBarrierPavingStone(Properties properties) { super(false,properties); }
    @Override protected MapCodec<? extends BlockBarrierPavingStone> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileBarrierStone(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != BarrierRegistries.TYPE) return null;
        return (world,pos,current,tile) -> TileBarrierStone.tick(world,pos,current,(TileBarrierStone)tile);
    }
}
