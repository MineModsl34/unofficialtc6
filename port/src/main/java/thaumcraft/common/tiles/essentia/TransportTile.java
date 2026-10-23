package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.Connection;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.blocks.essentia.BlockTube;

/** Native persistent/synchronized equivalent of TileThaumcraft for the transport family. */
public abstract class TransportTile extends BlockEntity {
    protected TransportTile(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public abstract void update();
    public void readSyncNBT(CompoundTag tag) {}
    public CompoundTag writeSyncNBT(CompoundTag tag) { return tag; }
    public void markDirty() { setChanged(); }
    public void syncTile(boolean rerender) {
        if (level != null && !level.isClientSide) {
            if (getBlockState().getBlock() instanceof BlockTube tube) tube.refreshConnections(level, worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), rerender ? 3 : 2);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            invalidateCapabilities();
        }
    }
    public void changedAndSync() { markDirty(); syncTile(false); }
    public boolean gettingPower() { return level != null && level.hasNeighborSignal(worldPosition); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) { super.saveAdditional(tag, provider); writeSyncNBT(tag); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) { super.loadAdditional(tag, provider); readSyncNBT(tag); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) { return writeSyncNBT(new CompoundTag()); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    /** Native default ignores empty packets; empty is meaningful for cleared input/centrifuge. */
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider provider) {
        loadWithComponents(packet.getTag(), provider); clientRebuild();
    }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) { loadWithComponents(tag, provider); clientRebuild(); }
    private void clientRebuild() { if (level != null && level.isClientSide) { requestModelDataUpdate(); level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); } }
}
