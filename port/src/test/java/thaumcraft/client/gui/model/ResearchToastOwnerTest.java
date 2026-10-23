package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ResearchToastOwnerTest {
    @Test void sourceNotificationDoesNotFollowAnEqualButDifferentOwnerOrConnection() {
        var connection = new String("connection");
        var player = new String("owner");
        var notification = new ResearchToastOwner(connection, player);
        assertTrue(notification.current(connection, player));
        assertFalse(notification.current(new String("connection"), player));
        assertFalse(notification.current(connection, new String("owner")));
        assertFalse(notification.current(null, player));
        assertFalse(notification.current(connection, null));
    }

    @Test void notificationWithoutAnActualOwnerCannotAcquireOneFromALaterLogin() {
        var connection = new Object();
        var player = new Object();
        assertFalse(new ResearchToastOwner(null, player).current(connection, player));
        assertFalse(new ResearchToastOwner(connection, null).current(connection, player));
        assertFalse(new ResearchToastOwner(null, null).current(null, null));
    }
}
