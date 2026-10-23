package thaumcraft.common.tiles.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.container.ContainerArcaneWorkbench;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;
import thaumcraft.common.world.aura.AuraHandler;

/** Source 9 crafting + 6 crystal inventory survives menu close and world save. */
public final class TileArcaneWorkbench extends BlockEntity implements thaumcraft.api.crafting.IArcaneWorkbench, MenuProvider {
    private final NonNullList<ItemStack> items = NonNullList.withSize(15, ItemStack.EMPTY);
    public TileArcaneWorkbench(BlockPos pos, BlockState state) { super(ArcaneWorkbenchRegistries.ARCANE_WORKBENCH, pos, state); }
    @Override public int getContainerSize() { return 15; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < 15 ? items.get(slot) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= 15 || amount <= 0) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(items, slot, amount); if (!result.isEmpty()) setChanged(); return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return slot >= 0 && slot < 15 ? ContainerHelper.takeItem(items, slot) : ItemStack.EMPTY; }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= 15) return;
        ItemStack copy = stack.copy(); copy.setCount(Math.min(copy.getCount(), Math.min(64, copy.getMaxStackSize()))); items.set(slot, copy); setChanged();
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot >= 0 && slot < 9 || slot >= 9 && slot < 15 && ArcaneWorkbenchCosts.crystal(stack, slot - 9); }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this && player.level() == level
            && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); ContainerHelper.saveAllItems(tag, items, registries); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); items.clear(); ContainerHelper.loadAllItems(tag, items, registries); }
    @Override public Component getDisplayName() { return Component.translatable("container.arcaneworkbench"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new ContainerArcaneWorkbench(id, inventory, this); }

    private boolean charged() { return BlocksTC.arcaneWorkbenchCharger != null && level.getBlockState(worldPosition.above()).is(BlocksTC.arcaneWorkbenchCharger); }
    public int availableVis() {
        if (level == null || level.isClientSide) return 0;
        if (!charged()) return (int) AuraHandler.getVis(level, worldPosition);
        int total = 0;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) total = (int) (total + AuraHandler.getVis(level, worldPosition.offset(x * 16, 0, z * 16)));
        return total;
    }
    /** No callbacks between preflight and drain on the owning server thread. */
    public boolean spendVis(int amount) {
        if (level == null || level.isClientSide || availableVis() < amount) return false;
        if (amount <= 0) return true; // Original >100% discount does not add aura.
        if (!charged()) return AuraHandler.drainVis(level, worldPosition, amount, false) >= amount;
        int remaining = amount, portion = Math.max(1, amount / 9), attempts = 0;
        while (remaining > 0 && attempts++ <= 1000) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                portion = Math.min(portion, remaining);
                remaining = (int) (remaining - AuraHandler.drainVis(level, worldPosition.offset(x * 16, 0, z * 16), portion, false));
            }
        }
        return remaining <= 0;
    }
}
