package thaumcraft.common.items.curios;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;

/** Source symbolic research requirement, NOT an item that must itself be sacrificed.
 * Inventory matching accepts an actual enchanted item via ResearchItemResolver. */
public final class ItemEnchantmentPlaceholder extends Item {
    public ItemEnchantmentPlaceholder() { super(new Properties().stacksTo(1).rarity(Rarity.RARE).setNoRepair()); }
    @Override public boolean isFoil(ItemStack stack) { return true; }
    @Override public boolean isEnchantable(ItemStack stack) { return false; }
    @Override public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) { return true; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.enchanted_placeholder.text").withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_AQUA));
    }
}
