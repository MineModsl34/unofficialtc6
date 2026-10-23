package thaumcraft.common.lib.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;

@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class InfusionTooltipEvents {
    private InfusionTooltipEvents() {}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        for (var entry : EnumInfusionEnchantment.data(event.getItemStack()).entries()) {
            var enchantment = EnumInfusionEnchantment.values()[entry.id()];
            var line = Component.translatable("enchantment.infusion." + enchantment.name());
            if (enchantment.maxLevel > 1) line.append(" ").append(Component.translatable("enchantment.level." + entry.level()));
            event.getToolTip().add(Math.min(1, event.getToolTip().size()), line.withStyle(ChatFormatting.GOLD));
        }
        int runic = InfusionRunicAugmentRecipe.getRunicCharge(event.getItemStack());
        if (runic > 0) event.getToolTip().add(Component.translatable("item.runic.charge").append(" +" + runic).withStyle(ChatFormatting.GOLD));
    }
}
