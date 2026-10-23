package thaumcraft.common.container;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.tiles.crafting.*;

/** Source output slot and player inventory; native menu data slots are server-authoritative. */
public final class ContainerVoidSiphon extends AbstractContainerMenu {
    private final Container machine;
    private final ContainerData data;
    public ContainerVoidSiphon(int id,Inventory inventory){this(id,inventory,new SimpleContainer(1),new SimpleContainerData(1));}
    public ContainerVoidSiphon(int id,Inventory inventory,TileVoidSiphon tile){this(id,inventory,tile,new ContainerData(){public int get(int index){return index==0?tile.progress:0;}public void set(int index,int value){}public int getCount(){return 1;}});}
    private ContainerVoidSiphon(int id,Inventory inventory,Container machine,ContainerData data){super(Phase10RiftRegistries.VOID_SIPHON_MENU,id);this.machine=machine;this.data=data;
        addSlot(new Slot(machine,0,80,32){@Override public boolean mayPlace(ItemStack stack){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,142));addDataSlots(data);
    }
    public int progress(){return data.get(0);}
    @Override public boolean stillValid(Player player){return machine.stillValid(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var copy=stack.copy();
        // Source SlotOutput cannot accept any stack, so player -> output is never legal.
        if(index!=0||!machine.canPlaceItem(0,stack)||!moveItemStackTo(stack,1,37,true))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();if(stack.getCount()==copy.getCount())return ItemStack.EMPTY;slot.onTake(player,stack);return copy;
    }
}
