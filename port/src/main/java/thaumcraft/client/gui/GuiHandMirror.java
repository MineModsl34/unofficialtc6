package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.common.container.ContainerHandMirror;

public final class GuiHandMirror extends AbstractContainerScreen<ContainerHandMirror>{
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/gui/gui_handmirror.png");
    public GuiHandMirror(ContainerHandMirror menu,Inventory inventory,Component title){super(menu,inventory,title);}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override protected void renderBg(GuiGraphics graphics,float tick,int x,int y){
        graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight,256,256);
        if(menu.hand==InteractionHand.MAIN_HAND)graphics.blit(TEXTURE,leftPos+8+menu.selectedSlot*18,topPos+142,240,0,16,16,256,256);
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float tick){super.render(graphics,x,y,tick);renderTooltip(graphics,x,y);}
}
