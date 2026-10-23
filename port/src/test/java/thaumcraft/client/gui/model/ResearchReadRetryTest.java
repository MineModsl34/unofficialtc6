package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Pure scheduling checks only; this is not proof of TCP delivery or flag acceptance. */
class ResearchReadRetryTest {
    @Test void absentAcknowledgementCanRetryAtTwentyTicksRatherThanRemainPendingForever() {
        var retry = new ResearchReadRetry();
        var owner = new Object();
        assertTrue(retry.canSend(owner, 100));
        retry.sent(owner, 100);
        for (int elapsed = 0; elapsed < 20; elapsed++) assertFalse(retry.canSend(owner, 100 + elapsed));
        assertTrue(retry.canSend(owner, 120));
        retry.sent(owner, 120);
        assertFalse(retry.canSend(owner, 139));
        assertTrue(retry.canSend(owner, 140));
    }

    @Test void pageOrAcceptedSnapshotChangeCannotBypassSameOwnerGlobalCooldown() {
        var retry = new ResearchReadRetry();
        var owner = new Object();
        retry.sent(owner, 50); // Page A; a new owner S2C may arrive immediately afterward.
        assertFalse(retry.canSend(owner, 51)); // Opening page B does not send inside server5.
        assertFalse(retry.canSend(owner, 55));
        assertTrue(retry.canSend(owner, 70)); // B remains open and requests from its current snapshot.
    }

    @Test void eligibilityWithoutActualDispatchDoesNotConsumeTheRetry() {
        var retry = new ResearchReadRetry();
        var owner = new Object();
        assertTrue(retry.canSend(owner, 4));
        assertTrue(retry.canSend(owner, 4));
        assertTrue(retry.canSend(owner, 5));
        retry.sent(owner, 5);
        assertFalse(retry.canSend(owner, 6));
    }

    @Test void actualOwnerIdentityAndLifecycleResetDoNotRetainOldPendingReads() {
        var retry = new ResearchReadRetry();
        var oldOwner = new String("owner");
        var newOwner = new String("owner");
        retry.sent(oldOwner, 10);
        assertFalse(retry.canSend(oldOwner, 11));
        assertTrue(retry.canSend(newOwner, 11)); // Exact identity, not profile/value equality.
        retry.sent(newOwner, 11);
        assertFalse(retry.canSend(newOwner, 12));
        retry.reset();
        assertTrue(retry.canSend(newOwner, 12));
    }

    @Test void tickCounterWrapStillRequiresTwentyElapsedClientTicks() {
        var retry = new ResearchReadRetry();
        var owner = new Object();
        int start = Integer.MAX_VALUE - 9;
        retry.sent(owner, start);
        assertFalse(retry.canSend(owner, start + 19));
        assertTrue(retry.canSend(owner, start + 20));
        retry.sent(owner, start + 20);
        assertFalse(retry.canSend(owner, start + 39));
        assertTrue(retry.canSend(owner, start + 40));
    }

    @Test void missingOwnerCannotSendOrBeRecorded() {
        var retry = new ResearchReadRetry();
        assertFalse(retry.canSend(null, 0));
        assertThrows(NullPointerException.class, () -> retry.sent(null, 0));
        assertFalse(retry.canSend(null, 100));
    }
}
