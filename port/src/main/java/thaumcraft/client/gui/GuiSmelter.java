package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.common.container.ContainerSmelter;

public class GuiSmelter extends AbstractContainerScreen<ContainerSmelter> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("thaumcraft:textures/gui/gui_smelter.png");
    public GuiSmelter(ContainerSmelter menu,Inventory inventory,Component title) { super(menu,inventory,title); }
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y) {}
    @Override protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY) {
        graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight,256,256);
        int burn = menu.burnScaled(20); if (burn > 0) graphics.blit(TEXTURE,leftPos + 80,topPos + 46 - burn,176,20 - burn,16,burn,256,256);
        int cook = menu.cookScaled(46); graphics.blit(TEXTURE,leftPos + 106,topPos + 59 - cook,216,46 - cook,9,cook,256,256);
        int vis = menu.visScaled(48); graphics.blit(TEXTURE,leftPos + 61,topPos + 60 - vis,200,48 - vis,8,vis,256,256);
        graphics.blit(TEXTURE,leftPos + 60,topPos + 8,232,0,10,55,256,256);
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) { super.render(graphics,mouseX,mouseY,partialTick); renderTooltip(graphics,mouseX,mouseY); }
}
