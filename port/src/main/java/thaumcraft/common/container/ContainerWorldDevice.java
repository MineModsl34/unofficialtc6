package thaumcraft.common.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.blocks.devices.BlockWorldDevice;
import thaumcraft.common.tiles.devices.*;

/** Source slot coordinates/shift transfer. Spa's button is a native menu packet validated by distance/menu ownership. */
public final class ContainerWorldDevice extends AbstractContainerMenu {
    public final TileFluidDevice tile;
    public final boolean sprayer;
    public ContainerWorldDevice(int id,Inventory inventory,TileFluidDevice tile) {
        super(tile.kind() == BlockWorldDevice.Kind.SPRAYER ? DeviceRegistries.SPRAYER_MENU : DeviceRegistries.SPA_MENU,id); this.tile = tile; sprayer = tile.kind() == BlockWorldDevice.Kind.SPRAYER;
        addSlot(new Slot(tile,0,sprayer ? 56 : 65,sprayer ? 64 : 31) { @Override public boolean mayPlace(ItemStack stack) { return tile.canPlaceItem(0,stack); } });
        for (int y = 0; y < 3; y++) for (int x = 0; x < 9; x++) addSlot(new Slot(inventory,x + y * 9 + 9,(sprayer ? 16 : 8) + x * 18,(sprayer ? 151 : 84) + y * 18));
        for (int x = 0; x < 9; x++) addSlot(new Slot(inventory,x,(sprayer ? 16 : 8) + x * 18,sprayer ? 209 : 142));
    }
    public static ContainerWorldDevice fromNetwork(int id,Inventory inventory,RegistryFriendlyByteBuf buffer,boolean sprayer) { BlockPos pos = buffer.readBlockPos(); if (!(inventory.player.level().getBlockEntity(pos) instanceof TileFluidDevice tile) || (tile.kind() == BlockWorldDevice.Kind.SPRAYER) != sprayer) throw new IllegalArgumentException("Wrong device menu host at " + pos); return new ContainerWorldDevice(id,inventory,tile); }
    @Override public boolean stillValid(Player player) { return tile.stillValid(player); }
    @Override public boolean clickMenuButton(Player player,int button) { if (sprayer || button != 1 || !stillValid(player) || player.containerMenu != this || player.level().isClientSide) return false; tile.toggleMix(); return true; }
    @Override public ItemStack quickMoveStack(Player player,int index) { if (index < 0 || index >= slots.size()) return ItemStack.EMPTY; Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY; ItemStack stack = slot.getItem(), copy = stack.copy(); if (index == 0) { if (!tile.canPlaceItem(0,stack) || !moveItemStackTo(stack,1,slots.size(),true)) return ItemStack.EMPTY; } else if (!tile.canPlaceItem(0,stack) || !moveItemStackTo(stack,0,1,false)) return ItemStack.EMPTY; if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(player,stack); return copy; }
}
