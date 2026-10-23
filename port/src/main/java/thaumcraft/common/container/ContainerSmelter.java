package thaumcraft.common.container;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.tiles.essentia.EssentiaRegistries;
import thaumcraft.common.tiles.essentia.TileSmelter;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/** Source 2 furnace + 36 player slots and fuel-first shift transfer. */
public class ContainerSmelter extends AbstractContainerMenu {
    private final Container furnace;
    private final ContainerData data;
    public ContainerSmelter(int window,Inventory inventory) { this(window,inventory,new SimpleContainer(2),new SimpleContainerData(5)); }
    public ContainerSmelter(int window,Inventory inventory,Container furnace,ContainerData data) {
        super(EssentiaRegistries.SMELTER_MENU,window); this.furnace = furnace; this.data = data;
        addSlot(new Slot(furnace,0,80,8) { @Override public boolean mayPlace(ItemStack stack) { var aspects = ThaumcraftCraftingManager.getObjectTags(stack); return aspects != null && aspects.size() > 0; } });
        addSlot(new Slot(furnace,1,80,48));
        for (int y = 0; y < 3; y++) for (int x = 0; x < 9; x++) addSlot(new Slot(inventory,x + y * 9 + 9,8 + x * 18,84 + y * 18));
        for (int x = 0; x < 9; x++) addSlot(new Slot(inventory,x,8 + x * 18,142));
        addDataSlots(data);
    }
    @Override public boolean stillValid(Player player) { return furnace.stillValid(player); }
    public int burnScaled(int scale) { return data.get(1) * scale / (data.get(2) == 0 ? 200 : data.get(2)); }
    public int cookScaled(int scale) { return data.get(0) * scale / (data.get(4) == 0 ? 1 : data.get(4)); }
    public int visScaled(int scale) { return data.get(3) * scale / 256; }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index == 0 || index == 1) { if (!moveItemStackTo(stack,2,38,false)) return ItemStack.EMPTY; }
        else if (TileSmelter.isItemFuel(stack)) { if (!moveItemStackTo(stack,1,2,false) && !moveItemStackTo(stack,0,1,false)) return ItemStack.EMPTY; }
        else { var aspects = ThaumcraftCraftingManager.getObjectTags(stack); if (aspects != null && aspects.size() > 0) { if (!moveItemStackTo(stack,0,1,false)) return ItemStack.EMPTY; } else if (index < 29) { if (!moveItemStackTo(stack,29,38,false)) return ItemStack.EMPTY; } else if (!moveItemStackTo(stack,2,29,false)) return ItemStack.EMPTY; }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player,stack); return copy;
    }
}
