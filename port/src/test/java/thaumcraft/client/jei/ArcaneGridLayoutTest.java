package thaumcraft.client.jei;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArcaneGridLayoutTest {
    @Test void trimmedTwoByTwoUsesTheTablesRowStride() {
        assertEquals(List.of(0, 1, 3, 4), ArcaneGridLayout.shapedSlots(2, 2));
    }
    @Test void verticalAndHorizontalPatternsKeepTheirShapeAndCenter() {
        assertEquals(List.of(1, 4, 7), ArcaneGridLayout.shapedSlots(1, 3));
        assertEquals(List.of(3, 4, 5), ArcaneGridLayout.shapedSlots(3, 1));
        assertEquals(List.of(4), ArcaneGridLayout.shapedSlots(1, 1));
    }
    @Test void fullPatternKeepsEveryCellIncludingHoles() {
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), ArcaneGridLayout.shapedSlots(3, 3));
    }
    @Test void everySupportedShapeHasDistinctInBoundsCellsAndNativeIngredientCount() {
        for (int width = 1; width <= 3; width++) for (int height = 1; height <= 3; height++) {
            var cells = ArcaneGridLayout.shapedSlots(width, height);
            assertEquals(width * height, cells.size());
            assertEquals(cells.size(), cells.stream().distinct().count());
            assertTrue(cells.stream().allMatch(cell -> cell >= 0 && cell < 9));
            assertThrows(UnsupportedOperationException.class, () -> cells.add(0));
        }
    }
    @Test void invalidShapesAreRejectedInsteadOfDisplayingDifferentRecipes() {
        assertThrows(IllegalArgumentException.class, () -> ArcaneGridLayout.shapedSlots(0, 1));
        assertThrows(IllegalArgumentException.class, () -> ArcaneGridLayout.shapedSlots(4, 1));
        assertThrows(IllegalArgumentException.class, () -> ArcaneGridLayout.shapedSlots(1, -1));
        assertThrows(IllegalArgumentException.class, () -> ArcaneGridLayout.shapedSlots(1, 4));
    }
}
