package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.blocks.devices.BlockMirror;
import thaumcraft.common.lib.crafting.MirrorLinkRules;

/** Shared source pair protocol. Never initializes dimensions or loads remote chunks.
 * An unavailable destination is a temporary failure, not proof that the stored pair broke.
 * Native dimension ids replace source numeric ids; legacy vanilla ids 0/-1/1 can be imported.
 */
public abstract class TileMirrorBase extends BlockEntity {
    public boolean linked;
    public GlobalPos destination;
    public int instability;
    protected int count, inc = 40;
    protected TileMirrorBase(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type,pos,state); }
    protected abstract boolean essentia();
    public final Direction facing() { return getBlockState().getValue(BlockMirror.FACING); }
    public final TileMirrorBase destinationTile() {
        if (!(level instanceof ServerLevel own) || destination == null) return null;
        ServerLevel target = own.getServer().getLevel(destination.dimension());
        if (target == null || !target.hasChunkAt(destination.pos())) return null;
        return target.getBlockEntity(destination.pos()) instanceof TileMirrorBase mirror && mirror.essentia() == essentia() ? mirror : null;
    }
    private boolean destinationAvailable() {
        if (!(level instanceof ServerLevel own) || destination == null) return false;
        ServerLevel target = own.getServer().getLevel(destination.dimension());
        return target != null && target.hasChunkAt(destination.pos());
    }
    public boolean isLinkValidSimple() {
        TileMirrorBase other = destinationTile();
        return linked && other != null && other != this && other.linked && level != null
            && GlobalPos.of(level.dimension(),worldPosition).equals(other.destination);
    }
    public boolean isLinkValid() {
        if (!linked) return false;
        if (isLinkValidSimple()) return true;
        if (destinationAvailable()) { linked = false; changedAndSync(); }
        return false;
    }
    public boolean isDestinationValid() {
        TileMirrorBase other = destinationTile();
        if (other != null && other != this) return !other.isLinkValid();
        if (destinationAvailable()) { linked = false; changedAndSync(); }
        return false;
    }
    public void restoreLink() {
        if (!isDestinationValid()) return;
        TileMirrorBase other = destinationTile();
        if (other == null || level == null) return;
        other.destination = GlobalPos.of(level.dimension(),worldPosition);
        other.linked = linked = true;
        other.changedAndSync(); changedAndSync();
    }
    public void invalidateLink() {
        TileMirrorBase other = destinationTile();
        // Source invalidates the remote mirror on removal. Only the reciprocal actual pair
        // is changed, so a stale item cannot disconnect somebody else's newer pair.
        if (other != null && level != null && GlobalPos.of(level.dimension(),worldPosition).equals(other.destination)) {
            other.linked = false; other.changedAndSync();
        }
    }
    protected void updateLink() {
        if (level == null || level.isClientSide) return;
        if (MirrorLinkRules.pollutes(instability,essentia())) {
            AuraHelper.polluteAura(level,worldPosition,1F,true);
            instability -= MirrorLinkRules.pollutionThreshold(essentia()); setChanged();
        }
        if (instability > 0 && count % 100 == 0) { instability--; setChanged(); }
        if (count++ % inc == 0) {
            boolean valid = isLinkValidSimple(); inc = MirrorLinkRules.retryInterval(inc,valid);
            if (!valid) restoreLink();
        }
    }
    protected void addInstability(int amount) { instability += amount; setChanged(); }
    public void changedAndSync() {
        setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    protected void saveLink(CompoundTag tag) {
        tag.putBoolean("linked",linked); tag.putInt("instability",instability);
        if (destination != null) {
            tag.putString("linkDimension",destination.dimension().location().toString());
            tag.putInt("linkX",destination.pos().getX()); tag.putInt("linkY",destination.pos().getY()); tag.putInt("linkZ",destination.pos().getZ());
        }
    }
    protected void loadLink(CompoundTag tag) {
        linked = tag.getBoolean("linked"); instability = Math.max(0,tag.getInt("instability")); destination = null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("linkDimension"));
        if (id == null && tag.contains("linkDim")) id = switch(tag.getInt("linkDim")) { case 0 -> Level.OVERWORLD.location(); case -1 -> Level.NETHER.location(); case 1 -> Level.END.location(); default -> null; };
        if (id != null) destination = GlobalPos.of(ResourceKey.create(Registries.DIMENSION,id),new BlockPos(tag.getInt("linkX"),tag.getInt("linkY"),tag.getInt("linkZ")));
        if (destination == null) linked = false;
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.saveAdditional(tag,registries); saveLink(tag); }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.loadAdditional(tag,registries); loadLink(tag); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); saveLink(tag); return tag; }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries) { loadLink(tag); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries) { if(packet.getTag()!=null) loadLink(packet.getTag()); }
}
