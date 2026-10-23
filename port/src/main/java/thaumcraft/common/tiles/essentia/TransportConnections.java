package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumcraft.api.aspects.EssentiaCapabilities;
import thaumcraft.api.aspects.IEssentiaTransport;

/** Source neighbor lookup first; optional native add-on capability, never forces chunk loading. */
public final class TransportConnections {
    private TransportConnections() {}
    public static BlockEntity tile(BlockGetter world, BlockPos pos) {
        return world instanceof Level level && !level.hasChunkAt(pos) ? null : world.getBlockEntity(pos);
    }
    public static IEssentiaTransport at(BlockGetter world, BlockPos pos, Direction side) {
        BlockEntity tile = tile(world, pos);
        if (tile instanceof IEssentiaTransport transport) return transport;
        return world instanceof Level level && level.hasChunkAt(pos) ? level.getCapability(EssentiaCapabilities.TRANSPORT, pos, side) : null;
    }
    public static IEssentiaTransport connectable(BlockGetter world, BlockPos pos, Direction side) {
        if (side == null) return null;
        IEssentiaTransport transport = at(world, pos.relative(side), side.getOpposite());
        return transport != null && transport.isConnectable(side.getOpposite()) ? transport : null;
    }
    /** Exact old helper result: only a genuine transport TileEntity, not a capability wrapper. */
    public static BlockEntity getConnectableTile(BlockGetter world, BlockPos pos, Direction side) {
        if (side == null) return null;
        BlockEntity tile = tile(world, pos.relative(side));
        return tile instanceof IEssentiaTransport transport && transport.isConnectable(side.getOpposite()) ? tile : null;
    }
}
