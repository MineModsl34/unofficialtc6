package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.api.aspects.IEssentiaTransport;

/** Actual source single-aspect 250-unit jar, including label suction and brace blocking. */
public class TileJarFillable extends BlockEntity implements IAspectSource, IEssentiaTransport {
    public static final int CAPACITY = 250;
    public Aspect aspect, aspectFilter;
    public int amount, facing = 2;
    public boolean blocked;
    protected int count;
    public TileJarFillable(BlockPos pos, BlockState state) { this(EssentiaRegistries.JAR,pos,state); }
    protected TileJarFillable(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type,pos,state); }
    @Override public AspectList getAspects() { AspectList list = new AspectList(); if (aspect != null && amount > 0) list.add(aspect,amount); return list; }
    @Override public void setAspects(AspectList list) { if (list != null && list.size() > 0) { aspect = list.getAspectsSortedByAmount()[0]; amount = list.getAmount(aspect); } }
    @Override public int addToContainer(Aspect tag, int quantity) {
        if (quantity == 0) return 0;
        if (amount < CAPACITY && tag == aspect || amount == 0) { aspect = tag; int added = Math.min(quantity,CAPACITY - amount); amount += added; quantity -= added; }
        changedAndSync(); return quantity;
    }
    @Override public boolean takeFromContainer(Aspect tag, int quantity) {
        if (amount >= quantity && tag == aspect) { amount -= quantity; if (amount <= 0) { aspect = null; amount = 0; } changedAndSync(); return true; }
        return false;
    }
    @Override public boolean takeFromContainer(AspectList list) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect tag, int quantity) { return amount >= quantity && tag == aspect; }
    @Override public boolean doesContainerContain(AspectList list) { return aspect != null && amount > 0 && java.util.Arrays.asList(list.getAspects()).contains(aspect); }
    @Override public int containerContains(Aspect tag) { return tag == aspect ? amount : 0; }
    @Override public boolean doesContainerAccept(Aspect tag) { return aspectFilter == null || tag == aspectFilter; }
    @Override public boolean isBlocked() { return blocked; }
    @Override public boolean isConnectable(Direction side) { return side == Direction.UP; }
    @Override public boolean canInputFrom(Direction side) { return side == Direction.UP; }
    @Override public boolean canOutputTo(Direction side) { return side == Direction.UP; }
    @Override public void setSuction(Aspect tag, int quantity) {}
    @Override public Aspect getSuctionType(Direction side) { return aspectFilter != null ? aspectFilter : aspect; }
    @Override public int getSuctionAmount(Direction side) { return amount < CAPACITY ? (aspectFilter != null ? 64 : 32) : 0; }
    @Override public int getMinimumSuction() { return aspectFilter != null ? 64 : 32; }
    @Override public int takeEssentia(Aspect tag, int quantity, Direction side) { return canOutputTo(side) && takeFromContainer(tag,quantity) ? quantity : 0; }
    @Override public int addEssentia(Aspect tag, int quantity, Direction side) { return canInputFrom(side) ? quantity - addToContainer(tag,quantity) : 0; }
    @Override public Aspect getEssentiaType(Direction side) { return aspect; }
    @Override public int getEssentiaAmount(Direction side) { return amount; }
    public void update() { if (level != null && !level.isClientSide && ++count % 5 == 0 && amount < CAPACITY) fillJar(); }
    protected void fillJar() {
        if (!(level.getBlockEntity(worldPosition.above()) instanceof IEssentiaTransport transport) || !transport.isConnectable(Direction.DOWN) || !transport.canOutputTo(Direction.DOWN)) return;
        Aspect tag = aspectFilter != null ? aspectFilter : amount > 0 ? aspect : null;
        if (tag == null && transport.getEssentiaAmount(Direction.DOWN) > 0 && transport.getSuctionAmount(Direction.DOWN) < getSuctionAmount(Direction.UP) && getSuctionAmount(Direction.UP) >= transport.getMinimumSuction()) tag = transport.getEssentiaType(Direction.DOWN);
        if (tag != null && transport.getSuctionAmount(Direction.DOWN) < getSuctionAmount(Direction.UP)) addToContainer(tag,transport.takeEssentia(tag,1,Direction.DOWN));
    }
    public void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) { level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2); level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock()); } }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        if (aspect != null) tag.putString("Aspect",aspect.getTag()); if (aspectFilter != null) tag.putString("AspectFilter",aspectFilter.getTag());
        tag.putShort("Amount",(short)amount); tag.putByte("facing",(byte)facing); tag.putBoolean("blocked",blocked);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag,registries); aspect = Aspect.getAspect(tag.getString("Aspect")); aspectFilter = Aspect.getAspect(tag.getString("AspectFilter")); amount = tag.getShort("Amount"); facing = tag.getByte("facing"); blocked = tag.getBoolean("blocked"); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
