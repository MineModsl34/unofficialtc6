package thaumcraft.common.world.features;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TCWorldgenBoundsTest {
    @Test void includesExactlyTheAllowedChunkSquare() {
        assertTrue(TCWorldgenBounds.withinWriteRadius(-16, -16, 0, 0, 1));
        assertTrue(TCWorldgenBounds.withinWriteRadius(31, 31, 0, 0, 1));
        assertFalse(TCWorldgenBounds.withinWriteRadius(-17, 0, 0, 0, 1));
        assertFalse(TCWorldgenBounds.withinWriteRadius(0, 32, 0, 0, 1));
    }

    @Test void negativeCoordinatesUseFloorChunksAndActualRadius() {
        assertTrue(TCWorldgenBounds.withinWriteRadius(-1, -16, -1, -1, 0));
        assertFalse(TCWorldgenBounds.withinWriteRadius(0, -16, -1, -1, 0));
        assertTrue(TCWorldgenBounds.withinWriteRadius(63, -48, 0, 0, 3));
        assertFalse(TCWorldgenBounds.withinWriteRadius(64, -48, 0, 0, 3));
    }
}
