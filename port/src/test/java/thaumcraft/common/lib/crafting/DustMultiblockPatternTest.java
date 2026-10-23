package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.api.crafting.Part;

class DustMultiblockPatternTest {
    @Test void topLayerAndAllFourFacingsUseSourceRotationAndCoordinates() {
        Part top = new Part("top", null), bottom = new Part("bottom", null);
        var pattern = new DustMultiblockPattern(null, new Part[][][]{{{top, null}}, {{null, bottom}}});
        BlockPos origin = new BlockPos(10, 20, 30);
        for (Direction face : new Direction[]{Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST}) {
            var cells = pattern.cells(origin, new IDustTrigger.Placement(0, 0, 0, face));
            assertEquals(2, cells.size()); assertSame(top, cells.getFirst().part()); assertSame(bottom, cells.getLast().part());
            assertEquals(21, cells.getFirst().pos().getY()); assertEquals(20, cells.getLast().pos().getY());
        }
        var east = pattern.cells(origin, new IDustTrigger.Placement(0, 0, 0, Direction.EAST));
        assertEquals(origin.above(), east.getFirst().pos()); assertEquals(origin.south(), east.getLast().pos());
        var south = pattern.cells(origin, new IDustTrigger.Placement(0, 0, 0, Direction.SOUTH));
        assertEquals(origin.east().above(), south.getFirst().pos()); assertEquals(origin, south.getLast().pos());
    }
    @Test void searchReturnsTheOriginalNegativeAnchorOffsetsForAnAsymmetricPattern() {
        Part a = new Part("a", null), b = new Part("b", null), c = new Part("c", null), d = new Part("d", null);
        var pattern = new DustMultiblockPattern(null, new Part[][][]{{{a, b}, {c, d}}, {{d, null}, {null, a}}});
        BlockPos clicked = new BlockPos(10, 20, 30);
        var expected = new IDustTrigger.Placement(-1, -1, -1, Direction.EAST);
        Map<BlockPos, Part> actual = new HashMap<>();
        pattern.cells(clicked, expected).forEach(cell -> actual.put(cell.pos(), cell.part()));
        var found = pattern.findPlacement(clicked, (pos, part) -> actual.get(pos) == part);
        assertNotNull(found); assertEquals(-1, found.xOffset); assertEquals(-1, found.yOffset); assertEquals(-1, found.zOffset); assertEquals(Direction.EAST, found.facing);
        assertNull(pattern.findPlacement(clicked, (pos, part) -> false));
    }
    @Test void emptyHolesPreserveSourceFirstSearchOrderButDoNotBecomeAnExecutableTrigger() {
        var pattern = new DustMultiblockPattern(null, new Part[][][]{{{null}}});
        var found = pattern.findPlacement(BlockPos.ZERO, (pos, part) -> { fail("null blueprint holes must not read world state"); return false; });
        assertEquals(-1, found.xOffset); assertEquals(-1, found.yOffset); assertEquals(-1, found.zOffset); assertEquals(Direction.SOUTH, found.facing);
        assertFalse(IDustTrigger.class.isAssignableFrom(DustMultiblockPattern.class), "pattern-only future machines must never consume dust");
        assertFalse(DustMultiblockPattern.matchesSource(null, "unknown old Material"));
    }
    @Test void raggedOrOversizedBlueprintsFailBeforeWorldSearch() {
        assertThrows(IllegalArgumentException.class, () -> new DustMultiblockPattern(null, new Part[0][][]));
        assertThrows(IllegalArgumentException.class, () -> new DustMultiblockPattern(null, new Part[9][1][1]));
        assertThrows(IllegalArgumentException.class, () -> new DustMultiblockPattern(null, new Part[][][]{{{null}}, {{null, null}}}));
        var pattern = new DustMultiblockPattern(null, new Part[][][]{{{null}}});
        assertThrows(IllegalArgumentException.class, () -> pattern.cells(BlockPos.ZERO, new IDustTrigger.Placement(0, 0, 0, Direction.UP)));
    }
}
