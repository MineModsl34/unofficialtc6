package thaumcraft.client.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import thaumcraft.api.aspects.Aspect;

public final class AspectIngredientRenderer implements IIngredientRenderer<JeiAspectIngredient> {
    @Override public void render(GuiGraphics graphics, JeiAspectIngredient ingredient) {
        var aspect = Aspect.getAspect(ingredient.tag());
        if (aspect == null) return;
        int color = aspect.getColor();
        graphics.setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, 1);
        try {
            graphics.blit(aspect.getImage(), 0, 0, 16, 16, 0, 0, 256, 256, 256, 256);
        } finally {
            graphics.setColor(1, 1, 1, 1);
        }
        if (ingredient.amount() != 1) {
            var font = Minecraft.getInstance().font;
            String amount = Integer.toString(ingredient.amount());
            graphics.pose().pushPose();
            try {
                // Keeps even large exact counts inside the ingredient cell.
                float scale = Math.min(1F, 15F / Math.max(1, font.width(amount)));
                graphics.pose().scale(scale, scale, 1F);
                graphics.drawString(font, amount, (int) (16 / scale) - font.width(amount),
                    (int) (16 / scale) - font.lineHeight, 0xffffff, true);
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    @Override public List<Component> getTooltip(JeiAspectIngredient ingredient, TooltipFlag flag) {
        List<Component> tooltip = new ArrayList<>();
        var aspect = Aspect.getAspect(ingredient.tag());
        tooltip.add(Component.literal(aspect == null ? ingredient.tag() : aspect.getName()));
        if (aspect != null) tooltip.add(aspect.getDescription());
        tooltip.add(Component.translatable("thaumcraft.jei.aspect_amount", ingredient.amount()));
        if (flag.isAdvanced()) tooltip.add(Component.literal(ingredient.tag()));
        return tooltip;
    }
}
