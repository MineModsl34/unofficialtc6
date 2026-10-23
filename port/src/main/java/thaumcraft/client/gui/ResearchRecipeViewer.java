package thaumcraft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.ShapedRecipe;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.client.gui.model.ResearchRecipeGeometry;
import thaumcraft.common.lib.research.ResearchItemResolver;
import thaumcraft.common.lib.research.ResearchRecipeDisplayCatalog;
import thaumcraft.common.lib.research.ResearchRecipeDisplayCatalog.Display;
import thaumcraft.common.lib.research.ResearchRecipeDisplayCatalog.Kind;
import thaumcraft.common.lib.research.catalog.ResearchClientView.Snapshot;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.LegacyResourceId;

/** Read-only recipe insert: source bookmark groups/research gates, item cycling, native recipe
 * atlas geometry, aspect costs, vis/instability, variants and ingredient drill-down/back history.
 * Actual vanilla recipes are read from the synced RecipeManager; TC6 descriptors are DISPLAY
 * data only. Missing later-stage item registrations stay unknown, never synthetic craftables.
 * Blueprint cells use modern isometric item-model projection, an explicit render adaptation.
 */
final class ResearchRecipeViewer {
    private static final ResourceLocation PAPER = ResourceLocation.parse("thaumcraft:textures/gui/paper.png");
    private static final ResourceLocation OVERLAY = ResourceLocation.parse("thaumcraft:textures/gui/gui_researchbook_overlay.png");
    private static final ResourceLocation BOOK = ResourceLocation.parse("thaumcraft:textures/gui/gui_researchbook.png");
    private static ResearchRecipeDisplayCatalog catalog;
    private static Object resourcesOwner;
    private static WeakReference<Snapshot> recipesOwner = new WeakReference<>(null);
    private static WeakReference<HolderLookup.Provider> registryOwner = new WeakReference<>(null);
    private static long metadataEpoch;
    private static final Map<ResourceLocation, List<Recipe>> RESOLVED = new LinkedHashMap<>();
    private final Map<net.minecraft.world.item.Item, Selection> references = new LinkedHashMap<>();
    private final ArrayDeque<Selection> history = new ArrayDeque<>();
    private final List<Hit> hits = new ArrayList<>();
    private Snapshot snapshot;
    private ResourceLocation selected;
    private int variant, blueprintLayer;
    private boolean slices;
    private float blueprintRotation;
    private long referenceEpoch = -1;
    private record Selection(ResourceLocation key, int variant) {}
    private record Hit(int x, int y, ResourceLocation recipe, int variant) {}
    record Recipe(ResourceLocation id, Kind kind, ItemStack output, String rawOutput,
                  int width, int height, List<List<ItemStack>> ingredients, List<List<String>> rawIngredients,
                  Map<String, Integer> aspects, int vis, int instability, Display source) {}

    void setSnapshot(Snapshot current) {
        if (current == snapshot) return;
        snapshot = current;
        references.clear();
        if (current == null || (selected != null && recipes(current, selected).isEmpty())) { selected = null; variant = 0; history.clear(); }
    }
    boolean isOpen() { return selected != null; }
    boolean open(ResourceLocation key) {
        if (snapshot == null || recipes(snapshot, key).isEmpty()) return false;
        if (selected != null && !selected.equals(key) && history.size() < 64) history.push(new Selection(selected, variant));
        selected = key; variant = 0; blueprintLayer = 0; return true;
    }
    void toggle(ResourceLocation key) {
        if (key.equals(selected)) close();
        else { history.clear(); selected = null; open(key); }
    }
    void close() { selected = null; variant = 0; history.clear(); }
    boolean back() {
        if (!isOpen()) return false;
        if (history.isEmpty()) close();
        else { var previous = history.pop(); selected = previous.key(); variant = previous.variant(); }
        return true;
    }
    void previous() { variant = Math.max(0, variant - 1); }
    void next() { if (selected != null) variant = Math.min(recipes(snapshot, selected).size() - 1, variant + 1); }
    void rotate(double dx) { blueprintRotation += (float) dx * .02F; }

    static void clearMetadata() { catalog = null; resourcesOwner = null; recipesOwner = new WeakReference<>(null); registryOwner = new WeakReference<>(null); RESOLVED.clear(); metadataEpoch++; }
    private static HolderLookup.Provider registries() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : player.registryAccess();
    }
    /** Preserve source leaf-before-group priority, with raw unqualified group identities
     * retained separately from native ResourceLocation IDs in the display catalog. */
    static ResourceLocation sourceKey(LegacyResourceId handle) {
        if (handle == null || !handle.modernSyntax()) return null;
        ResourceLocation apiKey = ResourceLocation.tryParse(handle.normalized());
        if (apiKey != null && apiRecipe(apiKey) != null) return apiKey;
        ResourceLocation key = catalog().bookKey(handle.raw());
        // MC1.12 lowercases legacy recipe handles; raw group metadata must remain untouched.
        return key == null ? catalog().bookKey(handle.normalized()) : key;
    }
    static List<String> outputNames(Snapshot snapshot, ResourceLocation key) {
        if (snapshot == null) return List.of();
        Object api = apiRecipe(key);
        if (api != null) {
            if (api instanceof thaumcraft.api.ThaumcraftApi.BluePrint || api instanceof thaumcraft.api.crafting.RecipeMisc
                || !ResearchApiRecipeDisplay.supported(api)) return List.of();
            if (api instanceof thaumcraft.api.crafting.InfusionRecipe infusion && !(infusion.getRecipeOutput() instanceof ItemStack)) return List.of();
            ItemStack result = ResearchApiRecipeDisplay.project(key,api,registries()).output();
            return result.isEmpty() ? List.of() : List.of(result.getHoverName().getString());
        }
        var display = catalog().find(key);
        if (display != null) {
            // Source search does not expand ConfigRecipes groups or blueprint/misc displays
            // and samples a direct output BEFORE the recipe's strict research render gate.
            if (display.kind() != Kind.CRAFTING && display.kind() != Kind.ARCANE
                && display.kind() != Kind.CRUCIBLE && display.kind() != Kind.INFUSION) return List.of();
            ItemStack output = display.output().isEmpty() ? ItemStack.EMPTY : ResearchItemResolver.resolve(ResearchItemResolver.parse(display.output()), registries());
            return output.isEmpty() ? List.of() : List.of(output.getHoverName().getString());
        }
        return recipes(snapshot, key).stream().map(recipe -> recipe.output().isEmpty() ? null : recipe.output().getHoverName().getString()).filter(java.util.Objects::nonNull).toList();
    }

    static List<Recipe> recipes(Snapshot snapshot, ResourceLocation key) {
        if (snapshot == null || key == null) return List.of();
        catalog(); // Resource reload invalidates even cached recipes for the same received owner.
        if (snapshot != recipesOwner.get()) { recipesOwner = new WeakReference<>(snapshot); RESOLVED.clear(); }
        // Registry-backed enchantment holders are world/session-specific, not builtin IDs.
        var registries = registries();
        if (registries != registryOwner.get()) { registryOwner = new WeakReference<>(registries); RESOLVED.clear(); metadataEpoch++; }
        // Source public maps and recipe descriptors are mutable; their addon writes cannot
        // invalidate a private cache. Resolve live while either API catalog is populated.
        boolean liveApi = !thaumcraft.api.ThaumcraftApi.getCraftingRecipes().isEmpty()
            || !thaumcraft.api.ThaumcraftApi.getCraftingRecipesFake().isEmpty();
        var cached = liveApi ? null : RESOLVED.get(key); if (cached != null) return cached;
        List<Recipe> result = new ArrayList<>();
        collect(snapshot, key, result, new HashSet<>());
        var immutable = List.copyOf(result);
        if (!liveApi && RESOLVED.size() < ResearchRecipeDisplayCatalog.MAX_RECIPES) RESOLVED.put(key, immutable);
        return immutable;
    }
    private static void collect(Snapshot snapshot, ResourceLocation key, List<Recipe> output, Set<ResourceLocation> visiting) {
        if (!visiting.add(key) || visiting.size() > 256 || output.size() > 512) return;
        Object api = apiRecipe(key);
        if (api != null) {
            if (api instanceof List<?> group) {
                for (Object member : group) if (member instanceof ResourceLocation id) collect(snapshot,id,output,visiting);
            } else if (ResearchApiRecipeDisplay.supported(api)) {
                String research = ResearchApiRecipeDisplay.research(api);
                if (research == null || research.isEmpty() || snapshot.knowsStrict(research))
                    output.add(ResearchApiRecipeDisplay.project(key,api,registries()));
            }
            visiting.remove(key); return;
        }
        Display display = catalog().find(key);
        if (display != null) {
            if (display.kind() == Kind.GROUP) {
                for (ResourceLocation member : display.groupMembers()) collect(snapshot, member, output, visiting);
            } else if (display.research().isEmpty() || snapshot.knowsStrict(display.research())) {
                List<List<ItemStack>> ingredients = new ArrayList<>();
                for (var slot : display.ingredients()) {
                    List<ItemStack> choices = new ArrayList<>();
                    for (String token : slot) choices.addAll(ResearchItemResolver.alternatives(ResearchItemResolver.parse(token), registries()));
                    ingredients.add(List.copyOf(choices));
                }
                ItemStack result = display.output().isEmpty() ? ItemStack.EMPTY : ResearchItemResolver.resolve(ResearchItemResolver.parse(display.output()), registries());
                output.add(new Recipe(key, display.kind(), result, display.output(), display.width(), display.height(),
                    List.copyOf(ingredients), display.ingredients(), display.aspects(), display.vis(), display.instability(), display));
            }
        } else {
            var minecraft = Minecraft.getInstance();
            if (minecraft.level != null) minecraft.level.getRecipeManager().byKey(key).ifPresent(holder -> {
                var recipe = holder.value();
                List<List<ItemStack>> ingredients = recipe.getIngredients().stream().map(ingredient -> List.of(ingredient.getItems())).toList();
                int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
                int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : (ingredients.size() + 2) / 3;
                output.add(new Recipe(key, Kind.CRAFTING, recipe.getResultItem(minecraft.level.registryAccess()), "", width, height,
                    ingredients, List.of(), Map.of(), 0, 0, null));
            });
        }
        visiting.remove(key);
    }
    private static Object apiRecipe(ResourceLocation key) {
        Object recipe = thaumcraft.api.internal.CommonInternals.getCatalogRecipe(key);
        return recipe == null ? thaumcraft.api.internal.CommonInternals.getCatalogRecipeFake(key) : recipe;
    }
    private static ResearchRecipeDisplayCatalog catalog() {
        var manager = Minecraft.getInstance().getResourceManager();
        if (catalog != null && resourcesOwner == manager) return catalog;
        resourcesOwner = manager; catalog = ResearchRecipeDisplayCatalog.empty();
        RESOLVED.clear(); metadataEpoch++;
        var resource = manager.getResource(ResearchRecipeDisplayCatalog.RESOURCE);
        if (resource.isPresent()) {
            try (var stream = resource.get().open()) {
                byte[] bytes = stream.readNBytes(ResearchRecipeDisplayCatalog.MAX_BYTES + 1);
                if (bytes.length <= ResearchRecipeDisplayCatalog.MAX_BYTES)
                    catalog = ResearchRecipeDisplayCatalog.parse(new String(bytes, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException malformed) { /* Fail closed; no fallback fabricated recipes. */ }
        }
        return catalog;
    }

    /** Bookmark output is source-cycle first recipe variant. Returns human tooltip, never raw ID. */
    List<Component> drawBookmark(GuiGraphics graphics, ResourceLocation key, int x, int y, long now) {
        List<Recipe> variants = recipes(snapshot, key);
        graphics.setColor(1F, key.equals(selected) ? .5F : 1F, key.equals(selected) ? .5F : 1F, 1F);
        blit(graphics, BOOK, x, y, 120, 232, 28, 16); blit(graphics, BOOK, x, y, 116, 232, 4, 16);
        graphics.setColor(1, 1, 1, 1);
        if (variants.isEmpty()) { graphics.drawString(Minecraft.getInstance().font, "?", x + 10, y + 4, 0xFF695438, false); return List.of(Component.translatable("thaumcraft.research_ui.recipe_unavailable")); }
        Recipe recipe = variants.get((int) Math.floorMod(now / 1000, variants.size()));
        if (recipe.kind() == Kind.BLUEPRINT && recipe.output().isEmpty() && recipe.source() != null
            && !recipe.source().blueprint().isEmpty()) {
            drawBlueprintBookmark(graphics, recipe.source(), x + 15, y + 8);
            return recipe.rawOutput().isEmpty() ? List.of(Component.translatable("recipe.type.construct"))
                : List.of(Component.translatable("recipe.type.construct"), Component.translatable("thaumcraft.research_ui.requirement_unavailable"));
        }
        drawItem(graphics, recipe.output(), x + 7, y, false);
        return recipe.output().isEmpty() ? List.of(Component.translatable("thaumcraft.research_ui.requirement_unavailable")) : List.of(recipe.output().getHoverName());
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!isOpen()) return;
        List<Recipe> options = recipes(snapshot, selected);
        if (options.isEmpty()) { close(); return; }
        variant = Math.max(0, Math.min(variant, options.size() - 1));
        Recipe recipe = options.get(variant);
        int left = (screenWidth - 256) / 2, top = (screenHeight - 256) / 2, x = left + 128, y = top + 128;
        Font font = Minecraft.getInstance().font;
        hits.clear();
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 100);
        blit(graphics, PAPER, left, top, 0, 0, 255, 255);
        String heading = I18n.get(switch (recipe.kind()) {
            case ARCANE -> "recipe.type.arcane"; case CRUCIBLE -> "recipe.type.crucible";
            case INFUSION, ENCHANTMENT -> "recipe.type.infusion"; case BLUEPRINT -> "recipe.type.construct";
            default -> "recipe.type.workbench";
        });
        graphics.drawString(font, heading, x - font.width(heading) / 2, y - 104, 0xFF505050, false);
        List<Component> hover = null;
        switch (recipe.kind()) {
            case CRAFTING, ARCANE, MISC -> {
                scaledOverlay(graphics, x, y, -26, -26, recipe.kind() == Kind.ARCANE ? 112 : 60, 15, 52, 52);
                scaledOverlay(graphics, x, y, -8, -46, 20, 3, 16, 16);
                hover = drawSlot(graphics, recipe.output(), x - 8, y - 84, mouseX, mouseY, false, hover);
                for (var slot : ResearchRecipeGeometry.grid(recipe.width(), recipe.height(), recipe.ingredients().size())) {
                    hover = drawIngredientSlot(graphics, recipe, slot.index(), x + slot.x(), y + slot.y(), mouseX, mouseY, hover);
                }
                if (recipe.kind() == Kind.ARCANE) {
                    scaledOverlay(graphics, x, y, -6, 40, 68, 76, 12, 12);
                    String cost = Integer.toString(recipe.vis()); graphics.drawString(font, cost, x - font.width(cost) / 2, y + 90, 0xFF505050, false);
                    if (inside(mouseX, mouseY, x - font.width(cost) / 2 - 15, y + 75, 30, 30)) hover = List.of(Component.translatable("wandtable.text1"));
                    int index = 0, size = recipe.aspects().size();
                    for (var costEntry : recipe.aspects().entrySet()) {
                        Aspect aspect = Aspect.getAspect(costEntry.getKey()); if (aspect == null) continue;
                        hover = drawSlot(graphics, ThaumcraftApiHelper.makeCrystal(aspect, costEntry.getValue()),
                            x + 4 - size * 10 + index++ * 20, y + 59, mouseX, mouseY, true, hover);
                    }
                }
            }
            case CRUCIBLE -> {
                scaledOverlay(graphics, x, y, -28, -29, 0, 3, 56, 17);
                scaledOverlay(graphics, x, y, -28, -12, 0, 20, 56, 48);
                scaledOverlay(graphics, x, y, -25, -26, 100, 84, 11, 13);
                hover = drawSlot(graphics, recipe.output(), x - 8, y - 50, mouseX, mouseY, false, hover);
                if (!recipe.ingredients().isEmpty()) hover = drawIngredientSlot(graphics, recipe, 0, x - 64, y - 56, mouseX, mouseY, hover);
                hover = drawAspects(graphics, recipe.aspects(), x, y + 8, 3, mouseX, mouseY, hover);
            }
            case INFUSION, ENCHANTMENT -> {
                scaledOverlay(graphics, x, y + 20, -28, -56, 0, 3, 56, 17);
                scaledOverlay(graphics, x, y + 20, -28, -36, 200, 77, 60, 44);
                hover = drawSlot(graphics, recipe.output(), x - 8, y - 85, mouseX, mouseY, false, hover);
                if (!recipe.ingredients().isEmpty()) hover = drawIngredientSlot(graphics, recipe, 0, x - 8, y - 16, mouseX, mouseY, hover);
                int count = Math.max(0, recipe.ingredients().size() - 1);
                // Source integer 360/count, not an invented evenly fractional angle.
                for (var slot : ResearchRecipeGeometry.infusionRing(count)) {
                    int index = slot.index() + 1;
                    hover = drawIngredientSlot(graphics, recipe, index, x + slot.x(), y + slot.y(), mouseX, mouseY, hover);
                }
                hover = drawAspects(graphics, recipe.aspects(), x, y + 50, 5, mouseX, mouseY, hover);
                String instability = I18n.get("tc.inst") + " " + I18n.get("tc.inst." + Math.min(5, Math.max(0, recipe.instability() / 2)));
                graphics.drawString(font, instability, x - font.width(instability) / 2, y + 94, 0xFF505050, false);
            }
            case BLUEPRINT -> {
                if (recipe.source() != null && !recipe.source().blueprint().isEmpty()) {
                    var cells = recipe.source().blueprint();
                    int maxLayer = cells.stream().mapToInt(cell -> cell.y()).max().orElse(0);
                    blueprintLayer = maxLayer == 0 ? 0 : (int) Math.floorMod(System.currentTimeMillis() / 1000, maxLayer + 1);
                    var sorted = cells.stream().filter(cell -> !slices || cell.y() <= blueprintLayer)
                        .sorted(Comparator.comparingInt(cell -> cell.x() + cell.z() + cell.y() * 4)).toList();
                    int extent = cells.stream().mapToInt(cell -> Math.max(Math.max(Math.abs(cell.x()), Math.abs(cell.z())), Math.abs(cell.y()))).max().orElse(1);
                    float scale = Math.min(1F, 8F / Math.max(1, extent));
                    for (var cell : sorted) {
                        float rx = (float) (cell.x() * Math.cos(blueprintRotation) - cell.z() * Math.sin(blueprintRotation));
                        float rz = (float) (cell.x() * Math.sin(blueprintRotation) + cell.z() * Math.cos(blueprintRotation));
                        int bx = x + Math.round((rx - rz) * 8 * scale) - 8, by = y + Math.round((rx + rz) * 4 * scale - cell.y() * 10 * scale) - 8;
                        hover = drawSlot(graphics, ResearchItemResolver.resolve(ResearchItemResolver.parse(cell.item()), registries()), bx, by, mouseX, mouseY, true, hover);
                    }
                    blit(graphics, BOOK, x + 80, y + 100, slices ? 168 : 160, 224, 8, 8);
                }
                // Captured ingredient counts aggregate the structure's materials. Rendering
                // its individual cells alone loses both these counts and source click-through.
                for (var slot : ResearchRecipeGeometry.blueprintIngredients(recipe.ingredients().size()))
                    hover = drawIngredientSlot(graphics, recipe, slot.index(), x + slot.x(), y + slot.y(), mouseX, mouseY, hover);
            }
            default -> {}
        }
        if (variant > 0) blit(graphics, BOOK, left + 40, top + 232, 0, 184, 12, 8);
        if (variant < options.size() - 1) blit(graphics, BOOK, left + 204, top + 232, 12, 184, 12, 8);
        // Footer explicitly distinguishes source display data from recipes not yet playable.
        // No display ItemStack is normal for a source blueprint: its parts ARE the display.
        if (recipe.output().isEmpty() && (recipe.kind() != Kind.BLUEPRINT || !recipe.rawOutput().isEmpty())) {
            graphics.drawWordWrap(font, Component.translatable("thaumcraft.research_ui.recipe_content_unavailable"), left + 34, top + 210, 188, 0xFF76654B);
        }
        if (hover != null) graphics.renderTooltip(font, hover, java.util.Optional.empty(), mouseX, mouseY);
        graphics.pose().popPose();
    }

    private static void drawBlueprintBookmark(GuiGraphics graphics, Display source, int centerX, int centerY) {
        var cells = source.blueprint();
        var thumbnail = ResearchRecipeGeometry.blueprintThumbnail(cells.stream()
            .map(cell -> new ResearchRecipeGeometry.Voxel(cell.x(), cell.y(), cell.z())).toList());
        graphics.pose().pushPose(); graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(thumbnail.scale(), thumbnail.scale(), 1F);
        graphics.pose().translate(thumbnail.offsetX(), thumbnail.offsetY(), 0);
        for (var slot : thumbnail.cells()) {
            var cell = cells.get(slot.index());
            drawItem(graphics, ResearchItemResolver.resolve(ResearchItemResolver.parse(cell.item()), registries()), slot.x(), slot.y(), false);
        }
        graphics.pose().popPose();
    }

    /** Empty declared ingredients stay blank; genuinely unregistered tokens keep their
     * unknown icon and declared amount, rather than masquerading as an ordinary empty cell.
     */
    private List<Component> drawIngredientSlot(GuiGraphics graphics, Recipe recipe, int index, int x, int y,
                                               int mouseX, int mouseY, List<Component> hover) {
        List<ItemStack> choices = recipe.ingredients().get(index);
        List<String> raw = index < recipe.rawIngredients().size() ? recipe.rawIngredients().get(index) : List.of();
        if (ResearchRecipeGeometry.emptyIngredient(recipe.source() != null, raw.size(), choices.size())) return hover;
        ItemStack stack = cycle(choices, index);
        hover = drawSlot(graphics, stack, x, y, mouseX, mouseY, true, hover);
        if (!stack.isEmpty() || raw.isEmpty()) return hover;
        String current = raw.get((int) Math.floorMod(System.currentTimeMillis() / 1000 + index, raw.size()));
        int amount = ResearchItemResolver.parse(current).count();
        if (amount > 1) {
            var font = Minecraft.getInstance().font;
            String text = Integer.toString(amount);
            graphics.drawString(font, text, x + 17 - font.width(text), y + 9, 0xFFFFFFFF, true);
            if (inside(mouseX, mouseY, x, y, 16, 16)) hover = List.of(
                Component.translatable("thaumcraft.research_ui.requirement_unavailable"), Component.literal("×" + amount));
        }
        return hover;
    }

    boolean click(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!isOpen()) return false;
        int left = (screenWidth - 256) / 2, top = (screenHeight - 256) / 2;
        var options = recipes(snapshot, selected);
        boolean blueprint = !options.isEmpty() && options.get(Math.max(0, Math.min(variant, options.size() - 1))).kind() == Kind.BLUEPRINT;
        switch (ResearchRecipeGeometry.control(mouseX, mouseY, left, top, blueprint, variant, options.size())) {
            case PREVIOUS -> { previous(); return true; }
            case NEXT -> { next(); return true; }
            case SLICE -> { slices = !slices; return true; }
            default -> {}
        }
        for (Hit hit : hits) if (inside(mouseX, mouseY, hit.x(), hit.y(), 16, 16)) {
            open(hit.recipe()); variant = hit.variant(); return true;
        }
        return true; // An insert consumes clicks: no payment through a visible overlay.
    }

    private List<Component> drawSlot(GuiGraphics graphics, ItemStack stack, int x, int y, int mouseX, int mouseY, boolean reference, List<Component> hover) {
        drawItem(graphics, stack, x, y, true);
        if (reference && !stack.isEmpty()) {
            Selection recipe = reference(stack);
            if (recipe != null) hits.add(new Hit(x, y, recipe.key(), recipe.variant()));
        }
        if (inside(mouseX, mouseY, x, y, 16, 16)) {
            if (stack.isEmpty()) return List.of(Component.translatable("thaumcraft.research_ui.requirement_unavailable"));
            var minecraft = Minecraft.getInstance();
            List<Component> result = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player,
                minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL));
            if (reference && hits.stream().anyMatch(hit -> hit.x() == x && hit.y() == y)) result.add(Component.translatable("recipe.clickthrough"));
            return result;
        }
        return hover;
    }
    private Selection reference(ItemStack stack) {
        if (snapshot == null) return null;
        if (!thaumcraft.api.ThaumcraftApi.getCraftingRecipes().isEmpty()
            || !thaumcraft.api.ThaumcraftApi.getCraftingRecipesFake().isEmpty()) references.clear();
        if (referenceEpoch != metadataEpoch) { referenceEpoch = metadataEpoch; references.clear(); }
        if (references.containsKey(stack.getItem())) return references.get(stack.getItem());
        for (var entry : snapshot.spec().entries()) {
            if (entry.stages() == null) continue;
            for (var stage : entry.stages().values()) {
                if (stage.recipes() == null) continue;
                for (var handle : stage.recipes().values()) {
                    ResourceLocation id = sourceKey(handle);
                    if (id == null) continue;
                    List<Recipe> recipes = recipes(snapshot, id);
                    for (int index = 0; index < recipes.size(); index++)
                        if (!recipes.get(index).output().isEmpty() && ItemStack.isSameItem(stack, recipes.get(index).output())) {
                            Selection selection = new Selection(id, index); references.put(stack.getItem(), selection); return selection;
                        }
                }
            }
        }
        if (references.size() < ResearchRecipeDisplayCatalog.MAX_RECIPES) references.put(stack.getItem(), null);
        return null;
    }
    private static ItemStack cycle(List<ItemStack> choices, int offset) {
        return choices.isEmpty() ? ItemStack.EMPTY : choices.get((int) Math.floorMod(System.currentTimeMillis() / 1000 + offset, choices.size()));
    }
    private static void drawItem(GuiGraphics graphics, ItemStack stack, int x, int y, boolean amount) {
        if (stack.isEmpty()) graphics.drawString(Minecraft.getInstance().font, "?", x + 4, y + 4, 0xFF695438, false);
        else { graphics.renderItem(stack, x, y); if (amount) graphics.renderItemDecorations(Minecraft.getInstance().font, stack, x, y); }
    }
    private static List<Component> drawAspects(GuiGraphics graphics, Map<String, Integer> costs, int x, int y, int columns,
                                              int mouseX, int mouseY, List<Component> hover) {
        var ordered = costs.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        for (var slot : ResearchRecipeGeometry.aspects(ordered.size(), columns)) {
            var cost = ordered.get(slot.index()); Aspect aspect = Aspect.getAspect(cost.getKey()); if (aspect == null) continue;
            int ax = x + slot.x(), ay = y + slot.y();
            drawAspect(graphics, aspect, ax, ay, 16);
            String amount = Integer.toString(cost.getValue());
            graphics.drawString(Minecraft.getInstance().font, amount, ax + 16 - Minecraft.getInstance().font.width(amount), ay + 10, 0xFFFFFFFF, true);
            if (inside(mouseX, mouseY, ax, ay, 16, 16)) hover = List.of(Component.literal(aspect.getName()), aspect.getDescription(), Component.literal("×" + cost.getValue()));
        }
        return hover;
    }
    static void drawAspect(GuiGraphics graphics, Aspect aspect, int x, int y, int size) {
        int color = aspect.getColor(); graphics.setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, 1);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        graphics.blit(aspect.getImage(), x, y, size, size, 0, 0, 256, 256, 256, 256); graphics.setColor(1, 1, 1, 1);
    }
    private static void scaledOverlay(GuiGraphics graphics, int x, int y, int dx, int dy, int u, int v, int width, int height) {
        graphics.pose().pushPose(); graphics.pose().translate(x, y, 0); graphics.pose().scale(2, 2, 1);
        blit(graphics, OVERLAY, dx, dy, u, v, width, height); graphics.pose().popPose();
    }
    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); graphics.blit(texture, x, y, u, v, width, height);
    }
    private static boolean inside(double mx, double my, int x, int y, int width, int height) { return mx >= x && mx < x + width && my >= y && my < y + height; }
}
