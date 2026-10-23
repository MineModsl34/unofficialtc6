package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.common.container.ContainerConstruct;
import thaumcraft.common.entities.construct.*;

/** Native slots/menu-button packets on the original three 175x232 GUI textures. */
public final class GuiConstruct extends AbstractContainerScreen<ContainerConstruct> {
    private final ResourceLocation texture;
    public GuiConstruct(ContainerConstruct menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=175;imageHeight=232;texture=ResourceLocation.parse("thaumcraft:textures/gui/"+(menu.construct instanceof EntityArcaneBore?"gui_arcanebore":menu.construct instanceof EntityTurretCrossbowAdvanced?"gui_turret_advanced":"gui_turret_basic")+".png");}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){
        graphics.blit(texture,leftPos,topPos,0,0,imageWidth,imageHeight,256,256);boolean advanced=menu.construct instanceof EntityTurretCrossbowAdvanced;
        int health=(int)(39F*menu.construct.getHealth()/menu.construct.getMaxHealth());graphics.blit(texture,leftPos+(advanced?30:68),topPos+59,192,48,health,6,256,256);
        if(menu.construct instanceof EntityTurretCrossbowAdvanced turret){boolean[] flags={turret.getTargetAnimal(),turret.getTargetMob(),turret.getTargetPlayer(),turret.getTargetFriendly()};for(int i=0;i<4;i++){int yy=topPos+13+i*14;graphics.blit(texture,leftPos+90,yy,192,16,8,8,256,256);if(flags[i])graphics.blit(texture,leftPos+90,yy,192,24,8,8,256,256);graphics.drawString(font,Component.translatable("button.turretfocus."+(i+1)),leftPos+102,yy,-1);}}
        if(menu.construct instanceof EntityArcaneBore bore){var tool=bore.getMainHandItem();if(!tool.isEmpty() && tool.getDamageValue()+1>=tool.getMaxDamage())graphics.blit(texture,leftPos+80,topPos+29,240,0,16,16,256,256);
            graphics.pose().pushPose();graphics.pose().translate(leftPos+124,topPos+18,0);graphics.pose().scale(.5F,.5F,1);graphics.drawString(font,"Width: "+(1+bore.getDigRadius()*2),0,0,-1);graphics.drawString(font,"Depth: "+bore.getDigDepth(),64,0,-1);graphics.drawString(font,"Speed: +"+bore.getDigSpeed(Blocks.STONE.defaultBlockState()),0,10,-1);int yy=34,refining=bore.getRefining(),fortune=bore.getFortune();if(bore.hasSilkTouch() || refining>0 || fortune>0)graphics.drawString(font,"Other properties:",0,24,-1);if(refining>0){graphics.drawString(font,"Refining "+refining,4,yy,12632256);yy+=9;}if(fortune>0){graphics.drawString(font,"Fortune "+fortune,4,yy,15648330);yy+=9;}if(bore.hasSilkTouch())graphics.drawString(font,"Silk Touch",4,yy,8421631);graphics.pose().popPose();
        }
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0 && menu.construct instanceof EntityTurretCrossbowAdvanced && x>=leftPos+90 && x<leftPos+98)for(int i=0;i<4;i++)if(y>=topPos+13+i*14 && y<topPos+21+i*14){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,i+1);return true;}return super.mouseClicked(x,y,button);}
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){super.render(graphics,x,y,partial);renderTooltip(graphics,x,y);}
}
