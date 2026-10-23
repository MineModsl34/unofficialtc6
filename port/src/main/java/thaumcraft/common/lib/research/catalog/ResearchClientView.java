package thaumcraft.common.lib.research.catalog;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer;
import thaumcraft.common.lib.research.ResearchBrowserAvailability;
import thaumcraft.common.lib.research.ResearchReadFingerprint;
import thaumcraft.common.lib.research.catalog.ResearchCatalogClientSession.AcceptedView;

/**
 * Owning-thread coordinator for received owner knowledge and an actually accepted catalog view.
 * Connection, owner, catalog-generation and knowledge-generation checks use EXACT identity. A
 * freshly created/replaced LocalPlayer is not evidence that its default attachment was received.
 * The wrapper must call knowledgeApplied only AFTER the real owner packet has been applied.
 *
 * Snapshots are detached read-only queries, not grants, cost checks, authorization or a sender.
 * Their metadata is prepared privately from the immutable accepted spec and builtin category
 * definitions, never published or taken from the mutable global API. Native custom category
 * definitions are not in the catalog packet: this initial view refuses such candidates as a
 * whole instead of borrowing potentially newer/different definitions from an integrated server.
 */
public final class ResearchClientView {
    private Thread owningThread;
    private Object connection;
    private Received received;
    private Snapshot cached;

    private record Received(Object owner, Set<String> research, Map<String, Integer> stages,
                            CompoundTag nbt, String readStateDigest) {}

    /** Even a same-identity begin invalidates previously received owner data and tickets. */
    public boolean begin(Object actualConnection) {
        Objects.requireNonNull(actualConnection, "Actual research connection is required");
        checkThread(true);
        boolean changed = connection != actualConnection || received != null;
        connection = actualConnection;
        received = null;
        cached = null;
        return changed;
    }

    /** A late logout for a different connection cannot clear the new session. */
    public boolean end(Object actualConnection) {
        checkThread(false);
        if (connection == null || connection != actualConnection) return false;
        connection = null;
        received = null;
        cached = null;
        return true;
    }

    /**
     * Capture only an actual apply for this connection. Failed/malformed new owner data
     * invalidates the previous received epoch; it cannot authorize a stale snapshot instead.
     * No deserialization occurs: ambient AUTOUNLOCK metadata must not grant anything here.
     */
    public boolean knowledgeApplied(Object actualConnection, Object actualOwner, IPlayerKnowledge live) {
        return knowledgeApplied(actualConnection, actualOwner, live, null);
    }

    /** The token must describe the received wire body, before client AUTOUNLOCK normalization. */
    public boolean knowledgeApplied(Object actualConnection, Object actualOwner, IPlayerKnowledge live,
                                    CompoundTag receivedSnapshot) {
        checkThread(false);
        if (connection == null || connection != actualConnection) return false;
        received = null;
        cached = null;
        Objects.requireNonNull(actualOwner, "Actual owner is required");
        Objects.requireNonNull(live, "Applied owner knowledge is required");
        try {
            CompoundTag nbt = Objects.requireNonNull(live.serializeNBT(), "Owner knowledge NBT").copy();
            Set<String> research = new HashSet<>(Objects.requireNonNull(live.getResearchList()));
            Map<String, Integer> stages = new HashMap<>();
            for (String key : research) {
                Objects.requireNonNull(key, "Stored research key");
                stages.put(key, live.getResearchStage(key));
            }
            // This record's identity is the epoch: equal snapshots from separate packets are new.
            received = new Received(actualOwner, Collections.unmodifiableSet(research),
                Collections.unmodifiableMap(stages), nbt,
                ResearchReadFingerprint.of(receivedSnapshot == null ? nbt : receivedSnapshot));
            return true;
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    /** Null until BOTH an accepted catalog and an actual packet for this exact owner exist. */
    public Snapshot capture(AcceptedView catalog, Object actualOwner) {
        checkThread(false);
        Received current = received;
        if (connection == null || catalog == null || catalog.connection() != connection
            || current == null || current.owner() != actualOwner) return null;
        if (isCurrent(cached, catalog, actualOwner)) return cached;
        cached = null;
        try {
            var candidate = ResearchCatalogPublication.prepareWhole(catalog.spec(), Map.of(), Set.of());
            // Keep all mutable candidate/category/entry objects PRIVATE for the snapshot lifetime.
            cached = new Snapshot(this, owningThread, catalog, current, candidate.entries(), candidate.categories());
            return cached;
        } catch (RuntimeException malformedOrUnsupported) {
            return null;
        }
    }

    /**
     * Generation check only, not a server check. Callers must supply the CURRENT accepted view
     * and CURRENT owner from the actual session, not values retained alongside an old ticket.
     * A future sender must separately compare the live exact stage for its prepared key.
     */
    public boolean isCurrent(Snapshot snapshot, AcceptedView currentCatalog, Object actualOwner) {
        checkThread(false);
        return snapshot != null && snapshot.coordinator == this && connection != null
            && currentCatalog != null && currentCatalog == snapshot.catalog
            && currentCatalog.connection() == connection && received == snapshot.received
            && received != null && received.owner() == actualOwner;
    }

    private void checkThread(boolean bind) {
        Thread caller = Thread.currentThread();
        if (owningThread == null) {
            if (bind) owningThread = caller;
        } else if (owningThread != caller) {
            throw new IllegalStateException("Research client view must be used on its owning MAIN thread");
        }
    }

    /** Immutable detached owner queries. No mutable category/entry/model/NBT escapes this class. */
    public static final class Snapshot {
        private final ResearchClientView coordinator;
        private final Thread owningThread;
        private final AcceptedView catalog;
        private final Received received;
        private final ReadOnlyKnowledge knowledge;
        private final Map<String, ? extends ResearchEntry> entries;
        private final Map<String, ResearchCategory> categories;

        private Snapshot(ResearchClientView coordinator, Thread owningThread, AcceptedView catalog,
                         Received received, Map<String, ? extends ResearchEntry> entries,
                         Map<String, ResearchCategory> categories) {
            this.coordinator = coordinator;
            this.owningThread = owningThread;
            this.catalog = catalog;
            this.received = received;
            this.entries = entries;
            this.categories = categories;
            knowledge = new ReadOnlyKnowledge(received, catalog.spec().entriesByKey());
        }

        public AcceptedView accepted() { checkThread(); return catalog; }
        public ResearchCatalogSpec spec() { checkThread(); return catalog.spec(); }
        public String digest() { checkThread(); return catalog.digest(); }
        public String readStateDigest() { checkThread(); return received.readStateDigest(); }
        public boolean hasResearchFlag(String key, IPlayerKnowledge.EnumResearchFlag flag) {
            checkThread(); return knowledge.hasResearchFlag(key, flag);
        }
        public int getKnowledge(IPlayerKnowledge.EnumKnowledgeType type, String categoryKey) {
            checkThread();
            return categoryKey != null && !categories.containsKey(categoryKey) ? 0
                : knowledge.getKnowledge(type, categoryKey == null ? null : categories.get(categoryKey));
        }
        public int getKnowledgeRaw(IPlayerKnowledge.EnumKnowledgeType type, String categoryKey) {
            checkThread();
            return categoryKey != null && !categories.containsKey(categoryKey) ? 0
                : knowledge.getKnowledgeRaw(type, categoryKey == null ? null : categories.get(categoryKey));
        }
        /** Actual received-owner epoch identity, NOT a success ACK or a catalog revision.
         * Catalog-only reload cannot make a waiting screen infer a knowledge response. */
        public boolean sameOwnerKnowledgeGeneration(Snapshot previous) {
            checkThread();
            return previous != null && coordinator == previous.coordinator && received == previous.received;
        }
        public int storedStage(String key) { checkThread(); return knowledge.getResearchStage(key); }
        public IPlayerKnowledge.EnumResearchStatus knowledgeStatus(String key) {
            checkThread();
            return knowledge.getResearchStatus(key);
        }
        public boolean knows(String... keys) {
            checkThread();
            return ThaumcraftCapabilities.knowsResearchData(knowledge, keys);
        }
        public boolean knowsStrict(String... keys) {
            checkThread();
            return ThaumcraftCapabilities.knowsResearchStrictData(knowledge, keys);
        }
        /** Infusion's source gate calls isResearchKnown literally, without expression expansion. */
        public boolean knowsLiteral(String key) { checkThread(); return knowledge.isResearchKnown(key); }
        public ResearchBrowserAvailability.Decision availability(String key) {
            checkThread();
            return ResearchBrowserAvailability.evaluate(knowledge, key, entries::get, categories::get);
        }

        /**
         * Prepare a bounded request only for a registered, browser-available accepted key. No
         * packet is sent, no optimistic knowledge/stage/flag mutation occurs, and stage costs or
         * server-side gates are NOT evaluated. An old snapshot remains a queryable historical
         * view; isCurrent and the sender's live-stage comparison are required before dispatch.
         */
        public PacketSyncProgressToServer prepare(String key) {
            checkThread();
            if (!entries.containsKey(key) || !availability(key).mayRequestProgress()) return null;
            try {
                return new PacketSyncProgressToServer(key, storedStage(key), digest());
            } catch (RuntimeException malformedOrUnbounded) {
                return null;
            }
        }

        private void checkThread() {
            if (Thread.currentThread() != owningThread) {
                throw new IllegalStateException("Research snapshot queries require the owning MAIN thread");
            }
        }
    }

    /**
     * Only the tiny literal storage queries are copied from PlayerKnowledge: whole expression
     * AND/regex-OR evaluation remains the single ThaumcraftCapabilities source evaluator.
     * Status looks up the qualified key LITERALLY in this accepted spec, just like TC6 did.
     */
    private static final class ReadOnlyKnowledge implements IPlayerKnowledge {
        private final Received received;
        private final Map<String, ResearchCatalogSpec.Entry> entries;
        private final Map<String, Set<EnumResearchFlag>> flags = new HashMap<>();
        private final Map<String, Integer> pools = new HashMap<>();

        private ReadOnlyKnowledge(Received received, Map<String, ResearchCatalogSpec.Entry> entries) {
            this.received = received;
            this.entries = entries;
            var research = received.nbt().getList("research", Tag.TAG_COMPOUND);
            for (int i = 0; i < research.size(); i++) {
                var entry = research.getCompound(i);
                var values = new HashSet<EnumResearchFlag>();
                for (String name : entry.getString("flags").split(",")) {
                    try { values.add(EnumResearchFlag.valueOf(name)); }
                    catch (IllegalArgumentException ignored) { /* source ignores unknown flags */ }
                }
                flags.put(entry.getString("key"), Set.copyOf(values));
            }
            var knowledge = received.nbt().getList("knowledge", Tag.TAG_COMPOUND);
            for (int i = 0; i < knowledge.size(); i++) {
                var entry = knowledge.getCompound(i);
                pools.put(entry.getString("key"), entry.getInt("amount"));
            }
        }

        @Override public boolean isResearchKnown(String key) {
            if (key == null) return false;
            if (key.equals("")) return true;
            String[] parts = key.split("@");
            return parts.length > 1 && getResearchStage(parts[0]) < parseIntOrZero(parts[1])
                ? false : received.research().contains(parts[0]);
        }

        @Override public int getResearchStage(String key) {
            return key != null && received.research().contains(key)
                ? received.stages().getOrDefault(key, 0) : -1;
        }

        @Override public EnumResearchStatus getResearchStatus(String key) {
            if (!isResearchKnown(key)) return EnumResearchStatus.UNKNOWN;
            var entry = entries.get(key);
            return entry != null && entry.stages().legacyValues() != null
                && getResearchStage(key) <= entry.stages().values().size()
                ? EnumResearchStatus.IN_PROGRESS : EnumResearchStatus.COMPLETE;
        }

        @Override public boolean isResearchComplete(String key) {
            return getResearchStatus(key) == EnumResearchStatus.COMPLETE;
        }

        @Override public Set<String> getResearchList() { return received.research(); }
        @Override public CompoundTag serializeNBT() { return received.nbt().copy(); }
        @Override public void clear() { throw readOnly(); }
        @Override public boolean addResearch(String key) { throw readOnly(); }
        @Override public boolean setResearchStage(String key, int stage) { throw readOnly(); }
        @Override public boolean removeResearch(String key) { throw readOnly(); }
        @Override public boolean setResearchFlag(String key, EnumResearchFlag flag) { throw readOnly(); }
        @Override public boolean clearResearchFlag(String key, EnumResearchFlag flag) { throw readOnly(); }
        @Override public boolean hasResearchFlag(String key, EnumResearchFlag flag) {
            return flags.getOrDefault(key, Set.of()).contains(flag);
        }
        @Override public boolean addKnowledge(EnumKnowledgeType type, ResearchCategory category, int amount) { throw readOnly(); }
        @Override public int getKnowledge(EnumKnowledgeType type, ResearchCategory category) {
            return (int) Math.floor((double) getKnowledgeRaw(type, category) / type.getProgression());
        }
        @Override public int getKnowledgeRaw(EnumKnowledgeType type, ResearchCategory category) {
            return pools.getOrDefault(type.getAbbreviation() + "_" + (category == null ? "" : category.key), 0);
        }
        @Override public void sync(ServerPlayer player) { throw readOnly(); }
        @Override public void deserializeNBT(CompoundTag tag) { throw readOnly(); }

        private static int parseIntOrZero(String value) {
            try { return Integer.parseInt(value); }
            catch (NumberFormatException ignored) { return 0; }
        }

        private static UnsupportedOperationException readOnly() {
            return new UnsupportedOperationException("Private received research view is read-only");
        }
    }
}
