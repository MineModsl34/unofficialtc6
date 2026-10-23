package thaumcraft.common.items.baubles;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import thaumcraft.api.items.RechargeHelper;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class ItemAmuletVis extends Item implements ICurioItem {
    private final boolean crafted;

    public ItemAmuletVis(boolean crafted) {
        super(new Properties().stacksTo(1).rarity(crafted ? Rarity.RARE : Rarity.UNCOMMON));
        this.crafted = crafted;
    }

    public int rechargeInterval() {
        return crafted ? 5 : 40;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "item.thaumcraft.amulet_vis_" + (crafted ? "crafted" : "found");
    }

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        return "necklace".equals(context.identifier());
    }

    @Override
    public void curioTick(SlotContext context, ItemStack stack) {
        if (context.cosmetic() || !(context.entity() instanceof Player player) || player.level().isClientSide
            || player.tickCount % rechargeInterval() != 0) {
            return;
        }
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (recharge(player, player.getInventory().items.get(slot))) {
                return;
            }
        }
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isPresent()) {
            var equipped = curios.get().getEquippedCurios();
            for (int slot = 0; slot < equipped.getSlots(); slot++) {
                if (recharge(player, equipped.getStackInSlot(slot))) {
                    return;
                }
            }
        }
        for (ItemStack armor : player.getInventory().armor) {
            if (recharge(player, armor)) {
                return;
            }
        }
    }

    private static boolean recharge(Player player, ItemStack stack) {
        return RechargeHelper.rechargeItem(player.level(), stack, player.blockPosition(), player, 1) > 0.0F;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.amulet_vis.text").withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
