package thaumcraft.common.golems.client.gui;
import java.util.function.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
/** Native widgets use the original checkbox cells; all actions are server-bound menu IDs. */
public final class GuiGolemPropButton extends AbstractButton {
 private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/gui/gui_base.png");private final BooleanSupplier value;private final Runnable pressed;
 public GuiGolemPropButton(int x,int y,Component title,BooleanSupplier value,Runnable pressed){super(x,y,110,12,title);this.value=value;this.pressed=pressed;}
 @Override public void onPress(){pressed.run();}
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float partial){g.blit(TEXTURE,getX(),getY(),2,18,12,12,256,256);if(value.getAsBoolean())g.blit(TEXTURE,getX(),getY(),18,18,12,12,256,256);var font=net.minecraft.client.Minecraft.getInstance().font;g.drawString(font,font.plainSubstrByWidth(getMessage().getString(),100),getX()+14,getY()+2,active?-1:0xff777777);setTooltip(net.minecraft.client.gui.components.Tooltip.create(getMessage()));}
 @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output){defaultButtonNarrationText(output);}
}
