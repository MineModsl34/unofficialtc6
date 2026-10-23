package thaumcraft.common.container;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.entities.construct.*;

/** The three source equipment menus, with native owner/distance checks on every operation. */
public final class ContainerConstruct extends AbstractContainerMenu {
    public final EntityOwnedConstruct construct;
    private final ConstructEquipmentContainer equipment;
    public ContainerConstruct(int id,Inventory inventory,EntityOwnedConstruct construct){
        super(construct instanceof EntityArcaneBore?Mob8ConstructRegistries.BORE_MENU:construct instanceof EntityTurretCrossbowAdvanced?Mob8ConstructRegistries.ADVANCED_MENU:Mob8ConstructRegistries.BASIC_MENU,id);
        this.construct=construct;equipment=new ConstructEquipmentContainer(construct);
        addSlot(new Slot(equipment,0,construct instanceof EntityTurretCrossbowAdvanced?42:80,29){@Override public boolean mayPlace(ItemStack stack){return equipment.canPlaceItem(0,stack);}});
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inventory,x+y*9+9,8+x*18,84+y*18));
        for(int x=0;x<9;x++)addSlot(new Slot(inventory,x,8+x*18,142));
    }
    @Override public boolean stillValid(Player player){return equipment.stillValid(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(!stillValid(player) || index<0 || index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var old=stack.copy();
        if(index==0){if(!moveItemStackTo(stack,1,37,true))return ItemStack.EMPTY;}
        else if(!equipment.canPlaceItem(0,stack) || !moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();if(stack.getCount()==old.getCount())return ItemStack.EMPTY;slot.onTake(player,stack);return old;
    }
    @Override public boolean clickMenuButton(Player player,int id){if(!stillValid(player) || !(construct instanceof EntityTurretCrossbowAdvanced turret))return false;switch(id){case 1->turret.setTargetAnimal(!turret.getTargetAnimal());case 2->turret.setTargetMob(!turret.getTargetMob());case 3->turret.setTargetPlayer(!turret.getTargetPlayer());case 4->turret.setTargetFriendly(!turret.getTargetFriendly());default->{return false;}}return true;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(stillValid(player))super.clicked(slot,button,type,player);}
}
