package thaumcraft.common.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.GearHelper;
import thaumcraft.api.items.RechargeHelper;

/** Narrow TC6 PlayerEvents tooltip slice for the ported gear APIs. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class TCGearTooltips {
    private TCGearTooltips() {
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        int warp = GearHelper.getFinalWarp(event.getItemStack(), event.getEntity());
        if (warp > 0) {
            event.getToolTip().add(Component.translatable("item.warping").append(" " + warp).withStyle(ChatFormatting.DARK_PURPLE));
        }
        int discount = GearHelper.getFinalDiscount(event.getItemStack(), event.getEntity());
        if (discount > 0) {
            event.getToolTip().add(Component.translatable("tc.visdiscount").append(": " + discount + "%").withStyle(ChatFormatting.DARK_PURPLE));
        }
        int charge = RechargeHelper.getCharge(event.getItemStack());
        if (charge >= 0) {
            event.getToolTip().add(Component.translatable("tc.charge").append(" " + charge).withStyle(ChatFormatting.YELLOW));
        }
    }
}
