package thaumcraft.client.gui.model;

import java.lang.ref.WeakReference;
import java.util.Objects;

/** Bounded retry of the existing owner read acknowledgement, not a server result.
 * The server silently drops reads inside its five-server-tick interval. An open page
 * can retry after twenty client ticks while the accepted snapshot still has read flags.
 * This global owner cooldown survives snapshot/page changes, so a fast S2C or a second
 * page cannot bypass it. No research keys, snapshots, connections or flags are retained.
 */
public final class ResearchReadRetry {
    public static final int INTERVAL_TICKS = 20;
    private WeakReference<Object> owner = new WeakReference<>(null);
    private boolean sent;
    private int lastSentTick;

    /** Checking eligibility does not consume a retry; record only a real dispatch. */
    public boolean canSend(Object actualOwner, int clientTick) {
        return actualOwner != null && (!sent || owner.get() != actualOwner
            || Integer.toUnsignedLong(clientTick - lastSentTick) >= INTERVAL_TICKS);
    }

    public void sent(Object actualOwner, int clientTick) {
        owner = new WeakReference<>(Objects.requireNonNull(actualOwner, "Actual research owner"));
        lastSentTick = clientTick;
        sent = true;
    }

    public void reset() {
        owner.clear();
        sent = false;
        lastSentTick = 0;
    }
}
