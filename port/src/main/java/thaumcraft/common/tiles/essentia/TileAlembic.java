package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;

public class TileAlembic extends BlockEntity implements IAspectContainer, IEssentiaTransport {
    public Aspect aspect, aspectFilter;
    public int amount, maxAmount = 128, facing;
    public boolean aboveFurnace;
    public TileAlembic(BlockPos pos, BlockState state) { super(EssentiaRegistries.ALEMBIC,pos,state); }
    @Override public AspectList getAspects() { return aspect == null ? new AspectList() : new AspectList().add(aspect,amount); }
    @Override public void setAspects(AspectList aspects) {}
    @Override public int addToContainer(Aspect tag, int quantity) {
        if (aspectFilter != null && tag != aspectFilter) return quantity;
        if (amount < maxAmount && tag == aspect || amount == 0) { aspect = tag; int added = Math.min(quantity,maxAmount - amount); amount += added; quantity -= added; }
        changedAndSync(); return quantity;
    }
    @Override public boolean takeFromContainer(Aspect tag, int quantity) {
        if (amount == 0 || aspect == null) { amount = 0; aspect = null; }
        if (aspect != null && amount >= quantity && tag == aspect) { amount -= quantity; if (amount <= 0) { amount = 0; aspect = null; } changedAndSync(); return true; }
        return false;
    }
    @Override public boolean takeFromContainer(AspectList list) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect tag, int quantity) { return amount >= quantity && tag == aspect; }
    @Override public boolean doesContainerContain(AspectList list) { return amount > 0 && aspect != null && list.getAmount(aspect) > 0; }
    @Override public int containerContains(Aspect tag) { return tag == aspect ? amount : 0; }
    @Override public boolean doesContainerAccept(Aspect tag) { return true; }
    @Override public boolean isConnectable(Direction side) { return side != Direction.DOWN && side != Direction.from3DDataValue(facing); }
    @Override public boolean canOutputTo(Direction side) { return isConnectable(side); }
    @Override public boolean canInputFrom(Direction side) { return false; }
    @Override public void setSuction(Aspect tag, int quantity) {}
    @Override public Aspect getSuctionType(Direction side) { return null; }
    @Override public int getSuctionAmount(Direction side) { return 0; }
    @Override public int getMinimumSuction() { return 0; }
    @Override public int takeEssentia(Aspect tag, int quantity, Direction side) { return canOutputTo(side) && takeFromContainer(tag,quantity) ? quantity : 0; }
    @Override public int addEssentia(Aspect tag, int quantity, Direction side) { return 0; }
    @Override public Aspect getEssentiaType(Direction side) { return aspect; }
    @Override public int getEssentiaAmount(Direction side) { return amount; }
    public static boolean processAlembics(Level level, BlockPos furnace, Aspect tag) {
        BlockPos pos = furnace.above();
        while (level.getBlockEntity(pos) instanceof TileAlembic alembic) { if (alembic.aspect == tag && alembic.amount > 0 && alembic.addToContainer(tag,1) == 0) return true; pos = pos.above(); }
        pos = furnace.above();
        while (level.getBlockEntity(pos) instanceof TileAlembic alembic) { if ((alembic.aspectFilter == null || alembic.aspectFilter == tag) && alembic.addToContainer(tag,1) == 0) return true; pos = pos.above(); }
        return false;
    }
    public void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) { level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2); level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock()); } }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag,registries); if (aspect != null) tag.putString("aspect",aspect.getTag()); if (aspectFilter != null) tag.putString("AspectFilter",aspectFilter.getTag()); tag.putShort("amount",(short)amount); tag.putByte("facing",(byte)facing); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag,registries); aspect = Aspect.getAspect(tag.getString("aspect")); aspectFilter = Aspect.getAspect(tag.getString("AspectFilter")); amount = tag.getShort("amount"); facing = tag.getByte("facing"); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
