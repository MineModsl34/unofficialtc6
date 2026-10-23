package thaumcraft.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.common.container.ContainerFocusPouch;
public final class GuiFocusPouch extends AbstractContainerScreen<ContainerFocusPouch>{
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/gui/gui_focuspouch.png");
    public GuiFocusPouch(ContainerFocusPouch menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=175;imageHeight=232;}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight,256,256);}
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY){if(menu.pouchSlot<9)graphics.blit(TEXTURE,8+menu.pouchSlot*18,209,240,0,16,16,256,256);}
    @Override protected boolean checkHotbarKeyPressed(int key,int scan){return false;}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){super.render(graphics,mouseX,mouseY,partial);renderTooltip(graphics,mouseX,mouseY);}
}
