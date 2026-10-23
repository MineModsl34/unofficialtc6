package thaumcraft.client.gui;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.golems.*;
import thaumcraft.api.golems.parts.*;
import thaumcraft.common.container.ContainerGolemBuilder;
import thaumcraft.common.golems.*;

/** Source208x224 press layout, strict research-filtered part selection and server-owned payment preview. */
public final class GuiGolemBuilder extends AbstractContainerScreen<ContainerGolemBuilder> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("thaumcraft:textures/gui/gui_golembuilder.png");
    private static final ResourceLocation MATERIAL_ICON = ResourceLocation.parse("thaumcraft:textures/item/golem.png");
    private final List<GolemMaterial> materials = new ArrayList<>();
    private final List<GolemHead> heads = new ArrayList<>();
    private final List<GolemArm> arms = new ArrayList<>();
    private final List<GolemLeg> legs = new ArrayList<>();
    private final List<GolemAddon> addons = new ArrayList<>();
    private static int materialIndex,headIndex,armIndex,legIndex,addonIndex;
    private GolemProperties properties;
    private ItemStack[] components = new ItemStack[0];
    private int price;
    private long lastRequest = -1;
    private long clientTicks,nextSelectionRetry;
    private record Icon(int x,int y,ResourceLocation texture,List<Component> tooltip,ItemStack stack) {}
    private final List<Icon> icons = new ArrayList<>();
    public GuiGolemBuilder(ContainerGolemBuilder menu,Inventory inventory,Component title) { super(menu,inventory,title); imageWidth = 208; imageHeight = 224; }
    @Override protected void init() {
        super.init(); var player = minecraft.player; GolemProperties.initialize(); materials.clear(); heads.clear(); arms.clear(); legs.clear(); addons.clear();
        for (var part : GolemMaterial.getMaterials()) if (ThaumcraftCapabilities.knowsResearchStrict(player,part.research)) materials.add(part);
        for (var part : GolemHead.getHeads()) if (ThaumcraftCapabilities.knowsResearchStrict(player,part.research)) heads.add(part);
        for (var part : GolemArm.getArms()) if (ThaumcraftCapabilities.knowsResearchStrict(player,part.research)) arms.add(part);
        for (var part : GolemLeg.getLegs()) if (ThaumcraftCapabilities.knowsResearchStrict(player,part.research)) legs.add(part);
        for (var part : GolemAddon.getAddons()) if (ThaumcraftCapabilities.knowsResearchStrict(player,part.research)) addons.add(part);
        materialIndex = clamp(materialIndex,materials.size()); headIndex = clamp(headIndex,heads.size()); armIndex = clamp(armIndex,arms.size()); legIndex = clamp(legIndex,legs.size()); addonIndex = clamp(addonIndex,addons.size()); updateSelection();
    }
    private static int clamp(int index,int count) { return index < 0 || index >= count ? 0 : index; }
    private void updateSelection() {
        if (materials.isEmpty() || heads.isEmpty() || arms.isEmpty() || legs.isEmpty() || addons.isEmpty()) { properties = null; components = new ItemStack[0]; return; }
        properties = new GolemProperties(); properties.setMaterial(materials.get(materialIndex)); properties.setHead(heads.get(headIndex));
        properties.setArms(arms.get(armIndex)); properties.setLegs(legs.get(legIndex)); properties.setAddon(addons.get(addonIndex));
        components = properties.generateComponents(); price = GolemPropertyRules.workCost(properties.getTraits().size(),Arrays.stream(components).mapToInt(ItemStack::getCount).toArray());
        lastRequest = -1; request(false);
    }
    private void request(boolean craft) { if (properties == null) return; var packet = menu.request(properties.toLong(),craft); if (packet != null) { lastRequest = properties.toLong(); nextSelectionRetry = clientTicks + 20; PacketDistributor.sendToServer(packet); } }
    @Override protected void containerTick() {
        super.containerTick(); clientTicks++;
        if (properties != null && menu.ready() && !menu.busy() && (lastRequest != properties.toLong()
            || menu.selected() != properties.toLong() && clientTicks >= nextSelectionRetry)) request(false);
    }
    private boolean allFound() { if (properties == null || !menu.ready() || menu.busy() || menu.selected() != properties.toLong()) return false; for (int i = 0; i < components.length; i++) if (!menu.available(i)) return false; return true; }
    private void icon(GuiGraphics graphics,int x,int y,ResourceLocation texture,String category,String key) {
        if (texture != null) graphics.blit(texture,leftPos + x,topPos + y,0,0,16,16,16,16);
        icons.add(new Icon(x,y,texture,List.of(Component.translatable("golem." + category + "." + key.toLowerCase(Locale.ROOT)),Component.translatable("golem." + category + ".text." + key.toLowerCase(Locale.ROOT))),ItemStack.EMPTY));
    }
    private void arrows(GuiGraphics graphics,int x,int y,int count) { if (count > 1 && !menu.busy()) { graphics.drawString(font,"‹",leftPos + x - 18,topPos + y + 2,0xffffff); graphics.drawString(font,"›",leftPos + x + 14,topPos + y + 2,0xffffff); } }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY) {}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY) {
        graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight,256,256); icons.clear();
        for (int[] frame : new int[][]{{12,12},{12,60},{108,12},{108,36},{108,60}}) graphics.blit(TEXTURE,leftPos + frame[0],topPos + frame[1],228,124,24,24,256,256);
        if (properties == null) return;
        var material = properties.getMaterial(); var head = properties.getHead(); var arm = properties.getArms(); var leg = properties.getLegs(); var addon = properties.getAddon();
        graphics.setColor((material.itemColor >> 16 & 255) / 255F,(material.itemColor >> 8 & 255) / 255F,(material.itemColor & 255) / 255F,1);
        icon(graphics,24,24,MATERIAL_ICON,"material",material.key); graphics.setColor(1,1,1,1);
        icon(graphics,120,24,head.icon,"head",head.key); icon(graphics,120,48,arm.icon,"arm",arm.key); icon(graphics,120,72,leg.icon,"leg",leg.key);
        if (!addon.key.equals("NONE")) icon(graphics,24,72,addon.icon,"addon",addon.key);
        arrows(graphics,24,24,materials.size()); arrows(graphics,120,24,heads.size()); arrows(graphics,120,48,arms.size()); arrows(graphics,120,72,legs.size()); arrows(graphics,24,72,addons.size());
        var traits = properties.getTraits(); int n = 0,offsetY = traits.size() <= 4 ? (traits.size() - 1) % 4 * 8 : 24,offsetX = (traits.size() - 1) / 4 % 4 * 8;
        for (var trait : traits) { int x = 72 + n / 4 * 16 - offsetX,y = 48 + n % 4 * 16 - offsetY;
            graphics.blit(trait.icon,leftPos + x,topPos + y,0,0,16,16,16,16); icons.add(new Icon(x,y,trait.icon,List.of(Component.translatable("golem.trait." + trait.name().toLowerCase(Locale.ROOT)),Component.translatable("golem.trait.text." + trait.name().toLowerCase(Locale.ROOT))),ItemStack.EMPTY)); n++; }
        graphics.blit(Aspect.MECHANISM.getImage(),leftPos + 152,topPos + 24,0,0,16,16,16,16);
        icons.add(new Icon(152,24,Aspect.MECHANISM.getImage(),List.of(Component.translatable("tc.aspect.machina")),ItemStack.EMPTY));
        graphics.drawString(font,Integer.toString(price),leftPos + 162 - font.width(Integer.toString(price)),topPos + 24,0xffffff,true);
        int row = 1,col = 0;
        for (int i = 0; i < components.length && col < 4; i++) { int x = 152 + col * 16,y = 24 + row * 16;
            if (menu.selected() != properties.toLong() || !menu.available(i)) graphics.blit(TEXTURE,leftPos + x - 8,topPos + y - 8,240,0,16,16,256,256);
            graphics.renderItem(components[i],leftPos + x,topPos + y); graphics.renderItemDecorations(font,components[i],leftPos + x,topPos + y);
            icons.add(new Icon(x,y,null,List.of(),components[i])); if (++row > 3) { row = 0; col++; }
        }
        graphics.blit(TEXTURE,leftPos + 120,topPos + 104,216,64,24,16,256,256);
        if (!allFound()) graphics.blit(TEXTURE,leftPos + 120,topPos + 104,216,40,24,16,256,256);
        if (menu.cost() > 0 && menu.maximum() > 0) graphics.blit(TEXTURE,leftPos + 145,topPos + 89,209,89,(int)(46F * (1 - menu.cost() / (float)menu.maximum())),6,256,256);
        int hp = 10 + material.healthMod,armor = material.armor; double damage = properties.hasTrait(EnumGolemTrait.FIGHTER) ? material.damage : 0;
        if (properties.hasTrait(EnumGolemTrait.FRAGILE)) hp = (int)(hp * .75); if (properties.hasTrait(EnumGolemTrait.ARMORED)) armor = (int)Math.max(armor * 1.5,armor + 1);
        if (properties.hasTrait(EnumGolemTrait.FRAGILE)) armor = (int)(armor * .75); if (properties.hasTrait(EnumGolemTrait.BRUTAL)) damage = Math.max(damage * 1.5,damage + 1);
        graphics.drawCenteredString(font,Float.toString(hp / 2F),leftPos + 48,topPos + 108,0xffffff); graphics.drawCenteredString(font,Float.toString(armor / 2F),leftPos + 72,topPos + 108,0xffffff); graphics.drawCenteredString(font,Float.toString((float)(damage / 2)),leftPos + 97,topPos + 108,0xffffff);
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
        super.render(graphics,mouseX,mouseY,partial); renderTooltip(graphics,mouseX,mouseY);
        for (var icon : icons) if (mouseX >= leftPos + icon.x && mouseX < leftPos + icon.x + 16 && mouseY >= topPos + icon.y && mouseY < topPos + icon.y + 16) {
            if (!icon.stack.isEmpty()) graphics.renderTooltip(font,icon.stack,mouseX,mouseY); else graphics.renderComponentTooltip(font,icon.tooltip,mouseX,mouseY); break;
        }
    }
    private boolean scroll(double mx,double my,int x,int y,int count,java.util.function.IntConsumer change) {
        if (count < 2 || mx < leftPos + x - 18 || mx >= leftPos + x + 30 || my < topPos + y - 3 || my >= topPos + y + 13) return false;
        if (mx < leftPos + x - 8) change.accept(-1); else if (mx >= leftPos + x + 14) change.accept(1); else return false;
        updateSelection(); return true;
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if (button == 0 && !menu.busy()) {
            if (allFound() && x >= leftPos + 120 && x < leftPos + 144 && y >= topPos + 104 && y < topPos + 120) { request(true); return true; }
            if (scroll(x,y,24,24,materials.size(),delta -> materialIndex = Math.floorMod(materialIndex + delta,materials.size()))
                || scroll(x,y,120,24,heads.size(),delta -> headIndex = Math.floorMod(headIndex + delta,heads.size()))
                || scroll(x,y,120,48,arms.size(),delta -> armIndex = Math.floorMod(armIndex + delta,arms.size()))
                || scroll(x,y,120,72,legs.size(),delta -> legIndex = Math.floorMod(legIndex + delta,legs.size()))
                || scroll(x,y,24,72,addons.size(),delta -> addonIndex = Math.floorMod(addonIndex + delta,addons.size()))) return true;
        }
        return super.mouseClicked(x,y,button);
    }
}
