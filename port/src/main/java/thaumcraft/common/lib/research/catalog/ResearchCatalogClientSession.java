package thaumcraft.common.lib.research.catalog;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;

/**
 * Synchronous client-MAIN catalog ownership, without any client-only class, listener or handler.
 * A wrapper must supply the actual connection object and remember its memory/remote role at login;
 * disconnect-time singleplayer-server discovery is not an authoritative role check. Connections
 * are compared by EXACT identity, never equals. Stale apply/end calls cannot touch a newer session.
 *
 * Remote snapshots prepare/publish metadata as a whole, replacing only exact previous entry
 * identities. Integrated-memory snapshots borrow the shared server catalog and never republish or
 * remove it. This class does not resolve items/recipes, mutate player NBT or grant research.
 * Public TC6 metadata is mutable; callers/addons must use the owning MAIN thread, not concurrent
 * public-map writes. The first valid begin binds this instance to that thread.
 */
public final class ResearchCatalogClientSession {
    private static final ResearchCatalogSpec EMPTY = new ResearchCatalogSpec(Map.of(), List.of(), List.of());
    private final ResearchCatalogPublication publication = new ResearchCatalogPublication();
    private Thread owningThread;
    private Session session;

    /**
     * Immutable metadata from an ACTUAL successful apply, not a server query, player-data snapshot
     * or permission to progress. The connection is an exact-identity token, not an equals key.
     * Treat the view's identity as an opaque generation: every successful apply may replace it,
     * even when its digest is unchanged. A retained view is not proof that its session is current.
     */
    public record AcceptedView(Object connection, boolean memory, String digest, ResearchCatalogSpec spec) {
        public AcceptedView {
            Objects.requireNonNull(connection, "Actual research connection is required");
            Objects.requireNonNull(digest, "Accepted catalog digest is required");
            Objects.requireNonNull(spec, "Accepted catalog spec is required");
        }
    }

    private record Session(Object connection, boolean memory, String digest,
                           ResearchCatalogSpec spec, Set<ResearchEntry> ownedEntries, AcceptedView acceptedView) {}

    /** Same connection/same role is idempotent; role changes for the same identity are rejected. */
    public boolean begin(Object connection, boolean memory) {
        Objects.requireNonNull(connection, "Actual research connection is required");
        checkThread();
        Session current = session;
        if (current != null && current.connection() == connection) {
            requireRole(current, memory);
            return false;
        }
        if (current != null) removeRemoteOwned(current);
        session = new Session(connection, memory, null, null, Set.of(), null);
        return true;
    }

    /**
     * Return false for an old connection without parsing, dereferencing its packet or cleaning a
     * current session. A memory echo must match the actual integrated server's accepted payload;
     * its caller-supplied expected payload is never used as a substitute remote snapshot.
     */
    public boolean apply(Object connection, boolean memory, PacketSyncResearchCatalog packet,
                         PacketSyncResearchCatalog integratedExpected) {
        Session current = session;
        if (current == null || current.connection() != connection) return false;
        checkThread();
        requireRole(current, memory);
        Objects.requireNonNull(packet, "Research catalog packet is required");
        Map<String, String> sources = new LinkedHashMap<>();
        packet.sources().forEach((location, raw) -> sources.put(location.toString(), raw));
        Set<String> categories = new LinkedHashSet<>(ResearchCategoryDefinitions.newBuiltinCategories().keySet());
        categories.addAll(ResearchCategories.researchCategories.keySet());
        ResearchCatalogSpec parsed = ResearchCatalogParser.parse(sources, categories);
        if (memory) {
            Objects.requireNonNull(integratedExpected, "Actual integrated server catalog snapshot is required");
            if (!packet.digest().equals(integratedExpected.digest())) {
                // The shared server may already have committed a reload before an earlier valid
                // echo is dequeued on client MAIN. Do not publish, regress or accept that snapshot.
                return false;
            }
            var view = new AcceptedView(connection, true, packet.digest(), parsed);
            session = new Session(connection, true, packet.digest(), parsed, Set.of(), view);
        } else {
            var candidate = ResearchCatalogPublication.prepareWhole(parsed, ResearchCategories.researchCategories,
                current.ownedEntries());
            var accepted = publication.publish(candidate);
            // No session state is updated until the entire detached snapshot was published.
            var view = new AcceptedView(connection, false, packet.digest(), accepted.spec());
            session = new Session(connection, false, packet.digest(), accepted.spec(), accepted.ownedEntries(), view);
        }
        return true;
    }

    /** End only the exact remembered connection; memory catalogs remain server-owned. */
    public boolean end(Object connection) {
        Session current = session;
        if (current == null || current.connection() != connection) return false;
        checkThread();
        removeRemoteOwned(current);
        session = null;
        return true;
    }

    public boolean active() { return session != null; }
    public boolean isActive(Object connection) {
        Session current = session;
        return current != null && current.connection() == connection;
    }
    public String activeDigest() { return session == null ? null : session.digest(); }
    public boolean borrowed() { return session != null && session.memory(); }
    public ResearchCatalogSpec activeSpec() { return session == null ? null : session.spec(); }
    /**
     * MAIN-only cached view for this EXACT connection; null before apply, for an old connection
     * or after end. Reading before the first begin does not bind a thread. Memory connections
     * expose only their last accepted echo, never a newer shared/server payload. No publication,
     * cleanup, metadata mutation or player-data write occurs here.
     */
    public AcceptedView acceptedView(Object connection) {
        if (owningThread != null) checkThread();
        Session current = session;
        return current != null && current.connection() == connection ? current.acceptedView() : null;
    }
    /** Immutable identity set; mutable ResearchEntry objects still belong to the original public API. */
    public Set<ResearchEntry> ownedEntries() { return session == null ? Set.of() : session.ownedEntries(); }

    private void removeRemoteOwned(Session current) {
        if (current.memory() || current.ownedEntries().isEmpty()) return;
        var candidate = ResearchCatalogPublication.prepareWhole(EMPTY, ResearchCategories.researchCategories,
            current.ownedEntries());
        publication.publish(candidate);
    }

    private static void requireRole(Session current, boolean memory) {
        if (current.memory() != memory) throw new IllegalStateException("Research connection memory/remote role changed");
    }

    private void checkThread() {
        Thread caller = Thread.currentThread();
        if (owningThread == null) owningThread = caller;
        else if (owningThread != caller) throw new IllegalStateException("Research client session must be used on its owning MAIN thread");
    }
}
