package thaumcraft.common.golems.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import thaumcraft.api.golems.seals.*;
/** Original categories, inventory positions and ghost counts on native menu transport. */
public final class SealBaseGUI extends AbstractContainerScreen<SealBaseContainer> {
 private static final ResourceLocation TEX=ResourceLocation.parse("thaumcraft:textures/gui/gui_base.png");private int category=-1;
 public SealBaseGUI(SealBaseContainer menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=176;imageHeight=232;}
 private void request(int id){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
 private void button(int x,int y,String text,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(leftPos+x,topPos+y,18,16).build());}
 @Override protected void init(){super.init();setup();}
 private void setup(){clearWidgets();category=menu.category;for(int i=0;i<menu.categories.length;i++){final int n=i;addRenderableWidget(Button.builder(Component.translatable("button.category."+menu.categories[i]),b->request(n)).bounds(leftPos-76,topPos+16+i*22,72,20).build());}
  var seal=menu.seal;var red=addRenderableWidget(new GuiGolemStateButton(leftPos+140,topPos+111,GuiGolemStateButton.Kind.REDSTONE,seal::isRedstoneSensitive,()->request(seal.isRedstoneSensitive()?28:27)));red.active=seal.getOwner().equals(menuPlayerId());
  switch(category){case 0->{button(60,55,"−",()->request(80));button(98,55,"+",()->request(81));button(60,80,"−",()->request(82));button(98,80,"+",()->request(83));var lock=addRenderableWidget(new GuiGolemStateButton(leftPos+20,topPos+100,GuiGolemStateButton.Kind.LOCK,seal::isLocked,()->request(seal.isLocked()?26:25)));lock.active=seal.getOwner().equals(menuPlayerId());}
   case 1->{if(seal.seal instanceof ISealConfigFilter)addRenderableWidget(new GuiGolemStateButton(leftPos+80,topPos+112,GuiGolemStateButton.Kind.FILTER,()->((ISealConfigFilter)menu.seal.seal).isBlacklist(),()->request(((ISealConfigFilter)menu.seal.seal).isBlacklist()?21:20)));}
   case 2->{for(int i=0;i<3;i++){final int a=i;button(60,42+i*24,"−",()->request(90+a*2));button(98,42+i*24,"+",()->request(91+a*2));}}
   case 3->{if(seal.seal instanceof ISealConfigToggles t)for(int i=0;i<t.getToggles().length;i++){final int n=i;addRenderableWidget(new GuiGolemPropButton(leftPos+20,topPos+27+i*10,Component.translatable(t.getToggles()[i].name),()->((ISealConfigToggles)menu.seal.seal).getToggles()[n].value,()->request(((ISealConfigToggles)menu.seal.seal).getToggles()[n].value?60+n:30+n)));}}
  }
 }
 private String menuPlayerId(){return minecraft.player==null?"":minecraft.player.getUUID().toString();}
 @Override protected void containerTick(){super.containerTick();if(category!=menu.category)setup();}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){}
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.blit(TEX,leftPos+8,topPos-8,96,0,160,160,256,256);g.blit(TEX,leftPos,topPos+143,0,167,176,89,256,256);g.drawCenteredString(font,Component.translatable("button.category."+menu.category),leftPos+88,topPos+8,-1);
  switch(menu.category){case 0->{g.drawCenteredString(font,Component.translatable("golem.prop.priority"),leftPos+88,topPos+39,-1);g.drawCenteredString(font,""+menu.seal.getPriority(),leftPos+88,topPos+59,-1);g.drawCenteredString(font,menu.seal.getColor()==0?Component.translatable("golem.prop.colorall"):Component.translatable("color."+DyeColor.byId(menu.seal.getColor()-1).getName()),leftPos+88,topPos+82,-1);}
   case 1->{if(menu.seal.seal instanceof ISealConfigFilter f){int s=f.getFilterSize(),sx=16+(s-1)%3*12,sy=16+(s-1)/3*12;for(int i=0;i<s;i++)g.blit(TEX,leftPos+88+i%3*24-sx,topPos+72+i/3*24-sy,0,56,32,32,256,256);}}
   case 2->{int[] sizes={menu.seal.getArea().getY(),menu.seal.getArea().getX(),menu.seal.getArea().getZ()};for(int i=0;i<3;i++){g.drawCenteredString(font,Component.translatable(new String[]{"button.caption.y","button.caption.x","button.caption.z"}[i]),leftPos+88,topPos+32+i*24,-1);g.drawCenteredString(font,""+sizes[i],leftPos+88,topPos+45+i*24,-1);}}
   case 4->{g.drawCenteredString(font,Component.translatable("button.caption.required"),leftPos+88,topPos+30,-1);traits(g,menu.seal.seal.getRequiredTags(),48);g.drawCenteredString(font,Component.translatable("button.caption.forbidden"),leftPos+88,topPos+82,-1);traits(g,menu.seal.seal.getForbiddenTags(),100);}
  }
 }
 private void traits(GuiGraphics g,thaumcraft.api.golems.EnumGolemTrait[] traits,int y){if(traits==null)return;for(int i=0;i<traits.length;i++)g.blit(traits[i].icon,leftPos+80+i*18-(traits.length-1)*9,topPos+y,0,0,16,16,16,16);}
 @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);if(menu.category==1&&menu.seal.seal instanceof ISealConfigFilter f&&!f.isBlacklist()){for(int i=0;i<f.getFilterSize();i++){var slot=menu.getSlot(i);if(slot.hasItem())g.drawString(font,f.getFilterSlotSize(i)==0?"*":""+f.getFilterSlotSize(i),leftPos+slot.x+9,topPos+slot.y+9,-1);}}renderTooltip(g,x,y);if(menu.category==4){traitTooltip(g,x,y,menu.seal.seal.getRequiredTags(),48);traitTooltip(g,x,y,menu.seal.seal.getForbiddenTags(),100);}}
 private void traitTooltip(GuiGraphics g,int x,int y,thaumcraft.api.golems.EnumGolemTrait[] traits,int yy){if(traits==null)return;for(int i=0;i<traits.length;i++){int xx=leftPos+80+i*18-(traits.length-1)*9;if(x>=xx&&x<xx+16&&y>=topPos+yy&&y<topPos+yy+16)g.renderComponentTooltip(font,java.util.List.of(Component.literal(traits[i].getLocalizedName()),Component.literal(traits[i].getLocalizedDescription())),x,y);}}
}
