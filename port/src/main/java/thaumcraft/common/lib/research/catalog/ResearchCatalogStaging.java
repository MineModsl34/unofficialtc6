package thaumcraft.common.lib.research.catalog;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;

/**
 * Two-phase acceptance store. K is the EXACT reload-resources identity, O the server/session owner.
 * Applying our listener only stages a detached candidate. A later successful engine acceptance is
 * the only route to publication. Weak identity keys deliberately are not retained by their values,
 * and even the accepted resource key is weak. Call metadata operations on their owning thread;
 * synchronization here does not make arbitrary addon writes to the public TC API thread-safe.
 */
public final class ResearchCatalogStaging<K, O> {
    private final ReferenceQueue<K> collected = new ReferenceQueue<>();
    private final Map<IdentityKey<K>, Prepared> pending = new HashMap<>();
    private final ResearchCatalogPublication publication = new ResearchCatalogPublication();
    private volatile Current<K, O> current;

    private record Prepared(ResearchCatalogPublication.Candidate candidate, PacketSyncResearchCatalog payload) {}
    private record Current<K, O>(O owner, WeakReference<K> resources, PacketSyncResearchCatalog payload,
                                  ResearchCatalogPublication.Snapshot snapshot) {}

    public enum Status { COMMITTED, ALREADY_CURRENT, NOT_STAGED, OWNER_CONFLICT, STALE }
    public record Acceptance(Status status, RuntimeException failure) {
        public boolean accepted() { return status == Status.COMMITTED || status == Status.ALREADY_CURRENT; }
    }

    /** The caller already constructed the WHOLE bounded payload and validated all metadata. */
    public synchronized void stage(K resources, ResearchCatalogPublication.Candidate candidate,
                                    PacketSyncResearchCatalog payload) {
        Objects.requireNonNull(resources);
        Objects.requireNonNull(candidate);
        Objects.requireNonNull(payload);
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        payload.sources().forEach((location, raw) -> labels.put(location.toString(), raw));
        if (!labels.equals(candidate.spec().sources())
            || !List.copyOf(labels.keySet()).equals(List.copyOf(candidate.spec().sources().keySet()))) {
            throw new IllegalArgumentException("Candidate and transport source snapshots differ");
        }
        expungeCollected();
        IdentityKey<K> key = new IdentityKey<>(resources, collected);
        if (pending.containsKey(key)) throw new IllegalStateException("Resources already staged");
        pending.put(key, new Prepared(candidate, payload));
    }

    /** A failed/unaccepted global reload never calls this, hence cannot replace the public map. */
    public synchronized Acceptance accept(K acceptedResources, O owner) {
        Objects.requireNonNull(acceptedResources);
        Objects.requireNonNull(owner);
        expungeCollected();
        Current<K, O> previous = current;
        if (previous != null && previous.owner() != owner) return new Acceptance(Status.OWNER_CONFLICT, null);
        if (previous != null && previous.resources().get() == acceptedResources) {
            return new Acceptance(Status.ALREADY_CURRENT, null);
        }
        Prepared prepared = pending.remove(new IdentityKey<>(acceptedResources, null));
        if (prepared == null) return new Acceptance(Status.NOT_STAGED, null);
        try {
            ResearchCatalogPublication.Snapshot snapshot = publication.publish(prepared.candidate());
            current = new Current<>(owner, new WeakReference<>(acceptedResources), prepared.payload(), snapshot);
            return new Acceptance(Status.COMMITTED, null);
        } catch (RuntimeException stale) {
            // Do not reprepare after vanilla has committed: keep the last accepted catalog/payload.
            return new Acceptance(Status.STALE, stale);
        }
    }

    public PacketSyncResearchCatalog currentPayload(O owner) {
        Current<K, O> snapshot = current;
        return snapshot != null && snapshot.owner() == owner ? snapshot.payload() : null;
    }

    public ResearchCatalogPublication.Snapshot currentSnapshot(O owner) {
        Current<K, O> snapshot = current;
        return snapshot != null && snapshot.owner() == owner ? snapshot.snapshot() : null;
    }

    public Set<ResearchEntry> ownedEntries() {
        Current<K, O> snapshot = current;
        return snapshot == null ? Set.of() : snapshot.snapshot().ownedEntries();
    }

    /** Drops only exact entries owned by this authority; addon replacements and definitions win. */
    public synchronized boolean clearOwner(O owner) {
        Current<K, O> snapshot = current;
        if (snapshot == null || snapshot.owner() != owner) return false;
        boolean containsOwned = ResearchCategories.researchCategories.values().stream()
            .flatMap(category -> category.research.values().stream()).anyMatch(snapshot.snapshot().ownedEntries()::contains);
        if (containsOwned) {
            ResearchCatalogSpec empty = new ResearchCatalogSpec(Map.of(), List.of(), List.of());
            publication.publish(ResearchCatalogPublication.prepareWhole(empty,
                ResearchCategories.researchCategories, snapshot.snapshot().ownedEntries()));
        }
        current = null;
        expungeCollected();
        return true;
    }

    synchronized int pendingCount() { expungeCollected(); return pending.size(); }

    private void expungeCollected() {
        java.lang.ref.Reference<? extends K> key;
        while ((key = collected.poll()) != null) pending.remove(key);
    }

    /** HashMap equality must not equate distinct reload objects whose equals() happens to agree. */
    private static final class IdentityKey<K> extends WeakReference<K> {
        private final int hash;
        IdentityKey(K key, ReferenceQueue<K> queue) { super(key, queue); hash = System.identityHashCode(key); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            Object referent = get();
            return referent != null && other instanceof IdentityKey<?> key && referent == key.get();
        }
    }
}
