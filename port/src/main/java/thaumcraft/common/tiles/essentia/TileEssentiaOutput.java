package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.blocks.essentia.BlockEssentiaTransport;
import thaumcraft.common.lib.events.EssentiaHandler;

/** Confirmed remote drain: only consume the real source after adjacent output accepts one. */
public class TileEssentiaOutput extends TransportTile implements IEssentiaTransport {
    private int count;
    public TileEssentiaOutput(BlockPos pos, BlockState state) { super(TransportRegistries.OUTPUT, pos, state); }
    public Direction getFacing() { return getBlockState().getValue(BlockEssentiaTransport.FACING); }
    @Override public boolean isConnectable(Direction side) { return side == getFacing().getOpposite(); }
    @Override public boolean canInputFrom(Direction side) { return false; }
    @Override public boolean canOutputTo(Direction side) { return isConnectable(side); }
    @Override public void setSuction(Aspect aspect, int amount) {}
    @Override public int getMinimumSuction() { return 0; }
    @Override public Aspect getSuctionType(Direction side) { return null; }
    @Override public int getSuctionAmount(Direction side) { return 0; }
    @Override public Aspect getEssentiaType(Direction side) { return null; }
    @Override public int getEssentiaAmount(Direction side) { return 0; }
    @Override public int takeEssentia(Aspect aspect, int amount, Direction side) { return 0; }
    @Override public int addEssentia(Aspect aspect, int amount, Direction side) { return amount; }
    @Override public void update() { if (level != null && !level.isClientSide && ++count % 5 == 0) fillBuffer(); }
    private void fillBuffer() {
        Direction facing = getFacing(); IEssentiaTransport neighbor = TransportConnections.connectable(level, worldPosition, facing.getOpposite());
        if (neighbor == null || !neighbor.canInputFrom(facing)) return;
        if (neighbor.getSuctionAmount(facing) > 0 && neighbor.getSuctionType(facing) != null) {
            Aspect aspect = neighbor.getSuctionType(facing);
            if (EssentiaHandler.drainEssentiaWithConfirmation(this, aspect, facing, 16, false, 5) && neighbor.addEssentia(aspect, 1, facing) > 0) EssentiaHandler.confirmDrain();
        }
    }
}
