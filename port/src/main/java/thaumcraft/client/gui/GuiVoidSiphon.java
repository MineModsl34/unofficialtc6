package thaumcraft.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.common.container.ContainerVoidSiphon;
/** BETA26 GUI is the original176x166 background and output slot (no fabricated progress bar). */
public final class GuiVoidSiphon extends AbstractContainerScreen<ContainerVoidSiphon>{
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/gui/gui_void_siphon.png");
    public GuiVoidSiphon(ContainerVoidSiphon menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=166;}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight,256,256);}
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){super.render(graphics,x,y,partial);renderTooltip(graphics,x,y);}
}
