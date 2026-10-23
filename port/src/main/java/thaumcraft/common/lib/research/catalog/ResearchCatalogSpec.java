package thaumcraft.common.lib.research.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.research.ResearchEntry.EnumResearchMeta;

/**
 * Detached immutable description of TC6 research JSON. It does not resolve registries, publish
 * mutable API entries, create crafting hashes, or activate research requirements. Raw source text
 * and opaque keys are retained for later atomic loading, synchronization and source comparison.
 */
public record ResearchCatalogSpec(Map<String, String> sources, List<Entry> entries,
                                 List<Diagnostic> diagnostics) {
    public ResearchCatalogSpec {
        sources = orderedCopy(sources);
        entries = List.copyOf(entries);
        diagnostics = List.copyOf(diagnostics);
    }

    public Map<String, Entry> entriesByKey() {
        Map<String, Entry> result = new LinkedHashMap<>();
        for (Entry entry : entries) result.put(entry.key(), entry);
        return Collections.unmodifiableMap(result);
    }

    private static <K, V> Map<K, V> orderedCopy(Map<K, V> input) {
        Map<K, V> result = new LinkedHashMap<>();
        input.forEach((key, value) -> result.put(Objects.requireNonNull(key), Objects.requireNonNull(value)));
        return Collections.unmodifiableMap(result);
    }

    /** Missing and explicitly empty arrays remain distinguishable; both map to original null. */
    public record ArrayField<T>(boolean present, List<T> values) {
        public ArrayField {
            values = List.copyOf(values);
            if (!present && !values.isEmpty()) throw new IllegalArgumentException("Absent array has values");
        }

        public static <T> ArrayField<T> absent() { return new ArrayField<>(false, List.of()); }
        public List<T> legacyValues() { return values.isEmpty() ? null : values; }
    }

    public record Entry(String source, String path, String key, String name, String category,
                        ArrayField<IconToken> icons, ArrayField<String> parents,
                        ArrayField<String> siblings, ArrayField<MetaToken> meta,
                        ArrayField<Integer> location, ArrayField<ItemToken> rewardItems,
                        ArrayField<KnowledgeToken> rewardKnowledge, ArrayField<Stage> stages,
                        ArrayField<Addendum> addenda, Map<String, String> unknownFields) {
        public Entry { unknownFields = orderedCopy(unknownFields); }
        public int displayColumn() { return location.present() ? location.values().get(0) : 0; }
        public int displayRow() { return location.present() ? location.values().get(1) : 0; }
    }

    public record Stage(String text, ArrayField<LegacyResourceId> recipes,
                        ArrayField<ItemToken> requiredItems, ArrayField<ItemToken> requiredCraft,
                        ArrayField<KnowledgeToken> requiredKnowledge,
                        ArrayField<ResearchToken> requiredResearch, boolean warpPresent, int warp,
                        Map<String, String> unknownFields) {
        public Stage { unknownFields = orderedCopy(unknownFields); }
    }

    public record Addendum(String text, ArrayField<LegacyResourceId> recipes,
                           ArrayField<String> requiredResearch, Map<String, String> unknownFields) {
        public Addendum { unknownFields = orderedCopy(unknownFields); }
    }

    public record MetaToken(String raw, EnumResearchMeta value) {}
    public record KnowledgeToken(String raw, EnumKnowledgeType type, String category, int amount) {}

    /** The stage helper splits research/icon on semicolon; addendum/parent strings do not. */
    public record ResearchToken(String raw, String key, String icon) {}

    /**
     * MC1.12 ResourceLocation semantics, not a modern registry key. In particular it lowercases
     * using ROOT and defaults a zero/one-character prefix to minecraft. Invalid modern syntax is
     * retained for an explicit future resolver failure, never silently replaced by a different ID.
     */
    public record LegacyResourceId(String raw, String namespace, String path, boolean modernSyntax) {
        public String normalized() { return namespace + ":" + path; }
    }

    public enum ItemKind { ITEM, OREDICT }

    /**
     * All item/ore tokens are unresolved, including syntactically valid ones. Count/damage and
     * effective SNBT describe the legacy parser, not a materialized ItemStack. rawSnbt retains the
     * untouched suffix; legacySnbt also preserves the original split-at-semicolon quirk. Quote
     * replacement applies only to reward/obtain/craft helpers, not the icon/direct helper.
     */
    public record ItemToken(String raw, String legacyText, ItemKind kind, String name,
                            int count, int damage, String rawSnbt, String legacySnbt,
                            List<String> ignoredSegments, LegacyResourceId legacyId,
                            String unresolvedReason) {
        public ItemToken { ignoredSegments = List.copyOf(ignoredSegments); }
    }

    /** Registry lookup decides item versus texture; the parser must not invent that decision. */
    public record IconToken(String raw, ItemToken itemCandidate, String focusFallback,
                            LegacyResourceId resourceFallback) {}

    /** Unknown JSON is immutable canonical text; the complete source keeps original formatting. */
    public record Diagnostic(String source, String path, String code, String message) {}
}
