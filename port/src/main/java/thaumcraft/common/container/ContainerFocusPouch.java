package thaumcraft.common.container;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.items.casters.*;
/** Source18-slot layout with persistent native contents and protected owning item. */
public class ContainerFocusPouch extends AbstractContainerMenu {
    public final InventoryFocusPouch input=new InventoryFocusPouch();public final int pouchSlot;private final ItemStack pouch;private final Player player;private boolean initialized;
    public ContainerFocusPouch(int id,Inventory inventory,int slot){super(CasterRegistries.POUCH_MENU,id);if(slot<0||slot>=36&&slot!=40)throw new IllegalArgumentException("Pouch slot");pouchSlot=slot;player=inventory.player;pouch=inventory.getItem(slot);if(!(pouch.getItem() instanceof ItemFocusPouch item))throw new IllegalArgumentException("Pouch item");for(int i=0;i<18;i++)addSlot(new Slot(input,i,37+i%6*18,51+i/6*18){@Override public boolean mayPlace(ItemStack stack){return input.canPlaceItem(getContainerSlot(),stack);}@Override public int getMaxStackSize(){return 1;}});for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(playerSlot(inventory,x+y*9+9,8+x*18,151+y*18));for(int x=0;x<9;x++)addSlot(playerSlot(inventory,x,8+x*18,209));var contents=item.getInventory(pouch);for(int i=0;i<18;i++)input.setItem(i,contents.get(i));input.addListener(container->persist());initialized=true;}
    private Slot playerSlot(Inventory inventory,int index,int x,int y){return new Slot(inventory,index,x,y){@Override public boolean mayPickup(Player player){return index!=pouchSlot;}@Override public boolean mayPlace(ItemStack stack){return index!=pouchSlot;}};}
    private void persist(){if(!initialized||player.level().isClientSide||!stillValid(player))return;var contents=NonNullList.withSize(18,ItemStack.EMPTY);for(int i=0;i<18;i++)contents.set(i,input.getItem(i));((ItemFocusPouch)pouch.getItem()).setInventory(pouch,contents);player.getInventory().setChanged();}
    @Override public boolean stillValid(Player owner){return owner==player&&owner.getInventory().getItem(pouchSlot)==pouch&&pouch.getItem() instanceof ItemFocusPouch;}
    @Override public void clicked(int index,int button,ClickType type,Player player){if(index>=0&&index<slots.size()&&slots.get(index).container==player.getInventory()&&slots.get(index).getContainerSlot()==pouchSlot)return;if(type==ClickType.SWAP&&(button==pouchSlot||button==40&&pouchSlot==40))return;super.clicked(index,button,type,player);persist();}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(player))return ItemStack.EMPTY;var stack=slot.getItem();var copy=stack.copy();if(index<18){if(!moveItemStackTo(stack,18,slots.size(),true))return ItemStack.EMPTY;}else if(!(stack.getItem() instanceof ItemFocus)||!moveItemStackTo(stack,0,18,false))return ItemStack.EMPTY;if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();persist();return copy;}
    @Override public void removed(Player player){persist();super.removed(player);}
}
