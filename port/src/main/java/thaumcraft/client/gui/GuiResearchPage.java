package thaumcraft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Comparator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;
import thaumcraft.Thaumcraft;
import thaumcraft.client.gui.model.ResearchPageProjection;
import thaumcraft.client.gui.model.ResearchRequirementPresentation;
import thaumcraft.client.gui.model.ResearchRequirementPresentation.Group;
import thaumcraft.client.gui.model.ResearchRequirementPresentation.Requirement;
import thaumcraft.client.gui.model.ResearchTextLayout;
import thaumcraft.client.gui.model.ResearchPageImage;
import thaumcraft.client.gui.model.ResearchKnowledgePresentation;
import thaumcraft.client.gui.model.ResearchRequirementStatus;
import thaumcraft.client.gui.model.ResearchTextLayout.Element;
import thaumcraft.client.gui.model.ResearchTextLayout.Kind;
import thaumcraft.client.gui.model.ResearchTextLayout.Page;
import thaumcraft.client.lib.ClientResearchPresentation;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Entry;
import thaumcraft.common.lib.research.catalog.ResearchClientView;
import thaumcraft.common.lib.research.ResearchItemResolver;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchStatus;

/** Actual non-pausing source book Screen with illustrations, inserts and recipe drill-down.
 * Keeps source native256 texture regions, 256x181 pane scaled1.3, 140px columns, arrows,
 * page sounds and stage/addendum text. PAGE/BR/LINE/DIV are the finite markup prefix.
 * Finite IMG descriptors retain source pending-image queue/256px UV. Recipe displays retain
 * source groups/gates/geometry without making unported crafting engines falsely playable.
 * The source gui_researchbook_overlay texture is for recipe panels, not ordinary page text;
 * no invented overlay is composited onto this book. Modern Font/layout is an engine adaptation.
 *
 * Requirement icons use finite proved item identities and localized descriptions; unresolved
 * content is a question mark, never an AIR fallback or a raw source ID. No requirement is
 * claimed paid: checks reflect only observed requirements, never optimistic completion. Advance sends only
 * the facade's current known-stage checked request; no client grants, costs or flag clearing.
 * Hold ends only on a newly received owner-knowledge generation or a 200-client-tick retry
 * timeout (explicit modern adaptation, NOT an ACK/rejection/progress inference).
 */
public final class GuiResearchPage extends Screen {
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/gui/gui_researchbook.png");
    private static final int PANE_WIDTH = 256, PANE_HEIGHT = 181, RETRY_TICKS = 200;
    private static final int INK = 0xFF202020, TEXT = 0xFF000000;
    private final String key;
    private final GuiResearchBrowser browser;
    private ResearchClientView.Snapshot snapshot;
    private Language language;
    private ResearchPageProjection.View projection;
    private Entry entry;
    private List<Page<FormattedCharSequence>> pages = List.of();
    private List<Group> requirements = List.of();
    private ResearchRequirementStatus requirementStatus = ResearchRequirementStatus.empty();
    private List<ResearchKnowledgePresentation.Balance> balances = List.of();
    private List<Aspect> knownAspects = List.of();
    private final ResearchRecipeViewer recipeViewer = new ResearchRecipeViewer();
    private final ResourceLocation initialRecipe;
    private boolean initialRecipeOpened, showingAspects, showingKnowledge;
    private int aspectsPage;
    private ClientResearchPresentation.Prepared pending;
    private int pendingTicks, page;
    private boolean retryExpired, imagesDeferred, markupDeferred, layoutUnsupported;

    public GuiResearchPage(String key, GuiResearchBrowser browser) {
        this(key, browser, null);
    }
    public GuiResearchPage(String key, GuiResearchBrowser browser, ResourceLocation initialRecipe) {
        super(Component.literal(Objects.requireNonNull(key)));
        this.key = key; this.browser = Objects.requireNonNull(browser); this.initialRecipe = initialRecipe;
    }

    @Override protected void init() {
        refresh(true);
        ClientResearchPresentation.clearReadFlags(key);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(browser); }

    @Override public void tick() {
        ResearchClientView.Snapshot current = ClientResearchPresentation.view();
        if (pending != null) {
            if (current != null && !current.sameOwnerKnowledgeGeneration(pending.snapshot())) {
                pending = null; pendingTicks = 0; retryExpired = false;
            } else if (++pendingTicks >= RETRY_TICKS) {
                pending = null; pendingTicks = 0; retryExpired = true; // No fabricated server result.
            }
        }
        if (current != snapshot || language != Language.getInstance()) refresh(true);
        refreshRequirements();
        // A rate-limited read has no rejection/ACK packet. Retry while the real owner
        // snapshot still says unread; the facade bounds sends to once per twenty ticks.
        ClientResearchPresentation.clearReadFlags(key);
    }

    private void refresh(boolean force) {
        ResearchClientView.Snapshot current = ClientResearchPresentation.view();
        if (!force && current == snapshot) return;
        snapshot = current;
        recipeViewer.setSnapshot(current);
        language = Language.getInstance();
        entry = current == null ? null : current.spec().entriesByKey().get(key);
        projection = ClientResearchPresentation.page(key);
        balances = snapshot == null ? List.of() : ResearchKnowledgePresentation.project(
            ResearchCategoryDefinitions.newBuiltinCategories().keySet().stream().toList(), snapshot::getKnowledgeRaw);
        knownAspects = snapshot == null ? List.of() : Aspect.aspects.values().stream()
            .filter(aspect -> snapshot.knows("!" + aspect.getTag().toLowerCase(Locale.ROOT)))
            .sorted(Comparator.comparing(Aspect::getName)).toList();
        requirements = projection.hasContent() && !projection.addendaPhaseComplete()
            ? ResearchRequirementPresentation.groups(projection.stage()) : List.of();
        pages = List.of(); imagesDeferred = false; markupDeferred = false; layoutUnsupported = false;
        if (projection.hasContent()) {
            try {
                StringBuilder localized = new StringBuilder();
                appendLocalized(localized, I18n.get(projection.rawText()));
                for (var addendum : projection.eligibleAddenda()) {
                    appendLocalized(localized, "<PAGE>"); appendLocalized(localized, I18n.get("tc.addendumtext", addendum.number()));
                    appendLocalized(localized, "<BR>"); appendLocalized(localized, I18n.get(addendum.rawText()));
                }
                var parsed = ResearchTextLayout.parse(localized.toString());
                imagesDeferred = parsed.deferredImages() > 0; markupDeferred = parsed.unknownMarkup();
                List<Element<FormattedCharSequence>> elements = new ArrayList<>();
                for (var token : parsed.tokens()) {
                    switch (token.kind()) {
                        case TEXT -> {
                            for (var line : font.split(Component.literal(token.value()), ResearchTextLayout.PAGE_WIDTH)) {
                                addElement(elements, new Element<>(Kind.TEXT, line, font.lineHeight, false));
                            }
                        }
                        case PARAGRAPH -> {
                            if (!elements.isEmpty() && elements.getLast().kind() == Kind.TEXT) {
                                var previous = elements.removeLast();
                                elements.add(new Element<>(Kind.TEXT, previous.value(), previous.height(), true));
                            } else addElement(elements, new Element<>(Kind.BLANK, null, font.lineHeight, true));
                        }
                        case PAGE -> addElement(elements, new Element<>(Kind.PAGE, null, 0, false));
                        case LINE -> addElement(elements, new Element<>(Kind.LINE, null, 8, false));
                        case DIVIDER -> addElement(elements, new Element<>(Kind.DIVIDER, null, 8, false));
                        case IMAGE -> {
                            ResearchPageImage image = ResearchPageImage.parse(token.value());
                            if (image != null) addElement(elements, new Element<>(Kind.IMAGE, null, image.drawnHeight() + 2, false, image));
                        }
                        case IMAGE_DEFERRED -> {
                            for (var line : font.split(Component.translatable("thaumcraft.research_ui.image_deferred"), ResearchTextLayout.PAGE_WIDTH))
                                addElement(elements, new Element<>(Kind.IMAGE_DEFERRED, line, font.lineHeight, false));
                        }
                    }
                }
                boolean reserveAdvance = !requirements.isEmpty() || (snapshot != null && snapshot.storedStage(key) == 0);
                int firstHeight = ResearchTextLayout.FIRST_PAGE_HEIGHT - requirements.size() * 18 - (reserveAdvance ? 15 : 0);
                if (key.equals("KNOWLEDGETYPES")) firstHeight -= 2 + 20 * ResearchKnowledgePresentation.rowCount(balances) + 12;
                pages = ResearchTextLayout.paginate(elements, firstHeight, ResearchTextLayout.PAGE_HEIGHT, font.lineHeight);
            } catch (IllegalArgumentException unsupported) {
                layoutUnsupported = true; // Bounded malformed text gets a readable book notice, not a client crash.
            }
        }
        page = Math.max(0, Math.min(page, Math.max(0, ((pages.size() - 1) / 2) * 2)));
        aspectsPage = Math.max(0, Math.min(aspectsPage, Math.max(0, (knownAspects.size() - 1) / 5)));
        refreshRequirements();
        if (!initialRecipeOpened && snapshot != null && initialRecipe != null) { initialRecipeOpened = true; recipeViewer.open(initialRecipe); }
    }

    private void refreshRequirements() {
        if (snapshot == null || projection == null || !projection.hasContent() || minecraft == null || minecraft.player == null) {
            requirementStatus = ResearchRequirementStatus.empty(); return;
        }
        requirementStatus = ResearchRequirementStatus.evaluate(projection.stage(), snapshot::knowsStrict, snapshot::knows,
            token -> {
                long remaining = token.count();
                if (remaining <= 0) return false;
                // Original checks mainInventory only, not armor, offhand or Curios.
                for (ItemStack stack : minecraft.player.getInventory().items) if (ResearchItemResolver.matches(token, stack, minecraft.player.registryAccess())) {
                    remaining -= stack.getCount(); if (remaining <= 0) return true;
                }
                return false;
            }, ResearchItemResolver::craftReference, token -> snapshot.getKnowledge(token.type(), token.category()));
    }

    private static void addElement(List<Element<FormattedCharSequence>> elements, Element<FormattedCharSequence> element) {
        if (elements.size() >= ResearchTextLayout.MAX_ELEMENTS) throw new IllegalArgumentException("Bounded wrapped text limit exceeded");
        elements.add(element);
    }
    private static void appendLocalized(StringBuilder text, String addition) {
        if ((long) text.length() + addition.length() > ResearchTextLayout.MAX_TEXT_CHARS)
            throw new IllegalArgumentException("Bounded localized text limit exceeded");
        text.append(addition);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try {
            // Remote session/catalog/owner changes can arrive between tick and render.
            refresh(language != Language.getInstance());
            refreshRequirements();
            renderBackground(graphics, mouseX, mouseY, partialTick);
            int left = left(), top = top();
            int bookLeft = Math.round((width - PANE_WIDTH * 1.3F) / 2F), bookTop = Math.round((height - PANE_HEIGHT * 1.3F) / 2F);
            blitBook(graphics, bookLeft, bookTop, Math.round(PANE_WIDTH * 1.3F), Math.round(PANE_HEIGHT * 1.3F),
                0F, 0F, PANE_WIDTH, PANE_HEIGHT, 256, 256);
            if (page == 0) drawTitle(graphics, entry == null ? key : I18n.get(entry.name()), left, top);
            if (projection == null || !projection.hasContent() || layoutUnsupported) {
                graphics.drawWordWrap(font, Component.translatable(snapshot == null ? "thaumcraft.research_ui.catalog_unavailable"
                    : "thaumcraft.research_ui.content_unavailable"), left - 15, top + 18, 140, INK);
            } else {
                for (int side = 0; side < 2; side++) if (page + side < pages.size()) drawPage(graphics, pages.get(page + side), page + side, side, left, top);
            }
            if (page == 0 && projection != null && projection.hasContent() && !projection.addendaPhaseComplete()) drawRequirements(graphics, mouseX, mouseY, left, top);
            if (page == 0 && key.equals("KNOWLEDGETYPES") && complete("KNOWLEDGETYPES")) drawKnowledge(graphics, left, top + 194, true, mouseX, mouseY);
            drawInserts(graphics, left, top, mouseX, mouseY);
            drawBookmarks(graphics, left, top, mouseX, mouseY);
            if (!recipeViewer.isOpen() && !showingAspects && !showingKnowledge) {
                if (page > 0) blitBook(graphics, left - 16, top + 190, 0, 184, 12, 8);
                if (page < pages.size() - 2) blitBook(graphics, left + 262, top + 190, 12, 184, 12, 8);
            }
            blitBook(graphics, left + 118, top + 190, 38, 202, 20, 12); // Explicit same-browser return, not fake recipe history.
            if (inside(mouseX, mouseY, left + 118, top + 190, 20, 12)) graphics.renderTooltip(font, Component.translatable("recipe.return"), mouseX, mouseY);
            if (retryExpired) drawFooter(graphics, "thaumcraft.research_ui.retry_timeout", left, top, mouseX, mouseY);
            else if (imagesDeferred || markupDeferred) drawFooter(graphics, markupDeferred ? "thaumcraft.research_ui.markup_deferred" : "thaumcraft.research_ui.image_deferred", left, top, mouseX, mouseY);
            if (showingAspects) drawAspectsInsert(graphics, mouseX, mouseY);
            else if (showingKnowledge) drawKnowledgeInsert(graphics, mouseX, mouseY);
            else if (recipeViewer.isOpen()) recipeViewer.render(graphics, mouseX, mouseY, width, height);
            else drawWarpWarning(graphics, left, top, mouseX, mouseY);
            // Inserts are opaque paper, so the return affordance is composited AFTER them.
            if (recipeViewer.isOpen() || showingAspects || showingKnowledge) {
                graphics.pose().pushPose(); graphics.pose().translate(0, 0, 120);
                blitBook(graphics, left + 118, top + 190, 38, 202, 20, 12);
                if (inside(mouseX, mouseY, left + 118, top + 190, 20, 12))
                    graphics.renderTooltip(font, Component.translatable("recipe.return"), mouseX, mouseY);
                graphics.pose().popPose();
            }
        } finally {
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.disableBlend();
        }
    }

    // Font/tooltip buffered RenderType cleanup disables blend; every book sprite must restore it.
    private static void blitBook(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        graphics.blit(BOOK, x, y, u, v, width, height);
    }
    private static void blitBook(GuiGraphics graphics, int x, int y, int width, int height,
            float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        graphics.blit(BOOK, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    private void drawTitle(GuiGraphics graphics, String title, int left, int top) {
        blitBook(graphics, left + 4, top - 17, 24, 184, 96, 4);
        blitBook(graphics, left + 4, top, 24, 184, 96, 4);
        drawFitted(graphics, title, left - 15, top - 10, 140, INK);
    }
    private void drawPage(GuiGraphics graphics, Page<FormattedCharSequence> contents, int absolutePage, int side, int left, int top) {
        int x = left - 15 + side * 152, y = top - 16 + (absolutePage == 0 ? 28 : 0);
        for (var element : contents.elements()) {
            switch (element.kind()) {
                case TEXT -> graphics.drawString(font, element.value(), x, y, TEXT, false);
                case IMAGE_DEFERRED -> graphics.drawString(font, element.value(), x, y, 0xFF76654B, false);
                case LINE -> blitBook(graphics, x + (140 - 95) / 2, y + 1, 24, 184, 95, 6);
                case DIVIDER -> blitBook(graphics, x, y + 1, 28, 192, 140, 6);
                case IMAGE -> {
                    var image = element.image();
                    ResourceLocation actual = ResearchIconRenderer.texturePath(image.texture());
                    if (actual != null) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(x + (140 - image.drawnWidth()) / 2, y + 1, 0);
                        graphics.pose().scale(image.scale(), image.scale(), 1);
                        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
                        graphics.blit(actual, 0, 0, image.x(), image.y(), image.width(), image.height());
                        graphics.pose().popPose();
                    } else graphics.drawWordWrap(font, Component.translatable("thaumcraft.research_ui.image_unavailable"), x, y, 140, 0xFF76654B);
                }
                default -> {}
            }
            y += element.drawingAdvance();
        }
    }

    private void drawRequirements(GuiGraphics graphics, int mouseX, int mouseY, int left, int top) {
        int y = top + 194;
        List<Component> hoveredTooltip = null;
        for (Group group : requirements) {
            y -= 18;
            // The atlas strip itself contains the source category pictograms. Its label belongs
            // in the 16px hover region, not in text painted across those pictograms or the icons.
            graphics.setColor(1F, 1F, 1F, .25F);
            blitBook(graphics, left - 12, y - 1, group.kind().u(), group.kind().v(), 56, 16);
            graphics.setColor(1F, 1F, 1F, 1F);
            if (inside(mouseX, mouseY, left - 15, y, 16, 16)) {
                hoveredTooltip = List.of(Component.translatable(group.kind().labelKey()));
            }
            var hoveredSlot = mouseY >= y && mouseY < y + 16
                ? group.hoveredSlot(mouseX - (left - 15)) : null;
            for (int index = 0; index < group.slots().size(); index++) {
                var slot = group.slots().get(index);
                int iconX = left - 15 + slot.shift();
                boolean satisfied = requirementStatus.satisfied(group.kind(), index);
                List<Component> tooltip = drawRequirement(graphics, slot.requirement(), iconX, y, satisfied);
                if (satisfied) blitBook(graphics, iconX + 8, y, 159, 207, 10, 10);
                if (slot == hoveredSlot) hoveredTooltip = tooltip;
            }
        }
        if (requirements.isEmpty() && (snapshot == null || snapshot.storedStage(key) != 0)) return;
        y -= 12; // Source divider/advance spacing above the topmost requirement row.
        blitBook(graphics, left + 4, y - 2, 24, 184, 96, 8);
        int buttonX = left + 20, buttonY = y - 6;
        boolean ready = canAttempt();
        // Keep a source-shaped 64x12 advance region but do not draw fabricated green paid checks.
        graphics.setColor(1F, 1F, 1F, ready || pending != null ? 1F : .45F);
        blitBook(graphics, buttonX, buttonY, 84, 216, 64, 12);
        graphics.setColor(1F, 1F, 1F, 1F);
        drawFitted(graphics, I18n.get(pending == null ? "tc.stage.complete" : "tc.stage.hold"), buttonX, buttonY + 2, 64, pending == null ? INK : 0xFF695438);
        if (inside(mouseX, mouseY, buttonX, buttonY, 64, 12)) hoveredTooltip = List.of(
            Component.translatable(pending != null ? "tc.stage.hold" : ready
                ? "thaumcraft.research_ui.advance_hint" : "thaumcraft.research_ui.advance_unavailable"));
        // Source stores tipText and renders once after all sprites, not once per overlapping icon.
        if (hoveredTooltip != null && !recipeViewer.isOpen() && !showingAspects && !showingKnowledge)
            graphics.renderTooltip(font, hoveredTooltip, Optional.empty(), mouseX, mouseY);
    }

    private List<Component> drawRequirement(GuiGraphics graphics, Requirement requirement, int x, int y, boolean satisfied) {
        if (requirement instanceof ResearchRequirementPresentation.Item value) {
            var choices = ResearchItemResolver.alternatives(value.token(), minecraft.player == null ? null : minecraft.player.registryAccess());
            ItemStack stack = choices.isEmpty() ? ItemStack.EMPTY : choices.get((int) Math.floorMod(System.currentTimeMillis() / 1000, choices.size()));
            if (stack.isEmpty()) {
                drawUnknownRequirement(graphics, x, y);
                if (value.token().count() > 1) drawRequirementAmount(graphics, value.token().count(), x, y);
                return List.of(Component.translatable(ResearchRequirementPresentation.UNAVAILABLE));
            }
            graphics.renderItem(stack, x, y);
            if (value.token().count() > 1) drawRequirementAmount(graphics, value.token().count(), x, y);
            List<Component> tooltip = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player,
                minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL));
            if (value.token().count() > 1) tooltip.add(Component.literal("×" + value.token().count()));
            return List.copyOf(tooltip);
        }
        if (requirement instanceof ResearchRequirementPresentation.Research value) {
            var token = value.token();
            if (token.key() != null && token.key().startsWith("!")) {
                Aspect aspect = Aspect.getAspect(token.key().replace("!", ""));
                if (aspect != null) { ResearchRecipeViewer.drawAspect(graphics, aspect, x, y, 16); return List.of(Component.literal(aspect.getName()), aspect.getDescription()); }
            }
            var entries = snapshot.spec().entriesByKey();
            Entry referenced = entries.get(token.key());
            boolean drawn = referenced != null && referenced.icons() != null
                && ResearchIconRenderer.draw(graphics, referenced.icons().values(), x, y, false, System.currentTimeMillis());
            if (!drawn && token.icon() != null) {
                ResourceLocation icon = ResourceLocation.tryParse(token.icon());
                if (icon != null && icon.getPath().startsWith("textures/"))
                    drawn = ResearchIconRenderer.drawTexture(graphics, icon, x, y, false, System.currentTimeMillis());
            }
            if (!drawn && token.key() != null) {
                String path = token.key().startsWith("m_") ? "rd_map.png" : token.key().startsWith("c_")
                    ? "rd_chest.png" : token.key().startsWith("f_") ? "rd_flask.png" : null;
                if (path != null) drawn = ResearchIconRenderer.drawTexture(graphics,
                    ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/research/" + path), x, y, false, System.currentTimeMillis());
            }
            if (!drawn) drawUnknownRequirement(graphics, x, y);
            return List.of(Component.translatable(ResearchRequirementPresentation.researchLabel(token, entries, I18n::exists)));
        }
        if (requirement instanceof ResearchRequirementPresentation.Knowledge value) {
            var token = value.token();
            if (token.type() == null) {
                drawUnknownRequirement(graphics, x, y);
                return List.of(Component.translatable(ResearchRequirementPresentation.UNAVAILABLE));
            }
            String type = token.type().name().toLowerCase(Locale.ROOT);
            boolean drawn = ResearchIconRenderer.drawTexture(graphics,
                ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/research/knowledge_" + type + ".png"), x, y, false, System.currentTimeMillis());
            if (!drawn) drawUnknownRequirement(graphics, x, y);
            String categoryTranslation = null;
            if (token.type().hasFields() && token.category() != null) {
                ResourceLocation categoryIcon = knowledgeCategoryIcon(token.category());
                if (categoryIcon != null) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(x + 2, y + 2, 1F);
                    graphics.pose().scale(.75F, .75F, 1F);
                    ResearchIconRenderer.drawTexture(graphics, categoryIcon, 0, 0, false, System.currentTimeMillis());
                    graphics.pose().popPose();
                    String candidate = "tc.research_category." + token.category();
                    if (I18n.exists(candidate)) categoryTranslation = candidate;
                }
            }
            drawRequirementAmount(graphics, token.amount(), x, y, satisfied ? 0xFFFFFFFF : 0xFFFF5555);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("tc.type." + type));
            if (token.type().hasFields() && token.category() != null)
                tooltip.add(Component.translatable(categoryTranslation == null ? ResearchRequirementPresentation.UNAVAILABLE : categoryTranslation));
            tooltip.add(Component.literal("×" + token.amount()));
            return List.copyOf(tooltip);
        }
        drawUnknownRequirement(graphics, x, y);
        return List.of(Component.translatable(ResearchRequirementPresentation.UNAVAILABLE));
    }

    private static ResourceLocation knowledgeCategoryIcon(String category) {
        String path = switch (category) {
            case "BASICS" -> "textures/items/thaumonomicon_cheat.png";
            case "AUROMANCY" -> "textures/research/cat_auromancy.png";
            case "ALCHEMY" -> "textures/research/cat_alchemy.png";
            case "ARTIFICE" -> "textures/research/cat_artifice.png";
            case "INFUSION" -> "textures/research/cat_infusion.png";
            case "GOLEMANCY" -> "textures/research/cat_golemancy.png";
            case "ELDRITCH" -> "textures/research/cat_eldritch.png";
            default -> null;
        };
        return path == null ? null : ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, path);
    }

    private boolean complete(String research) { return snapshot != null && snapshot.knowledgeStatus(research) == EnumResearchStatus.COMPLETE; }
    private void drawInserts(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        if (complete("FIRSTSTEPS")) {
            blitBook(graphics, left - 45, top + 9, 79, 232, 21, 16);
            blitBook(graphics, left - 28, top + 9, 100, 232, 4, 16);
            if (inside(mouseX, mouseY, left - 48, top + 9, 25, 16)) graphics.renderTooltip(font, Component.translatable("tc.aspect.name"), mouseX, mouseY);
        }
        if (complete("KNOWLEDGETYPES") && !key.equals("KNOWLEDGETYPES")) {
            blitBook(graphics, left - 46, top + 32, 47, 232, 21, 16);
            blitBook(graphics, left - 29, top + 32, 100, 232, 4, 16);
            if (inside(mouseX, mouseY, left - 48, top + 32, 25, 16)) graphics.renderTooltip(font, Component.translatable("tc.knowledge.name"), mouseX, mouseY);
        }
    }
    private List<ResourceLocation> bookmarks() {
        if (projection == null) return List.of();
        List<ResourceLocation> result = new ArrayList<>();
        for (var raw : projection.recipeHandles()) {
            ResourceLocation id = ResearchRecipeViewer.sourceKey(raw);
            if (id != null && !result.contains(id)) result.add(id);
        }
        return List.copyOf(result);
    }
    private void drawBookmarks(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        var bookmarks = bookmarks(); if (bookmarks.isEmpty()) return;
        int spacing = Math.min(25, 200 / bookmarks.size()), row = 0;
        List<Component> tooltip = null;
        for (ResourceLocation bookmark : bookmarks) {
            int y = top - 9 + row++ * spacing;
            var tip = recipeViewer.drawBookmark(graphics, bookmark, left + 280, y, System.currentTimeMillis());
            if (inside(mouseX, mouseY, left + 280, y, 30, 16)) tooltip = tip;
        }
        if (tooltip != null) graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }
    private void drawKnowledge(GuiGraphics graphics, int x, int y, boolean inPage, int mouseX, int mouseY) {
        y -= 18;
        int step = inPage ? 18 : 164 / ResearchCategoryDefinitions.newBuiltinCategories().size();
        List<Component> tooltip = null;
        for (var balance : balances) {
            int bx = x - 10 + step * balance.column(), by = y - balance.row() * (inPage ? 20 : 28);
            String type = balance.type().name().toLowerCase(Locale.ROOT);
            ResearchIconRenderer.drawTexture(graphics, ResourceLocation.parse("thaumcraft:textures/research/knowledge_" + type + ".png"), bx, by, false, System.currentTimeMillis());
            ResourceLocation category = knowledgeCategoryIcon(balance.category());
            if (category != null) {
                graphics.pose().pushPose(); graphics.pose().translate(bx + 3, by + 3, 1); graphics.pose().scale(.66F, .66F, 1);
                ResearchIconRenderer.drawTexture(graphics, category, 0, 0, false, System.currentTimeMillis()); graphics.pose().popPose();
            }
            String amount = Integer.toString(balance.amount());
            graphics.drawString(font, amount, bx + 16 - font.width(amount), by + 8, 0xFFFFFFFF, true);
            if (balance.partial() > 0) {
                blitBook(graphics, bx, by + 17, 0, 232, balance.progressPixels(), 2);
                blitBook(graphics, bx + balance.progressPixels(), by + 17, balance.progressPixels(), 234, 16 - balance.progressPixels(), 2);
            }
            if (inside(mouseX, mouseY, bx, by, 16, 19)) tooltip = List.of(Component.translatable("tc.type." + type),
                Component.translatable("tc.research_category." + balance.category()));
        }
        if (inPage && !balances.isEmpty()) blitBook(graphics, x + 4, y - ResearchKnowledgePresentation.rowCount(balances) * 20 + 12, 24, 184, 96, 8);
        if (tooltip != null) graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }
    private void drawKnowledgeInsert(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 100);
        blitPaper(graphics);
        drawKnowledge(graphics, (width - 256) / 2 + 60, top() + 75, false, mouseX, mouseY);
        graphics.pose().popPose();
    }
    private void drawAspectsInsert(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 100);
        blitPaper(graphics);
        int left = (width - 256) / 2, top = (height - 256) / 2, x = left + 60, y = top + 24;
        List<Component> tooltip = null;
        for (int row = 0; row < 5 && aspectsPage * 5 + row < knownAspects.size(); row++) {
            Aspect aspect = knownAspects.get(aspectsPage * 5 + row);
            int ay = y + row * 40;
            ResearchRecipeViewer.drawAspect(graphics, aspect, x + 2, ay + 2, 24);
            drawScaledName(graphics, aspect.getName(), x + 16, ay + 29);
            if (inside(mouseX, mouseY, x, ay, 40, 40)) tooltip = List.of(Component.literal(aspect.getName()), aspect.getDescription());
            Aspect[] components = aspect.getComponents();
            if (components != null && components.length == 2) {
                for (int component = 0; component < 2; component++) {
                    Aspect part = components[component]; int cx = x + 60 + component * 42;
                    if (snapshot.knows("!" + part.getTag().toLowerCase(Locale.ROOT))) {
                        ResearchRecipeViewer.drawAspect(graphics, part, cx, ay + 4, 20);
                        drawScaledName(graphics, part.getName(), cx + 12, ay + 29);
                        if (inside(mouseX, mouseY, cx, ay + 4, 20, 20)) tooltip = List.of(Component.literal(part.getName()), part.getDescription());
                    } else {
                        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
                        graphics.blit(ResourceLocation.parse("thaumcraft:textures/aspects/_unknown.png"), cx, ay + 4, 20, 20, 0, 0, 256, 256, 256, 256);
                    }
                }
                graphics.drawString(font, "=", x + 39, ay + 12, 0xFF505050, false);
                graphics.drawString(font, "+", x + 86, ay + 12, 0xFF505050, false);
            }
        }
        if (aspectsPage > 0) blitBook(graphics, left + 40, top + 232, 0, 184, 12, 8);
        if ((aspectsPage + 1) * 5 < knownAspects.size()) blitBook(graphics, left + 204, top + 232, 12, 184, 12, 8);
        if (tooltip != null) graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
        graphics.pose().popPose();
    }
    private void drawScaledName(GuiGraphics graphics, String name, int centerX, int y) {
        graphics.pose().pushPose(); graphics.pose().translate(centerX, y, 0); graphics.pose().scale(.5F, .5F, 1);
        graphics.drawString(font, name, -font.width(name) / 2, 0, 0xFF505050, false); graphics.pose().popPose();
    }
    private void blitPaper(GuiGraphics graphics) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        graphics.blit(ResourceLocation.parse("thaumcraft:textures/gui/paper.png"), (width - 256) / 2, (height - 256) / 2, 0, 0, 255, 255);
    }
    private void drawWarpWarning(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        if (projection == null || !projection.hasContent() || complete(key) || projection.stage().warp() <= 0) return;
        int level = Math.min(5, projection.stage().warp());
        int frame = 160 + (minecraft.player == null ? 0 : Math.floorMod(minecraft.player.tickCount, 32));
        graphics.setColor(.33F, 0, .44F, .9F); RenderSystem.enableBlend();
        RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        graphics.blit(ResourceLocation.parse("thaumcraft:textures/misc/auranodes.png"), left - 102, top - 85, 90, 90,
            (frame % 32) * 8F, (frame / 32) * 8F, 8, 8, 256, 256);
        graphics.setColor(1, 1, 1, 1); RenderSystem.defaultBlendFunc();
        String text = I18n.get("tc.forbidden.level." + level);
        graphics.drawString(font, text, left - 56 - font.width(text) / 2, top - 43, 0xFFAA99FF, false);
        if (inside(mouseX, mouseY, left - 67, top - 50, 20, 20)) graphics.renderTooltip(font,
            Component.literal(I18n.get("tc.warp.warn").replace("%n", text)), mouseX, mouseY);
    }
    private void drawUnknownRequirement(GuiGraphics graphics, int x, int y) {
        drawFitted(graphics, "?", x, y + 4, 16, INK);
    }
    private void drawRequirementAmount(GuiGraphics graphics, int amount, int x, int y) {
        drawRequirementAmount(graphics, amount, x, y, INK);
    }
    private void drawRequirementAmount(GuiGraphics graphics, int amount, int x, int y, int color) {
        String text = Integer.toString(amount);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 16 - font.width(text) / 2F, y + 12, 5F);
        graphics.pose().scale(.5F, .5F, 1F);
        graphics.drawString(font, text, 0, 0, color, true);
        graphics.pose().popPose();
    }

    private boolean canAttempt() {
        if (pending != null || snapshot == null || projection == null || !projection.hasContent() || layoutUnsupported
            || projection.addendaPhaseComplete() || snapshot.storedStage(key) < 0
            || !requirementStatus.all() || recipeViewer.isOpen() || showingAspects || showingKnowledge
            || (requirements.isEmpty() && snapshot.storedStage(key) != 0)) return false;
        var request = ClientResearchPresentation.prepare(key);
        // A fresh packet arriving before the next Screen.tick must not make an old displayed
        // button pay a different/new stage. Availability is NOT cost satisfaction.
        return request != null && request.snapshot() == snapshot;
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int left = left(), top = top();
        if (inside(mouseX, mouseY, left + 118, top + 190, 20, 12)) { playPage(); back(); return true; }
        if (complete("FIRSTSTEPS") && inside(mouseX, mouseY, left - 48, top + 9, 25, 16)) {
            recipeViewer.close(); showingAspects = !showingAspects; showingKnowledge = false; playPage(); return true;
        }
        if (complete("KNOWLEDGETYPES") && !key.equals("KNOWLEDGETYPES") && inside(mouseX, mouseY, left - 48, top + 32, 25, 16)) {
            recipeViewer.close(); showingKnowledge = !showingKnowledge; showingAspects = false; playPage(); return true;
        }
        var bookmarks = bookmarks(); int spacing = bookmarks.isEmpty() ? 0 : Math.min(25, 200 / bookmarks.size());
        for (int row = bookmarks.size() - 1; row >= 0; row--) if (inside(mouseX, mouseY, left + 280, top - 8 + row * spacing, 30, 16)) {
            recipeViewer.toggle(bookmarks.get(row)); showingAspects = showingKnowledge = false; playPage(); return true;
        }
        if (recipeViewer.isOpen()) { boolean consumed = recipeViewer.click(mouseX, mouseY, width, height); if (consumed) playPage(); return consumed; }
        if (showingKnowledge) return true;
        if (showingAspects) {
            int paperLeft = (width - 256) / 2, paperTop = (height - 256) / 2;
            if (inside(mouseX, mouseY, paperLeft + 38, paperTop + 230, 16, 14)) previous();
            else if (inside(mouseX, mouseY, paperLeft + 202, paperTop + 230, 16, 14)) next();
            return true;
        }
        if (inside(mouseX, mouseY, left - 17, top + 189, 14, 10)) { previous(); return true; }
        if (inside(mouseX, mouseY, left + 261, top + 189, 14, 10)) { next(); return true; }
        if (page == 0 && canAttempt() && inside(mouseX, mouseY, left + 20, top + 176 - requirements.size() * 18, 64, 12)) {
            var request = ClientResearchPresentation.prepare(key);
            // Page cannot start an unknown entry. Browser alone owns that initial-start action.
            if (request != null && request.snapshot() == snapshot && request.packet().expectedStage() >= 0 && ClientResearchPresentation.submit(request)) {
                pending = request; pendingTicks = 0; retryExpired = false;
                if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundsTC.write, .66F, 1F);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE
            || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) { back(); return true; }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_PAGE_UP) { previous(); return true; }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) { next(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    private void back() {
        if (recipeViewer.back()) return;
        if (showingAspects || showingKnowledge) { showingAspects = showingKnowledge = false; return; }
        onClose();
    }
    private void previous() {
        if (recipeViewer.isOpen()) { recipeViewer.previous(); playPage(); }
        else if (showingAspects) { aspectsPage = Math.max(0, aspectsPage - 1); playPage(); }
        else if (!showingKnowledge && page >= 2) { page -= 2; playPage(); }
    }
    private void next() {
        if (recipeViewer.isOpen()) { recipeViewer.next(); playPage(); }
        else if (showingAspects) { aspectsPage = Math.min(Math.max(0, (knownAspects.size() - 1) / 5), aspectsPage + 1); playPage(); }
        else if (!showingKnowledge && page < pages.size() - 2) { page += 2; playPage(); }
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (recipeViewer.isOpen() && button == 0) { recipeViewer.rotate(dx); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return false;
        if (scrollY < 0) next(); else previous(); return true;
    }
    private void playPage() { if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundsTC.page, .66F, 1F); }
    private int left() { return (width - PANE_WIDTH) / 2; }
    private int top() { return (height - PANE_HEIGHT) / 2; }
    private static boolean inside(double x, double y, int left, int top, int width, int height) { return x >= left && y >= top && x < left + width && y < top + height; }
    private void drawFitted(GuiGraphics graphics, String text, int left, int y, int width, int color) {
        int textWidth = font.width(text);
        float scale = textWidth > width ? (float) width / textWidth : 1F;
        graphics.pose().pushPose();
        graphics.pose().translate(left + (width - textWidth * scale) / 2F, y, 0F);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
    private void drawFooter(GuiGraphics graphics, String translation, int left, int top, int mouseX, int mouseY) {
        String text = I18n.get(translation);
        drawFitted(graphics, text, left - 15, top + 207, 292, 0xFFE6D8B6);
        if (inside(mouseX, mouseY, left - 15, top + 205, 292, 12)) graphics.renderTooltip(font, Component.translatable(translation), mouseX, mouseY);
    }

}
