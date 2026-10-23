package thaumcraft.common.lib.network.playerdata;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.lib.research.ResearchBrowserAvailability;
import thaumcraft.common.lib.research.ServerResearchProgression;
import thaumcraft.common.lib.research.catalog.ResearchCatalogRuntime;

/** Browser-only C2S boundary. Trusted scanning/crafting/Manager APIs are not restricted here.
 * The packet supplies neither an owner UUID nor client-side requisites/noFlags booleans.
 * Current accepted JSON-owned entry identity and digest are required: arbitrary native addon
 * API registrations have no equivalent transport/revision yet and are explicitly unsupported.
 * SHA-256 identifies ordered catalog content, not sender authentication; the actual PLAY
 * connection supplies the owner. No GUI, acknowledgement packet or TCP acceptance claim.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class ServerResearchRequestHandler {
    public static final int REQUEST_INTERVAL_TICKS = 5;
    private static final int MAX_TRACKED_OWNER_SESSIONS = 4096;
    private static final Map<MinecraftServer, Attempts> ATTEMPTS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Attempts> READ_ATTEMPTS = new WeakHashMap<>();

    private ServerResearchRequestHandler() {}

    public enum Status {
        APPLIED, RATE_LIMITED, INVALID_REQUEST, CATALOG_UNAVAILABLE, STALE_CATALOG,
        NOT_ACCEPTED_ENTRY, BROWSER_UNAVAILABLE, PROGRESSION_REJECTED
    }
    public record Result(Status status, String detail, ServerResearchProgression.Result progression) {
        public boolean applied() { return status == Status.APPLIED; }
    }

    /** Only real PLAY serverbound contexts may reach the trusted server helper. Main-thread
     * work rejects a queued stale respawn/session owner rather than applying it to a new object. */
    public static void receive(PacketSyncProgressToServer packet, IPayloadContext context) {
        if (context.protocol() != ConnectionProtocol.PLAY || context.flow() != PacketFlow.SERVERBOUND
            || !(context.player() instanceof ServerPlayer owner)) return;
        context.enqueueWork(() -> {
            if (context.protocol() != ConnectionProtocol.PLAY || context.flow() != PacketFlow.SERVERBOUND
                || context.player() != owner || owner.getServer() == null
                || owner.connection.getConnection() != context.connection() || !context.connection().isConnected()) return;
            var result = tryHandle(owner, packet);
            // Default-off private recorder: only AFTER this actual contextual TCP route, never
            // from the trusted helper/GameTest seam, and never a replacement for authorization.
            if (Boolean.getBoolean("thaumcraft.researchProgressNetworkAudit")) {
                thaumcraft.common.lib.research.catalog.validation.ResearchProgressNetworkAudit
                    .observeRequest(owner, packet, result);
            }
            if (Boolean.getBoolean("thaumcraft.tcpBootstrapAudit")) {
                thaumcraft.common.lib.validation.TcpBootstrapAudit.observeRequest(owner, packet, result);
            }
            if (Boolean.getBoolean("thaumcraft.tcpAlchemyAudit")) {
                thaumcraft.common.lib.validation.TcpAlchemyAudit.observeRequest(owner, packet, result);
            }
            if (Boolean.getBoolean("thaumcraft.tcpCasterAudit")) {
                thaumcraft.common.lib.validation.TcpCasterAudit.observeRequest(owner, packet, result);
            }
        });
    }

    /** Caller is already trusted server code; this is NOT an alternate network authentication
     * path. Public for deterministic GameTests and server integrations with a real owner object.
     * Invalid, stale and replayed requests consume the same limiter before catalog traversal.
     */
    public static Result tryHandle(ServerPlayer owner, PacketSyncProgressToServer packet) {
        if (owner == null || owner.getServer() == null || !owner.getServer().isSameThread()) {
            throw new IllegalStateException("Research requests must run on the owning server thread");
        }
        MinecraftServer server = owner.getServer();
        if (!admit(server, owner)) return result(Status.RATE_LIMITED, "Wait five server ticks between attempts");
        if (packet == null) return result(Status.INVALID_REQUEST, "Missing request");
        var payload = ResearchCatalogRuntime.currentPayload(server);
        var snapshot = ResearchCatalogRuntime.currentSnapshot(server);
        if (payload == null || snapshot == null) return result(Status.CATALOG_UNAVAILABLE, "No accepted server catalog");
        if (!payload.digest().equals(packet.catalogDigest())) return result(Status.STALE_CATALOG, "Accepted catalog content changed");
        var accepted = snapshot.entries().get(packet.researchKey());
        if (accepted == null || accepted != ResearchCategories.getResearch(packet.researchKey())
            || !snapshot.ownedEntries().contains(accepted)) {
            return result(Status.NOT_ACCEPTED_ENTRY, "Not the current accepted JSON-owned entry identity");
        }
        var available = ResearchBrowserAvailability.evaluate(ThaumcraftCapabilities.getKnowledge(owner), packet.researchKey());
        if (!available.mayRequestProgress()) return result(Status.BROWSER_UNAVAILABLE, available.reason().name());
        var progression = ServerResearchProgression.tryProgress(owner, packet.researchKey(), packet.expectedStage());
        return new Result(progression.applied() ? Status.APPLIED : Status.PROGRESSION_REJECTED,
            progression.detail(), progression);
    }

    private static Result result(Status status, String detail) { return new Result(status, detail, null); }
    private static boolean admit(MinecraftServer server, ServerPlayer owner) {
        synchronized (ATTEMPTS) {
            return ATTEMPTS.computeIfAbsent(server, ignored -> new Attempts()).admit(owner, server.getTickCount());
        }
    }

    static boolean admitRead(ServerPlayer owner) {
        synchronized (ATTEMPTS) {
            return READ_ATTEMPTS.computeIfAbsent(owner.getServer(), ignored -> new Attempts())
                .admit(owner, owner.getServer().getTickCount());
        }
    }

    /** Lifecycle-only cleanup; no attachment serialization, research grant or network send. */
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer owner)) return;
        synchronized (ATTEMPTS) {
            Attempts attempts = ATTEMPTS.get(owner.getServer());
            if (attempts != null) attempts.forget(owner);
            Attempts reads = READ_ATTEMPTS.get(owner.getServer());
            if (reads != null) reads.forget(owner);
        }
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        synchronized (ATTEMPTS) { ATTEMPTS.remove(event.getServer()); READ_ATTEMPTS.remove(event.getServer()); }
    }

    /** Bounded weak identity/session keys: neither UUID aliases nor respawn/reconnect objects
     * inherit another object's limiter. Values retain no owner/server references. */
    private static final class Attempts {
        final ReferenceQueue<ServerPlayer> collected = new ReferenceQueue<>();
        final Map<OwnerKey, Integer> ticks = new HashMap<>();

        boolean admit(ServerPlayer owner, int now) {
            expunge();
            OwnerKey lookup = new OwnerKey(owner, null);
            Integer previous = ticks.get(lookup);
            if (previous != null && elapsed(now, previous) < REQUEST_INTERVAL_TICKS) return false;
            if (previous == null && ticks.size() >= MAX_TRACKED_OWNER_SESSIONS) {
                ticks.entrySet().removeIf(entry -> entry.getKey().get() == null
                    || elapsed(now, entry.getValue()) >= REQUEST_INTERVAL_TICKS);
                if (ticks.size() >= MAX_TRACKED_OWNER_SESSIONS) return false;
            }
            ticks.put(new OwnerKey(owner, collected), now);
            return true;
        }
        void forget(ServerPlayer owner) { expunge(); ticks.remove(new OwnerKey(owner, null)); }
        void expunge() {
            java.lang.ref.Reference<? extends ServerPlayer> reference;
            while ((reference = collected.poll()) != null) ticks.remove(reference);
        }
        // MinecraftServer's monotonic int tick counter wraps; world/day time is never consulted.
        static long elapsed(int now, int before) { return Integer.toUnsignedLong(now - before); }
    }
    private static final class OwnerKey extends WeakReference<ServerPlayer> {
        final int hash;
        OwnerKey(ServerPlayer owner, ReferenceQueue<ServerPlayer> queue) { super(owner, queue); hash = System.identityHashCode(owner); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            ServerPlayer owner = get();
            return owner != null && other instanceof OwnerKey key && owner == key.get();
        }
    }
}
