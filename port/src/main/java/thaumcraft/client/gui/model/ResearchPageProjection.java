package thaumcraft.client.gui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Addendum;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ArrayField;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Entry;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.LegacyResourceId;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Stage;

/**
 * Headless, read-only prefix of TC6 GuiResearchPage.parsePages (1810+) and
 * generateRecipesLists (769+). Raw text/recipe tokens are NOT translated, laid out or resolved.
 * Requirements in the retained Stage are descriptors, never a claim that checkRequisites passed.
 *
 * Source selects storedStage - 1, clamps negative view indices to zero, and exposes addenda
 * only when that UNCLAMPED index reaches the stage count. This differs from the knowledge
 * "complete" status used by checkRequisites; no such boolean is an input here. Large positive
 * stages use an O(1), long-arithmetic clamp instead of the source decrement loop. Stored stages
 * below unknown(-1) fail closed rather than reproducing corrupt/overflowed integer behavior.
 * Missing/empty stage arrays have no content; an API-created empty array crashes the old GUI.
 *
 * A strict query must be observational and refer to one coherent owner-knowledge snapshot.
 * It receives the WHOLE original addendum array, including the source OR early-return quirk.
 * Eligibility is sampled once and reused for this immutable view, not independently ANDed.
 * Absent/empty required_research maps to legacy null, for which the source strict helper throws;
 * malformed addenda/query failures produce an unsupported empty view, not unconditional content.
 * No grants, client classes, optimistic state, markup parsing, recipe lookup or icon resolution.
 */
public final class ResearchPageProjection {
    private ResearchPageProjection() {}

    @FunctionalInterface
    public interface StrictArrayQuery {
        boolean test(String[] wholeLegacyResearchArray);
    }

    public enum Status {
        CONTENT, MISSING_ENTRY, MISSING_STAGES, EMPTY_STAGES, CORRUPT_STORED_STAGE,
        MALFORMED_STAGE, MALFORMED_ADDENDUM, STRICT_QUERY_FAILED
    }

    public record Diagnostic(Status status, String path, String message) {
        public Diagnostic {
            Objects.requireNonNull(status);
            Objects.requireNonNull(path);
            Objects.requireNonNull(message);
        }
    }

    /** Source order index and one-based displayed addendum number are distinct after filtering. */
    public record VisibleAddendum(int sourceIndex, int number, Addendum addendum) {
        public VisibleAddendum {
            if (sourceIndex < 0 || number < 1) throw new IllegalArgumentException("Invalid addendum position");
            Objects.requireNonNull(addendum);
        }
        public String rawText() { return addendum.text(); }
    }

    /**
     * stage/addendum records are already immutable Spec values. recipeHandles retains source
     * order/duplicates/invalid modern IDs; they remain unresolved, not visible recipe outputs.
     * Source's outer stage-recipes-null gate suppresses ALL addendum recipe-list additions even
     * when their text is eligible. Their raw recipes remain available on the addendum descriptor.
     */
    public record View(Status status, int storedStage, int stageIndex, boolean addendaPhaseComplete,
                       Stage stage, List<VisibleAddendum> eligibleAddenda,
                       List<LegacyResourceId> recipeHandles, List<Diagnostic> diagnostics) {
        public View {
            Objects.requireNonNull(status);
            eligibleAddenda = List.copyOf(eligibleAddenda);
            recipeHandles = List.copyOf(recipeHandles);
            diagnostics = List.copyOf(diagnostics);
            if (status == Status.CONTENT) {
                if (stage == null || stageIndex < 0) throw new IllegalArgumentException("Content requires a stage");
            } else if (stage != null || stageIndex != -1 || addendaPhaseComplete
                || !eligibleAddenda.isEmpty() || !recipeHandles.isEmpty()) {
                throw new IllegalArgumentException("Unsupported view must not advertise partial content");
            }
        }
        public boolean hasContent() { return status == Status.CONTENT; }
        public String rawText() { return stage == null ? null : stage.text(); }
    }

    public static View project(Entry entry, int storedStage, StrictArrayQuery strictQuery) {
        Objects.requireNonNull(strictQuery, "A source strict-array query is required");
        if (entry == null) return unsupported(Status.MISSING_ENTRY, storedStage, "$", "No entry descriptor");
        ArrayField<Stage> stageField = entry.stages();
        if (stageField == null || !stageField.present()) {
            return unsupported(Status.MISSING_STAGES, storedStage, entry.path() + ".stages", "No source stages array/content");
        }
        if (stageField.values().isEmpty()) {
            return unsupported(Status.EMPTY_STAGES, storedStage, entry.path() + ".stages",
                "Empty JSON stages maps to legacy null; an API empty array is not indexable");
        }
        if (storedStage < -1) {
            return unsupported(Status.CORRUPT_STORED_STAGE, storedStage, entry.path() + ".stages",
                "Stored stage below the supported unknown(-1) sentinel; no overflow emulation");
        }
        int length = stageField.values().size();
        long rawIndex = (long) storedStage - 1L;
        int index = (int) Math.max(0L, Math.min(rawIndex, (long) length - 1L));
        boolean complete = rawIndex >= length;
        Stage stage = stageField.values().get(index);
        if (stage.text() == null) {
            return unsupported(Status.MALFORMED_STAGE, storedStage, entry.path() + ".stages[" + index + "].text",
                "Null stage text is unsupported, not an invented translation or blank page");
        }
        List<VisibleAddendum> eligible = new ArrayList<>();
        if (complete && entry.addenda() != null) {
            List<Addendum> addenda = entry.addenda().values();
            for (int sourceIndex = 0; sourceIndex < addenda.size(); sourceIndex++) {
                Addendum addendum = addenda.get(sourceIndex);
                String path = entry.path() + ".addenda[" + sourceIndex + "]";
                List<String> research = legacyValues(addendum.requiredResearch());
                if (research == null) {
                    return unsupported(Status.MALFORMED_ADDENDUM, storedStage, path + ".required_research",
                        "Missing/empty array becomes legacy null and source knowsResearchStrict throws; eligibility is not invented");
                }
                final boolean allowed;
                try {
                    // Fresh array: even a callback modifying its argument cannot mutate Spec data.
                    allowed = strictQuery.test(research.toArray(String[]::new));
                } catch (RuntimeException failure) {
                    return unsupported(Status.STRICT_QUERY_FAILED, storedStage, path + ".required_research",
                        failure.getClass().getName() + ": " + String.valueOf(failure.getMessage()));
                }
                if (allowed) {
                    // Source reads/localizes text only AFTER its strict visibility query succeeds.
                    if (addendum.text() == null) {
                        return unsupported(Status.MALFORMED_ADDENDUM, storedStage, path + ".text", "Null eligible addendum text is unsupported");
                    }
                    eligible.add(new VisibleAddendum(sourceIndex, eligible.size() + 1, addendum));
                }
            }
        }
        List<LegacyResourceId> recipes = new ArrayList<>();
        List<LegacyResourceId> stageRecipes = legacyValues(stage.recipes());
        if (stageRecipes != null) {
            recipes.addAll(stageRecipes);
            for (VisibleAddendum addendum : eligible) {
                List<LegacyResourceId> addendumRecipes = legacyValues(addendum.addendum().recipes());
                if (addendumRecipes != null) recipes.addAll(addendumRecipes);
            }
        }
        return new View(Status.CONTENT, storedStage, index, complete, stage, eligible, recipes, List.of());
    }

    private static <T> List<T> legacyValues(ArrayField<T> field) { return field == null ? null : field.legacyValues(); }
    private static View unsupported(Status status, int storedStage, String path, String message) {
        return new View(status, storedStage, -1, false, null, List.of(), List.of(),
            List.of(new Diagnostic(status, path, message)));
    }
}
