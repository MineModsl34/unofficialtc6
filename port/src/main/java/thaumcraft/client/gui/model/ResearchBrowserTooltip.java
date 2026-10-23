package thaumcraft.client.gui.model;

import java.util.ArrayList;
import java.util.List;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchStatus;
import thaumcraft.client.gui.model.ResearchBrowserProjection.Node;
import thaumcraft.common.lib.research.catalog.ResearchClientView.Snapshot;

/** Read-only title/stage/prerequisite prefix of TC6 GuiResearchBrowser.drawScreen (848-891).
 * Uses one accepted owner snapshot, not progress-request eligibility or raw icon descriptors.
 * The Screen appends PAGE/RESEARCH notices from the same accepted owner's received flags.
 */
public final class ResearchBrowserTooltip {
    private ResearchBrowserTooltip() {}

    public enum Kind { TITLE, BEGIN, STAGE, MISSING_PARENTS, MISSING_PARENT }
    /** translationKey is null only for the source's unresolved-parent literal '?'. */
    public record Line(Kind kind, String translationKey, String suffix) {}

    public static List<Line> project(Snapshot snapshot, Node node) {
        if (snapshot == null || node == null) return List.of();
        var byKey = snapshot.spec().entriesByKey(); // Owning-thread check must not be swallowed.
        var entry = node.rawSpec();
        if (byKey.get(entry.key()) != entry) return List.of(); // No stale catalog node.
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Kind.TITLE, entry.name(), ""));
        // A node retained by a caller can predate an owner packet even with the same catalog.
        // All knowledge-dependent hints must come from this snapshot, not its old node flags.
        if (snapshot.availability(entry.key()).parentsSatisfied()) {
            if (snapshot.knowledgeStatus(entry.key()) != EnumResearchStatus.COMPLETE
                && entry.stages().legacyValues() != null) {
                int stage = snapshot.storedStage(entry.key());
                lines.add(stage > 0
                    ? new Line(Kind.STAGE, "tc.research.stage", " " + stage + "/" + entry.stages().values().size())
                    : new Line(Kind.BEGIN, "tc.research.begin", ""));
            }
        } else {
            lines.add(new Line(Kind.MISSING_PARENTS, "tc.researchmissing", ""));
            for (String parent : entry.parents().values()) {
                if (snapshot.knowsStrict(parent)) continue; // Source query uses the RAW qualifier.
                String clean = parent.startsWith("~") ? parent.substring(1) : parent;
                int at = clean.indexOf('@');
                if (at >= 0) clean = clean.substring(0, at);
                var parentEntry = byKey.get(clean);
                lines.add(new Line(Kind.MISSING_PARENT, parentEntry == null ? null : parentEntry.name(), ""));
            }
        }
        return List.copyOf(lines);
    }
}
