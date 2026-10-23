package thaumcraft.common.tiles.essentia;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransportRulesTest {
    @Test void sourcePropagationSubtractsOneOrTruncatesHalf() { assertEquals(63, TransportRules.propagatedSuction(64, false)); assertEquals(31, TransportRules.propagatedSuction(63, true)); }
    @Test void equalAndPlusOneDoNotReplaceEarlierSourceSide() { assertFalse(TransportRules.supersedes(32, 32)); assertFalse(TransportRules.supersedes(33, 32)); assertTrue(TransportRules.supersedes(34, 32)); assertFalse(TransportRules.supersedes(0, 0)); }
    @Test void ventingIsIdentityConflictAtTwoExactStrengthsExceptFilter() { Object air = new Object(), fire = new Object(); assertTrue(TransportRules.ventConflict(31, 31, air, fire, false)); assertTrue(TransportRules.ventConflict(31, 30, air, fire, false)); assertFalse(TransportRules.ventConflict(31, 29, air, fire, false)); assertFalse(TransportRules.ventConflict(31, 30, air, air, false)); assertFalse(TransportRules.ventConflict(31, 30, air, fire, true)); assertFalse(TransportRules.ventConflict(0, 0, air, fire, false)); }
    @Test void bufferChokeChangesSuctionNotStoredCapacity() { assertEquals(1, TransportRules.bufferSuction(0, 0)); assertEquals(192, TransportRules.bufferSuction(6, 0)); assertEquals(1, TransportRules.bufferSuction(6, 1)); assertEquals(0, TransportRules.bufferSuction(6, 2)); assertEquals(0, TransportRules.nextChoke(2)); assertEquals(1, TransportRules.nextChoke(0)); assertEquals(2, TransportRules.nextChoke(1)); }
    @Test void comparatorIsSourceFourteenPlusOne() { assertEquals(0, TransportRules.comparator(0)); assertEquals(2, TransportRules.comparator(1)); assertEquals(8, TransportRules.comparator(5)); assertEquals(15, TransportRules.comparator(10)); }
    @Test void placementRetainsSourceEyeHeightGateInsteadOfLookDirection() { assertEquals(1, TransportRules.placementFacing(.5, 3, .5, 0, 0, 0, 3)); assertEquals(0, TransportRules.placementFacing(.5, -1, .5, 0, 0, 0, 3)); assertEquals(3, TransportRules.placementFacing(5, 3, .5, 0, 0, 0, 3)); assertEquals(3, TransportRules.placementFacing(.5, 2, .5, 0, 0, 0, 3)); }
}
