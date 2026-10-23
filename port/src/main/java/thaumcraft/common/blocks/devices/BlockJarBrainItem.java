package thaumcraft.common.blocks.devices;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

/** Original lowercase item xp tag; BE uses uppercase XP. */
public final class BlockJarBrainItem extends BlockItem {
    public BlockJarBrainItem(Block block,Properties properties) { super(block,properties); }
    public static int getXp(ItemStack stack) { return Math.max(0,stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("xp")); }
    public static void setXp(ItemStack stack,int xp) { CustomData.update(DataComponents.CUSTOM_DATA,stack,tag -> tag.putInt("xp",xp)); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag) { super.appendHoverText(stack,context,tooltip,flag); int xp = getXp(stack); if (xp > 0) tooltip.add(Component.literal(xp + " xp").withStyle(ChatFormatting.GREEN)); }
}
