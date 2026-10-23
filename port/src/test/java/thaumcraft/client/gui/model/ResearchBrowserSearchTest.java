package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCatalogClientSession.AcceptedView;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;
import thaumcraft.common.lib.research.catalog.ResearchClientView;

class ResearchBrowserSearchTest {
    private LinkedHashMap<String, ResearchCategory> previous;
    @BeforeEach void isolate() { previous = ResearchCategories.researchCategories; ResearchCategories.researchCategories = new LinkedHashMap<>(); }
    @AfterEach void restore() { ResearchCategories.researchCategories = previous; }
    @Test void searchOnlyAdvertisesKnownEntriesAccessibleCategoriesAndSourceStagePlusOneRecipes() {
        var owner = new PlayerKnowledge(); owner.addResearch("E"); owner.setResearchStage("E", 1);
        var snapshot = snapshot(owner);
        var before = owner.serializeNBT();
        var results = ResearchBrowserSearch.project(snapshot, List.of("BASICS"), "", key -> "localized:" + key,
            recipe -> List.of(recipe.toString()));
        assertEquals(3, results.size());
        assertTrue(results.stream().anyMatch(result -> result.category() && result.key().equals("BASICS")));
        assertTrue(results.stream().anyMatch(result -> !result.category() && result.recipe() == null && result.key().equals("E")));
        assertTrue(results.stream().anyMatch(result -> result.recipe() != null && result.recipe().toString().equals("test:third")));
        assertTrue(results.stream().noneMatch(result -> result.key().equals("UNKNOWN")));
        assertEquals(before, owner.serializeNBT());
    }
    @Test void categoryMatchesKeyRatherThanInventedTranslatedCategorySearchAndRecipeHandlesAreDeduplicated() {
        var owner = new PlayerKnowledge(); owner.addResearch("E"); owner.setResearchStage("E", 1);
        var snapshot = snapshot(owner);
        assertEquals(1, ResearchBrowserSearch.project(snapshot, List.of("BASICS"), "bas", key -> "Основы", recipe -> List.of("item")).size());
        assertTrue(ResearchBrowserSearch.project(snapshot, List.of("BASICS"), "основы", key -> key.equals("tc.research_category.BASICS") ? "Основы" : "Entry", recipe -> List.of("item")).isEmpty());
        assertEquals(1, ResearchBrowserSearch.project(snapshot, List.of(), "third", key -> key, recipe -> List.of(recipe.toString())).size());
        assertTrue(ResearchBrowserSearch.project(null, List.of("BASICS"), "", key -> key, recipe -> List.of()).isEmpty());
    }
    private static ResearchClientView.Snapshot snapshot(PlayerKnowledge knowledge) {
        String json = """
            {"entries":[{"key":"E","name":"Entry","category":"BASICS","location":[0,0],"stages":[
              {"text":"first","recipes":["test:first"]},{"text":"second","recipes":["test:second"]},
              {"text":"third","recipes":["test:third","test:third"]}]},
              {"key":"UNKNOWN","name":"Unknown","category":"BASICS","location":[2,0],"stages":[{"text":"hidden"}]}]}
            """;
        var spec = ResearchCatalogParser.parse(Map.of("test:search", json), ResearchCategoryDefinitions.newBuiltinCategories().keySet());
        Object connection = new Object(), player = new Object();
        var accepted = new AcceptedView(connection, false, "1".repeat(64), spec);
        var view = new ResearchClientView(); view.begin(connection); assertTrue(view.knowledgeApplied(connection, player, knowledge));
        var snapshot = view.capture(accepted, player); assertNotNull(snapshot); return snapshot;
    }
}
