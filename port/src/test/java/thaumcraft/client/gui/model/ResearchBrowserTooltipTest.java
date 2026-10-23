package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.client.gui.model.ResearchBrowserTooltip.Kind;
import thaumcraft.client.gui.model.ResearchBrowserTooltip.Line;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.research.catalog.ResearchCatalogClientSession.AcceptedView;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;
import thaumcraft.common.lib.research.catalog.ResearchClientView;
import thaumcraft.common.lib.research.catalog.ResearchClientView.Snapshot;

class ResearchBrowserTooltipTest {
    private LinkedHashMap<String, ResearchCategory> previous;
    private final PlayerKnowledge knowledge = new PlayerKnowledge();
    private final ResearchClientView client = new ResearchClientView();
    private final Object connection = new Object(), owner = new Object();

    @BeforeEach void isolate() {
        previous = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
    }
    @AfterEach void restore() { ResearchCategories.researchCategories = previous; }

    @Test void unknownResearchShowsSourceBeginHintAndNoIconPathOrServerCopy() {
        var entry = entry("E", 2);
        entry.add("icons", strings("thaumcraft:textures/items/thaumonomicon.png"));
        assertEquals(List.of(title("E"), new Line(Kind.BEGIN, "tc.research.begin", "")), lines(snapshot(entry), "E"));
        assertFalse(knowledge.isResearchKnown("E"));
    }

    @Test void knownStageZeroStillUsesBeginRatherThanARequestEligibilityMessage() {
        knowledge.addResearch("E");
        assertEquals(List.of(title("E"), new Line(Kind.BEGIN, "tc.research.begin", "")),
            lines(snapshot(entry("E", 2)), "E"));
        assertEquals(0, knowledge.getResearchStage("E"));
    }

    @Test void inProgressUsesExactSourceStoredStageAndCount() {
        knowledge.addResearch("E"); knowledge.setResearchStage("E", 2);
        assertEquals(List.of(title("E"), new Line(Kind.STAGE, "tc.research.stage", " 2/3")),
            lines(snapshot(entry("E", 3)), "E"));
    }

    @Test void completeOrStagelessResearchHasNoInventedStageHint() {
        knowledge.addResearch("E"); knowledge.setResearchStage("E", 4);
        assertEquals(List.of(title("E")), lines(snapshot(entry("E", 3)), "E"));
        assertEquals(List.of(title("EMPTY")), lines(snapshot(entry("EMPTY", 0)), "EMPTY"));
    }

    @Test void missingParentsUseRawStrictQueriesButOnlyCleanedTranslatedNames() {
        knowledge.addResearch("P"); knowledge.setResearchStage("P", 1);
        var child = entry("E", 1);
        child.add("parents", strings("P@2", "~P@2", "UNPORTED_MARKER"));
        var before = knowledge.serializeNBT().copy();
        assertEquals(List.of(title("E"), new Line(Kind.MISSING_PARENTS, "tc.researchmissing", ""),
            new Line(Kind.MISSING_PARENT, "name.P", ""), new Line(Kind.MISSING_PARENT, "name.P", ""),
            new Line(Kind.MISSING_PARENT, null, "")), lines(snapshot(entry("P", 3, 1), child), "E"));
        assertEquals(before, knowledge.serializeNBT());
    }

    @Test void missingParentsTakePrecedenceEvenForCompleteResearchLikeTheSource() {
        knowledge.addResearch("E"); knowledge.setResearchStage("E", 3);
        var child = entry("E", 1); child.add("parents", strings("MISSING"));
        assertEquals(List.of(title("E"), new Line(Kind.MISSING_PARENTS, "tc.researchmissing", ""),
            new Line(Kind.MISSING_PARENT, null, "")), lines(snapshot(child), "E"));
    }

    @Test void unreadyOrDifferentCatalogNodeCannotLeakOldTooltipContent() {
        var old = snapshot(entry("E", 1));
        var node = ResearchBrowserProjection.project(old, "BASICS", 320, 240).nodes().getFirst();
        assertTrue(ResearchBrowserTooltip.project(null, node).isEmpty());
        assertTrue(ResearchBrowserTooltip.project(old, null).isEmpty());
        assertTrue(ResearchBrowserTooltip.project(snapshot(entry("E", 1)), node).isEmpty());
    }

    @Test void sameCatalogOldOwnerNodeCannotSupplyOldStageOrPrerequisiteFlags() {
        var old = snapshot(entry("E", 1));
        var oldNode = ResearchBrowserProjection.project(old, "BASICS", 320, 240).nodes().getFirst();
        assertEquals(Kind.BEGIN, ResearchBrowserTooltip.project(old, oldNode).getLast().kind());
        var nextOwner = new Object();
        var nextKnowledge = new PlayerKnowledge();
        nextKnowledge.addResearch("E"); nextKnowledge.setResearchStage("E", 2);
        assertTrue(client.knowledgeApplied(connection, nextOwner, nextKnowledge));
        var current = client.capture(old.accepted(), nextOwner);
        assertNotNull(current);
        assertEquals(List.of(title("E")), ResearchBrowserTooltip.project(current, oldNode));
        assertFalse(knowledge.isResearchKnown("E"));
        assertEquals(2, nextKnowledge.getResearchStage("E"));
    }

    private List<Line> lines(Snapshot snapshot, String key) {
        var node = ResearchBrowserProjection.project(snapshot, "BASICS", 320, 240).nodes().stream()
            .filter(value -> value.rawSpec().key().equals(key)).findFirst().orElseThrow();
        return ResearchBrowserTooltip.project(snapshot, node);
    }

    private Snapshot snapshot(JsonObject... entries) {
        JsonObject root = new JsonObject(); JsonArray values = new JsonArray();
        for (JsonObject entry : entries) values.add(entry);
        root.add("entries", values);
        var spec = ResearchCatalogParser.parse(Map.of("test:tooltip", root.toString()),
            ResearchCategoryDefinitions.newBuiltinCategories().keySet());
        var catalog = new AcceptedView(connection, false, "1".repeat(64), spec);
        client.begin(connection);
        assertTrue(client.knowledgeApplied(connection, owner, knowledge));
        var snapshot = client.capture(catalog, owner); assertNotNull(snapshot); return snapshot;
    }
    private static Line title(String key) { return new Line(Kind.TITLE, "name." + key, ""); }
    private static JsonObject entry(String key, int stageCount) {
        return entry(key, stageCount, 0);
    }
    private static JsonObject entry(String key, int stageCount, int column) {
        JsonObject entry = new JsonObject();
        entry.addProperty("key", key); entry.addProperty("name", "name." + key); entry.addProperty("category", "BASICS");
        JsonArray location = new JsonArray(); location.add(column); location.add(0); entry.add("location", location);
        JsonArray stages = new JsonArray();
        for (int i = 0; i < stageCount; i++) { JsonObject stage = new JsonObject(); stage.addProperty("text", "text.E"); stages.add(stage); }
        entry.add("stages", stages); return entry;
    }
    private static JsonArray strings(String... values) {
        JsonArray array = new JsonArray(); for (String value : values) array.add(value); return array;
    }
}
