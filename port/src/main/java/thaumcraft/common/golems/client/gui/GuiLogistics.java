package thaumcraft.common.golems.client.gui;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.common.golems.seals.Golem9SealNetwork.LogisticsAction;
public final class GuiLogistics extends AbstractContainerScreen<ContainerLogistics> {
 private static final ResourceLocation TEX=ResourceLocation.parse("thaumcraft:textures/gui/gui_logistics.png");private int selected=-1,count=1;private EditBox search;
 public GuiLogistics(ContainerLogistics menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=imageHeight=215;}
 @Override protected void init(){super.init();addRenderableWidget(Button.builder(Component.literal("▲"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1)).bounds(leftPos+195,topPos+16,16,18).build());addRenderableWidget(Button.builder(Component.literal("▼"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+195,topPos+178,16,18).build());addRenderableWidget(Button.builder(Component.literal("−"),b->count=Math.max(1,count-1)).bounds(leftPos+13,topPos+193,18,18).build());addRenderableWidget(Button.builder(Component.literal("+"),b->count=Math.min(4096,count+1)).bounds(leftPos+58,topPos+193,18,18).build());
  addRenderableWidget(Button.builder(Component.translatable("tc.logistics.request"),b->{if(selected>=0&&menu.getSlot(selected).hasItem())PacketDistributor.sendToServer(new LogisticsAction(menu.containerId,1,selected,Math.min(count,menu.getSlot(selected).getItem().getCount()),""));}).bounds(leftPos+78,topPos+195,62,18).build());search=addRenderableWidget(new EditBox(font,leftPos+143,topPos+196,55,14,Component.translatable("tc.logistics.search")));search.setMaxLength(10);search.setResponder(text->PacketDistributor.sendToServer(new LogisticsAction(menu.containerId,0,0,0,text)));
 }
 @Override protected void slotClicked(Slot slot,int id,int button,ClickType type){if(slot!=null&&id>=0&&id<81&&slot.hasItem()){selected=id;count=Math.max(1,Math.min(count,slot.getItem().getCount()));}}
 @Override public boolean mouseScrolled(double x,double y,double sx,double sy){if(sy!=0){selected=-1;minecraft.gameMode.handleInventoryButtonClick(menu.containerId,sy>0?1:0);return true;}return super.mouseScrolled(x,y,sx,sy);}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){}
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.blit(TEX,leftPos,topPos,0,0,215,215,256,256);if(selected>=0){g.blit(TEX,leftPos+17+selected%9*19,topPos+17+selected/9*19,222,46,20,20,256,256);g.drawCenteredString(font,""+count,leftPos+45,topPos+197,0xff333333);}}
 @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);renderTooltip(g,x,y);}
}
