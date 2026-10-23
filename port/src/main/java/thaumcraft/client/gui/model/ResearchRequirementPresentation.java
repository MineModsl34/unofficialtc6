package thaumcraft.client.gui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Entry;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.KnowledgeToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ResearchToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Stage;

/** Read-only requirement row geometry from TC6 GuiResearchPage.drawRequirements (465-725).
 * The source labels are hover hints, not text painted across the 56x16 strip. Each requirement
 * starts at shift24 and advances18, or110/count when more than6 are present. No item registry,
 * knowledge mutation, inventory inspection or paid/satisfied state is inferred by this model.
 * Translation lookup is explicit: unresolved keys become a human fallback, never a raw ID.
 */
public final class ResearchRequirementPresentation {
    public static final String UNAVAILABLE = "thaumcraft.research_ui.requirement_unavailable";
    private ResearchRequirementPresentation() {}

    public enum Kind {
        RESEARCH("tc.need.research", 232), OBTAIN("tc.need.obtain", 216),
        CRAFT("tc.need.craft", 200), KNOWLEDGE("tc.need.know", 184);

        private final String labelKey;
        private final int v;
        Kind(String labelKey, int v) { this.labelKey = labelKey; this.v = v; }
        public String labelKey() { return labelKey; }
        public int u() { return 200; }
        public int v() { return v; }
    }

    public sealed interface Requirement permits Research, Item, Knowledge {}
    public record Research(ResearchToken token) implements Requirement {
        public Research { Objects.requireNonNull(token); }
    }
    public record Item(ItemToken token) implements Requirement {
        public Item { Objects.requireNonNull(token); }
    }
    public record Knowledge(KnowledgeToken token) implements Requirement {
        public Knowledge { Objects.requireNonNull(token); }
    }
    public record Slot(int shift, Requirement requirement) {}
    public record Group(Kind kind, List<Slot> slots) {
        public Group { slots = List.copyOf(slots); }
        /** Source tipText assignment: the last matching slot wins when compressed icons overlap.
         * Coordinates are relative to the row's left-15 origin; y is checked by the Screen.
         */
        public Slot hoveredSlot(int relativeMouseX) {
            Slot hovered = null;
            for (Slot slot : slots)
                if (relativeMouseX >= slot.shift() && relativeMouseX < slot.shift() + 16) hovered = slot;
            return hovered;
        }
    }

    public static List<Group> groups(Stage stage) {
        if (stage == null) return List.of();
        List<Group> groups = new ArrayList<>();
        if (stage.requiredResearch() != null)
            add(groups, Kind.RESEARCH, stage.requiredResearch().values().stream().map(Research::new).toList());
        if (stage.requiredItems() != null)
            add(groups, Kind.OBTAIN, stage.requiredItems().values().stream().map(Item::new).toList());
        if (stage.requiredCraft() != null)
            add(groups, Kind.CRAFT, stage.requiredCraft().values().stream().map(Item::new).toList());
        if (stage.requiredKnowledge() != null)
            add(groups, Kind.KNOWLEDGE, stage.requiredKnowledge().values().stream().map(Knowledge::new).toList());
        return List.copyOf(groups);
    }

    private static void add(List<Group> groups, Kind kind, List<? extends Requirement> requirements) {
        if (requirements.isEmpty()) return;
        int step = requirements.size() > 6 ? 110 / requirements.size() : 18;
        List<Slot> slots = new ArrayList<>(requirements.size());
        for (int index = 0; index < requirements.size(); index++)
            slots.add(new Slot(24 + index * step, requirements.get(index)));
        groups.add(new Group(kind, slots));
    }

    /** Source entry names take precedence over discovery-marker translations. */
    public static String researchLabel(ResearchToken token, Map<String, Entry> entries,
                                       Predicate<String> hasTranslation) {
        Objects.requireNonNull(entries); Objects.requireNonNull(hasTranslation);
        if (token == null || token.key() == null) return UNAVAILABLE;
        Entry entry = entries.get(token.key());
        if (entry != null && entry.name() != null && hasTranslation.test(entry.name())) return entry.name();
        String marker = "research." + token.key() + ".text";
        return hasTranslation.test(marker) ? marker : UNAVAILABLE;
    }
}
