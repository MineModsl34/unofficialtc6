package thaumcraft.common.golems.tasks;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TaskRulesTest {
    @Test void priorityIsSquaredDistanceOffsetNotASeparatePrimarySort() {
        assertEquals(-252D, TaskRules.selectionScore(4, (byte) 1));
        assertTrue(TaskRules.selectionScore(260, (byte) 1) > TaskRules.selectionScore(1, (byte) 0));
        assertEquals(258D, TaskRules.selectionScore(2, (byte) -1));
    }
    @Test void cleanupBeginsAtZeroAndRunsOncePerSecondNotEveryTick() {
        assertTrue(TaskRules.cleanupTick(0));
        assertFalse(TaskRules.cleanupTick(1));
        assertFalse(TaskRules.cleanupTick(19));
        assertTrue(TaskRules.cleanupTick(20));
    }
    @Test void sourceReservationAndCompletionArithmeticRetainShortOverflow() {
        assertEquals(420, TaskRules.reservationLifespan((short) 300));
        assertEquals(301, TaskRules.completionLifespan((short) 300));
        assertEquals(Short.MIN_VALUE, TaskRules.completionLifespan(Short.MAX_VALUE));
        assertEquals((short) (32760 + 120), TaskRules.reservationLifespan((short) 32760));
    }
    @Test void rankRequirementsAreQuadraticAndEachCallConsumesOnlyOneThreshold() {
        assertEquals(1000, TaskRules.rankThreshold(0));
        assertEquals(4000, TaskRules.rankThreshold(1));
        assertEquals(100000, TaskRules.rankThreshold(9));
    }
}
