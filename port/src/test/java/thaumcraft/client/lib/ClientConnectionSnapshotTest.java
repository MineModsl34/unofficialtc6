package thaumcraft.client.lib;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClientConnectionSnapshotTest {
    @Test void waitingForFirstSyncAndDifferentConnectionsCannotReadData() {
        var state = new ClientConnectionSnapshot<List<String>>();
        Object connection = new Object();
        assertNull(state.capture(connection));
        assertTrue(state.begin(connection));
        assertNull(state.capture(connection));
        assertTrue(state.apply(connection, List.of("server A")));
        assertEquals(List.of("server A"), state.capture(connection).value());
        assertNull(state.capture(new Object()));
    }
    @Test void reloadReplacesTheWholeSnapshotAndAdvancesRevision() {
        var state = new ClientConnectionSnapshot<List<String>>();
        Object connection = new Object();
        state.begin(connection);
        state.apply(connection, List.of("removed", "retained"));
        long revision = state.capture(connection).revision();
        state.apply(connection, List.of("retained"));
        assertEquals(List.of("retained"), state.capture(connection).value());
        assertTrue(state.capture(connection).revision() > revision);
    }
    @Test void firstPacketBeforeLoginIsPreservedAndStaleEventsCannotAffectNewOwner() {
        var state = new ClientConnectionSnapshot<String>();
        Object oldConnection = new Object(), newConnection = new Object();
        state.begin(oldConnection);
        state.apply(oldConnection, "old");
        assertFalse(state.begin(oldConnection));
        assertEquals("old", state.capture(oldConnection).value());
        state.begin(newConnection);
        assertFalse(state.apply(oldConnection, "late packet"));
        assertFalse(state.end(oldConnection));
        assertNull(state.capture(newConnection));
        state.apply(newConnection, "new");
        assertEquals("new", state.capture(newConnection).value());
        assertTrue(state.end(newConnection));
        assertNull(state.capture(newConnection));
    }
    @Test void emptySyncedTableIsReadyAndDistinctFromMissingSync() {
        var state = new ClientConnectionSnapshot<List<String>>();
        Object connection = new Object();
        state.begin(connection);
        state.apply(connection, List.of());
        assertNotNull(state.capture(connection));
        assertTrue(state.capture(connection).value().isEmpty());
    }
    @Test void ownershipRequiresExactConnectionIdentityEvenWhenEqualsMatches() {
        var state = new ClientConnectionSnapshot<String>();
        Object first = new String("same"), second = new String("same");
        state.begin(first);
        state.apply(first, "accepted");
        assertNull(state.capture(second));
        assertFalse(state.apply(second, "wrong owner"));
        assertFalse(state.end(second));
        assertEquals("accepted", state.capture(first).value());
    }
}
