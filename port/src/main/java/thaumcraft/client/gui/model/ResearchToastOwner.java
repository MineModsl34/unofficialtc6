package thaumcraft.client.gui.model;

import java.lang.ref.WeakReference;

/** A source popup must not follow a previous owner's notification into another session.
 * Exact identities only; no connection/player is kept alive and no knowledge is granted.
 */
public final class ResearchToastOwner {
    private final WeakReference<Object> connection;
    private final WeakReference<Object> player;

    public ResearchToastOwner(Object actualConnection, Object actualPlayer) {
        connection = new WeakReference<>(actualConnection);
        player = new WeakReference<>(actualPlayer);
    }

    public boolean current(Object actualConnection, Object actualPlayer) {
        return actualConnection != null && actualPlayer != null
            && connection.get() == actualConnection && player.get() == actualPlayer;
    }
}
