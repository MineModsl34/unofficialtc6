package thaumcraft.common.tiles.essentia;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OneUnitEscrowTest {
    @Test void failedArbitrationCannotDepositAnUnpaidRemoteUnit() {
        var escrow = new OneUnitEscrow<String>(); AtomicInteger commits = new AtomicInteger();
        assertFalse(escrow.acquire("aer", () -> false)); assertFalse(escrow.commit(aspect -> { commits.incrementAndGet(); return true; }));
        assertNull(escrow.get()); assertEquals(0, commits.get());
    }
    @Test void fullRemoteStoreRetainsExactlyOnePaidUnitForRetry() {
        var escrow = new OneUnitEscrow<String>(); AtomicInteger source = new AtomicInteger(3), destination = new AtomicInteger();
        assertTrue(escrow.acquire("aer", () -> source.getAndDecrement() > 0)); assertFalse(escrow.commit(aspect -> false));
        assertFalse(escrow.acquire("ignis", () -> { source.decrementAndGet(); return true; }));
        assertEquals(2, source.get()); assertEquals("aer", escrow.get());
        assertTrue(escrow.commit(aspect -> { destination.incrementAndGet(); return true; }));
        assertFalse(escrow.commit(aspect -> { destination.incrementAndGet(); return true; })); assertEquals(3, source.get() + destination.get());
    }
    @Test void savedEscrowRestoresWithoutDrainingAgainAndCannotAcceptASecondPoint() {
        var original = new OneUnitEscrow<String>(); assertTrue(original.deposit("aer"));
        var restored = new OneUnitEscrow<String>(); restored.restore(original.get()); assertFalse(restored.deposit("ignis"));
        assertTrue(restored.commit("aer"::equals)); assertNull(restored.get());
    }
    @Test void nullAspectAndFailedDestinationNeverMintOrConsume() { var escrow = new OneUnitEscrow<String>(); assertFalse(escrow.deposit(null)); assertFalse(escrow.acquire(null, () -> fail("Must not drain"))); assertNull(escrow.get()); }
}
