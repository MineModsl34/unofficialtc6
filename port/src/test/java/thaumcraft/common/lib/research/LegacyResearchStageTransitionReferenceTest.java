package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;

/**
 * Independent control-flow oracle transcribed from TC6 ResearchManager.progressResearch
 * (reference/tc6/src, lines 118-170). This is source comparison, NOT an executed original
 * JVM/gameplay report. It does not check requirements, events, siblings or award warp.
 */
class LegacyResearchStageTransitionReferenceTest {
    @Test
    void seventySixThousandFiveHundredValidStageShapesMatchSourceArithmetic() {
        int comparisons = 0;
        int[] warpValues = {0, 1, -3, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int length = 1; length <= 6; length++) {
            for (int emptyPositions = 0; emptyPositions < (1 << length); emptyPositions++) {
                for (int nonNullRequirements = 1; nonNullRequirements < 16; nonNullRequirements++) {
                    for (int warpOffset = 0; warpOffset < warpValues.length; warpOffset++) {
                        ResearchStage[] stages = new ResearchStage[length];
                        for (int i = 0; i < length; i++) {
                            stages[i] = new ResearchStage();
                            stages[i].setWarp(warpValues[(i + warpOffset) % warpValues.length]);
                            if ((emptyPositions & (1 << i)) == 0) {
                                // Empty API arrays are intentionally NON-null, not source-empty.
                                if ((nonNullRequirements & 1) != 0) stages[i].setCraft(new Object[0]);
                                if ((nonNullRequirements & 2) != 0) stages[i].setObtain(new Object[0]);
                                if ((nonNullRequirements & 4) != 0) stages[i].setKnow(new ResearchStage.Knowledge[0]);
                                if ((nonNullRequirements & 8) != 0) stages[i].setResearch(new String[0]);
                            }
                        }
                        ResearchEntry entry = entry(stages);
                        for (int stored = 0; stored <= length + 2; stored++) {
                            SourceTransition expected = source(stages, stored);
                            var actual = LegacyResearchStageTransition.calculate(entry, stored);
                            String context = "n=" + length + ", empty=" + emptyPositions + ", requirements="
                                + nonNullRequirements + ", warpOffset=" + warpOffset + ", stored=" + stored;
                            assertEquals(stored, actual.previousStage(), context);
                            assertTrue(actual.writesStage(), context);
                            assertEquals(expected.nextStage(), actual.nextStage(), context);
                            assertEquals(expected.completed(), actual.completed(), context);
                            assertEquals(expected.warp(), actual.warp(), context);
                            comparisons++;
                        }
                    }
                }
            }
        }
        assertEquals(76_500, comparisons);
    }

    @Test
    void onlyFinalEmptyStageSkipsAndSourceWarpDoesNotDefineEmptiness() {
        var first = new ResearchStage();
        first.setResearch(new String[]{"required"});
        first.setWarp(3);
        var middle = new ResearchStage();
        middle.setWarp(5);
        var last = new ResearchStage();
        last.setWarp(7);
        var entry = entry(new ResearchStage[]{first, middle, last});
        var start = LegacyResearchStageTransition.calculate(entry, 0);
        assertEquals(1, start.nextStage());
        assertEquals(0, start.warp());
        var advance = LegacyResearchStageTransition.calculate(entry, 1);
        assertEquals(2, advance.nextStage());
        assertFalse(advance.completed());
        assertEquals(6, advance.warp()); // Same exited stage added twice.
        var finish = LegacyResearchStageTransition.calculate(entry, 2);
        assertEquals(4, finish.nextStage());
        assertTrue(finish.completed());
        assertEquals(12, finish.warp()); // Exited middle + skipped final stage.
    }

    @Test
    void unknownOpaqueAndNullStageCatalogEntryHaveDifferentCompletionEffects() {
        var opaque = LegacyResearchStageTransition.calculate(null, 0);
        assertFalse(opaque.writesStage());
        assertFalse(opaque.completed());
        var catalog = LegacyResearchStageTransition.calculate(entry(null), 0);
        assertFalse(catalog.writesStage());
        assertTrue(catalog.completed());
        assertEquals(0, catalog.nextStage());
        assertEquals(0, catalog.warp());
    }

    private record SourceTransition(int nextStage, boolean completed, int warp) {}

    // Kept separate from the production helper: direct source condition/order, including
    // Java int addition and the old duplicate-current-stage warp behavior.
    private static SourceTransition source(ResearchStage[] stages, int stored) {
        int cs = stored;
        ResearchStage currentStage = null;
        if (cs > 0) {
            cs = Math.min(cs, stages.length);
            currentStage = stages[cs - 1];
        }
        if (stages.length == 1 && cs == 0
            && stages[0].getCraft() == null && stages[0].getObtain() == null
            && stages[0].getKnow() == null && stages[0].getResearch() == null) {
            cs++;
        } else if (stages.length > 1 && stages.length <= cs + 1 && cs < stages.length
            && stages[cs].getCraft() == null && stages[cs].getObtain() == null
            && stages[cs].getKnow() == null && stages[cs].getResearch() == null) {
            cs++;
        }
        int next = Math.min(stages.length + 1, cs + 1);
        boolean popups = cs >= stages.length;
        int warp = currentStage == null ? 0 : currentStage.getWarp();
        if (popups) {
            cs = Math.min(cs, stages.length);
            currentStage = stages[cs - 1];
        }
        if (currentStage != null) warp += currentStage.getWarp();
        return new SourceTransition(next, popups, warp);
    }

    private static ResearchEntry entry(ResearchStage[] stages) {
        var entry = new ResearchEntry();
        entry.setKey("SOURCE_STAGE_ORACLE");
        entry.setStages(stages);
        return entry;
    }
}
