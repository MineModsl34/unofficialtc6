package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchAddendum;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;

/**
 * Synchronous test-only catalog scope. NEVER merge a production catalog into a fixture, clone
 * its mutable entries, or restore a replacement copy: restore the EXACT original map object.
 * No events, deferred work, registry serialization, runtime catalog publication or player grants.
 * Use structured/LIFO scopes on the test's owning thread, without yielding to another game tick.
 * Public metadata is checked; opaque addon-private icon internals are retained by identity only.
 */
public final class ResearchCatalogTestScope implements AutoCloseable {
    private final Snapshot original;
    private final LinkedHashMap<String, ResearchCategory> installed;
    private boolean closed;

    private ResearchCatalogTestScope(LinkedHashMap<String, ResearchCategory> installed, Snapshot original) {
        if (!original.restored()) throw new IllegalStateException("Original catalog changed before test scope");
        if (!detached(installed, original.catalog)) throw new IllegalStateException("Synthetic catalog aliases original mutable metadata");
        this.original = original;
        this.installed = installed;
        ResearchCategories.researchCategories = installed;
    }

    public static Snapshot snapshot() { return new Snapshot(ResearchCategories.researchCategories); }

    public static ResearchCatalogTestScope install(LinkedHashMap<String, ResearchCategory> synthetic) {
        return install(synthetic, snapshot());
    }

    public static ResearchCatalogTestScope install(LinkedHashMap<String, ResearchCategory> synthetic, Snapshot original) {
        return new ResearchCatalogTestScope(java.util.Objects.requireNonNull(synthetic), java.util.Objects.requireNonNull(original));
    }

    public boolean detached() { return detached(installed, original.catalog); }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        boolean active = ResearchCategories.researchCategories == installed;
        // Restore first, including when an assertion/native fixture exception unwinds the scope.
        ResearchCategories.researchCategories = original.catalog;
        if (!active) throw new IllegalStateException("Catalog scope replaced unexpectedly/non-LIFO close");
        if (!original.restored()) throw new IllegalStateException("Original public catalog metadata mutated during test scope");
    }

    public static final class Snapshot {
        private final LinkedHashMap<String, ResearchCategory> catalog;
        private final List<Object> metadata;
        private final int categoryCount;
        private final int entryCount;

        private Snapshot(LinkedHashMap<String, ResearchCategory> catalog) {
            this.catalog = java.util.Objects.requireNonNull(catalog);
            metadata = fingerprint(catalog);
            categoryCount = catalog.size();
            int entries = 0;
            for (ResearchCategory category : catalog.values()) if (category != null && category.research != null) entries += category.research.size();
            entryCount = entries;
        }
        public int categoryCount() { return categoryCount; }
        public int entryCount() { return entryCount; }
        public boolean contentsUnchanged() { return metadata.equals(fingerprint(catalog)); }
        public boolean restored() { return ResearchCategories.researchCategories == catalog && contentsUnchanged(); }
    }

    private static boolean detached(Object replacement, Object original) {
        Set<Object> previous = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> next = Collections.newSetFromMap(new IdentityHashMap<>());
        mutableIdentities(original, previous);
        mutableIdentities(replacement, next);
        for (Object identity : next) if (previous.contains(identity)) return false;
        return true;
    }

    /** Mutable public metadata graph only; sharing immutable Strings/enums/locations is safe. */
    private static void mutableIdentities(Object value, Set<Object> result) {
        if (!(value instanceof Map<?, ?> || value instanceof Object[] || value instanceof int[] || value instanceof ResearchCategory
            || value instanceof ResearchEntry || value instanceof ResearchStage || value instanceof ResearchAddendum
            || value instanceof ResearchStage.Knowledge || value instanceof AspectList || value instanceof ItemStack)) return;
        if (!result.add(value)) return;
        if (value instanceof Map<?, ?> map) for (Object child : map.values()) mutableIdentities(child, result);
        else if (value instanceof Object[] array) for (Object child : array) mutableIdentities(child, result);
        else if (value instanceof AspectList aspects) mutableIdentities(aspects.aspects, result);
        else if (value instanceof ResearchCategory category) {
            mutableIdentities(category.formula, result); mutableIdentities(category.research, result);
        } else if (value instanceof ResearchEntry entry) {
            for (Object child : new Object[]{entry.getParents(), entry.getSiblings(), entry.getIcons(), entry.getMeta(),
                entry.getRewardItem(), entry.getRewardKnow(), entry.getStages(), entry.getAddenda()}) mutableIdentities(child, result);
        } else if (value instanceof ResearchStage stage) {
            for (Object child : new Object[]{stage.getRecipes(), stage.getObtain(), stage.getCraft(), stage.getCraftReference(),
                stage.getKnow(), stage.getResearch(), stage.getResearchIcon()}) mutableIdentities(child, result);
        } else if (value instanceof ResearchAddendum addendum) {
            mutableIdentities(addendum.getRecipes(), result); mutableIdentities(addendum.getResearch(), result);
        } else if (value instanceof ResearchStage.Knowledge knowledge) mutableIdentities(knowledge.category, result);
    }

    private static List<Object> fingerprint(Map<String, ResearchCategory> catalog) {
        List<Object> result = new ArrayList<>(); result.add(new Identity(catalog));
        for (var category : catalog.entrySet()) result.add(values(category.getKey(), category.getValue()));
        return Collections.unmodifiableList(result);
    }

    private static List<Object> values(Object... input) {
        List<Object> result = new ArrayList<>();
        for (Object value : input) result.add(value(value));
        return Collections.unmodifiableList(result);
    }

    private static Object value(Object input) {
        if (input == null || input instanceof Identity || input instanceof String || input instanceof Number || input instanceof Enum<?>
            || input instanceof ResourceLocation || input instanceof Record) return input;
        if (input instanceof ItemStack item) return new ItemValue(item);
        if (input instanceof Object[] array) {
            List<Object> result = new ArrayList<>(); result.add(new Identity(array));
            for (Object child : array) result.add(value(child)); return Collections.unmodifiableList(result);
        }
        if (input instanceof int[] array) return values(new Identity(array), Arrays.toString(array));
        if (input instanceof AspectList aspects) {
            List<Object> result = new ArrayList<>(); result.add(new Identity(aspects)); result.add(new Identity(aspects.aspects));
            for (var term : aspects.aspects.entrySet()) result.add(values(new Identity(term.getKey()), term.getValue()));
            return Collections.unmodifiableList(result);
        }
        if (input instanceof ResearchCategory category) {
            List<Object> result = new ArrayList<>(values(new Identity(category), category.key, category.researchKey, category.formula,
                category.icon, category.background, category.background2, category.minDisplayColumn, category.minDisplayRow,
                category.maxDisplayColumn, category.maxDisplayRow));
            result.add(new Identity(category.research));
            if (category.research != null) for (var entry : category.research.entrySet()) result.add(values(entry.getKey(), entry.getValue()));
            return Collections.unmodifiableList(result);
        }
        if (input instanceof ResearchEntry entry) return values(new Identity(entry), entry.getKey(), entry.getCategory(), entry.getName(),
            entry.getDisplayColumn(), entry.getDisplayRow(), entry.getParents(), entry.getSiblings(), entry.getIcons(), entry.getMeta(),
            entry.getRewardItem(), entry.getRewardKnow(), entry.getStages(), entry.getAddenda());
        if (input instanceof ResearchStage stage) return values(new Identity(stage), stage.getText(), stage.getRecipes(), stage.getObtain(),
            stage.getCraft(), stage.getCraftReference(), stage.getKnow(), stage.getResearch(), stage.getResearchIcon(), stage.getWarp());
        if (input instanceof ResearchAddendum addendum) return values(new Identity(addendum), addendum.getText(), addendum.getRecipes(), addendum.getResearch());
        if (input instanceof ResearchStage.Knowledge knowledge) return values(new Identity(knowledge), knowledge.type,
            new Identity(knowledge.category), knowledge.category == null ? null : knowledge.category.key, knowledge.amount);
        return new Identity(input);
    }

    private static final class Identity {
        private final Object object;
        Identity(Object object) { this.object = object; }
        @Override public boolean equals(Object other) { return other instanceof Identity identity && object == identity.object; }
        @Override public int hashCode() { return System.identityHashCode(object); }
    }

    /** No registry/NBT serialization; vanilla component snapshots obey ItemStack's immutable-component contract. */
    private static final class ItemValue {
        private final ItemStack identity;
        private final ItemStack snapshot;
        ItemValue(ItemStack stack) { identity = stack; snapshot = stack.copy(); }
        @Override public boolean equals(Object other) { return other instanceof ItemValue item && identity == item.identity && ItemStack.matches(snapshot, item.snapshot); }
        @Override public int hashCode() { return System.identityHashCode(identity); }
    }
}
