package thaumcraft.common.lib.research.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.research.ResearchAddendum;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ArrayField;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Diagnostic;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.IconToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.KnowledgeToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.LegacyResourceId;

/**
 * Source-shaped metadata adapters, not a requirement evaluator. Registry-dependent descriptions
 * remain explicit immutable tokens. In particular, a nonempty obtain/craft requirement never
 * becomes an empty ItemStack, and no MC1.21 ItemStack hash is substituted for TC6's hash.
 * Future progression/rendering code must consult deferredDependencies instead of assuming that
 * these API-shaped mutable objects have resolved item, recipe or focus semantics.
 */
public final class PreparedResearchMetadata {
    private PreparedResearchMetadata() {}

    public enum DependencyKind { ITEM, CRAFT_ITEM, CRAFT_REFERENCE, REWARD_ITEM, ICON, FOCUS_ICON, RECIPE_ID, RECIPE_LOOKUP }

    /** Token is an immutable Spec record (or a list of such records), never a registry object. */
    public record DeferredDependency(DependencyKind kind, String path, Object token, String reason) {}

    /** Positional descriptor, including invalid IDs; resourceLocation is null only if blocked. */
    public record RecipeBinding(LegacyResourceId token, ResourceLocation resourceLocation) {
        public boolean blocked() { return resourceLocation == null; }
    }

    public static final class Entry extends ResearchEntry {
        private final ResearchCatalogSpec.Entry rawSpec;
        private final List<DeferredDependency> dependencies;

        private Entry(ResearchCatalogSpec.Entry rawSpec, List<DeferredDependency> dependencies) {
            this.rawSpec = rawSpec;
            this.dependencies = List.copyOf(dependencies);
        }
        public ResearchCatalogSpec.Entry rawSpec() { return rawSpec; }
        public List<ItemToken> deferredRewards() { return rawSpec.rewardItems().values(); }
        public List<DeferredDependency> deferredDependencies() { return dependencies; }
        public boolean hasUnresolved() { return !dependencies.isEmpty(); }
    }

    public static final class Stage extends ResearchStage {
        private final ResearchCatalogSpec.Stage rawSpec;
        private final List<DeferredDependency> dependencies;
        private final List<RecipeBinding> recipeBindings;

        private Stage(ResearchCatalogSpec.Stage rawSpec, List<DeferredDependency> dependencies, List<RecipeBinding> recipes) {
            this.rawSpec = rawSpec;
            this.dependencies = List.copyOf(dependencies);
            recipeBindings = List.copyOf(recipes);
        }
        public ResearchCatalogSpec.Stage rawSpec() { return rawSpec; }
        public List<DeferredDependency> deferredDependencies() { return dependencies; }
        public List<RecipeBinding> recipeBindings() { return recipeBindings; }
        public boolean hasUnresolved() { return !dependencies.isEmpty(); }
    }

    public static final class Addendum extends ResearchAddendum {
        private final ResearchCatalogSpec.Addendum rawSpec;
        private final List<DeferredDependency> dependencies;
        private final List<RecipeBinding> recipeBindings;

        private Addendum(ResearchCatalogSpec.Addendum rawSpec, List<DeferredDependency> dependencies, List<RecipeBinding> recipes) {
            this.rawSpec = rawSpec;
            this.dependencies = List.copyOf(dependencies);
            recipeBindings = List.copyOf(recipes);
        }
        public ResearchCatalogSpec.Addendum rawSpec() { return rawSpec; }
        public List<DeferredDependency> deferredDependencies() { return dependencies; }
        public List<RecipeBinding> recipeBindings() { return recipeBindings; }
        public boolean hasUnresolved() { return !dependencies.isEmpty(); }
    }

    static Entry prepare(ResearchCatalogSpec.Entry spec, Map<String, ResearchCategory> categories, List<Diagnostic> diagnostics) {
        List<DeferredDependency> dependencies = new ArrayList<>();
        Object[] icons = null;
        if (!spec.icons().values().isEmpty()) {
            icons = new Object[spec.icons().values().size()];
            for (int i = 0; i < icons.length; i++) {
                icons[i] = icon(spec.icons().values().get(i), spec.source(), spec.path() + ".icons[" + i + "]", dependencies, diagnostics);
            }
        }
        for (int i = 0; i < spec.rewardItems().values().size(); i++) {
            deferred(DependencyKind.REWARD_ITEM, spec.path() + ".reward_item[" + i + "]", spec.rewardItems().values().get(i),
                "Reward ItemStack resolution deferred; full reward token retained, not granted", spec.source(), dependencies, diagnostics);
        }
        ResearchStage[] stages = null;
        if (!spec.stages().values().isEmpty()) {
            stages = new ResearchStage[spec.stages().values().size()];
            for (int i = 0; i < stages.length; i++) {
                Stage stage = stage(spec.stages().values().get(i), categories, spec.source(), spec.path() + ".stages[" + i + "]", diagnostics);
                stages[i] = stage;
                dependencies.addAll(stage.deferredDependencies());
            }
        }
        ResearchAddendum[] addenda = null;
        if (!spec.addenda().values().isEmpty()) {
            addenda = new ResearchAddendum[spec.addenda().values().size()];
            for (int i = 0; i < addenda.length; i++) {
                Addendum addendum = addendum(spec.addenda().values().get(i), spec.source(), spec.path() + ".addenda[" + i + "]", diagnostics);
                addenda[i] = addendum;
                dependencies.addAll(addendum.deferredDependencies());
            }
        }
        Entry result = new Entry(spec, dependencies);
        result.setKey(spec.key());
        result.setCategory(spec.category());
        result.setName(spec.name());
        result.setDisplayColumn(spec.displayColumn());
        result.setDisplayRow(spec.displayRow());
        result.setParents(strings(spec.parents()));
        result.setSiblings(strings(spec.siblings()));
        result.setIcons(icons);
        result.setMeta(spec.meta().values().isEmpty() ? null : spec.meta().values().stream()
            .map(ResearchCatalogSpec.MetaToken::value).toArray(ResearchEntry.EnumResearchMeta[]::new));
        // Typed ItemStack[] cannot hold deferred tokens. rawSpec/deferredRewards retain EVERY reward.
        result.setRewardItem(null);
        result.setRewardKnow(knowledge(spec.rewardKnowledge(), categories, spec.source(), spec.path() + ".reward_knowledge"));
        result.setStages(stages);
        result.setAddenda(addenda);
        return result;
    }

    private static Stage stage(ResearchCatalogSpec.Stage spec, Map<String, ResearchCategory> categories, String source,
                               String path, List<Diagnostic> diagnostics) {
        List<DeferredDependency> dependencies = new ArrayList<>();
        List<RecipeBinding> recipes = recipes(spec.recipes(), source, path, dependencies, diagnostics);
        Object[] obtain = items(spec.requiredItems(), DependencyKind.ITEM, source, path + ".required_item", dependencies, diagnostics);
        Object[] craft = items(spec.requiredCraft(), DependencyKind.CRAFT_ITEM, source, path + ".required_craft", dependencies, diagnostics);
        if (craft != null) {
            deferred(DependencyKind.CRAFT_REFERENCE, path + ".required_craft", spec.requiredCraft().values(),
                "Legacy ItemStack/ore crafting reference index pending; no modern hash substituted", source, dependencies, diagnostics);
        }
        Stage result = new Stage(spec, dependencies, recipes);
        result.setText(spec.text());
        result.setRecipes(apiRecipes(recipes));
        result.setObtain(obtain);
        result.setCraft(craft);
        result.setCraftReference(null);
        result.setKnow(knowledge(spec.requiredKnowledge(), categories, source, path + ".required_knowledge"));
        if (!spec.requiredResearch().values().isEmpty()) {
            result.setResearch(spec.requiredResearch().values().stream().map(ResearchCatalogSpec.ResearchToken::key).toArray(String[]::new));
            result.setResearchIcon(spec.requiredResearch().values().stream().map(ResearchCatalogSpec.ResearchToken::icon).toArray(String[]::new));
        }
        result.setWarp(spec.warpPresent() ? spec.warp() : 0);
        return result;
    }

    private static Addendum addendum(ResearchCatalogSpec.Addendum spec, String source, String path, List<Diagnostic> diagnostics) {
        List<DeferredDependency> dependencies = new ArrayList<>();
        List<RecipeBinding> recipes = recipes(spec.recipes(), source, path, dependencies, diagnostics);
        Addendum result = new Addendum(spec, dependencies, recipes);
        result.setText(spec.text());
        result.setRecipes(apiRecipes(recipes));
        result.setResearch(strings(spec.requiredResearch()));
        return result;
    }

    private static Object[] items(ArrayField<ItemToken> field, DependencyKind kind, String source, String path,
                                  List<DeferredDependency> dependencies, List<Diagnostic> diagnostics) {
        if (field.values().isEmpty()) return null;
        Object[] result = field.values().toArray();
        for (int i = 0; i < result.length; i++) {
            ItemToken token = field.values().get(i);
            deferred(kind, path + "[" + i + "]", token, token.unresolvedReason(), source, dependencies, diagnostics);
        }
        return result;
    }

    private static ResearchStage.Knowledge[] knowledge(ArrayField<KnowledgeToken> field, Map<String, ResearchCategory> categories,
                                                       String source, String path) {
        if (field.values().isEmpty()) return null;
        ResearchStage.Knowledge[] result = new ResearchStage.Knowledge[field.values().size()];
        for (int i = 0; i < result.length; i++) {
            KnowledgeToken token = field.values().get(i);
            ResearchCategory category = token.category() == null ? null : categories.get(token.category());
            if (token.type() == null || token.amount() <= 0 || (token.type().hasFields() && category == null)
                || (token.category() != null && category == null)) {
                throw new ResearchCatalogPublication.PreparationException(source, path + "[" + i + "]", "Invalid/missing candidate knowledge category");
            }
            result[i] = new ResearchStage.Knowledge(token.type(), category, token.amount());
        }
        return result;
    }

    private static List<RecipeBinding> recipes(ArrayField<LegacyResourceId> field, String source, String path,
                                                List<DeferredDependency> dependencies, List<Diagnostic> diagnostics) {
        List<RecipeBinding> result = new ArrayList<>();
        for (int i = 0; i < field.values().size(); i++) {
            LegacyResourceId token = field.values().get(i);
            ResourceLocation id = modernId(token);
            result.add(new RecipeBinding(token, id));
            deferred(id == null ? DependencyKind.RECIPE_ID : DependencyKind.RECIPE_LOOKUP,
                path + ".recipes[" + i + "]", token,
                id == null ? "Invalid modern recipe identifier; positional descriptor retained and API recipe array blocked"
                    : "Recipe identifier normalized exactly; recipe-registry existence/semantics not resolved",
                source, dependencies, diagnostics);
        }
        return result;
    }

    /** Mixed valid/invalid arrays are blocked as a whole, never filtered or exposed with null slots. */
    private static ResourceLocation[] apiRecipes(List<RecipeBinding> recipes) {
        return recipes.isEmpty() || recipes.stream().anyMatch(RecipeBinding::blocked) ? null
            : recipes.stream().map(RecipeBinding::resourceLocation).toArray(ResourceLocation[]::new);
    }

    private static Object icon(IconToken token, String source, String path, List<DeferredDependency> dependencies, List<Diagnostic> diagnostics) {
        // "focus:" is TC6's explicit focus rendering protocol, not a registered item assertion.
        if (token.focusFallback() != null && token.raw().startsWith("focus:") && token.raw().length() > 6
            && !token.raw().contains(";")) {
            deferred(DependencyKind.FOCUS_ICON, path, token, "Focus rendering metadata retained; focus registry not resolved", source, dependencies, diagnostics);
            return token.focusFallback();
        }
        LegacyResourceId texture = token.resourceFallback();
        ResourceLocation id = texture == null ? null : modernId(texture);
        if (id != null && id.getPath().startsWith("textures/")
            && (id.getPath().endsWith(".png") || id.getPath().endsWith(".jpg") || id.getPath().endsWith(".jpeg"))
            && !token.raw().contains(";")) return id;
        deferred(DependencyKind.ICON, path, token, "Item-versus-texture registry-dependent icon retained explicitly unresolved", source, dependencies, diagnostics);
        return token;
    }

    private static ResourceLocation modernId(LegacyResourceId token) {
        return token.modernSyntax() ? ResourceLocation.tryParse(token.normalized()) : null;
    }

    private static String[] strings(ArrayField<String> field) {
        return field.values().isEmpty() ? null : field.values().toArray(String[]::new);
    }

    private static void deferred(DependencyKind kind, String path, Object token, String reason, String source,
                                  List<DeferredDependency> dependencies, List<Diagnostic> diagnostics) {
        dependencies.add(new DeferredDependency(kind, path, token, reason));
        diagnostics.add(new Diagnostic(source, path, "DEFERRED_" + kind.name(), reason));
    }
}
