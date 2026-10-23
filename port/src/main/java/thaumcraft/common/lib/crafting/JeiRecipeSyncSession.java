package thaumcraft.common.lib.crafting;

import java.util.ArrayList;
import java.util.List;
import thaumcraft.common.lib.network.misc.PacketSyncJeiRecipes;

/** Bounded atomic assembly; a partial revision never replaces a previously accepted catalog. */
public final class JeiRecipeSyncSession {
    private Object connection;
    private long acceptedRevision, pendingRevision;
    private List<List<byte[]>> pending;
    private int count, bytes;
    public void begin(Object connection) {
        java.util.Objects.requireNonNull(connection);
        if (this.connection == connection) return;
        this.connection = connection; acceptedRevision = 0; resetPending();
    }
    public boolean end(Object connection) {
        if (connection == null || this.connection != connection) return false;
        this.connection = null; acceptedRevision = 0; resetPending(); return true;
    }
    public List<byte[]> accept(Object connection, PacketSyncJeiRecipes packet) {
        if (connection == null || this.connection != connection || packet.revision() <= acceptedRevision || packet.revision() < pendingRevision) return null;
        if (packet.revision() > pendingRevision) {
            resetPending(); pendingRevision = packet.revision();
            pending = new ArrayList<>(java.util.Collections.nCopies(packet.chunks(), null));
        }
        if (pending == null || pending.size() != packet.chunks() || pending.get(packet.index()) != null)
            throw new IllegalArgumentException("Inconsistent or repeated recipe chunk");
        List<byte[]> bodies = packet.recipes();
        for (byte[] body : bodies) { bytes = Math.addExact(bytes, body.length); count++; }
        if (bytes > PacketSyncJeiRecipes.MAX_SNAPSHOT_BYTES || count > PacketSyncJeiRecipes.MAX_RECIPES)
            throw new IllegalArgumentException("Recipe snapshot exceeds bounds");
        pending.set(packet.index(), bodies);
        if (pending.stream().anyMatch(java.util.Objects::isNull)) return null;
        List<byte[]> complete = pending.stream().flatMap(List::stream).toList();
        acceptedRevision = pendingRevision; resetPending(); return complete;
    }
    public long revision() { return acceptedRevision; }
    private void resetPending() { pending = null; pendingRevision = 0; count = bytes = 0; }
}
