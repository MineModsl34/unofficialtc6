package thaumcraft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.joml.Quaternionf;
import thaumcraft.common.container.ContainerArcaneWorkbench;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;

/** Original 190x234 atlas/slots, rotating primal costs and half-scale source vis labels.
 * Rendering never grants research, simulates a successful craft or edits the server aura.
 */
public final class GuiArcaneWorkbench extends AbstractContainerScreen<ContainerArcaneWorkbench> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/arcaneworkbench.png");
    public GuiArcaneWorkbench(ContainerArcaneWorkbench menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 190; imageHeight = 234;
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {} // Source texture has no native inventory/title labels.
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        var recipe = menu.displayedArcaneRecipe();
        float discount = ArcaneWorkbenchCosts.discount(minecraft.player);
        int cost = recipe == null ? 0 : ArcaneWorkbenchCosts.visCost(recipe.vis(), discount);
        if (recipe != null) for (int index = 0; index < 6; index++) if (recipe.crystals().get(index) > 0) {
            int color = ArcaneWorkbenchCosts.PRIMALS[index].getColor();
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos + ArcaneWorkbenchCosts.X[index] + 7.5F, topPos + ArcaneWorkbenchCosts.Y[index] + 8F, 0F);
            graphics.pose().mulPose(new Quaternionf().rotationZ((float) Math.toRadians(index * 60 + minecraft.player.tickCount % 360 + partialTick)));
            graphics.pose().scale(.5F, .5F, 1F);
            graphics.flush(); RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, .33F);
            graphics.blit(TEXTURE, -32, -32, 192, 0, 64, 64, 256, 256);
            graphics.flush(); RenderSystem.setShaderColor(1F, 1F, 1F, 1F); RenderSystem.defaultBlendFunc(); graphics.pose().popPose();
        }
        graphics.pose().pushPose(); graphics.pose().translate(leftPos + 168F, topPos + 46F, 0F); graphics.pose().scale(.5F, .5F, 1F);
        graphics.drawCenteredString(font, Component.literal(menu.availableVis() + " ").append(Component.translatable("workbench.available")), 0, 0,
            cost > menu.availableVis() ? 15625838 : 7237358);
        graphics.pose().popPose();
        if (recipe != null) {
            Component label = Component.literal(cost + " ").append(Component.translatable("workbench.cost"));
            int percentage = (int) (discount * 100F);
            if (percentage > 0) label = label.copy().append(" (" + percentage + "% ").append(Component.translatable("workbench.discount")).append(")");
            graphics.pose().pushPose(); graphics.pose().translate(leftPos + 168F, topPos + 38F, 0F); graphics.pose().scale(.5F, .5F, 1F);
            graphics.drawCenteredString(font, label, 0, 0, 12648447); graphics.pose().popPose();
            // Source dark preview when vis is insufficient; NOT a takeable menu output.
            if (cost > menu.availableVis()) {
                graphics.renderItem(recipe.getResultItem(minecraft.level.registryAccess()), leftPos + 160, topPos + 64);
                graphics.fill(leftPos + 160, topPos + 64, leftPos + 176, topPos + 80, 0x88000000);
            }
        }
        graphics.flush(); RenderSystem.disableBlend(); RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
    }
}
