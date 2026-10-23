package thaumcraft.common.items.baubles;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import thaumcraft.api.capabilities.*;
import thaumcraft.api.items.*;
public final class ItemVoidseerCharm extends SourceCurioItem implements IVisDiscountGear,IWarpingGear {
    public ItemVoidseerCharm(){super("charm");}
    @Override public int getVisDiscount(ItemStack stack,Player player){if(player==null)return 0;var warp=ThaumcraftCapabilities.getWarp(player);return warp==null?0:(int)(Math.min(100,warp.get(IPlayerWarp.EnumWarpType.PERMANENT))/100F*25F);}
    @Override public int getWarp(ItemStack stack,Player player){return getVisDiscount(stack,player)/5;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){tooltip.add(Component.translatable("item.voidseer_charm.text").withStyle(ChatFormatting.DARK_BLUE,ChatFormatting.ITALIC));super.appendHoverText(stack,context,tooltip,flag);}
}
