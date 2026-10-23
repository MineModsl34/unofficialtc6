package thaumcraft.common.container;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.items.tools.ItemHandMirror;
import thaumcraft.common.tiles.devices.MirrorRegistries;

/** Source single sending slot; failed sends return the item and close returns all leftovers.
 * Native validation binds the actual held object and hand, instead of trusting a client link.
 */
public final class ContainerHandMirror extends AbstractContainerMenu {
    private final Player owner;
    public final InteractionHand hand;
    public final int selectedSlot;
    private final ItemStack mirror;
    private boolean sending;
    public final SimpleContainer input=new SimpleContainer(1){@Override public void setChanged(){super.setChanged();sendInput();}};
    public ContainerHandMirror(int id,Inventory inventory,InteractionHand hand){
        super(MirrorRegistries.HAND_MENU,id);owner=inventory.player;this.hand=hand;selectedSlot=inventory.selected;mirror=owner.getItemInHand(hand);
        addSlot(new Slot(input,0,80,24){@Override public boolean mayPlace(ItemStack stack){return !(stack.getItem() instanceof ItemHandMirror);}});
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inventory,9+y*9+x,8+x*18,84+y*18));
        for(int x=0;x<9;x++)addSlot(new Slot(inventory,x,8+x*18,142));
    }
    private void sendInput(){
        if(sending || owner==null || owner.level().isClientSide || !stillValid(owner))return;
        ItemStack items=input.getItem(0);if(items.isEmpty() || items.getItem() instanceof ItemHandMirror)return;
        sending=true;
        try{if(ItemHandMirror.transport(mirror,items,owner,owner.level())){input.setItem(0,ItemStack.EMPTY);broadcastChanges();}}
        finally{sending=false;}
    }
    @Override public boolean stillValid(Player player){return player==owner && owner.getItemInHand(hand)==mirror && mirror.getItem() instanceof ItemHandMirror;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(!stillValid(player))return;
        if(slot>=0 && slot<slots.size() && slots.get(slot).getItem().getItem() instanceof ItemHandMirror)return;
        if(type==ClickType.SWAP && (button==40 && hand==InteractionHand.OFF_HAND || button==selectedSlot && hand==InteractionHand.MAIN_HAND))return;
        super.clicked(slot,button,type,player);sendInput();
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0 || index>=slots.size() || !stillValid(player))return ItemStack.EMPTY;
        Slot slot=slots.get(index);ItemStack stack=slot.getItem();if(stack.isEmpty() || stack.getItem() instanceof ItemHandMirror)return ItemStack.EMPTY;
        ItemStack copy=stack.copy();if(index==0){if(!moveItemStackTo(stack,1,37,true))return ItemStack.EMPTY;}else if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);sendInput();return copy;
    }
    @Override public void removed(Player player){super.removed(player);if(!player.level().isClientSide)clearContainer(player,input);}
}
