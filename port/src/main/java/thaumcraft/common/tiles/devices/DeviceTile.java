package thaumcraft.common.tiles.devices;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.blocks.devices.BlockWorldDevice;

/** Native equivalent of TileThaumcraft(Inventory); inventory is synced in the BE update packet. */
public abstract class DeviceTile extends BlockEntity implements Container {
    protected final NonNullList<ItemStack> items;
    protected int counter;
    protected DeviceTile(BlockPos pos, BlockState state, int size) { super(DeviceRegistries.DEVICE,pos,state); items = NonNullList.withSize(size,ItemStack.EMPTY); }
    public static DeviceTile create(BlockPos pos, BlockState state) {
        return switch (((BlockWorldDevice)state.getBlock()).kind) {
            case GENERATOR, RECHARGE, LEVITATOR -> new TileEnergyDevice(pos,state);
            case LAMP_ARCANE, LAMP_GROWTH, LAMP_FERTILITY -> new TileLampDevice(pos,state);
            case EAR, EAR_TOGGLE, RELAY, DIOPTRA -> new TileSignalDevice(pos,state);
            case CHEST, BRAIN -> new TileStorageDevice(pos,state);
            case URN, SPA, SPRAYER -> new TileFluidDevice(pos,state);
            case CONDENSER -> new TileCondenser(pos,state);
            case CRAFTER -> new TilePatternCrafter(pos,state);
        };
    }
    public BlockWorldDevice.Kind kind() { return ((BlockWorldDevice)getBlockState().getBlock()).kind; }
    public Direction facing() { return getBlockState().getValue(BlockWorldDevice.FACING); }
    public boolean enabled() { return getBlockState().getValue(BlockWorldDevice.ENABLED); }
    public void enabled(boolean value) { if (level != null && enabled() != value) level.setBlock(worldPosition,getBlockState().setValue(BlockWorldDevice.ENABLED,value),3); }
    public abstract void update();
    public void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) { level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2); level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock()); } }
    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY; }
    public ItemStack getStackInSlot(int slot) { return getItem(slot); }
    public ItemStack getSyncedStackInSlot(int slot) { return getItem(slot); }
    @Override public ItemStack removeItem(int slot,int amount) { ItemStack out = ContainerHelper.removeItem(items,slot,amount); if (!out.isEmpty()) inventoryChanged(); return out; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items,slot); }
    @Override public void setItem(int slot,ItemStack stack) { items.set(slot,stack); if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize()); inventoryChanged(); }
    public void setInventorySlotContentsFromInfusion(int slot,ItemStack stack) { setItem(slot,stack); }
    protected void inventoryChanged() { changedAndSync(); }
    @Override public void clearContent() { items.clear(); inventoryChanged(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.saveAdditional(tag,registries); ContainerHelper.saveAllItems(tag,items,registries); writeDevice(tag,registries); }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.loadAdditional(tag,registries); items.clear(); ContainerHelper.loadAllItems(tag,items,registries); readDevice(tag,registries); }
    protected void writeDevice(CompoundTag tag,HolderLookup.Provider registries) {}
    protected void readDevice(CompoundTag tag,HolderLookup.Provider registries) {}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
