package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Stage;

class ResearchRequirementStatusTest {
    @Test void sourceChecksUseExactStrictKeysLegacyCraftMarkerAndKnowledgeAmount() {
        Stage stage = stage("""
            {"text":"text","required_research":["P@2","!aer"],"required_item":["minecraft:stick;2;0"],
              "required_craft":["minecraft:crafting_table"],"required_knowledge":["THEORY;BASICS;2"]}
            """);
        var status = ResearchRequirementStatus.evaluate(stage, key -> List.of("P@2","!aer").contains(key),
            key -> key.equals("[#]-123456"), token -> token.count() == 2, token -> -123456, token -> 2);
        assertTrue(status.all()); assertEquals(List.of(true, true), status.research()); assertEquals(List.of(true), status.craft());
        assertTrue(status.satisfied(ResearchRequirementPresentation.Kind.CRAFT, 0));
        assertFalse(status.satisfied(ResearchRequirementPresentation.Kind.CRAFT, 1));
        assertThrows(UnsupportedOperationException.class, () -> status.research().clear());
    }
    @Test void unresolvedCraftNeverChecksInventedModernHashAndDoesNotSatisfyStage() {
        AtomicInteger markerQueries = new AtomicInteger();
        var status = ResearchRequirementStatus.evaluate(stage("{\"text\":\"text\",\"required_craft\":[\"thaumcraft:future\"]}"),
            key -> true, key -> { markerQueries.incrementAndGet(); return true; }, token -> true, token -> null, token -> 100);
        assertEquals(0, markerQueries.get()); assertFalse(status.all()); assertEquals(List.of(false), status.craft());
    }
    @Test void eachFailedSourceRequirementKeepsAdvanceDisabledWithoutPayingOrGranting() {
        Stage stage = stage("""
            {"text":"text","required_research":["P"],"required_item":["minecraft:stick"],
             "required_craft":["minecraft:crafting_table"],"required_knowledge":["OBSERVATION;BASICS;2"]}
            """);
        assertFalse(ResearchRequirementStatus.evaluate(stage, key -> false, key -> true, token -> true, token -> 123, token -> 2).all());
        assertFalse(ResearchRequirementStatus.evaluate(stage, key -> true, key -> true, token -> false, token -> 123, token -> 2).all());
        assertFalse(ResearchRequirementStatus.evaluate(stage, key -> true, key -> false, token -> true, token -> 123, token -> 2).all());
        assertFalse(ResearchRequirementStatus.evaluate(stage, key -> true, key -> true, token -> true, token -> 123, token -> 1).all());
        assertTrue(ResearchRequirementStatus.evaluate(stage("{\"text\":\"text\"}"), key -> false, key -> false, token -> false, token -> null, token -> 0).all());
        assertFalse(ResearchRequirementStatus.empty().all());
    }
    private static Stage stage(String stage) {
        String json = "{\"entries\":[{\"key\":\"E\",\"name\":\"name\",\"category\":\"BASICS\",\"location\":[0,0],\"stages\":[" + stage + "]}]}";
        return ResearchCatalogParser.parse(Map.of("test:requirement", json), java.util.Set.of("BASICS")).entries().getFirst().stages().values().getFirst();
    }
}
