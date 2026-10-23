package thaumcraft.client.gui;
import java.util.*;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.casters.ICaster;
import thaumcraft.client.CasterClientEvents;
import thaumcraft.common.items.casters.CasterManager;
import thaumcraft.common.lib.network.misc.PacketCasterAction;
/** Non-pausing native screen replaces source ungrabbed-mouse HUD, retaining sorted radius, hover and F-release selection. */
public final class GuiFocusRadial extends Screen{
    private final LinkedHashMap<String,ItemStack> foci=new LinkedHashMap<>();private final Map<String,Float> scales=new HashMap<>();private String hovered;private long opened=System.currentTimeMillis(),previous=opened;
    public GuiFocusRadial(){super(Component.translatable("key.changefocus"));var player=net.minecraft.client.Minecraft.getInstance().player;if(player!=null)foci.putAll(CasterManager.getAvailableFoci(player));for(String key:foci.keySet())scales.put(key,1F);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partial){}
    @Override public void tick(){if(minecraft.player==null||minecraft.player.isSpectator()){onClose();return;}if(!com.mojang.blaze3d.platform.InputConstants.isKeyDown(minecraft.getWindow().getWindow(),CasterClientEvents.FOCUS.getKey().getValue()))selectAndClose();}
    private void selectAndClose(){if(hovered!=null)PacketDistributor.sendToServer(PacketCasterAction.focus(hovered));onClose();}
    @Override public boolean keyReleased(int key,int scan,int mod){if(CasterClientEvents.FOCUS.matches(key,scan)){selectAndClose();return true;}return super.keyReleased(key,scan,mod);}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&hovered!=null){selectAndClose();return true;}return super.mouseClicked(x,y,button);}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){if(minecraft.player==null||foci.isEmpty())return;long now=System.currentTimeMillis();float scale=Math.min(1,(now-opened)/150F),width=16+foci.size()*2.5F;int cx=this.width/2,cy=this.height/2;float rotation=partial+minecraft.player.tickCount%720/2F;ring(graphics,"radial",cx,cy,width*2.75F*scale,rotation);ring(graphics,"radial2",cx,cy,width*2.55F*scale,-rotation);var held=minecraft.player.getMainHandItem().getItem() instanceof ICaster?minecraft.player.getMainHandItem():minecraft.player.getOffhandItem();ItemStack tooltip=null;if(held.getItem() instanceof ICaster caster){var current=caster.getFocusStack(held);if(current!=null&&!current.isEmpty()){graphics.renderItem(current,cx-8,cy-8);if(Math.abs(mouseX-cx)<=10&&Math.abs(mouseY-cy)<=10)tooltip=current;}}hovered=null;float angle=-90*scale;for(var entry:foci.entrySet()){double x=Math.cos(angle*Math.PI/180)*width,y=Math.sin(angle*Math.PI/180)*width;angle+=360F/foci.size();boolean over=Math.abs(mouseX-cx-x)<=10&&Math.abs(mouseY-cy-y)<=10;float old=scales.get(entry.getKey()),delta=(now-previous)/(over?150F:250F);float itemScale=over?Math.min(1.3F,old+delta):Math.max(1,old-delta);scales.put(entry.getKey(),itemScale);if(over){hovered=entry.getKey();tooltip=entry.getValue();}graphics.pose().pushPose();graphics.pose().translate(cx+(int)(x*scale),cy+(int)(y*scale),100);graphics.pose().scale(scale*itemScale,scale*itemScale,1);graphics.renderItem(entry.getValue(),-8,-8);graphics.pose().popPose();}previous=now;if(tooltip!=null)graphics.renderTooltip(font,tooltip,mouseX,mouseY);}
    private void ring(GuiGraphics graphics,String file,int x,int y,float size,float angle){graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));graphics.setColor(.5F,.5F,.5F,.5F);int s=(int)size;graphics.blit(ResourceLocation.parse("thaumcraft:textures/misc/"+file+".png"),-s/2,-s/2,s,s,0,0,256,256,256,256);graphics.setColor(1,1,1,1);graphics.pose().popPose();}
}
