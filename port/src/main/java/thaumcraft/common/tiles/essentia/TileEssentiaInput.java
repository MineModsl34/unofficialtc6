package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.blocks.essentia.BlockEssentiaTransport;
import thaumcraft.common.lib.events.EssentiaHandler;

/** Real remote input with a persistent one-unit escrow. Explicit conservation adaptation:
 * source deposited remotely BEFORE checking whether Buffer arbitration allowed the drain.
 * Here the actual side/arbitration take must succeed before commit; a full/failed destination
 * retains the unit in BE NBT for retry rather than losing or duplicating it.
 */
public class TileEssentiaInput extends TransportTile implements IEssentiaTransport {
    protected int count;
    private final OneUnitEscrow<Aspect> escrow = new OneUnitEscrow<>();
    public TileEssentiaInput(BlockPos pos, BlockState state) { super(TransportRegistries.INPUT, pos, state); }
    public Direction getFacing() { return getBlockState().getValue(BlockEssentiaTransport.FACING); }
    @Override public boolean isConnectable(Direction side) { return side == getFacing().getOpposite(); }
    @Override public boolean canInputFrom(Direction side) { return isConnectable(side); }
    @Override public boolean canOutputTo(Direction side) { return false; }
    @Override public void setSuction(Aspect aspect, int amount) {}
    @Override public int getMinimumSuction() { return 0; }
    @Override public Aspect getSuctionType(Direction side) { return null; }
    @Override public int getSuctionAmount(Direction side) { return escrow.get() == null ? 128 : 0; }
    @Override public Aspect getEssentiaType(Direction side) { return escrow.get(); }
    @Override public int getEssentiaAmount(Direction side) { return escrow.get() == null ? 0 : 1; }
    @Override public int takeEssentia(Aspect aspect, int amount, Direction side) { return 0; }
    @Override public int addEssentia(Aspect aspect, int amount, Direction side) {
        if (canInputFrom(side) && amount > 0 && escrow.deposit(aspect)) { changedAndSync(); return 1; } return 0;
    }
    @Override public void readSyncNBT(CompoundTag tag) { escrow.restore(Aspect.getAspect(tag.getString("pendingType"))); }
    @Override public CompoundTag writeSyncNBT(CompoundTag tag) { if (escrow.get() != null) tag.putString("pendingType", escrow.get().getTag()); return tag; }
    @Override public void update() { if (level != null && !level.isClientSide && ++count % 5 == 0) fillJar(); }
    private void fillJar() {
        if (escrow.get() != null) { commitEscrow(); return; }
        Direction facing = getFacing(); IEssentiaTransport neighbor = TransportConnections.connectable(level, worldPosition, facing.getOpposite());
        if (neighbor == null || !neighbor.canOutputTo(facing)) return;
        if (neighbor.getEssentiaAmount(facing) > 0 && neighbor.getSuctionAmount(facing) < getSuctionAmount(facing.getOpposite()) && getSuctionAmount(facing.getOpposite()) >= neighbor.getMinimumSuction()) {
            Aspect aspect = neighbor.getEssentiaType(facing);
            if (escrow.acquire(aspect, () -> neighbor.takeEssentia(aspect, 1, facing) == 1)) { changedAndSync(); commitEscrow(); }
        }
    }
    private void commitEscrow() { if (escrow.commit(aspect -> EssentiaHandler.addEssentia(this, aspect, getFacing(), 16, false, 5))) changedAndSync(); }
}
