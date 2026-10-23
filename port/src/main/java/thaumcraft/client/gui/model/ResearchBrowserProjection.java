package thaumcraft.client.gui.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchStatus;
import thaumcraft.api.research.ResearchEntry.EnumResearchMeta;
import thaumcraft.common.lib.research.ResearchBrowserAvailability.Decision;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Entry;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;
import thaumcraft.common.lib.research.catalog.ResearchClientView.Snapshot;

/**
 * Read-only headless prefix of TC6 GuiResearchBrowser.updateResearch (114-275) and
 * genResearchBackgroundZoomable (515-756). Uses ONLY one detached accepted owner snapshot and
 * canonical builtin category definitions; no mutable global catalog or Minecraft renderer.
 * Raw icons/stage text remain unresolved descriptors. PAGE/RESEARCH sparks, search, animation,
 * addon tabs and grants are intentionally absent, not replaced by synthetic effects.
 *
 * Percent is source KNOWN count, not completed-stage count; AUTOUNLOCK entries are excluded.
 * Links retain source order/duplicates, exact raw-reference tests, drawLine endpoint order and
 * REVERSE flag. Coordinates and bounds use source int arithmetic. An empty visible map uses
 * safe zero bounds rather than the original inverted sentinel interval; a locked/missing
 * selection falls back to the first accessible builtin tab. Malformed query failures refuse
 * the whole projection instead of publishing partial data or crashing the old GUI.
 */
public final class ResearchBrowserProjection {
    private ResearchBrowserProjection() {}

    public record Tab(String key, String unlockKey, ResourceLocation icon,
                      ResourceLocation background, ResourceLocation background2,
                      int known, int total, int percent) {
        public Tab { Objects.requireNonNull(key); }
    }

    public record Node(Entry rawSpec, int column, int row, int x, int y,
                       Decision availability, int frameU, int frameV,
                       boolean spiky, boolean hasWarp, boolean complete) {
        public Node {
            Objects.requireNonNull(rawSpec);
            Objects.requireNonNull(availability);
        }
    }

    public enum LinkKind { PARENT, SIBLING }

    /** x1/y1/x2/y2 are SOURCE columns/rows, not pixels. Parent child->parent;
     * sibling sibling->source. REVERSE is source-node metadata, not endpoint reversal here.
     * knownTarget tests the referenced parent/sibling, NOT necessarily the toKey endpoint. */
    public record Link(LinkKind kind, String rawReference, String fromKey, String toKey,
                       int x1, int y1, int x2, int y2, boolean knownTarget,
                       boolean reverse, boolean arrow, float r, float g, float b, float zMod) {
        public Link {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(rawReference);
            Objects.requireNonNull(fromKey);
            Objects.requireNonNull(toKey);
        }
    }

    public record Bounds(int left, int top, int right, int bottom, boolean empty) {
        /** Original int addition/division, including truncation toward zero. */
        public int centerX() { return (left + right) / 2; }
        public int centerY() { return (top + bottom) / 2; }
    }

    public record Browser(List<Tab> tabs, List<Node> nodes, List<Link> links,
                          String selected, Bounds bounds) {
        public Browser {
            tabs = List.copyOf(tabs);
            nodes = List.copyOf(nodes);
            links = List.copyOf(links);
            Objects.requireNonNull(bounds);
        }
    }

    /** Dimensions are the FULL screen: source map viewport is (width-32,height-32). */
    public static Browser project(Snapshot snapshot, String selectedKey, int viewportWidth, int viewportHeight) {
        if (snapshot == null) return empty();
        // Read before the malformed-candidate catch: wrong owning-thread use must still throw.
        var spec = snapshot.spec();
        try {
            List<Tab> tabs = new ArrayList<>();
            for (var category : ResearchCategoryDefinitions.newBuiltinCategories().values()) {
                if (category.researchKey != null && !snapshot.knowsStrict(category.researchKey)) continue;
                int total = 0, known = 0;
                for (Entry entry : spec.entries()) {
                    if (category.key.equals(entry.category()) && !hasMeta(entry, EnumResearchMeta.AUTOUNLOCK)) {
                        total++;
                        if (snapshot.knows(entry.key())) known++;
                    }
                }
                // Source float division: 0/0 is NaN, whose int conversion is zero.
                int percent = (int) ((float) known / total * 100.0F);
                tabs.add(new Tab(category.key, category.researchKey, category.icon,
                    category.background, category.background2, known, total, percent));
            }
            String selected = null;
            for (Tab tab : tabs) if (tab.key().equals(selectedKey)) { selected = tab.key(); break; }
            if (selected == null && !tabs.isEmpty()) selected = tabs.getFirst().key();
            List<Node> nodes = new ArrayList<>();
            Map<String, Node> visible = new LinkedHashMap<>();
            for (Entry entry : spec.entries()) {
                if (!Objects.equals(selected, entry.category())) continue;
                Decision available = snapshot.availability(entry.key());
                if (!available.visible()) continue;
                boolean hidden = hasMeta(entry, EnumResearchMeta.HIDDEN);
                int frameU = hasMeta(entry, EnumResearchMeta.ROUND) ? 144
                    : hasMeta(entry, EnumResearchMeta.HEX) ? 112 : 80;
                Node node = new Node(entry, entry.displayColumn(), entry.displayRow(),
                    entry.displayColumn() * 24, entry.displayRow() * 24, available,
                    frameU, 48 + (hidden ? 32 : 0), hasMeta(entry, EnumResearchMeta.SPIKY),
                    entry.stages().values().stream().anyMatch(stage -> stage.warp() > 0),
                    snapshot.knowledgeStatus(entry.key()) == EnumResearchStatus.COMPLETE);
                nodes.add(node);
                visible.put(entry.key(), node);
            }
            List<Link> links = new ArrayList<>();
            var byKey = spec.entriesByKey();
            for (Node source : nodes) {
                Entry entry = source.rawSpec();
                boolean reverse = hasMeta(entry, EnumResearchMeta.REVERSE);
                for (String rawParent : entry.parents().values()) {
                    Entry parent = byKey.get(cleanParent(rawParent));
                    if (parent == null || !Objects.equals(selected, parent.category())
                        || parent.siblings().values().contains(entry.key()) || rawParent.startsWith("~")) continue;
                    boolean known = snapshot.knowsStrict(rawParent);
                    if (!known && !visible.containsKey(parent.key())) continue;
                    float color = known ? 0.6F : 0.2F;
                    links.add(new Link(LinkKind.PARENT, rawParent, entry.key(), parent.key(),
                        source.column(), source.row(), parent.displayColumn(), parent.displayRow(),
                        known, reverse, true, color, color, color, known ? 3.0F : 2.0F));
                }
                for (String rawSibling : entry.siblings().values()) {
                    // Unlike parent lookup, source sibling lookup is LITERAL, without ~/@ cleanup.
                    Entry sibling = byKey.get(rawSibling);
                    if (sibling == null || !Objects.equals(selected, sibling.category()) || rawSibling.startsWith("~")) continue;
                    boolean known = snapshot.knowsStrict(sibling.key());
                    if (!known && !visible.containsKey(sibling.key())) continue;
                    links.add(new Link(LinkKind.SIBLING, rawSibling, sibling.key(), entry.key(),
                        sibling.displayColumn(), sibling.displayRow(), source.column(), source.row(),
                        known, reverse, false, known ? 0.3F : 0.1875F,
                        known ? 0.3F : 0.1875F, known ? 0.4F : 0.25F, known ? 1.0F : 0.0F));
                }
            }
            return new Browser(tabs, nodes, links, selected,
                bounds(nodes, viewportWidth - 32, viewportHeight - 32));
        } catch (RuntimeException malformed) {
            return empty();
        }
    }

    private static boolean hasMeta(Entry entry, EnumResearchMeta meta) {
        return entry.meta().values().stream().anyMatch(token -> token.value() == meta);
    }

    /** ResearchEntry.getParentsClean: strip one leading ~, then the first @ and its suffix. */
    private static String cleanParent(String raw) {
        String result = raw.startsWith("~") ? raw.substring(1) : raw;
        int at = result.indexOf('@');
        return at < 0 ? result : result.substring(0, at);
    }

    private static Bounds bounds(List<Node> nodes, int screenX, int screenY) {
        if (nodes.isEmpty()) return new Bounds(0, 0, 0, 0, true);
        int left = 99999, top = 99999, right = -99999, bottom = -99999;
        for (Node node : nodes) {
            left = Math.min(left, node.x() - screenX + 48);
            right = Math.max(right, node.x() - 24);
            top = Math.min(top, node.y() - screenY + 48);
            bottom = Math.max(bottom, node.y() - 24);
        }
        return new Bounds(left, top, right, bottom, false);
    }

    private static Browser empty() { return new Browser(List.of(), List.of(), List.of(), null, new Bounds(0, 0, 0, 0, true)); }
}
