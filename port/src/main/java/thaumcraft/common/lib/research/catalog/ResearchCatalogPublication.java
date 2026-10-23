package thaumcraft.common.lib.research.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Diagnostic;

/**
 * Explicit catalog publication. TC6 ResearchManager.java:250-300 appended
 * valid entries after individual errors; this modern adapter prepares a WHOLE detached candidate,
 * rejects JSON/addon collisions, then resets indexes/bounds with one public API map-reference
 * replacement. Bounds still include zero, as ResearchManager.java:661-707 did.
 *
 * The mutable public TC6 API is deliberately retained. Call prepare/publish on the catalog's
 * owning thread, without concurrent addon mutations; synchronized publication cannot make
 * arbitrary writes to public API fields thread-safe. A stale snapshot is rejected before any
 * replacement. The immutable spec/ownership snapshot is published through one volatile field;
 * readers needing coherent spec+ownership must use published(), not combine separate reads.
 * This class does not touch player data, grant/unlock research or evaluate requirements.
 */
public final class ResearchCatalogPublication {
    private volatile Snapshot published;

    public ResearchCatalogPublication() {}

    public Snapshot published() { return published; }

    public Set<ResearchEntry> ownedEntries() {
        Snapshot current = published;
        return current == null ? Set.of() : current.ownedEntries();
    }

    public record Snapshot(ResearchCatalogSpec spec, Set<ResearchEntry> ownedEntries,
                           Map<String, PreparedResearchMetadata.Entry> entries) {
        public Snapshot {
            Objects.requireNonNull(spec);
            ownedEntries = identityCopy(ownedEntries);
            entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }
    }

    public static final class Candidate {
        private final Map<String, ResearchCategory> origin;
        private final ExistingState originState;
        private final LinkedHashMap<String, ResearchCategory> categories;
        private final ExistingState preparedState;
        private final Snapshot snapshot;

        private Candidate(Map<String, ResearchCategory> origin, ExistingState originState,
                          LinkedHashMap<String, ResearchCategory> categories, Snapshot snapshot) {
            this.origin = origin;
            this.originState = originState;
            this.categories = categories;
            this.snapshot = snapshot;
            preparedState = ExistingState.capture(categories);
        }
        public ResearchCatalogSpec spec() { return snapshot.spec(); }
        public Map<String, ResearchCategory> categories() { return Collections.unmodifiableMap(categories); }
        public Map<String, PreparedResearchMetadata.Entry> entries() { return snapshot.entries(); }
        public Set<ResearchEntry> ownedEntries() { return snapshot.ownedEntries(); }
        public boolean hasUnresolved() { return entries().values().stream().anyMatch(PreparedResearchMetadata.Entry::hasUnresolved); }
    }

    public static final class PreparationException extends IllegalArgumentException {
        private final String source;
        private final String path;
        public PreparationException(String source, String path, String message) {
            super(source + " " + path + ": " + message);
            this.source = source;
            this.path = path;
        }
        public String source() { return source; }
        public String path() { return path; }
    }

    /**
     * existingCategories is read only. Exact entry identity, not key/type/equals, establishes
     * previous ownership: an addon replacing an old owned entry by a new object is preserved.
     * Existing category definitions override built-in defaults; extra categories are copied too.
     */
    public static Candidate prepareWhole(ResearchCatalogSpec spec, Map<String, ResearchCategory> existingCategories,
                                           Set<? extends ResearchEntry> previousOwnedEntries) {
        Objects.requireNonNull(spec);
        Objects.requireNonNull(existingCategories);
        Set<ResearchEntry> oldOwned = identityCopy(previousOwnedEntries);
        ExistingState originState = ExistingState.capture(existingCategories);
        LinkedHashMap<String, ResearchCategory> categories = ResearchCategoryDefinitions.newBuiltinCategories();
        for (var existing : existingCategories.entrySet()) {
            ResearchCategory category = ResearchCategoryDefinitions.detachedDefinition(existing.getValue());
            // Source map keys are also preserved, including external aliases; opaque keys aren't ResourceLocations.
            categories.put(existing.getKey(), category);
            category.research = new LinkedHashMap<>();
            for (var entry : existing.getValue().research.entrySet()) {
                if (!oldOwned.contains(entry.getValue())) category.research.put(entry.getKey(), entry.getValue());
            }
        }
        Set<String> keys = new HashSet<>();
        Set<Coordinate> coordinates = new HashSet<>();
        for (var category : categories.entrySet()) {
            for (var entry : category.getValue().research.entrySet()) {
                keys.add(entry.getKey());
                keys.add(entry.getValue().getKey());
                coordinates.add(new Coordinate(category.getKey(), entry.getValue().getDisplayColumn(), entry.getValue().getDisplayRow()));
            }
        }
        List<Diagnostic> diagnostics = new ArrayList<>(spec.diagnostics());
        LinkedHashMap<String, PreparedResearchMetadata.Entry> owned = new LinkedHashMap<>();
        for (ResearchCatalogSpec.Entry entry : spec.entries()) {
            ResearchCategory category = categories.get(entry.category());
            if (category == null) throw fail(entry, ".category", "Unknown candidate category " + entry.category());
            if (!keys.add(entry.key())) throw fail(entry, ".key", "JSON key collides with another JSON/API entry: " + entry.key());
            if (!coordinates.add(new Coordinate(entry.category(), entry.displayColumn(), entry.displayRow()))) {
                throw fail(entry, ".location", "JSON coordinate collides with another JSON/API entry in " + entry.category());
            }
            PreparedResearchMetadata.Entry prepared = PreparedResearchMetadata.prepare(entry, categories, diagnostics);
            category.research.put(prepared.getKey(), prepared);
            owned.put(prepared.getKey(), prepared);
        }
        for (ResearchCategory category : categories.values()) rebuildBounds(category);
        ResearchCatalogSpec enriched = new ResearchCatalogSpec(spec.sources(), spec.entries(), diagnostics);
        Snapshot snapshot = new Snapshot(enriched, identityCopy(owned.values()), owned);
        return new Candidate(existingCategories, originState, categories, snapshot);
    }

    /** Explicit call only; no event subscription or automatic startup publication. */
    public synchronized Snapshot publish(Candidate candidate) {
        Objects.requireNonNull(candidate);
        if (ResearchCategories.researchCategories != candidate.origin || !candidate.originState.matches(candidate.origin)) {
            throw new IllegalStateException("Catalog/API changed since preparation; prepare a new whole candidate");
        }
        if (!candidate.preparedState.matches(candidate.categories)) {
            throw new IllegalStateException("Detached candidate was mutated after preparation");
        }
        // Everything that can fail has completed. One API map-reference replacement; no append window.
        ResearchCategories.researchCategories = candidate.categories;
        published = candidate.snapshot;
        return candidate.snapshot;
    }

    private record Coordinate(String category, int column, int row) {}

    private static void rebuildBounds(ResearchCategory category) {
        category.minDisplayColumn = category.minDisplayRow = category.maxDisplayColumn = category.maxDisplayRow = 0;
        for (ResearchEntry entry : category.research.values()) {
            category.minDisplayColumn = Math.min(category.minDisplayColumn, entry.getDisplayColumn());
            category.maxDisplayColumn = Math.max(category.maxDisplayColumn, entry.getDisplayColumn());
            category.minDisplayRow = Math.min(category.minDisplayRow, entry.getDisplayRow());
            category.maxDisplayRow = Math.max(category.maxDisplayRow, entry.getDisplayRow());
        }
    }

    private static PreparationException fail(ResearchCatalogSpec.Entry entry, String suffix, String message) {
        return new PreparationException(entry.source(), entry.path() + suffix, message);
    }

    private static Set<ResearchEntry> identityCopy(Iterable<? extends ResearchEntry> entries) {
        Objects.requireNonNull(entries);
        Set<ResearchEntry> copy = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ResearchEntry entry : entries) copy.add(Objects.requireNonNull(entry));
        return Collections.unmodifiableSet(copy);
    }

    /** Defensive preflight against ordinary API additions/replacements during a detached prepare. */
    private record ExistingState(List<CategoryState> categories) {
        static ExistingState capture(Map<String, ResearchCategory> source) {
            List<CategoryState> categories = new ArrayList<>();
            for (var entry : source.entrySet()) categories.add(CategoryState.capture(entry.getKey(), entry.getValue()));
            return new ExistingState(List.copyOf(categories));
        }
        boolean matches(Map<String, ResearchCategory> source) {
            if (source.size() != categories.size()) return false;
            int index = 0;
            for (var entry : source.entrySet()) if (!categories.get(index++).matches(entry.getKey(), entry.getValue())) return false;
            return true;
        }
    }

    private record CategoryState(String mapKey, ResearchCategory identity, String key, String unlock, AspectList formula,
                                  Object icon, Object background, Object background2, List<EntryState> entries,
                                  int minColumn, int minRow, int maxColumn, int maxRow) {
        static CategoryState capture(String mapKey, ResearchCategory category) {
            if (mapKey == null || category == null || category.research == null) {
                throw new PreparationException("<api>", "categories", "Null external category/map");
            }
            List<EntryState> entries = new ArrayList<>();
            for (var entry : category.research.entrySet()) entries.add(EntryState.capture(entry.getKey(), entry.getValue()));
            return new CategoryState(mapKey, category, category.key, category.researchKey,
                category.formula == null ? null : category.formula.copy(), category.icon, category.background, category.background2,
                List.copyOf(entries), category.minDisplayColumn, category.minDisplayRow, category.maxDisplayColumn, category.maxDisplayRow);
        }
        boolean matches(String currentKey, ResearchCategory category) {
            if (!Objects.equals(mapKey, currentKey) || identity != category || !Objects.equals(key, category.key)
                || !Objects.equals(unlock, category.researchKey) || !Objects.equals(formula, category.formula)
                || !Objects.equals(icon, category.icon) || !Objects.equals(background, category.background)
                || !Objects.equals(background2, category.background2) || category.research == null
                || entries.size() != category.research.size() || minColumn != category.minDisplayColumn
                || minRow != category.minDisplayRow || maxColumn != category.maxDisplayColumn || maxRow != category.maxDisplayRow) return false;
            int index = 0;
            for (var entry : category.research.entrySet()) if (!entries.get(index++).matches(entry.getKey(), entry.getValue())) return false;
            return true;
        }
    }

    private record EntryState(String mapKey, ResearchEntry identity, String key, String category, int column, int row) {
        static EntryState capture(String mapKey, ResearchEntry entry) {
            if (mapKey == null || entry == null || entry.getKey() == null) {
                throw new PreparationException("<api>", "entries", "Null external research key/entry");
            }
            return new EntryState(mapKey, entry, entry.getKey(), entry.getCategory(), entry.getDisplayColumn(), entry.getDisplayRow());
        }
        boolean matches(String currentKey, ResearchEntry entry) {
            return Objects.equals(mapKey, currentKey) && identity == entry && Objects.equals(key, entry.getKey())
                && Objects.equals(category, entry.getCategory()) && column == entry.getDisplayColumn() && row == entry.getDisplayRow();
        }
    }
}
