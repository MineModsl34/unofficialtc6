package thaumcraft.common.lib.research;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEntry.EnumResearchMeta;

/**
 * Read-only common-side browser availability, not a GUI or a C2S authorization endpoint.
 * TC6 GuiResearchBrowser's known-first visibility, HIDDEN null-v-empty parents, literal
 * visibility recursion and strict stripped-parent/category checks are retained. The same
 * capability expression evaluator is reused, including its unusual regex-OR behavior.
 *
 * Two deliberate modern adaptations: malformed/cyclic/over-budget visibility queries fail
 * closed, and known stage-zero entries may request the checked service's explicit resume.
 * Old GUI page checks could not resume that state. A positive decision does NOT prove stage
 * costs, accepted catalog ownership/revision, or permission to grant research; those must be
 * checked independently by the server. No writes, events, grants, reloads or cross-query cache.
 * Like the public mutable metadata API, call this synchronously on its owning thread.
 */
public final class ResearchBrowserAvailability {
    public static final int MAX_VISIBILITY_DEPTH = 128;
    /** Work units count visited nodes and literal parent lookups, including missing parents. */
    public static final int MAX_VISIBILITY_VISITS = 4096;

    private ResearchBrowserAvailability() {}

    public enum Reason {
        START_UNKNOWN, RESUME_KNOWN_ZERO, ADVANCE_KNOWN,
        COMPLETE, NOT_VISIBLE, CATEGORY_LOCKED, MISSING_PARENTS,
        UNKNOWN_ENTRY, MISSING_CATEGORY, INVALID_STAGE, MALFORMED,
        VISIBILITY_CYCLE, DEPTH_LIMIT, VISIT_LIMIT
    }

    /** visible is intrinsic node visibility, independent of categoryAccessible/tab visibility.
     * Failed malformed/bounded queries retain only facts already established (known nodes stay
     * visible) and conservatively deny progress. The returned booleans/enumeration are a snapshot.
     */
    public record Decision(boolean known, boolean visible, boolean categoryAccessible,
                           boolean parentsSatisfied, boolean mayRequestProgress, Reason reason) {
        public Decision { Objects.requireNonNull(reason, "reason"); }
    }

    public static Decision evaluate(IPlayerKnowledge knowledge, String key) {
        return evaluate(knowledge, key, ResearchCategories::getResearch, ResearchCategories::getResearchCategory);
    }

    /** Read-only lookup seam for a detached accepted client catalog. Both lookups and knowledge
     * must describe that SAME catalog snapshot. This is not a grant/cost/authorization endpoint;
     * ordinary server callers retain the current public API lookups through the two-argument form. */
    public static Decision evaluate(IPlayerKnowledge knowledge, String key,
                                    Function<String, ResearchEntry> entries,
                                    Function<String, ResearchCategory> categories) {
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(entries, "entry lookup");
        Objects.requireNonNull(categories, "category lookup");
        boolean known = false, visible = false, categoryAccessible = false, parentsSatisfied = false;
        try {
            known = ThaumcraftCapabilities.knowsResearchData(knowledge, key);
            ResearchEntry entry = entries.apply(key);
            if (entry == null) return decision(known, false, false, true, false, Reason.UNKNOWN_ENTRY);
            // Known-first is essential: known HIDDEN entries do not recurse into their parents.
            visible = known;
            Query query = new Query(knowledge, entries);
            if (!visible) visible = query.visible(entry, 0);
            parentsSatisfied = query.parents(entry);
            var category = categories.apply(entry.getCategory());
            if (category == null) return decision(known, visible, false, parentsSatisfied, false, Reason.MISSING_CATEGORY);
            categoryAccessible = category.researchKey == null
                || ThaumcraftCapabilities.knowsResearchStrictData(knowledge, category.researchKey);
            if (known && knowledge.isResearchComplete(key)) {
                return decision(known, visible, categoryAccessible, parentsSatisfied, false, Reason.COMPLETE);
            }
            if (!visible) return decision(known, false, categoryAccessible, parentsSatisfied, false, Reason.NOT_VISIBLE);
            if (!known && !categoryAccessible) {
                return decision(false, true, false, parentsSatisfied, false, Reason.CATEGORY_LOCKED);
            }
            if (!parentsSatisfied) {
                return decision(known, true, categoryAccessible, false, false, Reason.MISSING_PARENTS);
            }
            if (!known) return decision(false, true, categoryAccessible, true, true, Reason.START_UNKNOWN);
            int stage = knowledge.getResearchStage(key);
            if (stage < 0) return decision(true, true, categoryAccessible, true, false, Reason.INVALID_STAGE);
            return decision(true, true, categoryAccessible, true, true,
                stage == 0 ? Reason.RESUME_KNOWN_ZERO : Reason.ADVANCE_KNOWN);
        } catch (Bounded rejected) {
            return decision(known, visible || known, categoryAccessible, parentsSatisfied, false, rejected.reason);
        } catch (RuntimeException malformed) {
            // Preserve native errors in the public query API; only this browser boundary refuses.
            return decision(known, visible || known, categoryAccessible, parentsSatisfied, false, Reason.MALFORMED);
        }
    }

    private static Decision decision(boolean known, boolean visible, boolean categoryAccessible,
                                     boolean parentsSatisfied, boolean mayRequestProgress, Reason reason) {
        return new Decision(known, visible, categoryAccessible, parentsSatisfied, mayRequestProgress, reason);
    }

    private static final class Query {
        private final IPlayerKnowledge knowledge;
        private final Function<String, ResearchEntry> entries;
        private final Set<ResearchEntry> path = Collections.newSetFromMap(new IdentityHashMap<>());
        private int visits;

        Query(IPlayerKnowledge knowledge, Function<String, ResearchEntry> entries) {
            this.knowledge = knowledge;
            this.entries = entries;
        }

        boolean parents(ResearchEntry entry) {
            String[] raw = entry.getParents();
            if (raw != null && raw.length > MAX_VISIBILITY_VISITS) throw new Bounded(Reason.VISIT_LIMIT);
            // Retain TC6's null element -> literal "null" and one leading '~' stripping.
            String[] stripped = entry.getParentsStripped();
            return stripped == null || ThaumcraftCapabilities.knowsResearchStrictData(knowledge, stripped);
        }

        boolean visible(ResearchEntry entry, int depth) {
            if (depth >= MAX_VISIBILITY_DEPTH) throw new Bounded(Reason.DEPTH_LIMIT);
            visit();
            if (ThaumcraftCapabilities.knowsResearchData(knowledge, entry.getKey())) return true;
            if (!path.add(entry)) throw new Bounded(Reason.VISIBILITY_CYCLE);
            try {
                boolean hidden = entry.hasMeta(EnumResearchMeta.HIDDEN);
                if (hidden && !parents(entry)) return false;
                String[] rawParents = entry.getParents();
                // A zero-length parent array is deliberately different from null here.
                if (hidden && rawParents == null) return false;
                if (rawParents != null) {
                    if (rawParents.length > MAX_VISIBILITY_VISITS) throw new Bounded(Reason.VISIT_LIMIT);
                    for (String parent : rawParents) {
                        visit();
                        // Source lookup is literal: DO NOT strip '~', '@' or expressions.
                        ResearchEntry parentEntry = entries.apply(parent);
                        if (parentEntry != null && !visible(parentEntry, depth + 1)) return false;
                    }
                }
                return true;
            } finally { path.remove(entry); }
        }

        private void visit() {
            if (++visits > MAX_VISIBILITY_VISITS) throw new Bounded(Reason.VISIT_LIMIT);
        }
    }

    private static final class Bounded extends RuntimeException {
        final Reason reason;
        Bounded(Reason reason) { this.reason = reason; }
    }
}
