package thaumcraft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.locale.Language;
import org.lwjgl.glfw.GLFW;
import thaumcraft.client.gui.model.ResearchBrowserProjection;
import thaumcraft.client.gui.model.ResearchBrowserSearch;
import thaumcraft.client.gui.model.ResearchBrowserProjection.Browser;
import thaumcraft.client.gui.model.ResearchBrowserProjection.Link;
import thaumcraft.client.gui.model.ResearchBrowserProjection.Node;
import thaumcraft.client.gui.model.ResearchBrowserTooltip;
import thaumcraft.client.gui.model.ResearchLineGeometry;
import thaumcraft.client.gui.model.ResearchTexturePaths;
import thaumcraft.client.lib.ClientResearchPresentation;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.research.catalog.ResearchClientView;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;

/** Actual client-only TC6 map prefix: source tabs,24px nodes/frames/links/background/border,
 * 1..2 zoom and pan, same-browser page return, accepted owner+catalog reads, checked real C2S.
 * Search, read badges, focus-node art and source forbidden sprites are client presentation.
 * No local flag clearing or optimistic progression. Modern scissor,
 * left-only click/drag, instant boundary clamp and bounded link budget are engine/safety adaptations,
 * not an executed graphical-parity claim. Session-unready screens never retain the old owner map.
 */
public final class GuiResearchBrowser extends Screen {
    private static final ResourceLocation ATLAS = ResourceLocation.parse("thaumcraft:textures/gui/gui_research_browser.png");
    private static final int MAX_MAP_SPRITES = 16384;
    // Source remembers map position; these are only presentation preferences, never owner data.
    private static String lastCategory;
    private static double lastX = Double.NaN, lastY = Double.NaN;
    private String selected = lastCategory;
    private double panX = lastX, panY = lastY;
    private float zoom = 1F;
    private ResearchClientView.Snapshot snapshot;
    private Browser browser = ResearchBrowserProjection.project(null, null, 0, 0);
    private List<PaintedLink> links = List.of();
    private boolean linksDeferred;
    private EditBox searchField;
    private boolean searching;
    private List<ResearchBrowserSearch.Result> searchResults = List.of();
    private int searchScroll;
    private Language searchLanguage;
    private final Set<String> unresolvedIcons = new HashSet<>();
    private record PaintedLink(Link style, List<ResearchLineGeometry.Sprite> sprites) {}

    public GuiResearchBrowser() { super(Component.translatable("item.thaumcraft.thaumonomicon_normal")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void init() {
        String previous = searchField == null ? "" : searchField.getValue();
        searchField = new EditBox(font, 20, 20, 89, font.lineHeight + 4, Component.translatable("tc.search"));
        searchField.setMaxLength(15); searchField.setValue(previous);
        searchField.setResponder(value -> { searchScroll = 0; updateSearch(); });
        searchField.setVisible(searching); addRenderableWidget(searchField);
        setFocused(searching ? searchField : null); searchField.setFocused(searching);
        refresh(true);
    }
    @Override public void tick() { refresh(false); }
    @Override public void onClose() { remember(); super.onClose(); }

    private void refresh(boolean force) {
        var current = ClientResearchPresentation.view();
        if (!force && snapshot == current && searchLanguage == Language.getInstance()) return;
        snapshot = current;
        searchLanguage = Language.getInstance();
        browser = ResearchBrowserProjection.project(current, selected, width, height);
        boolean selectionChanged = !Objects.equals(selected, browser.selected());
        selected = browser.selected();
        if (selectionChanged) panX = panY = Double.NaN;
        clampPan();
        List<PaintedLink> prepared = new ArrayList<>();
        linksDeferred = browser.nodes().stream().anyMatch(node -> !safeNode(node));
        int count = 0;
        for (Link link : browser.links()) {
            try {
                var sprites = ResearchLineGeometry.layout(link.x1(), link.y1(), link.x2(), link.y2(), link.arrow(), link.reverse());
                if (count + sprites.size() > MAX_MAP_SPRITES) { linksDeferred = true; continue; }
                count += sprites.size();
                prepared.add(new PaintedLink(link, sprites));
            } catch (IllegalArgumentException unsafeGeometry) { linksDeferred = true; }
        }
        links = List.copyOf(prepared);
        if (searching) updateSearch();
    }

    private void updateSearch() {
        searchResults = ResearchBrowserSearch.project(snapshot, browser.tabs().stream().map(tab -> tab.key()).toList(),
            searchField == null ? "" : searchField.getValue(), net.minecraft.client.resources.language.I18n::get,
            id -> ResearchRecipeViewer.outputNames(snapshot, id));
        searchScroll = Math.max(0, Math.min(searchScroll, Math.max(0, searchResults.size() - searchRows())));
    }
    private int searchRows() { return Math.max(1, (height - 66) / 10); }
    private void toggleSearch() {
        searching = !searching; searchField.setVisible(searching); searchField.setFocused(searching);
        setFocused(searching ? searchField : null); searchScroll = 0; updateSearch(); playPage();
    }

    private void clampPan() {
        var bounds = browser.bounds();
        if (bounds.empty()) { panX = panY = 0; return; }
        double left = bounds.left() * (double) zoom, right = bounds.right() * (double) zoom;
        double top = bounds.top() * (double) zoom, bottom = bounds.bottom() * (double) zoom;
        double centerX = (left + right) / 2, centerY = (top + bottom) / 2;
        if (!Double.isFinite(panX) || left > right) panX = centerX;
        else panX = Math.max(left, Math.min(right, panX));
        if (!Double.isFinite(panY) || top > bottom) panY = centerY;
        else panY = Math.max(top, Math.min(bottom, panY));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refresh(false);
        renderBackground(graphics, mouseX, mouseY, partialTick);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        unresolvedIcons.clear();
        long now = System.currentTimeMillis();
        if (width > 32 && height > 32 && snapshot != null && !searching) {
            graphics.enableScissor(16, 16, width - 16, height - 16);
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            var category = browser.tabs().stream().filter(tab -> tab.key().equals(selected)).findFirst().orElse(null);
            if (category != null && category.background() != null) graphics.blit(ResearchTexturePaths.forRendering(category.background()), 14, 14, width - 28, height - 28,
                (float) panX / 2, (float) panY / 2, Math.round((width - 28) * zoom), Math.round((height - 28) * zoom), 256, 256);
            if (category != null && category.background2() != null) graphics.blit(category.background2(), 14, 14, width - 28, height - 28,
                (float) panX / 1.5F, (float) panY / 1.5F, Math.round((width - 28) * zoom), Math.round((height - 28) * zoom), 256, 256);
            graphics.pose().pushPose();
            graphics.pose().scale(1F / zoom, 1F / zoom, 1F);
            for (PaintedLink line : links) {
                var tint = line.style();
                graphics.setColor(tint.r(), tint.g(), tint.b(), 1F);
                RenderSystem.enableBlend();
                graphics.pose().pushPose();
                graphics.pose().translate(0F, 0F, tint.zMod());
                for (var sprite : line.sprites()) {
                    int x = screenPixel(sprite.x(), panX), y = screenPixel(sprite.y(), panY);
                    if (visibleWorld(x, y, sprite.width(), sprite.height()))
                        blitAtlas(graphics, x, y, sprite.u(), sprite.v(), sprite.width(), sprite.height());
                }
                graphics.pose().popPose();
            }
            graphics.setColor(1F, 1F, 1F, 1F);
            for (Node node : browser.nodes()) {
                if (!safeNode(node)) continue;
                int x = screenPixel(node.x(), panX), y = screenPixel(node.y(), panY);
                if (!visibleWorld(x - 8, y - 8, 32, 32)) continue;
                if (node.hasWarp()) drawForbidden(graphics, x + 8, y + 8);
                float light = node.complete() ? 1F : node.availability().parentsSatisfied()
                    ? (float) Math.sin((Util.getMillis() % 600L) / 600D * Math.PI * 2D) * .25F + .75F : .3F;
                graphics.setColor(light, light, light, 1F);
                RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
                blitAtlas(graphics, x - 8, y - 8, node.frameU(), node.frameV(), 32, 32);
                if (node.spiky()) blitAtlas(graphics, x - 8, y - 8, 176, node.frameV(), 32, 32);
                graphics.setColor(1F, 1F, 1F, 1F);
                drawReadBadges(graphics, node.rawSpec().key(), x - 9, y - 9);
                if (!ResearchIconRenderer.draw(graphics, node.rawSpec().icons().values(), x, y,
                    !node.availability().parentsSatisfied(), now)) {
                    unresolvedIcons.add(node.rawSpec().key());
                    graphics.drawString(font, "?", x + 4, y + 4, 0xFFCCB997, false);
                }
            }
            graphics.pose().popPose();
            graphics.disableScissor();
        }
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        drawBorder(graphics);
        for (int index = 0; index < browser.tabs().size(); index++) {
            var tab = browser.tabs().get(index);
            int y = tabY(index);
            if (tab.key().equals(selected)) blitAtlas(graphics, -2, y - 3, 13, 13, 22, 22);
            if (!ResearchIconRenderer.drawTexture(graphics, tab.icon(), 1, y, false, now))
                graphics.drawString(font, "?", 5, y + 4, 0xFFCCB997, false);
            boolean researchFlag = snapshot != null && snapshot.spec().entries().stream().anyMatch(entry -> tab.key().equals(entry.category())
                && snapshot.knows(entry.key()) && snapshot.hasResearchFlag(entry.key(), EnumResearchFlag.RESEARCH));
            boolean pageFlag = snapshot != null && snapshot.spec().entries().stream().anyMatch(entry -> tab.key().equals(entry.category())
                && snapshot.knows(entry.key()) && snapshot.hasResearchFlag(entry.key(), EnumResearchFlag.PAGE));
            if (researchFlag) drawBadge(graphics, -2, y - 2, 176);
            if (pageFlag) drawBadge(graphics, -2, y + 9, 208);
        }
        // Source bottom-left search button; the edit field has the original 15-character bound.
        blitAtlas(graphics, 1, height - 17, 160, 16, 16, 16);
        if (searching) {
            searchField.render(graphics, mouseX, mouseY, partialTick);
            for (int row = 0; row < searchRows() && searchScroll + row < searchResults.size(); row++) {
                var result = searchResults.get(searchScroll + row);
                int y = 34 + row * 10;
                boolean hover = mouseX >= 20 && mouseX < width - 20 && mouseY >= y && mouseY < y + 10;
                graphics.drawString(font, result.label(), 22, y, hover ? 0xFFFFFFFF : result.category() ? 0xFFAA88DD : 0xFFDDDDDD, false);
            }
            if (searchScroll + searchRows() < searchResults.size()) graphics.drawString(font, Component.translatable("tc.search.more"), 22, height - 28, 0xFFAAAAAA);
        }
        if (snapshot == null) graphics.drawCenteredString(font, Component.translatable("thaumcraft.research_ui.catalog_unavailable"), width / 2, height / 2, 0xFFE4D6B6);
        else if (browser.nodes().isEmpty()) graphics.drawCenteredString(font, Component.translatable("thaumcraft.research_ui.map_empty"), width / 2, height / 2, 0xFFE4D6B6);
        if (linksDeferred) graphics.drawCenteredString(font, Component.translatable("thaumcraft.research_ui.links_deferred"), width / 2, height - 32, 0xFFE4D6B6);
        renderHover(graphics, mouseX, mouseY);
        RenderSystem.disableBlend();
    }

    private void renderHover(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseX >= 0 && mouseX < 17 && mouseY >= height - 17) {
            graphics.renderTooltip(font, Component.translatable("tc.search"), mouseX, mouseY); return;
        }
        for (int index = 0; index < browser.tabs().size(); index++) if (overTab(mouseX, mouseY, index)) {
            var tab = browser.tabs().get(index);
            graphics.renderTooltip(font, List.of(Component.translatable("tc.research_category." + tab.key()),
                Component.literal(tab.percent() + "% (" + tab.known() + "/" + tab.total() + ")")), Optional.empty(), mouseX, mouseY);
            return;
        }
        if (searching) return;
        Node node = hovered(mouseX, mouseY);
        if (node == null) return;
        List<Component> tooltip = new ArrayList<>();
        for (var line : ResearchBrowserTooltip.project(snapshot, node)) {
            var text = line.translationKey() == null ? Component.literal("?")
                : Component.translatable(line.translationKey());
            if (!line.suffix().isEmpty()) text.append(line.suffix());
            if (line.kind() == ResearchBrowserTooltip.Kind.MISSING_PARENT)
                text = Component.literal(" - ").append(text);
            tooltip.add(text.withStyle(switch (line.kind()) {
                case TITLE -> ChatFormatting.GOLD;
                case BEGIN -> ChatFormatting.GREEN;
                case STAGE -> ChatFormatting.AQUA;
                case MISSING_PARENTS -> ChatFormatting.RED;
                case MISSING_PARENT -> ChatFormatting.YELLOW;
            }));
        }
        if (unresolvedIcons.contains(node.rawSpec().key())) tooltip.add(Component.translatable("thaumcraft.research_ui.icon_deferred"));
        if (snapshot.hasResearchFlag(node.rawSpec().key(), EnumResearchFlag.RESEARCH)) tooltip.add(Component.translatable("tc.research.newresearch"));
        if (snapshot.hasResearchFlag(node.rawSpec().key(), EnumResearchFlag.PAGE)) tooltip.add(Component.translatable("tc.research.newpage"));
        graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        refresh(false);
        if (mouseX >= 0 && mouseX < 17 && mouseY >= height - 17) { toggleSearch(); return true; }
        for (int index = 0; index < browser.tabs().size(); index++) if (overTab(mouseX, mouseY, index)) {
            selected = browser.tabs().get(index).key(); panX = panY = Double.NaN;
            searching = false; searchField.setVisible(false); searchField.setFocused(false); setFocused(null);
            refresh(true); remember(); playPage(); return true;
        }
        if (searching) {
            int row = (int) (mouseY - 34) / 10;
            if (mouseY >= 34 && row < searchRows() && mouseX >= 20 && mouseX < width - 20 && searchScroll + row < searchResults.size()) {
                var result = searchResults.get(searchScroll + row);
                if (result.category()) {
                    selected = result.key(); panX = panY = Double.NaN; toggleSearch(); refresh(true); remember();
                } else if (snapshot != null && snapshot.knows(result.key())) {
                    ClientResearchPresentation.clearReadFlags(result.key()); remember(); playPage();
                    minecraft.setScreen(new GuiResearchPage(result.key(), this, result.recipe()));
                }
                return true;
            }
            return searchField.mouseClicked(mouseX, mouseY, button);
        }
        Node node = hovered(mouseX, mouseY);
        if (node != null && snapshot != null) {
            if (!snapshot.knows(node.rawSpec().key())) {
                var request = ClientResearchPresentation.prepare(node.rawSpec().key());
                if (request == null || request.packet().expectedStage() != -1 || !ClientResearchPresentation.submit(request)) return true;
            }
            else ClientResearchPresentation.clearReadFlags(node.rawSpec().key());
            remember(); playPage();
            minecraft.setScreen(new GuiResearchPage(node.rawSpec().key(), this));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && !searching && inMap(mouseX, mouseY) && snapshot != null) {
            panX -= dx * zoom; panY -= dy * zoom; clampPan(); remember(); return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (searching && scrollY != 0) {
            searchScroll = Math.max(0, Math.min(Math.max(0, searchResults.size() - searchRows()), searchScroll + (scrollY < 0 ? 1 : -1))); return true;
        }
        if (scrollY == 0 || !inMap(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        zoom = Math.max(1F, Math.min(2F, zoom + (scrollY < 0 ? .25F : -.25F)));
        clampPan(); remember(); return true;
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searching && keyCode == GLFW.GLFW_KEY_ESCAPE) { toggleSearch(); return true; }
        if (searching && searchField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (!searching && keyCode == GLFW.GLFW_KEY_F && hasControlDown()) { toggleSearch(); return true; }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            onClose(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean charTyped(char codePoint, int modifiers) { return searching ? searchField.charTyped(codePoint, modifiers) : super.charTyped(codePoint, modifiers); }

    private void drawReadBadges(GuiGraphics graphics, String key, int x, int y) {
        if (snapshot.hasResearchFlag(key, EnumResearchFlag.RESEARCH)) drawBadge(graphics, x, y, 176);
        if (snapshot.hasResearchFlag(key, EnumResearchFlag.PAGE)) drawBadge(graphics, x, y + 18, 208);
    }
    private static void drawBadge(GuiGraphics graphics, int x, int y, int u) {
        graphics.pose().pushPose(); graphics.pose().translate(x, y, 5); graphics.pose().scale(.5F, .5F, 1);
        blitAtlas(graphics, 0, 0, u, 16, 32, 32); graphics.pose().popPose();
    }
    private void drawForbidden(GuiGraphics graphics, int x, int y) {
        int frame = 160 + (minecraft.player == null ? 0 : Math.floorMod(minecraft.player.tickCount, 32));
        graphics.setColor(.33F, 0, .44F, .9F);
        RenderSystem.enableBlend(); RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        graphics.blit(ResourceLocation.parse("thaumcraft:textures/misc/auranodes.png"), x - 45, y - 45, 90, 90,
            (frame % 32) * 8F, (frame / 32) * 8F, 8, 8, 256, 256);
        RenderSystem.defaultBlendFunc(); graphics.setColor(1, 1, 1, 1);
    }

    private Node hovered(double mouseX, double mouseY) {
        if (!inMap(mouseX, mouseY)) return null;
        for (Node node : browser.nodes()) if (safeNode(node)) {
            double x = (16D + node.x() - panX) / zoom, y = (16D + node.y() - panY) / zoom;
            if (mouseX >= x - 2 / zoom && mouseX <= x + 18 / zoom && mouseY >= y - 2 / zoom && mouseY <= y + 18 / zoom) return node;
        }
        return null;
    }
    private boolean inMap(double x, double y) { return x >= 16 && y >= 16 && x < width - 16 && y < height - 16; }
    private boolean overTab(double x, double y, int index) { return x >= 0 && x < 17 && y >= tabY(index) && y < tabY(index) + 16; }
    private static int tabY(int index) { return 10 + (index + 1) * 24; }
    private static boolean safeNode(Node node) {
        return Math.abs((long) node.column() * 24) < Integer.MAX_VALUE - 64L && Math.abs((long) node.row() * 24) < Integer.MAX_VALUE - 64L;
    }
    private static int screenPixel(int world, double pan) {
        return (int) Math.max(Integer.MIN_VALUE + 64D, Math.min(Integer.MAX_VALUE - 64D, 16D + world - pan));
    }
    private boolean visibleWorld(int x, int y, int w, int h) { return x < (width - 16D) * zoom && y < (height - 16D) * zoom && x + (double) w > 16D * zoom && y + (double) h > 16D * zoom; }
    private void remember() { lastCategory = selected; lastX = panX; lastY = panY; }
    private void playPage() { if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundsTC.page, .66F, 1F); }

    private void drawBorder(GuiGraphics graphics) {
        for (int x = 16; x < width - 16; x += 64) {
            int part = Math.min(64, width - 16 - x);
            blitAtlas(graphics, x, -2, 48, 13, part, 22);
            blitAtlas(graphics, x, height - 20, 48, 13, part, 22);
        }
        for (int y = 16; y < height - 16; y += 64) {
            int part = Math.min(64, height - 16 - y);
            blitAtlas(graphics, -2, y, 13, 48, 22, part);
            blitAtlas(graphics, width - 20, y, 13, 48, 22, part);
        }
        blitAtlas(graphics, -2, -2, 13, 13, 22, 22);
        blitAtlas(graphics, -2, height - 20, 13, 13, 22, 22);
        blitAtlas(graphics, width - 20, -2, 13, 13, 22, 22);
        blitAtlas(graphics, width - 20, height - 20, 13, 13, 22, 22);
    }

    private static void blitAtlas(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
        // Modern unmanaged Font/tooltip flush cleans RenderType blend state between sprites.
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        graphics.blit(ATLAS, x, y, u, v, width, height);
    }
}
