package thaumcraft.common.items.casters;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import thaumcraft.common.container.ContainerFocusPouch;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
public class ItemFocusPouch extends Item implements ICurioItem {
    public ItemFocusPouch(Properties properties){super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));}
    public NonNullList<ItemStack> getInventory(ItemStack stack){var items=NonNullList.withSize(18,ItemStack.EMPTY);stack.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyInto(items);return items;}
    public void setInventory(ItemStack stack,NonNullList<ItemStack> items){if(items.size()!=18)throw new IllegalArgumentException("Focus pouch slots");stack.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(items));}
    @Override public boolean canEquip(SlotContext context,ItemStack stack){return context.identifier().equals("belt");}
    @Override public boolean isFoil(ItemStack stack){return false;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){var stack=player.getItemInHand(hand);int slot=hand==InteractionHand.MAIN_HAND?player.getInventory().selected:40;if(!level.isClientSide)player.openMenu(new SimpleMenuProvider((id,inventory,owner)->new ContainerFocusPouch(id,inventory,slot),Component.translatable("container.focuspouch")),buffer->buffer.writeVarInt(slot));return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);}
}
