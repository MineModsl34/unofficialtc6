package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import thaumcraft.client.gui.model.ResearchLineGeometry.Sprite;

/** Sprite/control-flow checks against local TC6 source, NOT executed original GL/render parity. */
class ResearchLineGeometryTest {
    @Test
    void literalVerticalAndHorizontalSpritesPreserveArrowUvAndSegmentOrder() {
        assertEquals(List.of(sprite(-8, -8, 96, 112, 32), sprite(-4, 20, 0, 228, 24), sprite(-4, 44, 0, 228, 24)),
            ResearchLineGeometry.layout(0, 0, 0, 3, true, false));
        assertEquals(List.of(sprite(-8, -8, 160, 112, 32), sprite(20, -4, 24, 228, 24), sprite(44, -4, 24, 228, 24)),
            ResearchLineGeometry.layout(0, 0, 3, 0, true, false));
        assertEquals(List.of(sprite(-8, -8, 64, 112, 32), sprite(-4, -28, 0, 228, 24), sprite(-4, -52, 0, 228, 24)),
            ResearchLineGeometry.layout(0, 0, 0, -3, true, false));
        assertEquals(List.of(sprite(-8, -8, 128, 112, 32), sprite(-28, -4, 24, 228, 24), sprite(-52, -4, 24, 228, 24)),
            ResearchLineGeometry.layout(0, 0, -3, 0, true, false));
    }

    @Test
    void fourSmallCornerQuadrantsUseLiteralTwentyFourPixelTiles() {
        assertEquals(List.of(sprite(-4, 20, 48, 228, 24)), ResearchLineGeometry.layout(0, 0, -1, 1, false, false));
        assertEquals(List.of(sprite(-4, 20, 72, 228, 24)), ResearchLineGeometry.layout(0, 0, 1, 1, false, false));
        assertEquals(List.of(sprite(-4, -28, 96, 228, 24)), ResearchLineGeometry.layout(0, 0, -1, -1, false, false));
        assertEquals(List.of(sprite(-4, -28, 120, 228, 24)), ResearchLineGeometry.layout(0, 0, 1, -1, false, false));
    }

    @Test
    void fourBigCornerQuadrantsRetainFortyEightPixelOffsetsAndSourceTextureTiles() {
        assertEquals(List.of(sprite(-4, 20, 0, 228, 24), sprite(-28, 44, 0, 180, 48), sprite(-52, 68, 24, 228, 24)),
            ResearchLineGeometry.layout(0, 0, -3, 3, false, false));
        assertEquals(List.of(sprite(-4, 20, 0, 228, 24), sprite(-4, 44, 48, 180, 48), sprite(44, 68, 24, 228, 24)),
            ResearchLineGeometry.layout(0, 0, 3, 3, false, false));
        assertEquals(List.of(sprite(-4, -28, 0, 228, 24), sprite(-28, -76, 96, 180, 48), sprite(-52, -76, 24, 228, 24)),
            ResearchLineGeometry.layout(0, 0, -3, -3, false, false));
        assertEquals(List.of(sprite(-4, -28, 0, 228, 24), sprite(-4, -76, 144, 180, 48), sprite(44, -76, 24, 228, 24)),
            ResearchLineGeometry.layout(0, 0, 3, -3, false, false));
    }

    @Test
    void flippedArrowStaysAtOriginalFirstEndpointAndPrioritizesHorizontalDirection() {
        var normal = ResearchLineGeometry.layout(0, 0, 3, 3, true, false);
        var flipped = ResearchLineGeometry.layout(0, 0, 3, 3, true, true);
        assertEquals(sprite(-8, -8, 96, 112, 32), normal.getFirst()); // Source nonflipped: ym priority.
        assertEquals(sprite(-8, -8, 160, 112, 32), flipped.getFirst()); // Flipped: xm priority, not start-node position.
        assertEquals(sprite(-8, -8, 128, 112, 32), ResearchLineGeometry.layout(0, 0, -3, 3, true, true).getFirst());
        assertEquals(sprite(-8, -8, 96, 112, 32), ResearchLineGeometry.layout(0, 0, 0, 3, true, true).getFirst());
        assertEquals(sprite(-8, -8, 64, 112, 32), ResearchLineGeometry.layout(0, 0, 0, -3, true, true).getFirst());
        assertEquals(ResearchLineGeometry.layout(3, 3, 0, 0, false, false),
            ResearchLineGeometry.layout(0, 0, 3, 3, false, true));
    }

    @Test
    void zeroLengthSelfLinksHaveNeitherCornerNorArrowAndAdjacentAxisHasOnlyRequestedArrow() {
        for (boolean flipped : new boolean[] {false, true}) {
            for (boolean arrow : new boolean[] {false, true}) {
                assertTrue(ResearchLineGeometry.layout(2, -3, 2, -3, arrow, flipped).isEmpty());
            }
            assertTrue(ResearchLineGeometry.layout(0, 0, 1, 0, false, flipped).isEmpty());
            assertEquals(1, ResearchLineGeometry.layout(0, 0, 1, 0, true, flipped).size());
        }
    }

    @Test
    void oneNodeTranslationMovesEverySpriteTwentyFourPixelsWithoutChangingUvOrderOrSize() {
        for (int[] end : new int[][] {{3, 3}, {-3, 3}, {-3, -3}, {3, -3}, {0, 3}, {-3, 0}}) {
            for (boolean arrow : new boolean[] {false, true}) {
                for (boolean flipped : new boolean[] {false, true}) {
                    var base = ResearchLineGeometry.layout(0, 0, end[0], end[1], arrow, flipped);
                    var translated = ResearchLineGeometry.layout(1, 1, end[0] + 1, end[1] + 1, arrow, flipped);
                    assertEquals(base.stream().map(s -> new Sprite(s.x() + 24, s.y() + 24, s.u(), s.v(), s.width(), s.height())).toList(),
                        translated);
                }
            }
        }
    }

    @Test
    void finiteSourceControlFlowOracleMatchesTwoThousandFiveHundredCoordinateAndArrowCases() {
        int cells = 0;
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) {
            for (int x2 = -2; x2 <= 2; x2++) for (int y2 = -2; y2 <= 2; y2++) {
                for (boolean arrow : new boolean[] {false, true}) for (boolean flipped : new boolean[] {false, true}) {
                    assertEquals(literalSource(x, y, x2, y2, arrow, flipped),
                        ResearchLineGeometry.layout(x, y, x2, y2, arrow, flipped));
                    cells++;
                }
            }
        }
        assertEquals(2500, cells);
    }

    @Test
    void exactBudgetIsCheckedBeforeLoopsAndUnrepresentableEndpointsNeverWrap() {
        assertEquals(4096, ResearchLineGeometry.layout(0, 0, 0, 4097, false, false).size());
        assertEquals(4096, ResearchLineGeometry.layout(0, 0, 4097, 0, false, false).size());
        assertEquals(4096, ResearchLineGeometry.layout(0, 0, 2050, 2049, false, false).size());
        assertThrows(IllegalArgumentException.class, () -> ResearchLineGeometry.layout(0, 0, 0, 4097, true, false));
        assertThrows(IllegalArgumentException.class, () -> ResearchLineGeometry.layout(0, 0, 2050, 2050, false, false));
        assertThrows(IllegalArgumentException.class,
            () -> ResearchLineGeometry.layout(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0, false, false));
        assertThrows(IllegalArgumentException.class,
            () -> ResearchLineGeometry.layout(Integer.MIN_VALUE, 0, Integer.MIN_VALUE, 0, false, true));
        assertThrows(IllegalArgumentException.class,
            () -> ResearchLineGeometry.layout(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, true, true));
        var result = ResearchLineGeometry.layout(0, 0, 1, 1, false, false);
        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    private static Sprite sprite(int x, int y, int u, int v, int size) { return new Sprite(x, y, u, v, size, size); }

    /** Literal source arithmetic/control flow on SMALL bounded inputs; draw calls become records.
     * No original JVM/GL execution is claimed, and the production safety guards are not the oracle. */
    private static List<Sprite> literalSource(int x, int y, int x2, int y2, boolean arrow, boolean flipped) {
        boolean bigCorner = false;
        int xd, yd, xm, ym, xx, yy;
        if (flipped) {
            xd = Math.abs(x2 - x); yd = Math.abs(y2 - y);
            xm = xd == 0 ? 0 : (x2 - x > 0 ? -1 : 1);
            ym = yd == 0 ? 0 : (y2 - y > 0 ? -1 : 1);
            if (xd > 1 && yd > 1) bigCorner = true;
            xx = x2 * 24 - 4; yy = y2 * 24 - 4;
        } else {
            xd = Math.abs(x - x2); yd = Math.abs(y - y2);
            xm = xd == 0 ? 0 : (x - x2 > 0 ? -1 : 1);
            ym = yd == 0 ? 0 : (y - y2 > 0 ? -1 : 1);
            if (xd > 1 && yd > 1) bigCorner = true;
            xx = x * 24 - 4; yy = y * 24 - 4;
        }
        List<Sprite> out = new ArrayList<>();
        if (arrow) {
            if (flipped) {
                int xx3 = x * 24 - 8, yy3 = y * 24 - 8;
                if (xm < 0) out.add(sprite(xx3, yy3, 160, 112, 32));
                else if (xm > 0) out.add(sprite(xx3, yy3, 128, 112, 32));
                else if (ym > 0) out.add(sprite(xx3, yy3, 64, 112, 32));
                else if (ym < 0) out.add(sprite(xx3, yy3, 96, 112, 32));
            } else if (ym < 0) out.add(sprite(xx - 4, yy - 4, 64, 112, 32));
            else if (ym > 0) out.add(sprite(xx - 4, yy - 4, 96, 112, 32));
            else if (xm > 0) out.add(sprite(xx - 4, yy - 4, 160, 112, 32));
            else if (xm < 0) out.add(sprite(xx - 4, yy - 4, 128, 112, 32));
        }
        int v = 1, h = 0;
        while (v < yd - (bigCorner ? 1 : 0)) {
            out.add(sprite(xx + xm * 24 * h, yy + ym * 24 * v, 0, 228, 24)); v++;
        }
        if (bigCorner) {
            if (xm < 0 && ym > 0) out.add(sprite(xx + xm * 24 * h - 24, yy + ym * 24 * v, 0, 180, 48));
            if (xm > 0 && ym > 0) out.add(sprite(xx + xm * 24 * h, yy + ym * 24 * v, 48, 180, 48));
            if (xm < 0 && ym < 0) out.add(sprite(xx + xm * 24 * h - 24, yy + ym * 24 * v - 24, 96, 180, 48));
            if (xm > 0 && ym < 0) out.add(sprite(xx + xm * 24 * h, yy + ym * 24 * v - 24, 144, 180, 48));
        } else {
            if (xm < 0 && ym > 0) out.add(sprite(xx + xm * 24 * h, yy + ym * 24 * v, 48, 228, 24));
            if (xm > 0 && ym > 0) out.add(sprite(xx + xm * 24 * h, yy + ym * 24 * v, 72, 228, 24));
            if (xm < 0 && ym < 0) out.add(sprite(xx + xm * 24 * h, yy + ym * 24 * v, 96, 228, 24));
            if (xm > 0 && ym < 0) out.add(sprite(xx + xm * 24 * h, yy + ym * 24 * v, 120, 228, 24));
        }
        v += bigCorner ? 1 : 0;
        for (int segment = h + (bigCorner ? 2 : 1); segment < xd; segment++) {
            out.add(sprite(xx + xm * 24 * segment, yy + ym * 24 * v, 24, 228, 24));
        }
        return out;
    }
}
