package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.client.gui.model.ResearchBrowserProjection.Bounds;
import thaumcraft.client.gui.model.ResearchBrowserProjection.LinkKind;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.research.ResearchBrowserAvailability.Reason;
import thaumcraft.common.lib.research.catalog.ResearchCatalogClientSession.AcceptedView;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;
import thaumcraft.common.lib.research.catalog.ResearchClientView;
import thaumcraft.common.lib.research.catalog.ResearchClientView.Snapshot;

/** Source-derived browser descriptors, not GUI layout/visual pose, packet send or progression. */
class ResearchBrowserProjectionTest {
    private LinkedHashMap<String, ResearchCategory> previous;
    private PlayerKnowledge knowledge;
    private ResearchClientView client;
    private Object connection;
    private Object owner;

    @BeforeEach
    void isolate() {
        previous = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
        knowledge = new PlayerKnowledge(); client = new ResearchClientView();
        connection = new Object(); owner = new Object();
    }

    @AfterEach
    void restore() { ResearchCategories.researchCategories = previous; }

    @Test
    void pendingOwnerViewHasNoTabsNodesLinksSelectionOrNonemptyBounds() {
        var pending = ResearchBrowserProjection.project(null, "BASICS", 320, 240);
        assertTrue(pending.tabs().isEmpty()); assertTrue(pending.nodes().isEmpty()); assertTrue(pending.links().isEmpty());
        assertNull(pending.selected()); assertEquals(new Bounds(0, 0, 0, 0, true), pending.bounds());
    }

    @Test
    void accessibleTabsFollowSourceRegistrationOrderAndStrictGatesNotClassificationArrayOrder() {
        for (String gate : List.of("UNLOCKAUROMANCY", "UNLOCKALCHEMY", "UNLOCKARTIFICE", "UNLOCKINFUSION",
            "UNLOCKGOLEMANCY", "UNLOCKELDRITCH")) knowledge.addResearch(gate); // Unknown catalog markers are complete.
        var browser = project(snapshot(), null);
        assertEquals(List.of("BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH"),
            browser.tabs().stream().map(ResearchBrowserProjection.Tab::key).toList());
        assertEquals("BASICS", browser.selected());
        for (var tab : browser.tabs()) {
            assertEquals(0, tab.total()); assertEquals(0, tab.known()); assertEquals(0, tab.percent());
            var definition = ResearchCategoryDefinitions.newBuiltinCategories().get(tab.key());
            assertEquals(definition.icon, tab.icon()); assertEquals(definition.background, tab.background());
            assertEquals(definition.background2, tab.background2()); assertEquals(definition.researchKey, tab.unlockKey());
        }
        var gate = entry("UNLOCKAUROMANCY", "BASICS", 1, 0, 0);
        knowledge.setResearchStage("UNLOCKAUROMANCY", 1);
        var incomplete = project(snapshot(gate), "AUROMANCY");
        assertFalse(incomplete.tabs().stream().anyMatch(tab -> tab.key().equals("AUROMANCY")));
        assertEquals("BASICS", incomplete.selected());
        knowledge.setResearchStage("UNLOCKAUROMANCY", 2);
        var complete = project(snapshot(gate), "AUROMANCY");
        assertEquals("AUROMANCY", complete.selected());
    }

    @Test
    void tabPercentCountsKnownZeroAndInProgressExcludesAutounlockAndTruncatesSourceFloat() {
        knowledge.addResearch("ZERO");
        knowledge.addResearch("IN_PROGRESS"); knowledge.setResearchStage("IN_PROGRESS", 1);
        knowledge.addResearch("AUTO");
        var auto = entry("AUTO", "BASICS", 1, 3, 0); auto.add("meta", strings("AUTOUNLOCK"));
        var browser = project(snapshot(entry("ZERO", "BASICS", 1, 0, 0),
            entry("IN_PROGRESS", "BASICS", 2, 1, 0), entry("UNKNOWN", "BASICS", 1, 2, 0), auto), "BASICS");
        var tab = browser.tabs().getFirst();
        assertEquals(2, tab.known()); assertEquals(3, tab.total()); assertEquals(66, tab.percent());
        assertFalse(browser.nodes().get(0).complete()); assertFalse(browser.nodes().get(1).complete());
        assertTrue(browser.nodes().stream().anyMatch(node -> node.rawSpec().key().equals("AUTO")));
    }

    @Test
    void selectionUsesOnlyAnAccessibleBuiltinAndPreservesSelectedCategorySourceEntryOrder() {
        knowledge.addResearch("UNLOCKALCHEMY");
        var snapshot = snapshot(entry("BASICS_NODE", "BASICS", 1, 0, 0),
            entry("LATE_X", "ALCHEMY", 1, 8, 0), entry("EARLY_X", "ALCHEMY", 1, -4, 0));
        var selected = project(snapshot, "ALCHEMY");
        assertEquals("ALCHEMY", selected.selected());
        assertEquals(List.of("LATE_X", "EARLY_X"), selected.nodes().stream().map(node -> node.rawSpec().key()).toList());
        assertEquals(List.of("BASICS_NODE"), project(snapshot, "AUROMANCY").nodes().stream()
            .map(node -> node.rawSpec().key()).toList());
        assertEquals("BASICS", project(snapshot, "NO_CATEGORY").selected());
    }

    @Test
    void nodeVisibilityRetainsKnownHiddenAndVisibleLockedNodesButOmitsUnknownHiddenAndItsLiteralChildren() {
        var hidden = entry("HIDDEN", "BASICS", 1, 0, 0); hidden.add("meta", strings("HIDDEN"));
        var child = entry("HIDDEN_CHILD", "BASICS", 1, 1, 0); child.add("parents", strings("HIDDEN"));
        var tilde = entry("TILDE_CHILD", "BASICS", 1, 2, 0); tilde.add("parents", strings("~HIDDEN"));
        var locked = entry("LOCKED", "BASICS", 1, 3, 0); locked.add("parents", strings("!missing"));
        var knownHidden = entry("KNOWN_HIDDEN", "BASICS", 1, 4, 0); knownHidden.add("meta", strings("HIDDEN"));
        knowledge.addResearch("KNOWN_HIDDEN");
        var browser = project(snapshot(hidden, child, tilde, locked, knownHidden), "BASICS");
        assertEquals(List.of("TILDE_CHILD", "LOCKED", "KNOWN_HIDDEN"), browser.nodes().stream()
            .map(node -> node.rawSpec().key()).toList());
        assertEquals(Reason.MISSING_PARENTS, browser.nodes().get(0).availability().reason());
        assertEquals(Reason.MISSING_PARENTS, browser.nodes().get(1).availability().reason());
        assertEquals(Reason.RESUME_KNOWN_ZERO, browser.nodes().get(2).availability().reason());
        assertTrue(browser.links().isEmpty()); // Tilde parent is not a drawn link; missing marker has no node.
    }

    @Test
    void sourceGridPixelsFramesWarpAndCompleteAreImmutableDescriptorsNotSyntheticFlagEffects() {
        var square = entry("SQUARE", "BASICS", 1, -3, 2);
        var hex = entry("HEX", "BASICS", 1, 1, -2); hex.add("meta", strings("HEX"));
        var round = entry("ROUND", "BASICS", 2, 2, 1); round.add("meta", strings("HEX", "ROUND", "HIDDEN", "SPIKY"));
        round.getAsJsonArray("stages").get(0).getAsJsonObject().addProperty("warp", -3);
        round.getAsJsonArray("stages").get(1).getAsJsonObject().addProperty("warp", 1);
        knowledge.addResearch("ROUND"); knowledge.setResearchStage("ROUND", 3);
        var nodes = project(snapshot(square, hex, round), "BASICS").nodes();
        assertEquals(-3, nodes.get(0).column()); assertEquals(2, nodes.get(0).row());
        assertEquals(-72, nodes.get(0).x()); assertEquals(48, nodes.get(0).y());
        assertEquals(80, nodes.get(0).frameU()); assertEquals(48, nodes.get(0).frameV());
        assertFalse(nodes.get(0).hasWarp()); assertFalse(nodes.get(0).complete());
        assertEquals(112, nodes.get(1).frameU());
        assertEquals(144, nodes.get(2).frameU()); assertEquals(80, nodes.get(2).frameV());
        assertTrue(nodes.get(2).spiky()); assertTrue(nodes.get(2).hasWarp()); assertTrue(nodes.get(2).complete());
    }

    @Test
    void parentLinksCleanLookupButStrictlyTestRawReferenceOmitTildeAndRetainDuplicatesAndChildReverse() {
        knowledge.addResearch("P"); knowledge.setResearchStage("P", 1);
        var parent = entry("P", "BASICS", 2, -2, 3);
        var child = entry("CHILD", "BASICS", 1, 4, -1);
        child.add("parents", strings("P@1", "P", "~P@1", "MISSING", "P@1", "CROSS"));
        child.add("meta", strings("REVERSE"));
        var cross = entry("CROSS", "AUROMANCY", 1, 0, 0);
        var browser = project(snapshot(parent, child, cross), "BASICS");
        var links = browser.links();
        assertEquals(3, links.size());
        assertEquals(List.of("P@1", "P", "P@1"), links.stream().map(ResearchBrowserProjection.Link::rawReference).toList());
        var qualified = links.get(0);
        assertEquals(LinkKind.PARENT, qualified.kind()); assertEquals("CHILD", qualified.fromKey()); assertEquals("P", qualified.toKey());
        assertEquals(4, qualified.x1()); assertEquals(-1, qualified.y1()); assertEquals(-2, qualified.x2()); assertEquals(3, qualified.y2());
        assertTrue(qualified.knownTarget()); assertTrue(qualified.reverse()); assertTrue(qualified.arrow());
        assertEquals(0.6F, qualified.r()); assertEquals(3.0F, qualified.zMod());
        var incomplete = links.get(1);
        assertFalse(incomplete.knownTarget()); assertEquals(0.2F, incomplete.g()); assertEquals(2.0F, incomplete.zMod());
    }

    @Test
    void parentsDeclaredAsParentSiblingsSuppressOnlyTheirParentLineAndUseSiblingEndpointOrder() {
        var parent = entry("P", "BASICS", 1, 1, 2);
        parent.add("siblings", strings("CHILD")); parent.add("meta", strings("REVERSE"));
        var child = entry("CHILD", "BASICS", 1, 3, 4); child.add("parents", strings("P"));
        var links = project(snapshot(parent, child), "BASICS").links();
        assertEquals(1, links.size());
        var sibling = links.getFirst();
        assertEquals(LinkKind.SIBLING, sibling.kind()); assertEquals("CHILD", sibling.fromKey()); assertEquals("P", sibling.toKey());
        assertEquals(3, sibling.x1()); assertEquals(4, sibling.y1()); assertEquals(1, sibling.x2()); assertEquals(2, sibling.y2());
        assertTrue(sibling.reverse()); assertFalse(sibling.arrow()); assertFalse(sibling.knownTarget());
        assertEquals(0.1875F, sibling.r()); assertEquals(0.25F, sibling.b()); assertEquals(0.0F, sibling.zMod());
    }

    @Test
    void siblingLinksUseLiteralLookupSourceVisibilityAndDifferentKnownAndVisibleStyles() {
        var source = entry("SOURCE", "BASICS", 1, 0, 0);
        source.add("siblings", strings("SIB", "SIB@1", "~SIB", "INVISIBLE", "CROSS", "SIB"));
        var sibling = entry("SIB", "BASICS", 1, 2, 0);
        var hidden = entry("INVISIBLE", "BASICS", 1, 3, 0); hidden.add("meta", strings("HIDDEN"));
        var cross = entry("CROSS", "ALCHEMY", 1, 4, 0);
        var dark = project(snapshot(source, sibling, hidden, cross), "BASICS").links();
        assertEquals(2, dark.size()); // Qualified/tilde lookup is NOT normalized to SIB.
        assertFalse(dark.getFirst().knownTarget()); assertEquals(0.1875F, dark.getFirst().g());
        knowledge.addResearch("SIB"); knowledge.setResearchStage("SIB", 2);
        var bright = project(snapshot(source, sibling, hidden, cross), "BASICS").links();
        assertEquals(2, bright.size()); assertTrue(bright.getFirst().knownTarget());
        assertEquals(0.3F, bright.getFirst().r()); assertEquals(0.4F, bright.getFirst().b()); assertEquals(1.0F, bright.getFirst().zMod());
        assertEquals("SIB", bright.getFirst().fromKey()); assertEquals("SOURCE", bright.getFirst().toKey());
    }

    @Test
    void visibleBoundsUseFullScreenMinus32SourceFormulaAndIntegerCenterWithSafeEmptyMap() {
        var browser = project(snapshot(entry("LOW", "BASICS", 1, -2, -1), entry("HIGH", "BASICS", 1, 5, 3)), "BASICS");
        assertEquals(new Bounds(-288, -184, 96, 48, false), browser.bounds());
        assertEquals(-96, browser.bounds().centerX()); assertEquals(-68, browser.bounds().centerY());
        var odd = new Bounds(-5, -9, 2, 2, false);
        assertEquals(-1, odd.centerX()); assertEquals(-3, odd.centerY()); // Not floating center or floor.
        var hidden = entry("HIDDEN", "BASICS", 1, 10, 10); hidden.add("meta", strings("HIDDEN"));
        var empty = project(snapshot(hidden), "BASICS");
        assertEquals("BASICS", empty.selected()); assertTrue(empty.nodes().isEmpty());
        assertEquals(new Bounds(0, 0, 0, 0, true), empty.bounds());
    }

    @Test
    void modelIsDefensivelyImmutableIndependentOfGlobalsAndLaterOwnerChangesAndNeverMutatesNbt() {
        var node = entry("NODE", "BASICS", 1, 0, 0);
        var snapshot = snapshot(node);
        CompoundTag before = knowledge.serializeNBT().copy();
        var browser = project(snapshot, "BASICS");
        assertThrows(UnsupportedOperationException.class, () -> browser.tabs().clear());
        assertThrows(UnsupportedOperationException.class, () -> browser.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> browser.links().clear());
        assertThrows(UnsupportedOperationException.class, () -> browser.nodes().getFirst().rawSpec().parents().values().add("FAKE"));
        assertEquals(before, knowledge.serializeNBT());
        knowledge.addResearch("NODE"); knowledge.setResearchStage("NODE", 2);
        ResearchCategories.researchCategories = null;
        assertEquals(browser, project(snapshot, "BASICS"));
        assertFalse(browser.nodes().getFirst().complete());
        assertNull(ResearchCategories.researchCategories);
    }

    @Test
    void malformedNativeQueryRefusesWholeProjectionRatherThanPartialTabsOrNodes() {
        // Parser/source key remains opaque; literal source query of '@' has its native index error.
        var snapshot = snapshot(entry("GOOD", "BASICS", 1, 0, 0), entry("@", "BASICS", 1, 1, 0));
        CompoundTag before = knowledge.serializeNBT().copy();
        var model = project(snapshot, "BASICS");
        assertTrue(model.tabs().isEmpty()); assertTrue(model.nodes().isEmpty()); assertTrue(model.links().isEmpty());
        assertNull(model.selected()); assertTrue(model.bounds().empty());
        assertEquals(before, knowledge.serializeNBT());
    }

    private ResearchBrowserProjection.Browser project(Snapshot snapshot, String selected) {
        return ResearchBrowserProjection.project(snapshot, selected, 320, 240);
    }

    private Snapshot snapshot(JsonObject... entries) {
        JsonObject root = new JsonObject(); JsonArray array = new JsonArray();
        for (JsonObject entry : entries) array.add(entry);
        root.add("entries", array);
        var spec = ResearchCatalogParser.parse(Map.of("test:browser", root.toString()),
            ResearchCategoryDefinitions.newBuiltinCategories().keySet());
        var catalog = new AcceptedView(connection, false, "1".repeat(64), spec);
        client.begin(connection);
        assertTrue(client.knowledgeApplied(connection, owner, knowledge));
        var snapshot = client.capture(catalog, owner); assertNotNull(snapshot);
        return snapshot;
    }

    private static JsonObject entry(String key, String category, int stageCount, int column, int row) {
        JsonObject entry = new JsonObject();
        entry.addProperty("key", key); entry.addProperty("name", "research.title"); entry.addProperty("category", category);
        JsonArray location = new JsonArray(); location.add(column); location.add(row); entry.add("location", location);
        JsonArray stages = new JsonArray();
        for (int i = 0; i < stageCount; i++) {
            JsonObject stage = new JsonObject(); stage.addProperty("text", "stage.text"); stages.add(stage);
        }
        entry.add("stages", stages);
        return entry;
    }

    private static JsonArray strings(String... values) {
        JsonArray array = new JsonArray(); for (String value : values) array.add(value); return array;
    }
}
