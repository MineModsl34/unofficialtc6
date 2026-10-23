package thaumcraft.client.jei;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.client.lib.ClientAspectSnapshot;
import thaumcraft.client.lib.ClientJeiRecipeSnapshot;
import thaumcraft.client.lib.ClientResearchPresentation;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchRecipes;
import thaumcraft.common.lib.crafting.InfusionEnchantmentRecipe;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;
import thaumcraft.common.lib.crafting.RecipeArcaneWorkbench;
import thaumcraft.common.lib.research.catalog.ResearchClientView;

/** Opt-in read-only runtime evidence. No event subscription, player writes, UI, packets or exit. */
public final class JeiClientAudit {
    private static final String PROPERTY = "thaumcraft.jeiAuditReport";
    private static final String[] ENCHANTMENTS = {"iecollector", "iedestructive", "ieburrowing", "iesounding",
        "ierefining", "iearcing", "ieessence", "ielamplight"};
    private static final String[] TAGS = {"helmgoggles", "maskangryghost", "maskgrinningdevil",
        "masksippingfiend", "verdantheartlife", "verdantheartsustain"};
    private static final List<RecipeType<?>> TYPES = List.of(ThaumcraftJeiPlugin.ARCANE, ThaumcraftJeiPlugin.CRUCIBLE,
        ThaumcraftJeiPlugin.INFUSION, AspectJeiRecipes.COMBINATIONS, AspectJeiRecipes.CONTENTS);
    private static final Map<String, JsonElement> BOOKMARKS = new LinkedHashMap<>();
    private static final JsonArray HISTORY = new JsonArray();
    private static Object lastConnection;
    private static IJeiRuntime lastRuntime;
    private static ResearchClientView.Snapshot lastKnowledge;
    private static long lastRecipes = -1, lastAspects = -1, connectionGeneration, knowledgeGeneration, observations;
    private static long lastTriples = -1;
    private static int lastIngredients = -1;
    private static boolean lastHide;
    private static Path lastPath;

    private JeiClientAudit() {}

    public static void tick(IJeiRuntime runtime) {
        String configured = System.getProperty(PROPERTY);
        if (configured == null || configured.isBlank() || runtime == null) return;
        var minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("JEI audit requires client MAIN");
        var listener = minecraft.getConnection();
        var aspects = ClientAspectSnapshot.view();
        var knowledge = ClientResearchPresentation.view();
        if (listener == null || aspects == null || knowledge == null || !ClientJeiRecipeSnapshot.ready()) return;
        long recipesRevision = ClientJeiRecipeSnapshot.revision(), aspectsRevision = aspects.revision();
        long triplesRevision = TripleMeatJeiRecipes.revision();
        int ingredients = runtime.getIngredientManager().getAllItemStacks().size();
        boolean hide = ModConfig.CONFIG_JEI.hideRecipesIfMissingResearch;
        Path path;
        try { path = Path.of(configured).toAbsolutePath().normalize(); }
        catch (RuntimeException invalidPath) { Thaumcraft.log.error("Invalid opt-in JEI audit report path", invalidPath); return; }
        Object connection = listener.getConnection();
        if (lastRuntime == runtime && lastConnection == connection && lastRecipes == recipesRevision
            && lastAspects == aspectsRevision && lastKnowledge == knowledge && lastHide == hide
            && lastIngredients == ingredients && lastTriples == triplesRevision && path.equals(lastPath)) return;
        if (lastConnection != connection) connectionGeneration++;
        if (lastKnowledge == null || !knowledge.sameOwnerKnowledgeGeneration(lastKnowledge)) knowledgeGeneration++;
        lastRuntime = runtime; lastConnection = connection; lastRecipes = recipesRevision;
        lastAspects = aspectsRevision; lastKnowledge = knowledge; lastHide = hide;
        lastTriples = triplesRevision;
        lastIngredients = ingredients; lastPath = path;

        var audit = new Audit();
        audit.report.addProperty("schemaVersion", 1);
        audit.report.addProperty("purpose", "thaumcraft-jei-client-read-only");
        audit.report.addProperty("observedAt", Instant.now().toString());
        audit.report.addProperty("observation", ++observations);
        audit.report.addProperty("connectionGeneration", connectionGeneration);
        audit.report.addProperty("memoryConnection", listener.getConnection().isMemoryConnection());
        audit.report.addProperty("recipeRevision", recipesRevision);
        audit.report.addProperty("aspectRevision", aspectsRevision);
        audit.report.addProperty("tripleMeatRevision", triplesRevision);
        audit.report.addProperty("knowledgeGeneration", knowledgeGeneration);
        audit.report.addProperty("catalogDigest", knowledge.digest());
        audit.report.addProperty("knowledgeDigest", knowledge.readStateDigest());
        audit.report.addProperty("hideUnknownResearch", hide);
        audit.report.addProperty("acceptedSnapshotsReady", true);
        audit.report.addProperty("registeredItemIngredients", ingredients);
        try { inspect(runtime, aspects, knowledge, hide, audit); }
        catch (RuntimeException | LinkageError failure) { audit.fail("audit exception: " + failure); }
        audit.report.add("failures", audit.failures);
        audit.report.addProperty("passed", audit.failures.size() == 0);
        HISTORY.add(audit.report.deepCopy());
        while (HISTORY.size() > 128) HISTORY.remove(0);
        var output = audit.report.deepCopy(); output.add("observations", HISTORY.deepCopy());
        try { write(path, output); }
        catch (IOException | RuntimeException failure) { Thaumcraft.log.error("Cannot save opt-in JEI audit report", failure); }
    }

    private static void inspect(IJeiRuntime runtime, ClientAspectSnapshot.Snapshot aspects,
        ResearchClientView.Snapshot knowledge, boolean hide, Audit audit) {
        var manager = runtime.getRecipeManager();
        JsonArray categories = new JsonArray(); int registered = 0;
        for (var type : TYPES) {
            var row = new JsonObject(); row.addProperty("type", type.getUid().toString());
            var category = manager.getRecipeCategory(type);
            row.addProperty("registered", category != null);
            if (category != null) { registered++; row.addProperty("implementation", category.getClass().getName()); }
            categories.add(row);
        }
        audit.report.add("categories", categories); audit.report.addProperty("registeredCategoryCount", registered);
        audit.check(registered == 5, "Five Thaumcraft categories must be registered");
        var arcane = current(runtime, ThaumcraftJeiPlugin.ARCANE, true);
        var crucible = current(runtime, ThaumcraftJeiPlugin.CRUCIBLE, true);
        var infusion = current(runtime, ThaumcraftJeiPlugin.INFUSION, true);
        var combinations = manager.createRecipeLookup(AspectJeiRecipes.COMBINATIONS).includeHidden().get().toList();
        var contents = manager.createRecipeLookup(AspectJeiRecipes.CONTENTS).get().toList();
        var ingredientAspects = runtime.getIngredientManager().getAllIngredients(AspectIngredientHelper.TYPE);
        audit.report.addProperty("searchableAspectCount", ingredientAspects.size());
        audit.check(ingredientAspects.size() == AspectJeiRecipes.ingredients().size(), "Every registered aspect must be searchable");
        audit.report.addProperty("combinationCount", combinations.size());
        audit.check(combinations.size() == AspectJeiRecipes.combinations().size(), "Every registered compound must have a combination");
        audit.report.addProperty("itemCompositionCount", contents.size());
        audit.check(!contents.isEmpty(), "Actual item aspect compositions must be present");

        JsonObject counts = new JsonObject();
        counts.add("arcane", station(runtime, ThaumcraftJeiPlugin.ARCANE, arcane, holder -> holder.value().research(),
            holder -> knowledge.knowsLiteral(holder.value().research()), hide, audit));
        counts.add("crucible", station(runtime, ThaumcraftJeiPlugin.CRUCIBLE, crucible, holder -> holder.value().getResearch(),
            holder -> knowledge.knowsStrict(holder.value().getResearch()), hide, audit));
        counts.add("infusion", station(runtime, ThaumcraftJeiPlugin.INFUSION, infusion, holder -> holder.value().getResearch(),
            holder -> InfusionDisplayRecipes.sourceRecipe(holder) instanceof InfusionEnchantmentRecipe
                ? knowledge.knows(holder.value().getResearch()) : knowledge.knowsLiteral(holder.value().getResearch()), hide, audit));
        audit.report.add("recipeCounts", counts);
        var level = Minecraft.getInstance().level;
        var expectedArcane = level == null ? List.<RecipeHolder<IArcaneRecipe>>of()
            : level.getRecipeManager().getAllRecipesFor(ArcaneWorkbenchRecipes.TYPE).stream()
                .filter(holder -> holder.value() instanceof RecipeArcaneWorkbench || holder.value() instanceof ShapelessArcaneRecipe)
                .filter(holder -> !holder.value().getResultItem(level.registryAccess()).isEmpty()).toList();
        checkIds("arcane", arcane, expectedArcane, audit);
        checkIds("crucible", crucible, ClientJeiRecipeSnapshot.crucible(), audit);
        var acceptedInfusion = ClientJeiRecipeSnapshot.infusion();
        var actualInfusionSources = sourceIds(infusion);
        audit.report.add("infusionSourceIds", strings(actualInfusionSources));
        audit.report.addProperty("acceptedInfusionSources", acceptedInfusion.size());
        audit.check(actualInfusionSources.equals(ids(acceptedInfusion)), "All accepted infusion source IDs must be represented");
        JsonObject dynamic = new JsonObject();
        dynamic.add("enchantmentSources", required(ENCHANTMENTS, actualInfusionSources, audit));
        dynamic.add("tagSources", required(TAGS, actualInfusionSources, audit));
        boolean runic = actualInfusionSources.contains("thaumcraft:runicarmor");
        dynamic.addProperty("runicSourcePresent", runic); audit.check(runic, "Runic upgrade source must be represented");
        audit.report.add("dynamicInfusion", dynamic);
        var hedge = crucible.stream().filter(holder -> JeiRecipeIdentity.sourceId(holder).getPath().startsWith("hedge_")).toList();
        var acceptedHedge = ClientJeiRecipeSnapshot.crucible().stream().filter(holder -> holder.id().getPath().startsWith("hedge_")).toList();
        audit.report.addProperty("acceptedHedgeCount", acceptedHedge.size());
        audit.report.addProperty("jeiHedgeCount", hedge.size());
        audit.report.add("hedgeSourceIds", strings(sourceIds(hedge)));
        audit.check(sourceIds(hedge).equals(ids(acceptedHedge)), "Server API hedge recipes must match actual JEI sources");
        apiFixture(runtime, crucible, audit);

        JsonArray layouts = new JsonArray(), bookmarks = new JsonArray();
        audit.report.add("layouts", layouts); audit.report.add("bookmarks", bookmarks);
        var representativeArcane = new LinkedHashMap<String, RecipeHolder<IArcaneRecipe>>();
        for (var holder : arcane) representativeArcane.putIfAbsent(holder.value() instanceof ShapelessArcaneRecipe ? "shapeless" : "shaped", holder);
        for (var holder : representativeArcane.values()) {
            int crystals = (int) holder.value().crystals().stream().filter(count -> count > 0).count();
            layout(runtime, ThaumcraftJeiPlugin.ARCANE, holder, holder.value().getIngredients().size() + crystals, 1, audit, layouts);
            bookmark(runtime, ThaumcraftJeiPlugin.ARCANE, holder, audit, bookmarks);
        }
        if (!crucible.isEmpty()) {
            layout(runtime, ThaumcraftJeiPlugin.CRUCIBLE, crucible.getFirst(), 1, 1, audit, layouts);
            bookmark(runtime, ThaumcraftJeiPlugin.CRUCIBLE, crucible.getFirst(), audit, bookmarks);
        }
        if (!hedge.isEmpty()) {
            layout(runtime, ThaumcraftJeiPlugin.CRUCIBLE, hedge.getFirst(), 1, 1, audit, layouts);
            bookmark(runtime, ThaumcraftJeiPlugin.CRUCIBLE, hedge.getFirst(), audit, bookmarks);
        }
        for (var holder : crucible) if (JeiRecipeIdentity.sourceId(holder).equals(ResourceLocation.fromNamespaceAndPath("thaumcraft", "jei_review_api"))) {
            layout(runtime, ThaumcraftJeiPlugin.CRUCIBLE, holder, 1, 1, audit, layouts);
            bookmark(runtime, ThaumcraftJeiPlugin.CRUCIBLE, holder, audit, bookmarks);
        }
        var representativeInfusion = new LinkedHashMap<String, RecipeHolder<InfusionRecipe>>();
        for (var holder : infusion) {
            String key = holder.value() instanceof InfusionDisplayRecipe display
                ? display.kind() + "/" + JeiRecipeIdentity.sourceId(holder) : "fixed";
            representativeInfusion.putIfAbsent(key, holder);
        }
        for (var holder : representativeInfusion.values()) {
            layout(runtime, ThaumcraftJeiPlugin.INFUSION, holder, holder.value().getComponents().size() + 1, 1, audit, layouts);
            if (holder.value() instanceof InfusionDisplayRecipe display) focusedInfusionLayout(runtime, holder, display, audit, layouts);
            bookmark(runtime, ThaumcraftJeiPlugin.INFUSION, holder, audit, bookmarks);
        }
        if (!combinations.isEmpty()) layout(runtime, AspectJeiRecipes.COMBINATIONS, combinations.getFirst(), 2, 1, audit, layouts);
        itemSamples(runtime, aspects, contents, audit, layouts);
        tripleMeat(runtime, audit, layouts);
    }

    private static <R extends Recipe<?>> List<RecipeHolder<R>> current(IJeiRuntime runtime,
        RecipeType<RecipeHolder<R>> type, boolean includeHidden) {
        var lookup = runtime.getRecipeManager().createRecipeLookup(type);
        if (includeHidden) lookup.includeHidden();
        return lookup.get().filter(holder -> JeiRecipeIdentity.isCurrent(type, holder)).toList();
    }

    private static void apiFixture(IJeiRuntime runtime, List<RecipeHolder<CrucibleRecipe>> current, Audit audit) {
        var id = ResourceLocation.fromNamespaceAndPath("thaumcraft", "jei_review_api");
        var accepted = ClientJeiRecipeSnapshot.crucible().stream().filter(holder -> holder.id().equals(id)).findFirst();
        var displayed = current.stream().filter(holder -> JeiRecipeIdentity.sourceId(holder).equals(id)).findFirst();
        var row = new JsonObject(); audit.report.add("apiFixture", row);
        row.addProperty("sourceId", id.toString()); row.addProperty("acceptedPresent", accepted.isPresent());
        row.addProperty("jeiPresent", displayed.isPresent());
        audit.check(accepted.isPresent() == displayed.isPresent(), "API fixture presence must match the accepted connection snapshot");
        var previousToken = BOOKMARKS.get(ThaumcraftJeiPlugin.CRUCIBLE.getUid() + "/" + id);
        if (accepted.isEmpty() && previousToken != null) {
            var codec = runtime.getRecipeManager().getRecipeCategory(ThaumcraftJeiPlugin.CRUCIBLE)
                .getCodec(runtime.getJeiHelpers().getCodecHelper(), runtime.getRecipeManager());
            boolean rejected = codec.parse(JsonOps.INSTANCE, previousToken).result().isEmpty();
            row.addProperty("removedBookmarkRejected", rejected);
            audit.check(rejected, "A removed API fixture bookmark must not resolve a stale hidden holder");
        }
        for (var item : List.of(Items.DIAMOND, Items.EMERALD)) {
            String name = BuiltInRegistries.ITEM.getKey(item).getPath();
            var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, new ItemStack(item));
            long matches = runtime.getRecipeManager().createRecipeLookup(ThaumcraftJeiPlugin.CRUCIBLE).includeHidden()
                .limitFocus(List.of(focus)).get().filter(holder -> JeiRecipeIdentity.isCurrent(ThaumcraftJeiPlugin.CRUCIBLE, holder))
                .filter(holder -> JeiRecipeIdentity.sourceId(holder).equals(id)).count();
            boolean expected = accepted.isPresent() && accepted.get().value().getRecipeOutput().is(item);
            row.addProperty(name + "CurrentFocusCountIncludingHidden", matches);
            audit.check(matches == (expected ? 1 : 0), "API fixture output focus must update after replacement/removal: " + name);
        }
        if (accepted.isEmpty() || displayed.isEmpty()) return;
        var expected = accepted.get().value(); var actual = displayed.get().value();
        row.addProperty("acceptedOutput", BuiltInRegistries.ITEM.getKey(expected.getRecipeOutput().getItem()).toString());
        row.addProperty("jeiOutput", BuiltInRegistries.ITEM.getKey(actual.getRecipeOutput().getItem()).toString());
        row.addProperty("acceptedAirAmount", expected.getAspects().getAmount(thaumcraft.api.aspects.Aspect.AIR));
        row.addProperty("jeiAirAmount", actual.getAspects().getAmount(thaumcraft.api.aspects.Aspect.AIR));
        audit.check(ItemStack.isSameItemSameComponents(expected.getRecipeOutput(), actual.getRecipeOutput())
            && expected.getRecipeOutput().getCount() == actual.getRecipeOutput().getCount(), "API fixture output must preserve exact received data");
        audit.check(expected.getAspects().equals(actual.getAspects()), "API fixture amounts must update after mutable source setters");
        var category = runtime.getRecipeManager().getRecipeCategory(ThaumcraftJeiPlugin.CRUCIBLE);
        var ingredients = runtime.getRecipeManager().getRecipeIngredients(category, displayed.get());
        var outputs = ingredients.getIngredients(RecipeIngredientRole.OUTPUT).stream()
            .flatMap(ingredient -> ingredient.getIngredient(VanillaTypes.ITEM_STACK).stream()).toList();
        audit.check(outputs.size() == 1 && ItemStack.isSameItemSameComponents(outputs.getFirst(), expected.getRecipeOutput()),
            "Actual JEI fixture output ingredient must match the replacement");
        var amounts = new LinkedHashMap<String, Integer>();
        for (var typed : ingredients.getIngredients(RecipeIngredientRole.INPUT))
            typed.getIngredient(AspectIngredientHelper.TYPE).ifPresent(aspect -> amounts.merge(aspect.tag(), aspect.amount(), Integer::sum));
        var expectedAmounts = new LinkedHashMap<String, Integer>();
        for (var aspect : expected.getAspects().getAspects()) expectedAmounts.put(aspect.getTag(), expected.getAspects().getAmount(aspect));
        row.add("indexedAmounts", new GsonBuilder().create().toJsonTree(amounts));
        audit.check(amounts.equals(expectedAmounts), "Actual JEI fixture aspect index must match mutable source quantities");
    }

    private static <R extends Recipe<?>> JsonObject station(IJeiRuntime runtime, RecipeType<RecipeHolder<R>> type,
        List<RecipeHolder<R>> all, Function<RecipeHolder<R>, String> research, Function<RecipeHolder<R>, Boolean> known,
        boolean hide, Audit audit) {
        var visible = current(runtime, type, false); Set<RecipeHolder<R>> shown = Collections.newSetFromMap(new IdentityHashMap<>());
        shown.addAll(visible); int expected = 0;
        for (var holder : all) {
            String requirement = research.apply(holder);
            boolean shouldShow = !hide || requirement == null || requirement.isEmpty() || known.apply(holder);
            if (shouldShow) expected++;
            audit.check(shown.contains(holder) == shouldShow, "Visibility mismatch for " + type.getUid() + "/" + JeiRecipeIdentity.logicalId(holder));
        }
        var row = new JsonObject(); row.addProperty("currentIncludingHidden", all.size()); row.addProperty("visible", visible.size());
        row.addProperty("expectedVisible", expected); row.addProperty("distinctSourceIds", sourceIds(all).size());
        row.addProperty("allGenerationsIncludingHidden", runtime.getRecipeManager().createRecipeLookup(type).includeHidden().get().count());
        return row;
    }

    private static void itemSamples(IJeiRuntime runtime, ClientAspectSnapshot.Snapshot aspects, List<ItemAspectRecipe> contents,
        Audit audit, JsonArray layouts) {
        JsonArray samples = new JsonArray(); audit.report.add("itemAspectSamples", samples);
        var chosen = new LinkedHashMap<String, ItemAspectRecipe>();
        for (var recipe : contents) {
            String item = BuiltInRegistries.ITEM.getKey(recipe.stack().getItem()).toString();
            if (chosen.size() < 10 || item.equals("thaumcraft:crystal_essence") || item.equals("thaumcraft:phial"))
                chosen.putIfAbsent(item, recipe);
        }
        Set<ItemAspectRecipe> current = Collections.newSetFromMap(new IdentityHashMap<>()); current.addAll(contents);
        for (var recipe : chosen.values()) {
            var row = new JsonObject(); samples.add(row);
            var stack = recipe.stack(); row.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            var received = aspects.resolve(stack);
            var expected = received == null ? List.<JeiAspectIngredient>of() : JeiAspectIngredient.fromAmounts(received.aspects());
            var amounts = new JsonObject(); for (var aspect : recipe.aspects()) amounts.addProperty(aspect.tag(), aspect.amount());
            row.add("amounts", amounts); audit.check(recipe.aspects().equals(expected), "Aspect quantities mismatch for " + row.get("item"));
            layout(runtime, AspectJeiRecipes.CONTENTS, recipe, 1, recipe.aspects().size(), audit, layouts);
            var category = runtime.getRecipeManager().getRecipeCategory(AspectJeiRecipes.CONTENTS);
            var drawable = runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
            if (drawable.isPresent()) {
                var outputs = drawable.get().getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                    .flatMap(slot -> slot.getIngredients(AspectIngredientHelper.TYPE)).sorted(java.util.Comparator.comparing(JeiAspectIngredient::tag)).toList();
                audit.check(outputs.equals(expected), "Actual JEI output slots must contain exact quantities for " + row.get("item"));
            }
            var focusFactory = runtime.getJeiHelpers().getFocusFactory();
            var itemFocus = focusFactory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);
            var usages = runtime.getRecipeManager().createRecipeLookup(AspectJeiRecipes.CONTENTS).includeHidden()
                .limitFocus(List.of(itemFocus)).get().filter(current::contains).toList();
            row.addProperty("itemUsageFocusCountIncludingHidden", usages.size());
            audit.check(usages.contains(recipe), "Item usage focus must find its composition: " + row.get("item"));
            if (!recipe.aspects().isEmpty()) {
                var aspect = recipe.aspects().getFirst();
                var aspectFocus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, AspectIngredientHelper.TYPE, aspect.normalized());
                var sources = runtime.getRecipeManager().createRecipeLookup(AspectJeiRecipes.CONTENTS).includeHidden()
                    .limitFocus(List.of(aspectFocus)).get().filter(current::contains).toList();
                row.addProperty("reverseAspect", aspect.tag()); row.addProperty("aspectRecipeFocusCountIncludingHidden", sources.size());
                audit.check(sources.contains(recipe), "Aspect recipe focus must find its source item: " + row.get("item"));
                var usageFocus = focusFactory.createFocus(RecipeIngredientRole.INPUT, AspectIngredientHelper.TYPE, aspect.normalized());
                row.addProperty("arcaneAspectUsageCountIncludingHidden", focused(runtime, ThaumcraftJeiPlugin.ARCANE, usageFocus));
                row.addProperty("crucibleAspectUsageCountIncludingHidden", focused(runtime, ThaumcraftJeiPlugin.CRUCIBLE, usageFocus));
                row.addProperty("infusionAspectUsageCountIncludingHidden", focused(runtime, ThaumcraftJeiPlugin.INFUSION, usageFocus));
                long combinations = runtime.getRecipeManager().createRecipeLookup(AspectJeiRecipes.COMBINATIONS).includeHidden()
                    .limitFocus(List.of(usageFocus)).get().count();
                long expectedCombinations = AspectJeiRecipes.combinations().stream()
                    .filter(combination -> combination.first().equals(aspect.tag()) || combination.second().equals(aspect.tag())).count();
                row.addProperty("combinationUsageFocusCountIncludingHidden", combinations);
                audit.check(combinations == expectedCombinations, "Aspect usage focus must find its true combinations: " + aspect.tag());
                var helper = runtime.getIngredientManager().getIngredientHelper(AspectIngredientHelper.TYPE);
                audit.check(helper.getUid(aspect, UidContext.Recipe).equals(helper.getUid(aspect.normalized(), UidContext.Recipe)),
                    "Counted aspect lookup must ignore quantity: " + aspect.tag());
            }
        }
    }

    private static <R extends Recipe<?>> long focused(IJeiRuntime runtime, RecipeType<RecipeHolder<R>> type, IFocus<?> focus) {
        return runtime.getRecipeManager().createRecipeLookup(type).includeHidden().limitFocus(List.of(focus)).get()
            .filter(holder -> JeiRecipeIdentity.isCurrent(type, holder)).count();
    }

    private static void tripleMeat(IJeiRuntime runtime, Audit audit, JsonArray layouts) {
        var manager = runtime.getRecipeManager();
        var all = manager.createRecipeLookup(RecipeTypes.CRAFTING).includeHidden().get()
            .filter(TripleMeatJeiRecipes::isProjection).toList();
        var current = all.stream().filter(TripleMeatJeiRecipes::isCurrent).toList();
        var visible = manager.createRecipeLookup(RecipeTypes.CRAFTING).get().filter(TripleMeatJeiRecipes::isCurrent).toList();
        var sources = TripleMeatJeiRecipes.nativeSources();
        var row = new JsonObject(); audit.report.add("tripleMeat", row);
        row.addProperty("nativeSourceCount", sources.size()); row.addProperty("expectedCurrentProjections", sources.size() * 20);
        row.addProperty("currentProjections", current.size()); row.addProperty("visibleProjections", visible.size());
        row.addProperty("allGenerationsIncludingHidden", all.size());
        audit.check(current.size() == sources.size() * 20 && visible.size() == current.size(),
            "Every native triple meat source must expose its 20 valid distinct-meat crafting choices");
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var category = manager.getRecipeCategory(RecipeTypes.CRAFTING);
        var codec = category.getCodec(runtime.getJeiHelpers().getCodecHelper(), manager);
        int validated = 0, restored = 0;
        for (var holder : current) {
            var stacks = holder.value().getIngredients().stream().flatMap(ingredient -> java.util.Arrays.stream(ingredient.getItems())).toList();
            audit.check(holder.value().getIngredients().size() == 4 && stacks.size() == 4,
                "Triple meat projections must have four single-choice input slots");
            if (stacks.size() != 4) continue;
            var input = CraftingInput.of(2, 2, stacks);
            var source = sources.stream().filter(candidate -> candidate.value().matches(input, level)).findFirst();
            audit.check(source.isPresent(), "A displayed triple meat tuple must pass the actual native callback");
            if (source.isEmpty()) continue;
            var expected = source.get().value().assemble(input, level.registryAccess());
            var output = holder.value().getResultItem(level.registryAccess());
            audit.check(ItemStack.isSameItemSameComponents(expected, output) && expected.getCount() == output.getCount(),
                "Triple meat display output must be assembled by its actual native source");
            // JEI's vanilla crafting helper always creates all nine grid slots, including
            // its five EMPTY cells; the four filled slots each have exactly one choice.
            var drawable = layout(runtime, RecipeTypes.CRAFTING, holder, 9, 1, audit, layouts);
            if (drawable != null) {
                var filled = drawable.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT).stream().filter(slot -> !slot.isEmpty()).toList();
                audit.check(filled.size() == 4 && filled.stream().allMatch(slot -> slot.getIngredients(VanillaTypes.ITEM_STACK).count() == 1),
                    "Native JEI triple meat layouts must show four exact inputs and five empty grid cells");
            }
            var decoded = codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, holder).getOrThrow()).getOrThrow();
            var restoredOutput = decoded.value().getResultItem(level.registryAccess());
            audit.check(decoded.id().equals(holder.id()) && sameCraftingChoices(decoded.value(), holder.value())
                && ItemStack.isSameItemSameComponents(restoredOutput, output) && restoredOutput.getCount() == output.getCount(),
                "Vanilla bookmark codec must restore an exact standalone triple meat display");
            validated++; restored++;
        }
        row.addProperty("sourceValidatedTuples", validated); row.addProperty("bookmarkRoundtrips", restored);
        if (!current.isEmpty()) {
            var factory = runtime.getJeiHelpers().getFocusFactory();
            var output = current.getFirst().value().getResultItem(level.registryAccess());
            var focus = factory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, output);
            long matches = manager.createRecipeLookup(RecipeTypes.CRAFTING).includeHidden().limitFocus(List.of(focus)).get()
                .filter(TripleMeatJeiRecipes::isCurrent).count();
            row.addProperty("outputFocusCountIncludingHidden", matches);
            audit.check(matches == current.size(), "Triple meat OUTPUT focus must find every actual current tuple");
        }
    }

    private static boolean sameCraftingChoices(CraftingRecipe first, CraftingRecipe second) {
        var left = first.getIngredients(); var right = second.getIngredients();
        if (left.size() != right.size()) return false;
        for (int slot = 0; slot < left.size(); slot++) {
            var leftItems = left.get(slot).getItems(); var rightItems = right.get(slot).getItems();
            if (leftItems.length != rightItems.length) return false;
            for (int choice = 0; choice < leftItems.length; choice++)
                if (!ItemStack.isSameItemSameComponents(leftItems[choice], rightItems[choice])
                    || leftItems[choice].getCount() != rightItems[choice].getCount()) return false;
        }
        return true;
    }

    private static void focusedInfusionLayout(IJeiRuntime runtime, RecipeHolder<InfusionRecipe> holder,
        InfusionDisplayRecipe display, Audit audit, JsonArray rows) {
        // A real player variant need not be one of the indexed display candidates. Exercise
        // the input focus path with a local named copy, without changing player inventory.
        var input = display.firstInput(); input.set(DataComponents.CUSTOM_NAME, Component.literal("JEI audit focus"));
        var factory = runtime.getJeiHelpers().getFocusFactory();
        var focus = factory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, input);
        var focuses = factory.createFocusGroup(List.of(focus));
        int components = display.source() instanceof InfusionRunicAugmentRecipe runic
            ? runic.getComponents(input).size() : display.source().getComponents().size();
        var drawable = layout(runtime, ThaumcraftJeiPlugin.INFUSION, holder, components + 1, 1, focuses,
            "actual-input-variant", audit, rows);
        if (drawable == null) return;
        var expected = InfusionDisplayRecipes.focusedOutput(display, input, Minecraft.getInstance().level.registryAccess());
        var slots = drawable.getRecipeSlotsView();
        var central = slots.getSlotViews(RecipeIngredientRole.INPUT).stream()
            .filter(slot -> slot.getSlotName().orElse("").equals("central"))
            .flatMap(slot -> slot.getIngredients(VanillaTypes.ITEM_STACK)).toList();
        var output = slots.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
            .flatMap(slot -> slot.getIngredients(VanillaTypes.ITEM_STACK)).toList();
        audit.check(central.size() == 1 && ItemStack.isSameItemSameComponents(central.getFirst(), input),
            "Focused central slot must preserve the actual input variant: " + display.sourceId());
        audit.check(!expected.isEmpty() && output.size() == 1
            && ItemStack.isSameItemSameComponents(output.getFirst(), expected) && output.getFirst().getCount() == expected.getCount(),
            "Focused output must preserve the actual source upgrade and components: " + display.sourceId());
    }

    private static <T> IRecipeLayoutDrawable<T> layout(IJeiRuntime runtime, RecipeType<T> type, T recipe,
        int expectedInputs, int expectedOutputs,
        Audit audit, JsonArray rows) {
        return layout(runtime, type, recipe, expectedInputs, expectedOutputs,
            runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup(), "none", audit, rows);
    }

    private static <T> IRecipeLayoutDrawable<T> layout(IJeiRuntime runtime, RecipeType<T> type, T recipe,
        int expectedInputs, int expectedOutputs, IFocusGroup focuses, String focusMode, Audit audit, JsonArray rows) {
        var row = new JsonObject(); rows.add(row); row.addProperty("type", type.getUid().toString());
        row.addProperty("focusMode", focusMode);
        if (recipe instanceof RecipeHolder<?> holder) row.addProperty("sourceId", JeiRecipeIdentity.sourceId(holder).toString());
        try {
            var category = runtime.getRecipeManager().getRecipeCategory(type);
            var drawable = runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe, focuses);
            row.addProperty("created", drawable.isPresent()); audit.check(drawable.isPresent(), "Layout creation failed for " + row);
            if (drawable.isEmpty()) return null;
            // JEI 19.57 lazily creates extras on tick. This exercises native scroll grids
            // and text widgets too. Invisible lookup ingredients are not drawable slots;
            // shaped EMPTY holes and scroll-grid slots remain in getRecipeSlotsView.
            drawable.get().tick(); row.addProperty("extrasCreatedAndTicked", true);
            var slots = drawable.get().getRecipeSlotsView();
            int inputs = slots.getSlotViews(RecipeIngredientRole.INPUT).size(), outputs = slots.getSlotViews(RecipeIngredientRole.OUTPUT).size();
            row.addProperty("inputSlots", inputs); row.addProperty("outputSlots", outputs);
            row.addProperty("expectedInputSlots", expectedInputs); row.addProperty("expectedOutputSlots", expectedOutputs);
            row.addProperty("width", drawable.get().getRect().getWidth()); row.addProperty("height", drawable.get().getRect().getHeight());
            audit.check(inputs == expectedInputs && outputs == expectedOutputs, "Layout slot count mismatch for " + row);
            audit.check(slots.getSlotViews(RecipeIngredientRole.OUTPUT).stream().noneMatch(IRecipeSlotView::isEmpty), "Empty layout output for " + row);
            return drawable.get();
        } catch (RuntimeException | LinkageError failure) { row.addProperty("error", failure.toString()); audit.fail("Layout exception: " + row); }
        return null;
    }

    private static <R extends Recipe<?>> void bookmark(IJeiRuntime runtime, RecipeType<RecipeHolder<R>> type,
        RecipeHolder<R> holder, Audit audit, JsonArray rows) {
        var row = new JsonObject(); rows.add(row); row.addProperty("type", type.getUid().toString());
        row.addProperty("sourceId", JeiRecipeIdentity.sourceId(holder).toString());
        try {
            var manager = runtime.getRecipeManager(); var category = manager.getRecipeCategory(type);
            var codec = category.getCodec(runtime.getJeiHelpers().getCodecHelper(), manager);
            String key = type.getUid() + "/" + JeiRecipeIdentity.logicalId(holder);
            var token = codec.encodeStart(JsonOps.INSTANCE, holder).getOrThrow();
            var decoded = codec.parse(JsonOps.INSTANCE, token).getOrThrow();
            boolean current = decoded == holder && JeiRecipeIdentity.isCurrent(type, decoded);
            row.add("serialized", token); row.addProperty("roundtripCurrentHolder", current);
            audit.check(current, "Bookmark must resolve the current holder: " + key);
            var previous = BOOKMARKS.get(key);
            if (previous != null) {
                var restored = codec.parse(JsonOps.INSTANCE, previous).getOrThrow();
                boolean restoredCurrent = restored == holder && JeiRecipeIdentity.isCurrent(type, restored);
                row.addProperty("previousTokenResolvesCurrentHolder", restoredCurrent);
                audit.check(restoredCurrent, "Previous-generation bookmark must resolve current holder: " + key);
            }
            BOOKMARKS.put(key, token.deepCopy());
        } catch (RuntimeException | LinkageError failure) { row.addProperty("error", failure.toString()); audit.fail("Bookmark exception: " + row); }
    }

    private static <R extends Recipe<?>> void checkIds(String kind, List<RecipeHolder<R>> actual,
        List<RecipeHolder<R>> expected, Audit audit) {
        audit.check(sourceIds(actual).equals(ids(expected)), "Accepted " + kind + " source IDs must match JEI exactly");
        audit.report.add(kind + "SourceIds", strings(sourceIds(actual)));
        audit.report.addProperty("accepted" + Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + "Sources", expected.size());
    }
    private static Set<String> sourceIds(List<? extends RecipeHolder<?>> recipes) {
        Set<String> ids = new TreeSet<>(); for (var holder : recipes) ids.add(JeiRecipeIdentity.sourceId(holder).toString()); return ids;
    }
    private static Set<String> ids(List<? extends RecipeHolder<?>> recipes) {
        Set<String> ids = new TreeSet<>(); for (var holder : recipes) ids.add(holder.id().toString()); return ids;
    }
    private static JsonArray required(String[] names, Set<String> sources, Audit audit) {
        var result = new JsonArray();
        for (String name : names) {
            String source = "thaumcraft:" + name; var row = new JsonObject(); row.addProperty("sourceId", source);
            boolean present = sources.contains(source); row.addProperty("represented", present); result.add(row);
            audit.check(present, "Original dynamic infusion source must be represented: " + source);
        }
        return result;
    }
    private static JsonArray strings(Iterable<String> values) { var array = new JsonArray(); for (String value : values) array.add(value); return array; }
    private static void write(Path path, JsonObject report) throws IOException {
        Path parent = path.getParent(); if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static final class Audit {
        private final JsonObject report = new JsonObject();
        private final JsonArray failures = new JsonArray();
        private void check(boolean condition, String reason) { if (!condition) fail(reason); }
        private void fail(String reason) { failures.add(reason); }
    }
}
