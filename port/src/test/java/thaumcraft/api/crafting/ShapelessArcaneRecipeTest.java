package thaumcraft.api.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.crafting.ArcaneShapelessMatching;

/** Pure native matcher tests: no world, registries, clients or item bootstrap. */
class ShapelessArcaneRecipeTest {
    @Test void originalShapelessMatchingDoesNotDependOnInputOrderOrGreedyTagChoice() {
        List<Predicate<String>> ingredients = List.of(value -> value.equals("iron") || value.equals("gold"), value -> value.equals("iron"));
        assertTrue(ArcaneShapelessMatching.matches(List.of("iron", "gold"), ingredients));
        assertTrue(ArcaneShapelessMatching.matches(List.of("gold", "iron"), ingredients));
        assertFalse(ArcaneShapelessMatching.matches(List.of("gold", "gold"), ingredients));
    }
    @Test void repeatedIngredientsRequireDistinctOccupiedSlotsWithoutIgnoringExtraItems() {
        List<Predicate<String>> ingredients = List.of("quartz"::equals, "quartz"::equals);
        assertTrue(ArcaneShapelessMatching.matches(List.of("quartz", "quartz"), ingredients));
        assertFalse(ArcaneShapelessMatching.matches(List.of("quartz"), ingredients));
        assertFalse(ArcaneShapelessMatching.matches(List.of("quartz", "quartz", "dirt"), ingredients));
    }
    @Test void emptyAndOversizedCraftingMatricesCannotMatch() {
        assertFalse(ArcaneShapelessMatching.matches(List.of(), List.<Predicate<String>>of()));
        assertFalse(ArcaneShapelessMatching.matches(java.util.Collections.nCopies(10, "iron"), java.util.Collections.<Predicate<String>>nCopies(10, "iron"::equals)));
    }
}
