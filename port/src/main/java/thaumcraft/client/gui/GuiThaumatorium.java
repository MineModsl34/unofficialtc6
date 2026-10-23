package thaumcraft.client.gui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.container.ContainerThaumatorium;

/** Source book-free alchemy GUI: six icons in two columns and eight aspect cost indicators. */
public final class GuiThaumatorium extends AbstractContainerScreen<ContainerThaumatorium> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("thaumcraft:textures/gui/gui_thaumatorium.png");
    private int index;
    public GuiThaumatorium(ContainerThaumatorium menu,Inventory inventory,Component title) { super(menu,inventory,title); imageWidth = 175; imageHeight = 216; }
    private List<ContainerThaumatorium.View> page() {
        int start = Math.min(menu.views().size(),Math.max(0,index * 2));
        return menu.views().subList(start,Math.min(start + 6,menu.views().size()));
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY) {}
    @Override protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY) {
        graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight,256,256);
        index = Math.min(index,Math.max(0,(menu.views().size() - 5) / 2));
        if (index > 0) graphics.blit(TEXTURE,leftPos + 82,topPos + 56,176,56,8,11,256,256);
        if (index < menu.views().size() / 2F - 3) graphics.blit(TEXTURE,leftPos + 82,topPos + 93,176,93,8,11,256,256);
        long time = System.currentTimeMillis();
        var selected = menu.views().stream().filter(ContainerThaumatorium.View::selected).toList();
        if (!selected.isEmpty()) {
            var recipe = selected.get((int)(time / 1000 % selected.size())); int n = 0;
            for (Aspect aspect : recipe.cost().getAspectsSortedByName()) {
                int x = leftPos + 96 + 16 * (n % 2),y = topPos + 24 + 20 * (n / 2);
                graphics.blit(aspect.getImage(),x,y,0,0,16,16,16,16);
                graphics.drawString(font,Integer.toString(recipe.cost().getAmount(aspect)),x + 7,y + 8,0xffffff,true);
                graphics.blit(TEXTURE,x + 2,y + 16,176,4,12,3,256,256);
                int required = recipe.cost().getAmount(aspect); int width = required == 0 ? 0 : Math.max(0,Math.min(12,menu.contents().getAmount(aspect) * 12 / required));
                if (width > 0) graphics.fill(x + 2,y + 16,x + 2 + width,y + 19,0xff000000 | aspect.getColor());
                if (++n >= 8) break;
            }
        }
        if (menu.capacity() > 1) graphics.drawCenteredString(font,selected.size() + "/" + menu.capacity(),leftPos + 64,topPos + 46,0xffffff);
        int n = 0;
        for (var view : page()) {
            int x = leftPos + 48 + n % 2 * 16,y = topPos + 56 + n / 2 * 16;
            if (view.selected()) graphics.blit(TEXTURE,x,y,176,8,16,16,256,256);
            graphics.renderItem(view.output(),x,y); graphics.renderItemDecorations(font,view.output(),x,y); n++;
        }
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        super.render(graphics,mouseX,mouseY,partialTick); renderTooltip(graphics,mouseX,mouseY);
        int n = 0; for (var view : page()) { int x = leftPos + 48 + n % 2 * 16,y = topPos + 56 + n / 2 * 16; if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) graphics.renderTooltip(font,view.output(),mouseX,mouseY); n++; }
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        if (button == 0) {
            int n = 0; for (var view : page()) {
                int x = leftPos + 48 + n % 2 * 16,y = topPos + 56 + n / 2 * 16;
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    var packet = menu.selection(view.id()); if (packet != null) PacketDistributor.sendToServer(packet); return true;
                }
                n++;
            }
            if (mouseX >= leftPos + 82 && mouseX < leftPos + 90) {
                if (index > 0 && mouseY >= topPos + 56 && mouseY < topPos + 67) { index--; return true; }
                if (index < menu.views().size() / 2F - 3 && mouseY >= topPos + 93 && mouseY < topPos + 104) { index++; return true; }
            }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }
}
