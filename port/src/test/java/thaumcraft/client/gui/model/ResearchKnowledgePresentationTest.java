package thaumcraft.client.gui.model;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
class ResearchKnowledgePresentationTest {
    @Test void positiveRawCountsPreserveSourceDivisionRemainderAndCompressedRows() {
        var balances = ResearchKnowledgePresentation.project(List.of("BASICS","ARTIFICE"), (type, category) ->
            type == EnumKnowledgeType.THEORY ? (category.equals("BASICS") ? 33 : 0) : category.equals("BASICS") ? 15 : 16);
        assertEquals(3, balances.size()); assertEquals(2, ResearchKnowledgePresentation.rowCount(balances));
        var theory = balances.getFirst(); assertEquals(1, theory.amount()); assertEquals(1, theory.partial()); assertEquals(0, theory.progressPixels());
        var observation = balances.get(1); assertEquals(0, observation.amount()); assertEquals(15, observation.progressPixels());
        assertEquals(1, observation.row()); assertEquals(0, observation.column()); assertEquals(1, balances.getLast().column());
        assertThrows(UnsupportedOperationException.class, () -> balances.clear());
    }
    @Test void emptyOrNonpositiveBalancesDoNotReserveSyntheticKnowledgeRows() {
        assertTrue(ResearchKnowledgePresentation.project(List.of("BASICS"), (type, category) -> -1).isEmpty());
        assertEquals(0, ResearchKnowledgePresentation.rowCount(List.of()));
        assertEquals(1, ResearchKnowledgePresentation.rowCount(ResearchKnowledgePresentation.project(List.of("BASICS"),
            (type, category) -> type == EnumKnowledgeType.OBSERVATION ? 1 : 0)));
    }
}
