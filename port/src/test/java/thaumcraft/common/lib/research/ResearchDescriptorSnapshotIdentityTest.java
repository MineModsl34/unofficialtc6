package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.research.ResearchAddendum;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;

/** Addon equality overrides must not conceal replacement of mutable descriptor identities. */
class ResearchDescriptorSnapshotIdentityTest {
    private LinkedHashMap<String, ResearchCategory> previous;

    @BeforeEach
    void isolate() {
        previous = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = ResearchCategoryDefinitions.newBuiltinCategories();
    }

    @AfterEach
    void restore() { ResearchCategories.researchCategories = previous; }

    @Test
    void valueEqualEntryReplacementInsideTheSameMapIsNotTheCapturedEntry() {
        var first = new EqualEntry();
        first.setKey("ADDON_EQUAL"); first.setCategory("BASICS"); first.setName("test");
        var map = ResearchCategories.getResearchCategory("BASICS").research;
        map.put(first.getKey(), first);
        var snapshot = ResearchManager.captureDescriptorSnapshot();
        assertTrue(snapshot.unchanged());
        var replacement = new EqualEntry();
        replacement.setKey(first.getKey()); replacement.setCategory(first.getCategory()); replacement.setName(first.getName());
        assertEquals(first, replacement); // Deliberate legal-but-dangerous addon override.
        map.put(first.getKey(), replacement);
        assertFalse(snapshot.unchanged());
    }

    @Test
    void valueEqualStageReplacementInsideTheSameArrayIsNotTheCapturedStage() {
        var first = new EqualStage(); first.setText("test");
        var entry = entry(); entry.setStages(new ResearchStage[]{first});
        ResearchCategories.getResearchCategory("BASICS").research.put(entry.getKey(), entry);
        var snapshot = ResearchManager.captureDescriptorSnapshot();
        var replacement = new EqualStage(); replacement.setText(first.getText());
        assertEquals(first, replacement);
        entry.getStages()[0] = replacement;
        assertFalse(snapshot.unchanged());
    }

    @Test
    void valueEqualAddendumReplacementInsideTheSameArrayIsNotTheCapturedAddendum() {
        var first = new EqualAddendum(); first.setText("test");
        var entry = entry(); entry.setAddenda(new ResearchAddendum[]{first});
        ResearchCategories.getResearchCategory("BASICS").research.put(entry.getKey(), entry);
        var snapshot = ResearchManager.captureDescriptorSnapshot();
        var replacement = new EqualAddendum(); replacement.setText(first.getText());
        assertEquals(first, replacement);
        entry.getAddenda()[0] = replacement;
        assertFalse(snapshot.unchanged());
    }

    private static ResearchEntry entry() {
        var entry = new ResearchEntry();
        entry.setKey("ADDON_EQUAL"); entry.setCategory("BASICS"); entry.setName("test");
        return entry;
    }

    private static final class EqualEntry extends ResearchEntry {
        @Override public boolean equals(Object other) { return other instanceof EqualEntry; }
        @Override public int hashCode() { return 1; }
    }
    private static final class EqualStage extends ResearchStage {
        @Override public boolean equals(Object other) { return other instanceof EqualStage; }
        @Override public int hashCode() { return 1; }
    }
    private static final class EqualAddendum extends ResearchAddendum {
        @Override public boolean equals(Object other) { return other instanceof EqualAddendum; }
        @Override public int hashCode() { return 1; }
    }
}
