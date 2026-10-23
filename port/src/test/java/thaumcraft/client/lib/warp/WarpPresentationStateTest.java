package thaumcraft.client.lib.warp;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WarpPresentationStateTest {
    @Test void sourceLongMistEnvelopeRampsAndExpiresAfter2401Ticks() {
        var state = new WarpPresentationState();
        state.mist(false);
        assertEquals(2400, state.duration());
        state.tick();
        assertEquals(0.001F, state.density());
        assertTrue(state.fogActive());
        for (int i = 1; i < 2301; i++) state.tick();
        assertEquals(99, state.duration());
        state.tick();
        assertEquals(0.099F, state.density(), 0.000001F);
        for (int i = 0; i < 99; i++) state.tick();
        assertEquals(-1, state.duration());
        assertFalse(state.fogActive());
    }
    @Test void shortMistExtendsOnlyShorterRemainingDurationAndDoesNotResetDensity() {
        var state = new WarpPresentationState();
        state.mist(false);
        for (int i = 0; i < 2350; i++) state.tick();
        float density = state.density();
        state.mist(true);
        assertEquals(200, state.duration());
        assertEquals(density, state.density());
        state.mist(false);
        state.mist(true);
        assertEquals(2400, state.duration());
        state.clear();
        assertFalse(state.fogActive());
        assertEquals(0, state.density());
    }
    @Test void sourceWarpMessageSilenceAndWhispersAreNotInventedNotifications() {
        assertEquals(new WarpPresentationState.Feedback(null, true), WarpPresentationState.feedback(2, (byte) 0));
        assertEquals(new WarpPresentationState.Feedback(null, false), WarpPresentationState.feedback(-2, (byte) 0));
        assertEquals(new WarpPresentationState.Feedback("tc.addwarpsticky", true), WarpPresentationState.feedback(2, (byte) 1));
        assertEquals(new WarpPresentationState.Feedback("tc.removewarpsticky", false), WarpPresentationState.feedback(-2, (byte) 1));
        assertEquals(new WarpPresentationState.Feedback("tc.addwarptemp", false), WarpPresentationState.feedback(2, (byte) 2));
        assertEquals(new WarpPresentationState.Feedback(null, false), WarpPresentationState.feedback(-2, (byte) 2));
        for (byte type = 0; type <= 2; type++) assertEquals(new WarpPresentationState.Feedback(null, false), WarpPresentationState.feedback(0, type));
    }
}
