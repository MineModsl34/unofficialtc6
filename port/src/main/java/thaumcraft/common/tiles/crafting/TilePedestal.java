package thaumcraft.common.tiles.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.blocks.devices.BlockInlay;

/** Source one-slot, one-item pedestal with public inventory aliases used by infusion. */
public final class TilePedestal extends BlockEntity implements Container {
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    public TilePedestal(BlockPos pos, BlockState state) { super(InfusionRegistries.PEDESTAL, pos, state); }
    @Override public int getContainerSize() { return 1; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return items.get(0).isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? items.get(0) : ItemStack.EMPTY; }
    public ItemStack getStackInSlot(int slot) { return getItem(slot); }
    public ItemStack getSyncedStackInSlot(int slot) { return getItem(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { ItemStack out = slot == 0 ? ContainerHelper.removeItem(items, 0, amount) : ItemStack.EMPTY; if (!out.isEmpty()) changedAndSync(); return out; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return slot == 0 ? ContainerHelper.takeItem(items, 0) : ItemStack.EMPTY; }
    @Override public void setItem(int slot, ItemStack stack) { if (slot == 0) { items.set(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(Math.min(1, stack.getCount()))); changedAndSync(); } }
    public void setInventorySlotContents(int slot, ItemStack stack) { setItem(slot, stack); }
    public void setInventorySlotContentsFromInfusion(int slot, ItemStack stack) { setItem(slot, stack); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && (stack.isEmpty() || isEmpty()); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public void clearContent() { items.clear(); changedAndSync(); }
    public void markDirty() { setChanged(); }
    public void syncTile(boolean force) { changedAndSync(); }
    public void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); }
    public BlockPos findInstabilityMitigator() {
        if (level == null || !getBlockState().hasProperty(BlockInlay.CHARGE) || getBlockState().getValue(BlockInlay.CHARGE) <= 0) return null;
        return seekSource(worldPosition, getBlockState().getValue(BlockInlay.CHARGE));
    }
    private BlockPos seekSource(BlockPos pos, int lastCharge) {
        for (Direction direction : BlockInlay.HORIZONTALS) {
            BlockPos next = pos.relative(direction);
            if (BlockInlay.getSourceStrengthAt(level, next) >= 5) return next;
            BlockState state = level.getBlockState(next);
            if (state.hasProperty(BlockInlay.CHARGE) && state.getValue(BlockInlay.CHARGE) > lastCharge) {
                BlockPos found = seekSource(next, state.getValue(BlockInlay.CHARGE)); if (found != null) return found;
            }
        }
        return null;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); ContainerHelper.saveAllItems(tag, items, registries); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); items.clear(); ContainerHelper.loadAllItems(tag, items, registries); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public boolean triggerEvent(int id, int param) {
        if (id != 5 && id != 11 && id != 12) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) thaumcraft.common.lib.fx.SourceFX.local(level,"pedestal",net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition),net.minecraft.world.phys.Vec3.ZERO,0,id);
        return true;
    }
}
