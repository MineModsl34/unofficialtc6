package thaumcraft.client.gui.model;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import thaumcraft.client.gui.model.ResearchRecipeGeometry.Slot;
class ResearchRecipeGeometryTest {
    @Test void nonexistentNextArrowDoesNotConsumeSingleBlueprintSliceToggle() {
        assertEquals(ResearchRecipeGeometry.Control.SLICE, ResearchRecipeGeometry.control(212,232,0,0,true,0,1));
        assertEquals(ResearchRecipeGeometry.Control.NONE, ResearchRecipeGeometry.control(212,232,0,0,false,0,1));
        assertEquals(ResearchRecipeGeometry.Control.NEXT, ResearchRecipeGeometry.control(212,232,0,0,false,0,2));
        assertEquals(ResearchRecipeGeometry.Control.PREVIOUS, ResearchRecipeGeometry.control(45,235,0,0,false,1,2));
        assertEquals(ResearchRecipeGeometry.Control.NONE, ResearchRecipeGeometry.control(45,235,0,0,false,0,2));
    }
    @Test void sourceBlueprintIngredientListHasItsOwnSeventeenPixelCountedMaterialStrip() {
        assertEquals(List.of(new Slot(0,-85,90),new Slot(1,-68,90),new Slot(2,-51,90),new Slot(3,-34,90)),
            ResearchRecipeGeometry.blueprintIngredients(4));
        assertEquals(new Slot(4,-17,90), ResearchRecipeGeometry.blueprintIngredients(5).getLast());
        assertTrue(ResearchRecipeGeometry.blueprintIngredients(0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ResearchRecipeGeometry.blueprintIngredients(257));
        assertThrows(UnsupportedOperationException.class, () -> ResearchRecipeGeometry.blueprintIngredients(1).clear());
    }
    @Test void legitimateBlankIngredientIsDistinctFromUnresolvedDeclaredSourceItem() {
        assertTrue(ResearchRecipeGeometry.emptyIngredient(true, 0, 0));
        assertFalse(ResearchRecipeGeometry.emptyIngredient(true, 1, 0));
        assertFalse(ResearchRecipeGeometry.emptyIngredient(true, 2, 1));
        assertTrue(ResearchRecipeGeometry.emptyIngredient(false, 0, 0));
        assertFalse(ResearchRecipeGeometry.emptyIngredient(false, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ResearchRecipeGeometry.emptyIngredient(true, -1, 0));
    }
    @Test void sourceBlueprintWithoutDisplayItemGetsFiniteCenteredActualCellThumbnail() {
        var thumbnail = ResearchRecipeGeometry.blueprintThumbnail(List.of(
            new ResearchRecipeGeometry.Voxel(0,0,0), new ResearchRecipeGeometry.Voxel(2,2,2),
            new ResearchRecipeGeometry.Voxel(2,0,0), new ResearchRecipeGeometry.Voxel(0,0,2)));
        assertEquals(4, thumbnail.cells().size());
        assertTrue(Float.isFinite(thumbnail.scale())); assertTrue(thumbnail.scale() > 0F && thumbnail.scale() <= 1F);
        for (Slot slot : thumbnail.cells()) {
            assertTrue((slot.x() + thumbnail.offsetX()) * thumbnail.scale() >= -8.001F);
            assertTrue((slot.x() + 16 + thumbnail.offsetX()) * thumbnail.scale() <= 8.001F);
            assertTrue((slot.y() + thumbnail.offsetY()) * thumbnail.scale() >= -8.001F);
            assertTrue((slot.y() + 16 + thumbnail.offsetY()) * thumbnail.scale() <= 8.001F);
        }
        assertThrows(UnsupportedOperationException.class, () -> thumbnail.cells().clear());
        assertTrue(ResearchRecipeGeometry.blueprintThumbnail(List.of()).cells().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ResearchRecipeGeometry.blueprintThumbnail(List.of(new ResearchRecipeGeometry.Voxel(33,0,0))));
    }
    @Test void sourceShapedGridUsesColumnMajorRenderingWithRowMajorIngredientIndex() {
        assertEquals(List.of(new Slot(0,-40,-40),new Slot(2,-40,-8),new Slot(1,-8,-40),new Slot(3,-8,-8)),
            ResearchRecipeGeometry.grid(2,2,4));
        assertEquals(List.of(new Slot(0,-40,-40),new Slot(4,-40,-8),new Slot(1,-8,-40),new Slot(5,-8,-8),
            new Slot(2,24,-40),new Slot(6,24,-8)), ResearchRecipeGeometry.grid(4,2,8));
        assertEquals(3, ResearchRecipeGeometry.grid(1,6,6).size());
    }
    @Test void sourceInfusionRingHasRadiusFortyAndIntegerSliceDivision() {
        assertEquals(List.of(), ResearchRecipeGeometry.infusionRing(0));
        var ring = ResearchRecipeGeometry.infusionRing(4);
        assertEquals(new Slot(0,-8,-56), ring.getFirst()); assertEquals(new Slot(1,32,-16), ring.get(1));
        assertEquals(new Slot(2,-8,24), ring.get(2)); assertEquals(new Slot(3,-48,-16), ring.get(3));
        assertEquals(7, ResearchRecipeGeometry.infusionRing(7).size());
    }
    @Test void sourceAspectRowsRetainFinalFullRowShiftQuirkRatherThanCorrectingIt() {
        assertEquals(List.of(new Slot(0,-8,0)), ResearchRecipeGeometry.aspects(1,3));
        assertEquals(new Slot(3,-28,10), ResearchRecipeGeometry.aspects(6,3).get(3));
        assertEquals(new Slot(6,2,20), ResearchRecipeGeometry.aspects(9,3).get(6));
        assertEquals(new Slot(0,-18,0), ResearchRecipeGeometry.aspects(2,3).getFirst());
    }
    @Test void geometryReturnsImmutableBoundedEmptyOrFiniteLayouts() {
        assertThrows(IllegalArgumentException.class, () -> ResearchRecipeGeometry.grid(17,1,1));
        assertThrows(IllegalArgumentException.class, () -> ResearchRecipeGeometry.infusionRing(257));
        assertThrows(IllegalArgumentException.class, () -> ResearchRecipeGeometry.aspects(1,0));
        assertThrows(UnsupportedOperationException.class, () -> ResearchRecipeGeometry.grid(1,1,1).clear());
    }
}
