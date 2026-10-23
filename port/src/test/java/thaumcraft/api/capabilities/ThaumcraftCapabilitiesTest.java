package thaumcraft.api.capabilities;

import static org.junit.jupiter.api.Assertions.*;
import static thaumcraft.api.capabilities.ThaumcraftCapabilities.knowsResearchData;
import static thaumcraft.api.capabilities.ThaumcraftCapabilities.knowsResearchStrictData;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;

/** Exact original expression semantics, including its deliberately uncorrected regex-OR bug. */
class ThaumcraftCapabilitiesTest {
    private LinkedHashMap<String, ResearchCategory> previousCatalog;

    @BeforeEach
    void isolateMetadata() {
        previousCatalog = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
    }

    @AfterEach
    void restoreMetadata() {
        ResearchCategories.researchCategories = previousCatalog;
    }

    @Test
    void emptyQueriesReturnTrueWithoutAccessingPlayerData() {
        assertTrue(ThaumcraftCapabilities.knowsResearch(null));
        assertTrue(ThaumcraftCapabilities.knowsResearchStrict(null));
        assertTrue(knowsResearchData(null));
        assertTrue(knowsResearchStrictData(null));
    }

    @Test
    void ordinaryQueriesRequireEveryKeyAndConjunctionRecurses() {
        PlayerKnowledge data = new PlayerKnowledge();
        data.addResearch("ONE");
        data.addResearch("TWO");
        assertTrue(knowsResearchData(data, "ONE", "TWO"));
        assertFalse(knowsResearchData(data, "ONE", "MISSING"));
        assertTrue(knowsResearchData(data, "ONE&&TWO"));
        assertFalse(knowsResearchData(data, "ONE&&MISSING"));
        assertFalse(knowsResearchData(data, "MISSING", "ONE||TWO"));
        assertTrue(knowsResearchData(data, "ONE&&"));
        assertTrue(knowsResearchData(data, "&&")); // split drops all trailing empty tokens
    }

    @Test
    void failedOrFallsThroughEvenWhenNoAlternativeIsKnown() {
        PlayerKnowledge data = new PlayerKnowledge();
        assertTrue(knowsResearchData(data, "ALPHA||BETA"));
        assertTrue(knowsResearchStrictData(data, "ALPHA||BETA"));
        assertFalse(knowsResearchData(data, "ALPHA||BETA", "MISSING"));
        data.addResearch("PRESENT");
        assertTrue(knowsResearchData(data, "ALPHA||BETA", "PRESENT"));
    }

    @Test
    void regexOrChecksIndividualCharactersNotMultiLetterNames() {
        PlayerKnowledge data = new PlayerKnowledge();
        data.addResearch("ALPHA");
        assertFalse(knowsResearchData(data, "ALPHA||BETA", "MISSING"));
        data.addResearch("A");
        assertTrue(knowsResearchData(data, "ALPHA||BETA", "MISSING"));
        assertTrue(knowsResearchStrictData(data, "ALPHA||BETA", "MISSING"));
        assertFalse(knowsResearchData(data, "alpha||beta", "MISSING"));
    }

    @Test
    void aLiteralPipeCanSatisfyRegexOrAndSkipSubsequentKeys() {
        PlayerKnowledge data = new PlayerKnowledge();
        data.addResearch("|");
        assertTrue(knowsResearchData(data, "NONE||ABSENT", "MISSING"));
        assertTrue(knowsResearchStrictData(data, "NONE||ABSENT", "MISSING"));
    }

    @Test
    void conjunctionHasPriorityOverOrAndDoesNotSkipOuterRemainingKeys() {
        PlayerKnowledge data = new PlayerKnowledge();
        data.addResearch("OK");
        assertFalse(knowsResearchData(data, "ALPHA||BETA&&MISSING"));
        data.addResearch("A");
        assertTrue(knowsResearchData(data, "ALPHA||BETA&&OK"));
        assertFalse(knowsResearchData(data, "ALPHA||BETA&&OK", "MISSING"));
        // The nested OR early-returns the recursive expression before checking MISSING.
        assertTrue(knowsResearchData(data, "ALPHA||BETA&&MISSING"));
    }

    @Test
    void strictQueriesRequireCompletionExceptQualifiedStageQueries() {
        ResearchCategory category = new ResearchCategory("TEST", null, null, null, null);
        ResearchEntry entry = new ResearchEntry();
        entry.setKey("STAGED");
        entry.setStages(new ResearchStage[2]);
        category.research.put(entry.getKey(), entry);
        ResearchCategories.researchCategories.put(category.key, category);
        PlayerKnowledge data = new PlayerKnowledge();
        data.addResearch("STAGED");
        data.setResearchStage("STAGED", 1);
        assertTrue(knowsResearchData(data, "STAGED"));
        assertFalse(knowsResearchStrictData(data, "STAGED"));
        assertTrue(knowsResearchStrictData(data, "STAGED@1"));
        assertFalse(knowsResearchStrictData(data, "STAGED@2"));
        data.setResearchStage("STAGED", 2);
        assertFalse(knowsResearchStrictData(data, "STAGED"));
        data.setResearchStage("STAGED", 3);
        assertTrue(knowsResearchStrictData(data, "STAGED"));
        assertTrue(knowsResearchStrictData(data, ""));
    }

    @Test
    void strictOrCharactersStillRequireTheirOwnCompletion() {
        ResearchCategory category = new ResearchCategory("TEST", null, null, null, null);
        ResearchEntry entry = new ResearchEntry();
        entry.setKey("A");
        entry.setStages(new ResearchStage[1]);
        category.research.put("A", entry);
        ResearchCategories.researchCategories.put(category.key, category);
        PlayerKnowledge data = new PlayerKnowledge();
        data.addResearch("A");
        assertTrue(knowsResearchData(data, "ALPHA||BETA", "MISSING"));
        assertFalse(knowsResearchStrictData(data, "ALPHA||BETA", "MISSING"));
        data.setResearchStage("A", 2);
        assertTrue(knowsResearchStrictData(data, "ALPHA||BETA", "MISSING"));
    }

    @Test
    void nullAndNakedQualifierErrorsRemainVisible() {
        PlayerKnowledge data = new PlayerKnowledge();
        assertThrows(NullPointerException.class, () -> knowsResearchData(data, (String) null));
        assertThrows(NullPointerException.class, () -> knowsResearchStrictData(data, (String) null));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> knowsResearchData(data, "@"));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> knowsResearchStrictData(data, "@"));
    }
}
