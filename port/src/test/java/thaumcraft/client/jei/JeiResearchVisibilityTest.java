package thaumcraft.client.jei;

import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JeiResearchVisibilityTest {
    @Test void missingOwnerSnapshotCannotExposeGatedRecipes() {
        for (var rule : JeiResearchVisibility.Rule.values())
            assertFalse(JeiResearchVisibility.visible(true, "ALCHEMY", rule, null, null, null));
    }
    @Test void configAndUngatedRecipesDoNotRequireOwnerKnowledge() {
        for (var rule : JeiResearchVisibility.Rule.values()) {
            assertTrue(JeiResearchVisibility.visible(false, "ALCHEMY", rule, null, null, null));
            assertTrue(JeiResearchVisibility.visible(true, "", rule, null, null, null));
        }
    }
    @Test void stationsKeepTheirOwnCompletionAndExpressionSemantics() {
        Predicate<String> strict = key -> key.equals("COMPLETED") || key.equals("HEDGEALCHEMY@2");
        Predicate<String> known = key -> key.equals("IN_PROGRESS") || key.equals("A&&B");
        Predicate<String> literal = key -> key.equals("IN_PROGRESS") || key.equals("HEDGEALCHEMY@2");
        assertFalse(JeiResearchVisibility.visible(true, "IN_PROGRESS", JeiResearchVisibility.Rule.STRICT, strict, known, literal));
        assertTrue(JeiResearchVisibility.visible(true, "IN_PROGRESS", JeiResearchVisibility.Rule.KNOWN_LITERAL, strict, known, literal));
        assertTrue(JeiResearchVisibility.visible(true, "A&&B", JeiResearchVisibility.Rule.KNOWN, strict, known, literal));
        assertFalse(JeiResearchVisibility.visible(true, "A&&B", JeiResearchVisibility.Rule.KNOWN_LITERAL, strict, known, literal));
        assertTrue(JeiResearchVisibility.visible(true, "HEDGEALCHEMY@2", JeiResearchVisibility.Rule.STRICT, strict, known, literal));
        assertFalse(JeiResearchVisibility.visible(true, "HEDGEALCHEMY@3", JeiResearchVisibility.Rule.STRICT, strict, known, literal));
    }
    @Test void acceptedUnlockOrReconnectionImmediatelyChangesVisibility() {
        Predicate<String> unknown = key -> false, unlocked = key -> key.equals("INFUSION");
        assertFalse(JeiResearchVisibility.visible(true, "INFUSION", JeiResearchVisibility.Rule.KNOWN_LITERAL, unknown, unknown, unknown));
        assertTrue(JeiResearchVisibility.visible(true, "INFUSION", JeiResearchVisibility.Rule.KNOWN_LITERAL, unlocked, unlocked, unlocked));
        assertFalse(JeiResearchVisibility.visible(true, "INFUSION", JeiResearchVisibility.Rule.KNOWN_LITERAL, null, null, null));
    }
}
