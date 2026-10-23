package thaumcraft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.items.IScribeTools;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftManager;
import thaumcraft.common.container.ContainerResearchTable;
import thaumcraft.common.lib.research.theorycraft.TheorycraftAids;

/** Functional source research-table GUI: original texture/slot positions, aid grid, inspiration,
 * paper draw, two/three real card choices, required stacks, totals and complete/scrap actions.
 * Only aid selection and animation are local. Cards, ink, notes and awards are server-owned. */
public final class GuiResearchTable extends AbstractContainerScreen<ContainerResearchTable> {
    private static final ResourceLocation BACKGROUND = texture("gui_research_table.png"), BASE = texture("gui_base.png"),
        PAPER = texture("paper.png"), GILDED = texture("papergilded.png"), UNKNOWN = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/aspects/_unknown.png");
    private final Set<String> selectedAids = new HashSet<>();
    private Button create, complete, scrap;
    private boolean pending;
    private long pendingAt, observedState = -1, selectedAt = -1;
    private Component hoverText;
    private ItemStack hoverItem = ItemStack.EMPTY;
    public GuiResearchTable(ContainerResearchTable menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 255; imageHeight = 255;
        titleLabelX = 40; titleLabelY = 8; inventoryLabelX = 77; inventoryLabelY = 177;
    }
    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/" + name); }
    @Override protected void init() {
        super.init();
        create = addRenderableWidget(Button.builder(Component.translatable("button.create.theory"), button -> request(0, selectedAids))
            .bounds(leftPos + 79, topPos + 22, 98, 14).build());
        complete = addRenderableWidget(Button.builder(Component.translatable("button.complete.theory"), button -> request(7, Set.of()))
            .bounds(leftPos + 142, topPos + 96, 98, 14).build());
        scrap = addRenderableWidget(Button.builder(Component.translatable("button.scrap.theory"), button -> request(9, Set.of()))
            .bounds(leftPos + 79, topPos + 166, 98, 14).build());
        create.active = false; complete.active = false; scrap.active = false;
        complete.visible = false; scrap.visible = false;
    }
    private void request(int button, Set<String> aids) {
        if (pending || !menu.theoryReady() || !menu.theoryOwned()) return;
        var packet = menu.prepareTheoryAction(button, Set.copyOf(aids));
        if (packet == null) return;
        PacketDistributor.sendToServer(packet); pending = true; pendingAt = Util.getMillis();
    }
    @Override protected void containerTick() {
        super.containerTick();
        long state = menu.receivedStateGeneration();
        if (state != observedState) { observedState = state; pending = false; }
        if (pending && Util.getMillis() - pendingAt > 5000) pending = false; // Retry only; never infer a successful action.
        selectedAids.retainAll(menu.theoryAids());
        ResearchTableData data = menu.theoryData();
        if (data != null && data.cardChoices.stream().anyMatch(choice -> choice.selected)) {
            if (selectedAt < 0) selectedAt = Util.getMillis();
            if (!pending && Util.getMillis() - selectedAt >= 350) request(1, Set.of());
        } else selectedAt = -1;
        boolean idle = data == null, owned = menu.theoryOwned();
        create.visible = idle; create.active = idle && owned && !pending && hasInk() && hasPaper() && menu.theoryReady();
        complete.visible = data != null && data.isComplete(); complete.active = complete.visible && owned && !pending && data.cardChoices.isEmpty();
        scrap.visible = data != null && !data.isComplete(); scrap.active = scrap.visible && owned && !pending;
    }
    private boolean hasInk() { ItemStack stack = menu.table().getItem(0); return !stack.isEmpty() && stack.getItem() instanceof IScribeTools && stack.getDamageValue() < stack.getMaxDamage(); }
    private boolean hasPaper() { return menu.table().getItem(1).is(net.minecraft.world.item.Items.PAPER) && !menu.table().getItem(1).isEmpty(); }
    private static void blit(GuiGraphics graphics, ResourceLocation image, int x, int y, int w, int h, float u, float v, int sourceW, int sourceH, int tw, int th) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); graphics.blit(image, x, y, w, h, u, v, sourceW, sourceH, tw, th);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        hoverText = null; hoverItem = ItemStack.EMPTY;
        RenderSystem.setShaderColor(1, 1, 1, 1); blit(graphics, BACKGROUND, leftPos, topPos, 255, 255, 0, 0, 255, 255, 256, 256);
        ResearchTableData data = menu.theoryData();
        if (!menu.theoryReady()) { graphics.drawCenteredString(font, Component.translatable("thaumcraft.theorycraft.loading"), leftPos + 128, topPos + 90, 0xFF514638); RenderSystem.disableBlend(); return; }
        if (!menu.theoryOwned()) { graphics.drawWordWrap(font, Component.translatable("thaumcraft.theorycraft.other_owner"), leftPos + 45, topPos + 80, 165, 0xFF514638); RenderSystem.disableBlend(); return; }
        int inspiration = data == null ? menu.availableInspiration() - selectedAids.size() : data.inspiration;
        int maximum = data == null ? menu.availableInspiration() : data.inspirationStart;
        for (int index = 0; index < maximum; index++) blit(graphics, BASE, leftPos + 128 - maximum * 5 + index * 10, topPos + (data == null ? 55 : 16), 8, 8,
            inspiration <= index ? 48 : 32, 96, 16, 16, 256, 256);
        if (data == null) renderAids(graphics, mouseX, mouseY);
        else {
            if (data.cardChoices.isEmpty()) {
                if (!data.isComplete()) {
                    blit(graphics, PAPER, leftPos + 17, topPos + 52, 96, 96, 0, 0, 128, 128, 128, 128);
                    blit(graphics, UNKNOWN, leftPos + 53, topPos + 88, 24, 24, 0, 0, 16, 16, 16, 16);
                    if (inside(mouseX, mouseY, leftPos + 25, topPos + 55, 75, 90)) hoverText = Component.translatable("thaumcraft.theorycraft.draw", data.bonusDraws > 0 ? 3 : 2);
                }
                if (data.lastDraw != null) renderCard(graphics, data.lastDraw, leftPos + 191, topPos + 100, .75F, false, mouseX, mouseY);
                for (int index = 0; index < Math.min(30, data.bonusDraws); index++) blit(graphics, BASE, leftPos + 15 + index * 2, topPos + 150 + index, 16, 16, 64, 96, 16, 16, 256, 256);
            } else for (int index = 0; index < data.cardChoices.size(); index++) {
                var choice = data.cardChoices.get(index);
                int center = leftPos + 55 + 128 - 55 * data.cardChoices.size() + 110 * index;
                float scale = choice.selected ? .75F : 1F;
                renderCard(graphics, choice, center, topPos + 100, scale, true, mouseX, mouseY);
            }
            renderTotals(graphics, data, mouseX, mouseY);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.disableBlend();
    }
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height; }
    private List<String> aidKeys() { return new ArrayList<>(menu.theoryAids()); }
    private void renderAids(GuiGraphics graphics, int mouseX, int mouseY) {
        List<String> keys = aidKeys(); int columns = Math.min(keys.size(), 6); if (columns == 0) return;
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index); var aid = TheorycraftManager.aids.get(key); if (aid == null) continue;
            int x = leftPos + 128 + 20 * (index % columns) - columns * 10, y = topPos + 85 + 35 * (index / columns);
            if (selectedAids.contains(key)) blit(graphics, BASE, x, y, 16, 16, 0, 96, 16, 16, 256, 256);
            Object object = aid.getAidObject(); ItemStack stack = object instanceof ItemStack item ? item : object instanceof Block block ? new ItemStack(block) : ItemStack.EMPTY;
            if (!stack.isEmpty()) graphics.renderItem(stack, x, y);
            else graphics.drawString(font, "?", x + 5, y + 4, 0xFF514638, false);
            if (inside(mouseX, mouseY, x, y, 16, 16)) hoverText = aid instanceof TheorycraftAids.Aid builtin ? builtin.displayName() : Component.translatable(aid.translationKey());
        }
    }
    private void renderCard(GuiGraphics graphics, ResearchTableData.CardChoice choice, int cx, int cy, float scale, boolean offered, int mouseX, int mouseY) {
        boolean hovered = offered && inside(mouseX, mouseY, cx - 50, cy - 60, 100, 120);
        int size = Math.round(128 * scale);
        blit(graphics, choice.fromAid ? GILDED : PAPER, cx - size / 2, cy - size / 2, size, size, 0, 0, 128, 128, 128, 128);
        graphics.pose().pushPose(); graphics.pose().translate(cx, cy, 0); graphics.pose().scale(.5F * scale, .5F * scale, 1);
        Component name = choice.card.getLocalizedName(); graphics.drawString(font, name, -font.width(name) / 2, -65, 0xFF000000, false);
        graphics.drawWordWrap(font, choice.card.getLocalizedText(), -70, -48, 140, 0xFF000000); graphics.pose().popPose();
        int cost = choice.card.getInspirationCost(), count = cost < 0 ? Math.abs(cost) + 1 : cost;
        for (int index = 0; index < count; index++) blit(graphics, BASE, cx - count * 5 + index * 10, cy - 48, 8, 8,
            index == 0 && cost < 0 ? 48 : 32, index == 0 && cost < 0 ? 0 : 96, 16, 16, 256, 256);
        ItemStack[] required = choice.card.getRequiredItems();
        if (required != null) for (int index = 0; index < required.length; index++) {
            int x = cx - 9 * required.length + 18 * index, y = cy + 35;
            ItemStack stack = required[index];
            if (stack != null && !stack.isEmpty()) { graphics.renderItem(stack, x, y); graphics.renderItemDecorations(font, stack, x, y); }
            else blit(graphics, UNKNOWN, x, y, 16, 16, 0, 0, 16, 16, 16, 16);
            boolean[] consumed = choice.card.getRequiredItemsConsumed();
            if (consumed != null && consumed.length > index && consumed[index]) blit(graphics, BASE, x + 4, y + 14, 8, 8, 64, 120, 16, 16, 256, 256);
            if (inside(mouseX, mouseY, x, y, 16, 16)) { if (stack != null && !stack.isEmpty()) hoverItem = stack; else hoverText = Component.translatable("thaumcraft.theorycraft.item_unavailable"); }
        }
        if (hovered && hoverItem.isEmpty() && hoverText == null) hoverText = Component.translatable("thaumcraft.theorycraft.select");
    }
    private void renderTotals(GuiGraphics graphics, ResearchTableData data, int mouseX, int mouseY) {
        var totals = data.categoryTotals.entrySet().stream().sorted((a, b) -> b.getValue().compareTo(a.getValue())).toList();
        for (int index = 0; index < totals.size(); index++) {
            var entry = totals.get(index); int y = topPos + 16 + index * 18 + (index > data.penaltyStart ? 4 : 0);
            var category = ResearchCategories.getResearchCategory(entry.getKey());
            if (category != null && category.icon != null) blit(graphics, category.icon, leftPos + 253, y, 16, 16, 0, 0, 255, 255, 255, 255);
            String text = entry.getValue() + "%" + (index > data.penaltyStart ? " (-" + entry.getValue() / 3 + ")" : "");
            int color = data.categoriesBlocked.contains(entry.getKey()) ? 0xFF606060 : index <= data.penaltyStart ? 0xFFE0C000 : 0xFFFFFFFF;
            graphics.drawString(font, text, leftPos + 276, y + 4, color);
            if (inside(mouseX, mouseY, leftPos + 253, y, 100, 16)) hoverText = Component.translatable("tc.research_category." + entry.getKey());
        }
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !pending && menu.theoryReady() && menu.theoryOwned()) {
            ResearchTableData data = menu.theoryData();
            if (data == null) {
                List<String> keys = aidKeys(); int columns = Math.min(keys.size(), 6);
                for (int index = 0; index < keys.size(); index++) {
                    int x = leftPos + 128 + 20 * (index % columns) - columns * 10, y = topPos + 85 + 35 * (index / columns);
                    if (inside(mouseX, mouseY, x, y, 16, 16)) {
                        if (!selectedAids.remove(keys.get(index)) && selectedAids.size() + 1 < menu.availableInspiration()) selectedAids.add(keys.get(index));
                        return true;
                    }
                }
            } else if (!data.isComplete()) {
                if (data.cardChoices.isEmpty() && hasPaper() && inside(mouseX, mouseY, leftPos + 25, topPos + 55, 75, 90)) { request(data.bonusDraws > 0 ? 3 : 2, Set.of()); return true; }
                if (hasInk() && data.cardChoices.stream().noneMatch(choice -> choice.selected)) for (int index = 0; index < data.cardChoices.size(); index++) {
                    int x = leftPos + 5 + 128 - 55 * data.cardChoices.size() + 110 * index;
                    if (inside(mouseX, mouseY, x, topPos + 40, 100, 120)) { request(4 + index, Set.of()); return true; }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) { /* Source texture owns labels; no inventory title over inspiration. */ }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
        if (!hoverItem.isEmpty()) graphics.renderTooltip(font, hoverItem, mouseX, mouseY);
        else if (hoverText != null) graphics.renderTooltip(font, hoverText, mouseX, mouseY);
    }
}
