package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import thaumcraft.client.gui.model.ResearchPageProjection.Status;
import thaumcraft.client.gui.model.ResearchPageProjection.View;
import thaumcraft.client.gui.model.ResearchPageProjection.VisibleAddendum;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Addendum;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ArrayField;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Entry;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.LegacyResourceId;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ResearchToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Stage;

/** Registry/client/bootstrap-free checks; this is NOT screen/layout or registry-recipe acceptance. */
class ResearchPageProjectionTest {
    @Test
    void finiteStageSelectionMatchesLiteralSourceLoopIncludingUnknownAndKnownZero() {
        for (int length = 1; length <= 8; length++) {
            List<Stage> stages = new ArrayList<>();
            for (int i = 0; i < length; i++) stages.add(stage("raw.stage." + i, ArrayField.absent()));
            Entry entry = entry(field(stages), ArrayField.absent());
            for (int stored = -1; stored <= length + 6; stored++) {
                // The actual finite TC6 parsePages loop, not the new clamp formula.
                int sourceIndex = stored - 1;
                boolean sourceComplete = false;
                while (sourceIndex >= length) { sourceIndex--; sourceComplete = true; }
                if (sourceIndex < 0) sourceIndex = 0;
                View view = ResearchPageProjection.project(entry, stored, ignored -> { throw new AssertionError("No addenda"); });
                assertEquals(Status.CONTENT, view.status());
                assertEquals(stored, view.storedStage());
                assertEquals(sourceIndex, view.stageIndex());
                assertEquals(sourceComplete, view.addendaPhaseComplete());
                assertSame(stages.get(sourceIndex), view.stage());
                assertEquals("raw.stage." + sourceIndex, view.rawText());
            }
        }
    }

    @Test
    void finalStageIsNotAddendaCompleteUntilStoredStageMinusOneReachesCount() {
        Entry entry = entry(field(List.of(stage("a", ArrayField.absent()), stage("b", ArrayField.absent()))),
            field(List.of(addendum("extra", field(List.of("COMPLETE")), ArrayField.absent()))));
        AtomicInteger queries = new AtomicInteger();
        for (int stored : new int[] {-1, 0, 1, 2}) {
            View view = ResearchPageProjection.project(entry, stored, whole -> { queries.incrementAndGet(); return true; });
            assertTrue(view.hasContent());
            assertFalse(view.addendaPhaseComplete());
            assertTrue(view.eligibleAddenda().isEmpty());
        }
        assertEquals(0, queries.get());
        View completed = ResearchPageProjection.project(entry, 3, whole -> { queries.incrementAndGet(); return true; });
        assertEquals(1, completed.stageIndex());
        assertTrue(completed.addendaPhaseComplete());
        assertEquals(1, completed.eligibleAddenda().size());
        assertEquals(1, queries.get()); // No input complete flag/status can override the source threshold.
    }

    @Test
    void hugeAndMaximumPositiveStagesClampInConstantTimeWithoutArithmeticOverflow() {
        Entry entry = entry(field(List.of(stage("first", ArrayField.absent()), stage("last", ArrayField.absent()))),
            field(List.of(addendum("extra", field(List.of("OK")), ArrayField.absent()))));
        assertTimeout(Duration.ofSeconds(1), () -> {
            for (int stored : new int[] {10_000_000, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
                View view = ResearchPageProjection.project(entry, stored, ignored -> true);
                assertEquals(1, view.stageIndex());
                assertEquals("last", view.rawText());
                assertTrue(view.addendaPhaseComplete());
                assertEquals(1, view.eligibleAddenda().size());
            }
        });
    }

    @Test
    void corruptNegativeStoredStagesFailClosedRatherThanEmulatingWrappedSubtraction() {
        Entry entry = entry(field(List.of(stage("first", ArrayField.absent()))),
            field(List.of(addendum("extra", field(List.of("OK")), ArrayField.absent()))));
        for (int stored : new int[] {-2, -100, Integer.MIN_VALUE}) {
            View view = ResearchPageProjection.project(entry, stored, ignored -> { throw new AssertionError("Must not query"); });
            assertUnsupported(view, Status.CORRUPT_STORED_STAGE);
            assertEquals(stored, view.storedStage());
            assertTrue(view.diagnostics().getFirst().message().contains("no overflow emulation"));
        }
    }

    @Test
    void missingEntryAndMissingNullOrEmptyStageFieldHaveExplicitNoContent() {
        var query = (ResearchPageProjection.StrictArrayQuery) ignored -> { throw new AssertionError("Must not query"); };
        assertUnsupported(ResearchPageProjection.project(null, 1, query), Status.MISSING_ENTRY);
        assertUnsupported(ResearchPageProjection.project(entry(null, ArrayField.absent()), 1, query), Status.MISSING_STAGES);
        assertUnsupported(ResearchPageProjection.project(entry(ArrayField.absent(), ArrayField.absent()), 1, query), Status.MISSING_STAGES);
        View empty = ResearchPageProjection.project(entry(field(List.of()), ArrayField.absent()), 1, query);
        assertUnsupported(empty, Status.EMPTY_STAGES);
        assertTrue(empty.diagnostics().getFirst().message().contains("legacy null"));
    }

    @Test
    void nullStageTextFailsClosedInsteadOfInventingLocalizedContent() {
        View view = ResearchPageProjection.project(entry(field(List.of(stage(null, ArrayField.absent()))), ArrayField.absent()),
            1, ignored -> { throw new AssertionError("Must not query"); });
        assertUnsupported(view, Status.MALFORMED_STAGE);
        assertEquals("$.entries[0].stages[0].text", view.diagnostics().getFirst().path());
    }

    @Test
    void eligibleAddendaPreserveSourceOrderAndNumberOnlyTheEligibleOnes() {
        Addendum denied = addendum("denied", field(List.of("NO")), ArrayField.absent());
        Addendum first = addendum("first raw", field(List.of("YES")), ArrayField.absent());
        Addendum second = addendum("second raw", field(List.of("YES2")), ArrayField.absent());
        View view = ResearchPageProjection.project(entry(field(List.of(stage("raw", ArrayField.absent()))),
            field(List.of(denied, first, second))), 2, whole -> whole[0].startsWith("YES"));
        assertEquals(List.of(1, 2), view.eligibleAddenda().stream().map(VisibleAddendum::sourceIndex).toList());
        assertEquals(List.of(1, 2), view.eligibleAddenda().stream().map(VisibleAddendum::number).toList());
        assertEquals(List.of("first raw", "second raw"), view.eligibleAddenda().stream().map(VisibleAddendum::rawText).toList());
        assertSame(first, view.eligibleAddenda().getFirst().addendum());
        assertSame(second, view.eligibleAddenda().getLast().addendum());
        assertEquals("raw", view.rawText()); // No <PAGE>/localized addendum header fabricated here.
    }

    @Test
    void strictCallbackReceivesWholeArrayAndRetainsSourceOrEarlyReturnAndFallthroughQuirks() {
        List<List<String>> calls = new ArrayList<>();
        Addendum early = addendum("OR early", field(List.of("YES||NO", "MISSING")), ArrayField.absent());
        Addendum fallthrough = addendum("OR all-false", field(List.of("NO||ALSO_NO", "Y")), ArrayField.absent());
        View view = ResearchPageProjection.project(entry(field(List.of(stage("raw", ArrayField.absent()))),
            field(List.of(early, fallthrough))), 2, whole -> {
                calls.add(List.copyOf(Arrays.asList(whole)));
                return literalStrict(Set.of("Y"), whole);
            });
        assertEquals(List.of(List.of("YES||NO", "MISSING"), List.of("NO||ALSO_NO", "Y")), calls);
        assertEquals(2, view.eligibleAddenda().size());
        assertFalse(literalStrict(Set.of("Y"), "MISSING"));
        assertFalse(literalStrict(Set.of("Y"), "YES||NO") && literalStrict(Set.of("Y"), "MISSING"));
        assertFalse(literalStrict(Set.of("YES"), "YES&&MISSING"));
        assertTrue(literalStrict(Set.of("Y"), "YES||NO", "MISSING")); // Source regex splits into characters, 'Y' skips the later row.
        // Whole-array source evaluation is deliberately NOT element-wise AND.
    }

    @Test
    void missingNullAndEmptyAddendumResearchAreUnsupportedOnlyWhenAddendaPhaseIsReached() {
        List<ArrayField<String>> malformed = Arrays.asList(null, ArrayField.absent(), field(List.of()));
        for (ArrayField<String> required : malformed) {
            Entry entry = entry(field(List.of(stage("raw", ArrayField.absent()))),
                field(List.of(addendum("malformed", required, ArrayField.absent()))));
            View active = ResearchPageProjection.project(entry, 1, ignored -> { throw new AssertionError("No malformed-null callback"); });
            assertTrue(active.hasContent());
            View complete = ResearchPageProjection.project(entry, 2, ignored -> { throw new AssertionError("No malformed-null callback"); });
            assertUnsupported(complete, Status.MALFORMED_ADDENDUM);
            assertEquals("$.entries[0].addenda[0].required_research", complete.diagnostics().getFirst().path());
            assertTrue(complete.diagnostics().getFirst().message().contains("source knowsResearchStrict throws"));
        }
    }

    @Test
    void malformedLaterAddendumDoesNotAdvertiseAnEarlierPartialPageOrRecipes() {
        Addendum first = addendum("first", field(List.of("YES")), field(List.of(recipe("first"))));
        Addendum second = addendum(null, field(List.of("YES")), ArrayField.absent());
        View view = ResearchPageProjection.project(entry(field(List.of(stage("raw", field(List.of(recipe("stage")))))),
            field(List.of(first, second))), 2, ignored -> true);
        assertUnsupported(view, Status.MALFORMED_ADDENDUM);
        assertEquals("$.entries[0].addenda[1].text", view.diagnostics().getFirst().path());
    }

    @Test
    void unrevealedNullAddendumTextIsNeverReadAsInSourceStrictGateOrdering() {
        LegacyResourceId base = recipe("base");
        Addendum hidden = addendum(null, field(List.of("NO")), field(List.of(recipe("hidden"))));
        Addendum shown = addendum("shown", field(List.of("YES")), field(List.of(recipe("shown"))));
        View view = ResearchPageProjection.project(entry(field(List.of(stage("raw", field(List.of(base))))),
            field(List.of(hidden, shown))), 2, whole -> whole[0].equals("YES"));
        assertTrue(view.hasContent());
        assertTrue(view.diagnostics().isEmpty());
        assertEquals(List.of("shown"), view.eligibleAddenda().stream().map(VisibleAddendum::rawText).toList());
        assertEquals(List.of(base, recipe("shown")), view.recipeHandles());
        assertEquals(1, view.eligibleAddenda().getFirst().sourceIndex());
        assertEquals(1, view.eligibleAddenda().getFirst().number());
    }

    @Test
    void strictQueryExceptionIsReportedAsUnsupportedWithoutGrantingOrPartialContent() {
        Entry entry = entry(field(List.of(stage("raw", ArrayField.absent()))),
            field(List.of(addendum("extra", field(List.of("@")), ArrayField.absent()))));
        View view = ResearchPageProjection.project(entry, 2, ignored -> { throw new ArrayIndexOutOfBoundsException("bare @ source query"); });
        assertUnsupported(view, Status.STRICT_QUERY_FAILED);
        assertTrue(view.diagnostics().getFirst().message().contains("java.lang.ArrayIndexOutOfBoundsException"));
        assertTrue(view.diagnostics().getFirst().message().contains("bare @ source query"));
    }

    @Test
    void recipeHandlesUseOnlySelectedStageThenEligibleAddendaWithOrderDuplicatesAndUnresolvedIds() {
        LegacyResourceId old = recipe("old"), current = recipe("current"), extra = recipe("extra");
        LegacyResourceId invalid = new LegacyResourceId("TC:Bad Path", "tc", "Bad Path", false);
        Stage earlier = stage("earlier", field(List.of(old)));
        Stage selected = stage("selected", field(List.of(current, current, invalid)));
        Addendum yes = addendum("yes", field(List.of("YES")), field(List.of(extra, current)));
        Addendum no = addendum("no", field(List.of("NO")), field(List.of(old)));
        Entry entry = entry(field(List.of(earlier, selected)), field(List.of(yes, no)));
        View active = ResearchPageProjection.project(entry, 2, whole -> whole[0].equals("YES"));
        assertEquals(List.of(current, current, invalid), active.recipeHandles());
        View complete = ResearchPageProjection.project(entry, 3, whole -> whole[0].equals("YES"));
        assertEquals(List.of(current, current, invalid, extra, current), complete.recipeHandles());
        assertSame(invalid, complete.recipeHandles().get(2));
        assertFalse(complete.recipeHandles().get(2).modernSyntax());
        assertEquals("TC:Bad Path", complete.recipeHandles().get(2).raw());
        assertSame(selected, complete.stage());
    }

    @Test
    void stageLegacyNullRecipesSuppressAddendumRecipeListButRetainEligibleRawAddendumRecipes() {
        LegacyResourceId extra = recipe("extra");
        Addendum addendum = addendum("extra", field(List.of("YES")), field(List.of(extra)));
        List<ArrayField<LegacyResourceId>> nullRecipes = Arrays.asList(null, ArrayField.absent(), field(List.of()));
        for (ArrayField<LegacyResourceId> recipes : nullRecipes) {
            View view = ResearchPageProjection.project(entry(field(List.of(stage("raw", recipes))), field(List.of(addendum))), 2, ignored -> true);
            assertTrue(view.hasContent());
            assertEquals(1, view.eligibleAddenda().size());
            assertTrue(view.recipeHandles().isEmpty());
            assertEquals(List.of(extra), view.eligibleAddenda().getFirst().addendum().recipes().values());
        }
    }

    @Test
    void absentNullAndEmptyAddendaProduceNoCallbackAndNoInventedHeader() {
        List<ArrayField<Addendum>> fields = Arrays.asList(null, ArrayField.absent(), field(List.of()));
        for (ArrayField<Addendum> addenda : fields) {
            View view = ResearchPageProjection.project(entry(field(List.of(stage("literal <BR> markup", ArrayField.absent()))), addenda),
                2, ignored -> { throw new AssertionError("No addenda"); });
            assertTrue(view.hasContent());
            assertTrue(view.addendaPhaseComplete());
            assertEquals("literal <BR> markup", view.rawText());
            assertTrue(view.eligibleAddenda().isEmpty());
        }
    }

    @Test
    void inputSnapshotsReturnedCollectionsAndWholeQueryArrayCannotAliasMutableInputs() {
        List<String> mutableRequirements = new ArrayList<>(List.of("YES", "OTHER"));
        Addendum addendum = addendum("raw extra", field(mutableRequirements), field(List.of(recipe("extra"))));
        Stage stage = new Stage("raw <PAGE> text", field(List.of(recipe("stage"))), ArrayField.absent(), ArrayField.absent(),
            ArrayField.absent(), field(List.of(new ResearchToken("NO;icon", "NO", "icon"))), true, 9, Map.of("future", "1"));
        List<Stage> mutableStages = new ArrayList<>(List.of(stage));
        List<Addendum> mutableAddenda = new ArrayList<>(List.of(addendum));
        Entry entry = entry(field(mutableStages), field(mutableAddenda));
        mutableRequirements.clear(); mutableStages.clear(); mutableAddenda.clear();
        List<String[]> callbackArrays = new ArrayList<>();
        var query = (ResearchPageProjection.StrictArrayQuery) whole -> {
            assertEquals(List.of("YES", "OTHER"), Arrays.asList(whole));
            callbackArrays.add(whole); whole[0] = "MUTATED"; return true;
        };
        View first = ResearchPageProjection.project(entry, 2, query), second = ResearchPageProjection.project(entry, 2, query);
        assertEquals(first, second);
        assertNotSame(callbackArrays.get(0), callbackArrays.get(1));
        assertEquals(List.of("YES", "OTHER"), addendum.requiredResearch().values());
        assertSame(stage, first.stage());
        assertEquals("NO", first.stage().requiredResearch().values().getFirst().key()); // Only raw metadata, not satisfaction.
        assertEquals(9, first.stage().warp());
        assertThrows(UnsupportedOperationException.class, () -> first.eligibleAddenda().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.recipeHandles().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.diagnostics().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.stage().unknownFields().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.eligibleAddenda().getFirst().addendum().requiredResearch().values().clear());
    }

    private static void assertUnsupported(View view, Status expected) {
        assertEquals(expected, view.status()); assertFalse(view.hasContent());
        assertEquals(-1, view.stageIndex()); assertFalse(view.addendaPhaseComplete());
        assertNull(view.stage()); assertNull(view.rawText());
        assertTrue(view.eligibleAddenda().isEmpty()); assertTrue(view.recipeHandles().isEmpty());
        assertEquals(1, view.diagnostics().size()); assertEquals(expected, view.diagnostics().getFirst().status());
    }
    private static <T> ArrayField<T> field(List<T> values) { return new ArrayField<>(true, values); }
    private static Stage stage(String text, ArrayField<LegacyResourceId> recipes) {
        return new Stage(text, recipes, ArrayField.absent(), ArrayField.absent(), ArrayField.absent(), ArrayField.absent(), false, 0, Map.of());
    }
    private static Addendum addendum(String text, ArrayField<String> research, ArrayField<LegacyResourceId> recipes) {
        return new Addendum(text, recipes, research, Map.of());
    }
    private static LegacyResourceId recipe(String path) { return new LegacyResourceId("thaumcraft:" + path, "thaumcraft", path, true); }
    private static Entry entry(ArrayField<Stage> stages, ArrayField<Addendum> addenda) {
        return new Entry("fixture.json", "$.entries[0]", "PAGE", "name.PAGE", "BASICS", ArrayField.absent(), ArrayField.absent(),
            ArrayField.absent(), ArrayField.absent(), ArrayField.absent(), ArrayField.absent(), ArrayField.absent(), stages, addenda, Map.of());
    }

    /** Source literal array/AND/OR control flow; simple fixture symbols stand for strict known-complete. */
    private static boolean literalStrict(Set<String> complete, String... whole) {
        for (String research : whole) {
            if (research.contains("&&")) {
                if (!literalStrict(complete, research.split("&&"))) return false;
            } else if (research.contains("||")) {
                for (String alternative : research.split("||")) if (literalStrict(complete, alternative)) return true;
                // Original all-false OR falls through to the next row; no return false here.
            } else if (!complete.contains(research)) return false;
        }
        return true;
    }
}
