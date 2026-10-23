package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import thaumcraft.api.crafting.Part;

class MatrixTest {
    @Test void rectangularClockwiseRotationMatchesTheOriginalIndexFormula() {
        Part a = new Part("a", null), b = new Part("b", null), c = new Part("c", null), d = new Part("d", null), e = new Part("e", null);
        Matrix matrix = new Matrix(new Part[][]{{a, b, c}, {d, null, e}});
        matrix.Rotate90DegRight(1);
        assertEquals(3, matrix.rows); assertEquals(2, matrix.cols);
        assertArrayEquals(new Part[]{d, a}, matrix.getMatrix()[0]);
        assertArrayEquals(new Part[]{null, b}, matrix.getMatrix()[1]);
        assertArrayEquals(new Part[]{e, c}, matrix.getMatrix()[2]);
    }
    @Test void fourRotationsAndNegativeCountPreserveShapeAndLivePartIdentity() {
        Part a = new Part("a", "AIR"); Part[][] original = {{a, null}};
        Matrix matrix = new Matrix(original);
        assertNotSame(original, matrix.getMatrix()); assertNotSame(original[0], matrix.getMatrix()[0]);
        matrix.Rotate90DegRight(4); matrix.Rotate90DegRight(-2);
        assertEquals(1, matrix.rows); assertEquals(2, matrix.cols); assertSame(a, matrix.getMatrix()[0][0]);
        a.setTarget("changed"); assertEquals("changed", matrix.getMatrix()[0][0].getTarget());
        assertEquals(50, a.getPriority()); assertFalse(a.isOpp());
        assertSame(a, a.setApplyPlayerFacing(true)); assertTrue(a.getApplyPlayerFacing());
    }
    @Test void emptyAndRaggedMatricesCannotReachTheMatcher() {
        assertThrows(IllegalArgumentException.class, () -> new Matrix(new Part[0][]));
        assertThrows(IllegalArgumentException.class, () -> new Matrix(new Part[][]{{null}, {null, null}}));
    }
}
