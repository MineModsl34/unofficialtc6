package thaumcraft.client.gui;

import java.util.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.casters.*;
import thaumcraft.common.container.ContainerFocalManipulator;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.tiles.crafting.*;

/** Native validated focus editor with source 24x32 tree layout, connector sprites and typed nodes. */
public final class GuiFocalManipulator extends AbstractContainerScreen<ContainerFocalManipulator> {
    private final HashMap<Integer,FocusElementNode> graph = new HashMap<>();
    private final Map<String,Set<String>> allowedSettings = new HashMap<>();
    private EditBox name;
    private int selected = 1,palettePage;
    private long stateVersion = -1;
    private ItemStack lastFocus = ItemStack.EMPTY;
    private boolean dirty,awaiting,wasBusy;
    private String status = "";
    private int graphScrollX,graphScrollY;
    private boolean graphDragging;
    private static final ResourceLocation FRAME=ResourceLocation.parse("thaumcraft:textures/gui/gui_wandtable2.png"),PARTS=ResourceLocation.parse("thaumcraft:textures/gui/gui_wandtable.png");
    public GuiFocalManipulator(ContainerFocalManipulator menu,Inventory inventory,Component title) { super(menu,inventory,title); imageWidth = 356; imageHeight = 258; }
    @Override protected void init() { super.init(); rebuild(); }
    private void blankTree() {
        graph.clear(); FocusElementNode root = new FocusElementNode(); root.id = 0; root.parent = -1; root.node = new FocusMediumRoot(); root.children = new int[]{1}; graph.put(0,root);
        FocusElementNode blank = new FocusElementNode(); blank.id = 1; blank.parent = 0; graph.put(1,blank); selected = 1;
    }
    private void refresh() {
        ItemStack stack = menu.getSlot(0).getItem(); boolean itemChanged = !ItemStack.matches(stack,lastFocus),busyChanged = menu.busy() != wasBusy;
        if (menu.stateVersion() == stateVersion && !itemChanged && !busyChanged) return;
        CompoundTag state = menu.view(); boolean reload = !dirty || itemChanged || busyChanged || awaiting && !menu.rejected();
        if (reload) {
            graph.clear(); try { for (var row : TileFocalManipulator.readNodes(state)) graph.put(row.id,row); } catch (IllegalArgumentException empty) { blankTree(); }
            if (!graph.containsKey(selected)) selected = 1; dirty = false;
        }
        allowedSettings.clear(); ListTag known = state.getList("known",Tag.TAG_COMPOUND);
        for (int i = 0; i < known.size(); i++) { var row = known.getCompound(i); Set<String> settings = new HashSet<>(); var list = row.getList("settings",Tag.TAG_STRING); for (int j = 0; j < list.size(); j++) settings.add(list.getString(j)); allowedSettings.put(row.getString("key"),settings); }
        if (awaiting && menu.rejected()) status = Component.translatable("gui.thaumcraft.focus.rejected").getString();
        else if (awaiting) status = "";
        awaiting = false; wasBusy = menu.busy(); stateVersion = menu.stateVersion(); lastFocus = stack.copy();
        String draftName = reload ? state.getString("focusName") : name == null ? "" : name.getValue();
        // Source fills the unnamed focus title on the client, using its selected language.
        if (reload && draftName.isEmpty() && !stack.isEmpty()) draftName = stack.getHoverName().getString();
        rebuild(draftName);
    }
    @Override protected void containerTick() { super.containerTick(); refresh(); }
    private void rebuild() { rebuild(name == null ? "" : name.getValue()); }
    private void rebuild(String draftName) {
        layoutGraph();
        clearWidgets();
        name = new EditBox(font,leftPos + 146,topPos + 10,198,18,Component.translatable("gui.thaumcraft.focus.name")); name.setMaxLength(50); name.setValue(draftName); name.setEditable(!menu.busy()); name.setResponder(value -> dirty = true); addRenderableWidget(name);
        List<String> palette = palette(); int pages = Math.max(1,(palette.size() + 5) / 6); palettePage = Math.min(palettePage,pages - 1);
        for (int i = 0; i < 6; i++) { int n = palettePage * 6 + i; if (n >= palette.size()) break; String key = palette.get(n); FocusNode node = (FocusNode)FocusEngine.getElement(key);
            Button b = Button.builder(Component.translatable(node.getUnlocalizedName()),button -> choose(key)).bounds(leftPos + 8,topPos + 33 + i * 17,128,16).build(); b.active = !menu.busy() && menu.ready() && menu.getSlot(0).hasItem(); addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.literal("<"),b -> { palettePage = Math.max(0,palettePage - 1); rebuild(); }).bounds(leftPos + 8,topPos + 171,24,16).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b -> { palettePage = Math.min(pages - 1,palettePage + 1); rebuild(); }).bounds(leftPos + 110,topPos + 171,24,16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.thaumcraft.focus.reset"),b -> { if (!menu.busy()) { blankTree(); dirty = true; rebuild(); } }).bounds(leftPos + 8,topPos + 192,62,18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.thaumcraft.focus.save"),b -> submit(false)).bounds(leftPos + 74,topPos + 192,62,18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.thaumcraft.focus.craft"),b -> submit(true)).bounds(leftPos + 8,topPos + 222,128,18).build());
        FocusElementNode row = graph.get(selected);
        if (row != null && row.node != null) {
            List<String> settings = row.node.getSettingList().stream().sorted().toList();
            for (int i = 0; i < settings.size(); i++) { String key = settings.get(i); NodeSetting setting = row.node.getSetting(key); int x = leftPos + 146 + i % 2 * 100,y = topPos + 141 + i / 2 * 15;
                Button minus = Button.builder(Component.literal("−"),b -> { setting.decrement(); dirty = true; }).bounds(x,y,14,14).build(); Button plus = Button.builder(Component.literal("+"),b -> { setting.increment(); dirty = true; }).bounds(x + 82,y,14,14).build();
                boolean unlocked = allowedSettings.getOrDefault(row.node.getKey(),Set.of()).contains(key); minus.active = plus.active = unlocked && !menu.busy(); addRenderableWidget(minus); addRenderableWidget(plus);
            }
        }
    }
    private List<String> palette() {
        FocusElementNode row = graph.get(selected),parent = row == null ? null : graph.get(row.parent);
        if (parent == null || parent.node == null) return List.of();
        Set<String> exclusive = new HashSet<>(); boolean exclusiveMedium = false,hasMedium = false;
        Set<Integer> replaced = new HashSet<>(); collectIds(row,replaced);
        for (var placed : graph.values()) if (!replaced.contains(placed.id) && placed.node != null) {
            if (placed.node.isExclusive()) exclusive.add(placed.node.getKey());
            if (placed.node instanceof FocusMedium && !(placed.node instanceof FocusMediumRoot)) { hasMedium = true; if (placed.node.isExclusive()) exclusiveMedium = true; }
        }
        List<String> result = new ArrayList<>();
        for (String key : allowedSettings.keySet()) { IFocusElement element = FocusEngine.getElement(key); if (!(element instanceof FocusNode node) || node instanceof FocusMediumRoot || exclusive.contains(key)) continue;
            if (node instanceof FocusMedium && (exclusiveMedium || node.isExclusive() && hasMedium)) continue;
            if (node.mustBeSupplied() != null && Arrays.stream(node.mustBeSupplied()).anyMatch(parent.node::canSupply)) result.add(key);
        }
        result.sort(Comparator.comparing(key -> Component.translatable(((FocusNode)FocusEngine.getElement(key)).getUnlocalizedName()).getString())); return result;
    }
    private void collectIds(FocusElementNode row,Set<Integer> result) { if (row == null || !result.add(row.id)) return; for (int id : row.children) collectIds(graph.get(id),result); }
    private int nextId() { for (int id = 1; id < FocusGraphRules.MAX_NODES; id++) if (!graph.containsKey(id)) return id; throw new IllegalArgumentException("Focus node limit"); }
    private void choose(String key) {
        FocusElementNode row = graph.get(selected); if (row == null || selected == 0 || menu.busy()) return;
        Set<Integer> remove = new HashSet<>(); for (int child : row.children) collectIds(graph.get(child),remove); remove.forEach(graph::remove);
        row.node = (FocusNode)FocusEngine.getElement(key); int count = row.node instanceof FocusModSplit ? 2 : row.node instanceof FocusEffect ? 0 : 1; row.children = new int[count];
        for (int i = 0; i < count; i++) { FocusElementNode blank = new FocusElementNode(); blank.id = nextId(); blank.parent = row.id; blank.x=row.x+(count==2?(i==0?-1:1):0);blank.y=row.y+1;graph.put(blank.id,blank); row.children[i] = blank.id; }
        if (count > 0) selected = row.children[0]; dirty = true; palettePage = 0; status = ""; rebuild();
    }
    private List<FocusElementNode> ordered() { List<FocusElementNode> list = new ArrayList<>(); order(graph.get(0),list); return list; }
    private void order(FocusElementNode row,List<FocusElementNode> list) { if (row == null || list.size() >= FocusGraphRules.MAX_NODES) return; list.add(row); for (int id : row.children) order(graph.get(id),list); }
    private int depth(FocusElementNode row) { int depth = 0; while (row.parent >= 0 && depth < 64) { row = graph.get(row.parent); if (row == null) break; depth++; } return depth; }
    private void submit(boolean craft) {
        if (menu.busy() || !menu.ready()) return;
        try { if (!(menu.getSlot(0).getItem().getItem() instanceof ItemFocus item)) return;
            FocusGraphRules.validate(graph.values().stream().map(FocusElementNode::rule).toList(),item.getMaxComplexity(),craft);
            var packet = menu.request(TileFocalManipulator.writeNodes(graph.values()),name.getValue(),craft);
            if (packet != null) { awaiting = true; PacketDistributor.sendToServer(packet); }
        } catch (IllegalArgumentException invalid) { status = Component.translatable("gui.thaumcraft.focus.incomplete").getString(); }
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY) {}
    @Override protected void renderBg(GuiGraphics g,float partialTick,int mouseX,int mouseY) {
        sourcePanel(g,leftPos,topPos,imageWidth,imageHeight);
        g.fill(leftPos + 144,topPos + 31,leftPos + 346,topPos + 137,0xff211f2b); g.drawString(font,title,leftPos + 8,topPos + 12,0xffffd0,false);
        for (SlotSlot slot : inventoryBackground()) g.fill(leftPos + slot.x - 1,topPos + slot.y - 1,leftPos + slot.x + 17,topPos + slot.y + 17,0xff8b817a);
        drawGraph(g,mouseX,mouseY);
        FocusElementNode row = graph.get(selected);
        if (row != null && row.node != null) { var settings = row.node.getSettingList().stream().sorted().toList(); for (int i = 0; i < settings.size(); i++) { NodeSetting setting = row.node.getSetting(settings.get(i)); String value = setting.getLocalizedName() + ": " + setting.getValueText(); g.drawCenteredString(font,font.plainSubstrByWidth(value,65),leftPos + 195 + i % 2 * 100,topPos + 144 + i / 2 * 15,0xffffff); } }
        g.drawCenteredString(font,(palettePage + 1) + "/" + Math.max(1,(palette().size() + 5) / 6),leftPos + 70,topPos + 175,0xffffff);
        Map<thaumcraft.api.aspects.Aspect,Integer> crystals = new LinkedHashMap<>();
        for (var placed : graph.values()) if (placed.node != null && placed.node.getAspect() != null) crystals.merge(placed.node.getAspect(),1,Integer::sum);
        int crystalIndex = 0;
        for (var cost : crystals.entrySet()) { int x = leftPos + 10 + crystalIndex % 6 * 21,y = topPos + 138 + crystalIndex / 6 * 18;
            g.blit(cost.getKey().getImage(),x,y,0,0,16,16,16,16); g.drawString(font,Integer.toString(cost.getValue()),x + 9,y + 9,0xffffff,true); if (++crystalIndex >= 12) break;
        }
        if (menu.busy()) { float initial = menu.view().getFloat("initialVis"),remaining = menu.view().getFloat("vis"); g.drawString(font,String.format(java.util.Locale.ROOT,"Vis: %.1f / %.1f",remaining,initial),leftPos + 8,topPos + 212,0x80e8ff,false); }
        else if (menu.getSlot(0).getItem().getItem() instanceof ItemFocus item) try { int complexity = FocusGraphRules.validate(graph.values().stream().map(FocusElementNode::rule).toList(),item.getMaxComplexity(),false); var cost = FocusGraphRules.costs(complexity,item.getMaxComplexity()); g.drawString(font,complexity + "/" + item.getMaxComplexity() + " • XP " + cost.experience() + " • Vis " + (int)cost.vis(),leftPos + 8,topPos + 212,0xffffd0,false); } catch (IllegalArgumentException invalid) { g.drawString(font,"— / " + item.getMaxComplexity(),leftPos + 8,topPos + 212,0xff9999,false); }
        if (!status.isEmpty()) g.drawString(font,font.plainSubstrByWidth(status,335),leftPos + 8,topPos + 246,0xff8888,false);
    }
    private record SlotSlot(int x,int y) {}
    private List<SlotSlot> inventoryBackground() { return menu.slots.stream().map(slot -> new SlotSlot(slot.x,slot.y)).toList(); }
    private static void sourcePanel(GuiGraphics g,int x,int y,int width,int height){int edge=24,source=232;int[] dstX={x,x+edge,x+width-edge},dstY={y,y+edge,y+height-edge},dstW={edge,width-edge*2,edge},dstH={edge,height-edge*2,edge},src={0,edge,source-edge},srcSize={edge,source-edge*2,edge};for(int i=0;i<3;i++)for(int j=0;j<3;j++)g.blit(FRAME,dstX[i],dstY[j],dstW[i],dstH[j],src[i],src[j],srcSize[i],srcSize[j],256,256);}
    private boolean inGraph(double x,double y){return x>=leftPos+144&&x<leftPos+346&&y>=topPos+31&&y<topPos+137;}
    private int graphX(FocusElementNode row){return leftPos+245+row.x*24-graphScrollX;}
    private int graphY(FocusElementNode row){return topPos+49+row.y*32-graphScrollY;}
    private void clampGraph(){int min=0,max=0,height=0;for(var row:graph.values()){min=Math.min(min,row.x);max=Math.max(max,row.x);height=Math.max(height,row.y);}graphScrollX=Math.max(min*24-72,Math.min(max*24+72,graphScrollX));graphScrollY=Math.max(-8,Math.min(Math.max(0,height*32-70),graphScrollY));}
    private void layoutGraph(){
        assignY(graph.get(0),0,new HashSet<>());FocusElementNode first=graph.values().stream().filter(row->row.node instanceof FocusModSplit).min(Comparator.comparingInt(row->row.id)).orElse(null);
        if(first!=null){int[] bound={0};processLeft(first,bound,new HashSet<>());moveNodes(first,bound[0]/2,new HashSet<>());}
        for(var row:graph.values())if(row.node instanceof FocusModSplit){var parent=graph.get(row.parent);if(parent!=null&&parent.node!=null&&!(parent.node instanceof FocusModSplit))row.x=parent.x;else if(row.children.length>0){int x=0;for(int id:row.children){var child=graph.get(id);if(child!=null)x+=child.x;}row.x=x/row.children.length;}}
        clampGraph();
    }
    private void assignY(FocusElementNode row,int y,Set<Integer> seen){if(row==null||!seen.add(row.id))return;row.y=y;for(int i=0;i<row.children.length;i++){var child=graph.get(row.children[i]);if(child!=null)child.x=row.x+(row.children.length==2?(i==0?-1:1):0);assignY(child,y+1,seen);}}
    private void processLeft(FocusElementNode row,int[] bounds,Set<Integer> seen){if(row==null||!seen.add(row.id))return;if(row.children.length>0)processLeft(graph.get(row.children[0]),bounds,seen);int old=0;if(row.children.length==1){old=bounds[0]-1;var child=graph.get(row.children[0]);if(child!=null)bounds[0]=child.x;}row.x=bounds[0];if(row.children.length==1)bounds[0]=old;bounds[0]++;if(row.children.length>1)processLeft(graph.get(row.children[1]),bounds,seen);}
    private void moveNodes(FocusElementNode row,int amount,Set<Integer> seen){if(row==null||!seen.add(row.id))return;for(int id:row.children)moveNodes(graph.get(id),amount,seen);row.x-=amount;}
    private void graphSprite(GuiGraphics g,int x,int y,int u,int v,int w,int h){g.blit(PARTS,x,y,u,v,w,h,256,256);}
    private void drawGraph(GuiGraphics g,int mouseX,int mouseY){
        g.enableScissor(leftPos+144,topPos+31,leftPos+346,topPos+137);
        for(var row:graph.values()){int x=graphX(row),y=graphY(row);var parent=graph.get(row.parent);if(parent!=null){graphSprite(g,x-6,y-22,54,232,12,12);if(parent.node instanceof FocusModSplit){int q=Math.abs(row.x-parent.x);for(int i=0;i<q;i++)if(row.x<parent.x)graphSprite(g,i==0?x-4:x-12+i*24,y-36,i==0?8:72,240,i==0?16:24,16);else graphSprite(g,i==0?x-12:x-12-i*24,y-36,i==0?24:72,240,i==0?16:24,16);}}
            boolean hover=inGraph(mouseX,mouseY)&&Math.abs(mouseX-x)<=12&&Math.abs(mouseY-y)<=12;
            if(row.node==null)graphSprite(g,x-12,y-12,120,232,24,24);else drawPart(g,row.node,x,y,hover);
            if(row.id==selected||hover&&row.parent>=0)graphSprite(g,x-12,y-12,96,232,24,24);
            if(row.node==null&&parent!=null&&parent.node!=null){boolean target=parent.node.canSupply(FocusNode.EnumSupplyType.TARGET),trajectory=parent.node.canSupply(FocusNode.EnumSupplyType.TRAJECTORY);int shift=target&&trajectory?4:0;if(target)g.blit(PARTS,x-shift-4,y-4,8,8,152,240,16,16,256,256);if(trajectory)g.blit(PARTS,x+shift-4,y-4,8,8,168,240,16,16,256,256);}
        }g.disableScissor();
    }
    private void drawPart(GuiGraphics g,FocusNode node,int x,int y,boolean hover){
        boolean root=node.getType()==IFocusElement.EnumUnitType.MOD||node instanceof FocusMediumRoot;float scale=root?48:24;int color=FocusEngine.getElementColor(node.getKey());ResourceLocation decoration=node.getType()==IFocusElement.EnumUnitType.EFFECT?ResourceLocation.parse("thaumcraft:textures/foci/_effect.png"):node.getType()==IFocusElement.EnumUnitType.MEDIUM&&!root?ResourceLocation.parse("thaumcraft:textures/foci/_medium.png"):null;
        if(decoration!=null){g.flush();RenderSystem.setShaderColor((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,1);int size=(int)(scale*.9F+(hover?2:0));g.blit(decoration,x-size/2,y-size/2,size,size,0,0,16,16,16,16);g.flush();RenderSystem.setShaderColor(1,1,1,1);}
        var icon=FocusEngine.getElementIcon(node.getKey());if(icon!=null){int size=(int)(scale/2+(hover?2:0));g.blit(icon,x-size/2,y-size/2,size,size,0,0,16,16,16,16);}
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) { super.render(graphics,mouseX,mouseY,partialTick); renderTooltip(graphics,mouseX,mouseY);if(inGraph(mouseX,mouseY))for(var row:graph.values())if(row.node!=null&&Math.abs(mouseX-graphX(row))<=12&&Math.abs(mouseY-graphY(row))<=12){graphics.renderTooltip(font,Component.translatable(row.node.getUnlocalizedName()),mouseX,mouseY);break;} }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(inGraph(x,y)&&(button==0||button==1)){for(var row:graph.values())if(row.parent>=0&&Math.abs(x-graphX(row))<=12&&Math.abs(y-graphY(row))<=12){selected=row.id;palettePage=0;rebuild();return true;}graphDragging=true;return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(inGraph(x,y)){graphScrollY-=(int)(dy*32);graphScrollX-=(int)(dx*24);clampGraph();return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(graphDragging){graphScrollX-=(int)dx;graphScrollY-=(int)dy;clampGraph();return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){graphDragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int modifiers) { if (key != 256 && name != null && name.isFocused() && name.keyPressed(key,scan,modifiers)) return true; return super.keyPressed(key,scan,modifiers); }
}
