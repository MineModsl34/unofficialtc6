package thaumcraft.common.container;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.entities.construct.*;

/** A view of the real, saved and synced mob main-hand equipment, not a second inventory. */
public final class ConstructEquipmentContainer implements Container {
    private final EntityOwnedConstruct construct;
    public ConstructEquipmentContainer(EntityOwnedConstruct construct){this.construct=construct;}
    @Override public int getContainerSize(){return 1;}
    @Override public boolean isEmpty(){return construct.getMainHandItem().isEmpty();}
    @Override public ItemStack getItem(int slot){return slot==0?construct.getMainHandItem():ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(slot!=0 || count<=0)return ItemStack.EMPTY;var stack=construct.getMainHandItem();var result=stack.split(count);if(stack.isEmpty())construct.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){var stack=getItem(slot);if(slot==0)construct.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);return stack;}
    @Override public void setItem(int slot,ItemStack stack){if(slot==0)construct.setItemSlot(EquipmentSlot.MAINHAND,stack);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0 && (construct instanceof EntityArcaneBore?EntityArcaneBore.isPick(stack):stack.getItem() instanceof ArrowItem);}
    @Override public void setChanged(){}
    @Override public boolean stillValid(Player player){return construct.isAlive() && construct.isOwner(player) && player.distanceToSqr(construct)<=64;}
    @Override public void clearContent(){construct.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);}
}
