package thaumcraft.client.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.client.lib.ClientAspectSnapshot;
import thaumcraft.client.lib.ClientJeiLifecycle;
import thaumcraft.client.lib.ClientJeiRecipeSnapshot;
import thaumcraft.client.lib.ClientResearchPresentation;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchRecipes;
import thaumcraft.common.lib.crafting.InfusionEnchantmentRecipe;
import thaumcraft.common.lib.crafting.RecipeArcaneWorkbench;
import thaumcraft.common.lib.research.catalog.ResearchClientView;

/** MAIN-thread catalog replacement and owner visibility, driven by accepted connection data. */
final class ThaumcraftJeiRuntime {
    private IJeiRuntime runtime;
    private Object connection;
    private long recipesRevision = -1, aspectsRevision = -1;
    private int ingredientCount = -1;
    private List<RecipeHolder<IArcaneRecipe>> nativeArcane = List.of();
    private List<RecipeHolder<IArcaneRecipe>> arcane = List.of();
    private List<RecipeHolder<CrucibleRecipe>> crucible = List.of();
    private List<RecipeHolder<InfusionRecipe>> infusion = List.of();
    private List<ItemAspectRecipe> contents = List.of();
    private ResearchClientView.Snapshot knowledge;
    private boolean hideUnknown;
    private boolean visibilityDirty = true;

    void register(IRecipeRegistration registration) {
        rebuild(new ArrayList<>(registration.getIngredientManager().getAllItemStacks()));
        registration.addRecipes(ThaumcraftJeiPlugin.ARCANE, arcane);
        registration.addRecipes(ThaumcraftJeiPlugin.CRUCIBLE, crucible);
        registration.addRecipes(ThaumcraftJeiPlugin.INFUSION, infusion);
        registration.addRecipes(AspectJeiRecipes.COMBINATIONS, AspectJeiRecipes.combinations());
        registration.addRecipes(AspectJeiRecipes.CONTENTS, contents);
    }
    void available(IJeiRuntime runtime) {
        this.runtime = runtime; visibilityDirty = true; ClientJeiLifecycle.install(this::tick); tick();
    }
    void unavailable() {
        ClientJeiLifecycle.clear(); runtime = null; connection = null;
        nativeArcane = List.of(); arcane = List.of(); crucible = List.of(); infusion = List.of(); contents = List.of();
        knowledge = null; recipesRevision = aspectsRevision = -1; ingredientCount = -1;
        TripleMeatJeiRecipes.clear();
        JeiRecipeIdentity.clear(); visibilityDirty = true;
    }
    private List<RecipeHolder<IArcaneRecipe>> actualArcane() {
        var level = Minecraft.getInstance().level;
        return level == null ? List.of() : level.getRecipeManager().getAllRecipesFor(ArcaneWorkbenchRecipes.TYPE).stream()
            .filter(holder -> holder.value() instanceof RecipeArcaneWorkbench || holder.value() instanceof ShapelessArcaneRecipe)
            .filter(holder -> !holder.value().getResultItem(level.registryAccess()).isEmpty()).toList();
    }
    private boolean sameNative(List<RecipeHolder<IArcaneRecipe>> next) {
        if (next.size() != nativeArcane.size()) return false;
        for (int i = 0; i < next.size(); i++) if (!next.get(i).id().equals(nativeArcane.get(i).id())
            || next.get(i).value() != nativeArcane.get(i).value()) return false;
        return true;
    }
    private void rebuild(List<ItemStack> candidates) {
        var minecraft = Minecraft.getInstance(); var listener = minecraft.getConnection();
        connection = listener == null ? null : listener.getConnection();
        recipesRevision = ClientJeiRecipeSnapshot.revision();
        var aspects = ClientAspectSnapshot.view(); aspectsRevision = aspects == null ? 0 : aspects.revision();
        ingredientCount = candidates.size(); nativeArcane = actualArcane();
        long generation = JeiRecipeIdentity.nextGeneration();
        arcane = identify(ThaumcraftJeiPlugin.ARCANE, nativeArcane, RecipeHolder::id, generation);
        crucible = identify(ThaumcraftJeiPlugin.CRUCIBLE, ClientJeiRecipeSnapshot.crucible(), RecipeHolder::id, generation);
        var displayed = minecraft.level == null ? List.<RecipeHolder<InfusionRecipe>>of()
            : InfusionDisplayRecipes.create(ClientJeiRecipeSnapshot.infusion(), candidates, minecraft.level.registryAccess());
        infusion = identify(ThaumcraftJeiPlugin.INFUSION, displayed, InfusionDisplayRecipes::sourceId, generation);
        contents = AspectJeiRecipes.contents(candidates); visibilityDirty = true;
    }
    private static <R extends Recipe<?>> List<RecipeHolder<R>> identify(RecipeType<RecipeHolder<R>> type,
        List<RecipeHolder<R>> recipes, Function<RecipeHolder<R>, net.minecraft.resources.ResourceLocation> source, long generation) {
        JeiRecipeIdentity.begin(type);
        return recipes.stream().map(holder -> JeiRecipeIdentity.attach(type, holder, source.apply(holder), generation)).toList();
    }
    private void tick() {
        if (runtime == null) return;
        var minecraft = Minecraft.getInstance(); var listener = minecraft.getConnection();
        var actualConnection = listener == null ? null : listener.getConnection();
        var nextArcane = actualArcane(); var aspects = ClientAspectSnapshot.view();
        long nextAspects = aspects == null ? 0 : aspects.revision();
        var candidates = runtime.getIngredientManager().getAllItemStacks();
        if (connection != actualConnection || recipesRevision != ClientJeiRecipeSnapshot.revision()
            || aspectsRevision != nextAspects || ingredientCount != candidates.size() || !sameNative(nextArcane)) {
            var manager = runtime.getRecipeManager();
            manager.hideRecipes(ThaumcraftJeiPlugin.ARCANE, arcane);
            manager.hideRecipes(ThaumcraftJeiPlugin.CRUCIBLE, crucible);
            manager.hideRecipes(ThaumcraftJeiPlugin.INFUSION, infusion);
            manager.hideRecipes(AspectJeiRecipes.CONTENTS, contents);
            rebuild(new ArrayList<>(candidates));
            manager.addRecipes(ThaumcraftJeiPlugin.ARCANE, arcane);
            manager.addRecipes(ThaumcraftJeiPlugin.CRUCIBLE, crucible);
            manager.addRecipes(ThaumcraftJeiPlugin.INFUSION, infusion);
            manager.addRecipes(AspectJeiRecipes.CONTENTS, contents);
        }
        var nextKnowledge = ClientResearchPresentation.view();
        boolean nextHide = ModConfig.CONFIG_JEI.hideRecipesIfMissingResearch;
        if (visibilityDirty || knowledge != nextKnowledge || hideUnknown != nextHide) {
            knowledge = nextKnowledge; hideUnknown = nextHide;
            visibility(ThaumcraftJeiPlugin.ARCANE, arcane, holder -> JeiResearchVisibility.Rule.KNOWN_LITERAL, holder -> holder.value().research());
            visibility(ThaumcraftJeiPlugin.CRUCIBLE, crucible, holder -> JeiResearchVisibility.Rule.STRICT, holder -> holder.value().getResearch());
            visibility(ThaumcraftJeiPlugin.INFUSION, infusion, holder -> {
                var source = holder.value() instanceof InfusionDisplayRecipe display ? display.source() : holder.value();
                return source instanceof InfusionEnchantmentRecipe ? JeiResearchVisibility.Rule.KNOWN : JeiResearchVisibility.Rule.KNOWN_LITERAL;
            }, holder -> holder.value().getResearch());
            visibilityDirty = false;
        }
        TripleMeatJeiRecipes.tick(runtime);
        if (System.getProperty("thaumcraft.jeiAuditReport") != null) JeiClientAudit.tick(runtime);
        if (Boolean.getBoolean("thaumcraft.jeiReview")) {
            JeiClientAuditScreens.tick(runtime);
            JeiTransferClientAudit.tick(runtime);
        }
    }
    private <R extends Recipe<?>> void visibility(RecipeType<RecipeHolder<R>> type, List<RecipeHolder<R>> recipes,
        Function<RecipeHolder<R>, JeiResearchVisibility.Rule> rule, Function<RecipeHolder<R>, String> research) {
        List<RecipeHolder<R>> visible = new ArrayList<>(), hidden = new ArrayList<>();
        for (var holder : recipes) {
            boolean shown = JeiResearchVisibility.visible(hideUnknown, research.apply(holder), rule.apply(holder),
                knowledge == null ? null : knowledge::knowsStrict,
                knowledge == null ? null : knowledge::knows,
                knowledge == null ? null : knowledge::knowsLiteral);
            (shown ? visible : hidden).add(holder);
        }
        runtime.getRecipeManager().hideRecipes(type, hidden);
        runtime.getRecipeManager().unhideRecipes(type, visible);
    }
}
