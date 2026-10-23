package thaumcraft.common.golems.client.gui;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
/** BETA26's compact BW/lock/redstone texture cells and full translated hover text. */
public final class GuiGolemStateButton extends AbstractButton {
 public enum Kind { FILTER,LOCK,REDSTONE }
 private static final ResourceLocation TEX=ResourceLocation.parse("thaumcraft:textures/gui/gui_base.png");
 private final Kind kind;private final BooleanSupplier value;private final Runnable action;
 public GuiGolemStateButton(int x,int y,Kind kind,BooleanSupplier value,Runnable action){super(x,y,16,16,Component.empty());this.kind=kind;this.value=value;this.action=action;}
 public void onPress(){action.run();}
 protected void renderWidget(GuiGraphics g,int x,int y,float tick){boolean on=value.getAsBoolean();int u=kind.ordinal()*32+(on?0:16);String key=switch(kind){case FILTER->on?"button.bl":"button.wl";case LOCK->on?"golem.prop.lock":"golem.prop.unlock";case REDSTONE->on?"golem.prop.redon":"golem.prop.redoff";};setMessage(Component.translatable(key));setTooltip(Tooltip.create(getMessage()));g.blit(TEX,getX(),getY(),u,136,16,16,256,256);}
 protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output){defaultButtonNarrationText(output);}
}

