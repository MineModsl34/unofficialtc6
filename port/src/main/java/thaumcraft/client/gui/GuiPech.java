package thaumcraft.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.common.container.ContainerPech;
import thaumcraft.common.lib.SoundsTC;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
public final class GuiPech extends AbstractContainerScreen<ContainerPech> {
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/gui/gui_pech.png");
    public GuiPech(ContainerPech menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=175;imageHeight=232;}
    @Override protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY){graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight,256,256);if(menu.ready())graphics.blit(TEXTURE,leftPos+67,topPos+24,176,0,25,25,256,256);}
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY){}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){super.render(graphics,mouseX,mouseY,partialTick);renderTooltip(graphics,mouseX,mouseY);}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&menu.ready()&&x>=leftPos+67&&x<leftPos+92&&y>=topPos+24&&y<topPos+49){minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundsTC.pech_dice,.95F+minecraft.level.getRandom().nextFloat()*.1F,.5F));minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0);return true;}return super.mouseClicked(x,y,button);}
}
