package thaumcraft.client.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import thaumcraft.common.container.ContainerWorldDevice;
import thaumcraft.api.aspects.Aspect;

/** Source backgrounds, liquid gauge, mix control and aspect charge bars in the original positions. */
public final class GuiWorldDevice extends AbstractContainerScreen<ContainerWorldDevice> {
    private final ResourceLocation texture;
    public GuiWorldDevice(ContainerWorldDevice menu,Inventory inventory,Component title) { super(menu,inventory,title); if (menu.sprayer) { imageWidth = 192; imageHeight = 233; } texture = ResourceLocation.parse("thaumcraft:textures/gui/" + (menu.sprayer ? "gui_potion_sprayer.png" : "gui_spa.png")); }
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y) {}
    @Override protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY) {
        graphics.blit(texture,leftPos,topPos,0,0,imageWidth,imageHeight,256,256);
        var tile = menu.tile;
        if (!menu.sprayer) {
            graphics.blit(texture,leftPos + 89,topPos + 35,208,tile.mix ? 16 : 32,8,8,256,256);
            var fluid = tile.tank.getFluid();
            if (!fluid.isEmpty()) { var extension = IClientFluidTypeExtensions.of(fluid.getFluid()); var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(extension.getStillTexture(fluid)); int color = extension.getTintColor(fluid); graphics.setColor((color >> 16 & 255) / 255F,(color >> 8 & 255) / 255F,(color & 255) / 255F,1); for (int y = 0; y < 6; y++) graphics.blit(leftPos + 107,topPos + 15 + y * 8,0,8,8,sprite); graphics.setColor(1,1,1,1); graphics.blit(texture,leftPos + 107,topPos + 15,107,15,10,(int)(48 - 48F * tile.tank.getFluidAmount() / tile.tank.getCapacity()),256,256); }
            graphics.blit(texture,leftPos + 106,topPos + 11,232,0,10,55,256,256); return;
        }
        if (tile.charges > 0) { int color = tile.color; graphics.setColor((color >> 16 & 255) / 255F,(color >> 8 & 255) / 255F,(color & 255) / 255F,1); int scroll = minecraft.player.tickCount % 256; int height = tile.charges * 9; // wrap the animated texture rather than sampling outside its native atlas bounds
            int first = Math.min(height,256 - scroll); graphics.blit(texture,leftPos + 128,topPos + 36 + (8 - tile.charges) * 9,232,scroll,8,first,256,256); if (first < height) graphics.blit(texture,leftPos + 128,topPos + 36 + (8 - tile.charges) * 9 + first,232,0,8,height - first,256,256); graphics.setColor(1,1,1,1); }
        int pos = 0;
        for (Aspect aspect : tile.recipe.getAspectsSortedByName()) { int x = leftPos + 79 + 22 * (pos % 2), y = topPos + 31 + 16 * (pos / 2); int color = aspect.getColor(); graphics.setColor((color >> 16 & 255) / 255F,(color >> 8 & 255) / 255F,(color & 255) / 255F,1); graphics.blit(aspect.getImage(),x,y,0,0,16,16,16,16); graphics.setColor(1,1,1,1); graphics.drawString(font,Integer.toString(tile.recipe.getAmount(aspect)),x + 9,y + 9,0xffffff,true); graphics.fill(x + 17,y + 1,x + 19,y + 15,0xff333333); int filled = tile.recipeProgress.getAmount(aspect) * 14 / Math.max(1,tile.recipe.getAmount(aspect)); graphics.fill(x + 17,y + 15 - filled,x + 19,y + 15,0xff000000 | color); pos++; }
        graphics.blit(texture,leftPos + 125,topPos + 28,205,28,14,88,256,256);
    }
    @Override public boolean mouseClicked(double x,double y,int button) { if (!menu.sprayer && x >= leftPos + 89 && y >= topPos + 35 && x < leftPos + 97 && y < topPos + 43) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1); minecraft.player.playSound(thaumcraft.common.lib.SoundsTC.clack,.4F,1); return true; } return super.mouseClicked(x,y,button); }
    @Override public void render(GuiGraphics graphics,int x,int y,float tick) { super.render(graphics,x,y,tick); renderTooltip(graphics,x,y); if (!menu.sprayer) { if (x >= leftPos + 104 && x < leftPos + 114 && y >= topPos + 10 && y < topPos + 65 && !menu.tile.tank.isEmpty()) graphics.renderTooltip(font,List.of(menu.tile.tank.getFluid().getHoverName(),Component.literal(menu.tile.tank.getFluidAmount() + " mb")),java.util.Optional.empty(),x,y); if (x >= leftPos + 88 && x < leftPos + 98 && y >= topPos + 34 && y < topPos + 44) graphics.renderTooltip(font,Component.translatable("text.spa.mix." + menu.tile.mix),x,y); } else { int pos = 0; for (Aspect aspect : menu.tile.recipe.getAspectsSortedByName()) { int sx = leftPos + 79 + 22 * (pos % 2),sy = topPos + 31 + 16 * (pos / 2); if (x >= sx && x < sx + 16 && y >= sy && y < sy + 16) graphics.renderTooltip(font,Component.translatable("tc.aspect." + aspect.getTag()),x,y); pos++; } } }
}
