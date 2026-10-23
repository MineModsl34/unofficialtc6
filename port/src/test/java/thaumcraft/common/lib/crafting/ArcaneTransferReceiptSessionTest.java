package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArcaneTransferReceiptSessionTest {
    @Test void onlyExactConnectionMenuRequestAndStateReceiptCompletesOnce() {
        var session = new ArcaneTransferReceiptSession(); Object connection = new Object(), menu = new Object(); List<Boolean> results = new ArrayList<>();
        assertTrue(session.begin(connection, menu, 7, 19, 234, 100, results::add));
        assertFalse(session.begin(connection, menu, 8, 19, 234, 100, results::add));
        assertFalse(session.accept(new Object(), menu, 7, 19, 234, true));
        assertFalse(session.accept(connection, menu, 6, 19, 234, true));
        assertFalse(session.accept(connection, menu, 7, 18, 234, true));
        assertFalse(session.accept(connection, menu, 7, 19, 235, true));
        assertEquals(List.of(), results); assertTrue(session.busy());
        assertTrue(session.accept(connection, menu, 7, 19, 234, true));
        assertFalse(session.accept(connection, menu, 7, 19, 234, true)); session.end(); session.tick(connection, menu, 999);
        assertEquals(List.of(true), results); assertFalse(session.busy());
    }
    @Test void rejectedAckAndClosedMenuAndDisconnectAndTimeoutRejectOnce() {
        for (int reason = 0; reason < 5; reason++) {
            var session = new ArcaneTransferReceiptSession(); Object connection = new Object(), menu = new Object(); List<Boolean> results = new ArrayList<>();
            session.begin(connection, menu, 1, 2, 3, 100, results::add);
            if (reason == 0) session.accept(connection, menu, 1, 2, 3, false);
            if (reason == 1) session.accept(connection, new Object(), 1, 2, 3, true);
            if (reason == 2) session.tick(null, menu, 20);
            if (reason == 3) { session.tick(connection, menu, 99); assertTrue(session.busy()); session.tick(connection, menu, 100); }
            if (reason == 4) session.end();
            session.end(); session.accept(connection, menu, 1, 2, 3, true);
            assertEquals(List.of(false), results); assertFalse(session.busy());
        }
    }
    @Test void staleReceiptDoesNotCompleteTheNextRequestAndCallbackCanSafelyReenter() {
        var session = new ArcaneTransferReceiptSession(); Object connection = new Object(), menu = new Object(); List<Boolean> results = new ArrayList<>();
        session.begin(connection, menu, 1, 2, 3, 100, result -> {
            results.add(result); assertTrue(session.begin(connection, menu, 2, 2, 4, 200, results::add));
        });
        session.cancel(1); assertTrue(session.busy());
        assertFalse(session.accept(connection, menu, 1, 2, 3, true)); assertEquals(List.of(false), results);
        assertTrue(session.accept(connection, menu, 2, 2, 4, true)); assertEquals(List.of(false, true), results);
    }
}
