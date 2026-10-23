package thaumcraft.client.lib;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;
import thaumcraft.common.lib.research.catalog.ResearchCatalogClientSession;
import thaumcraft.common.lib.research.catalog.ResearchCatalogRuntime;

/** MAIN-thread metadata application; remote owns entries, integrated memory connections borrow. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientResearchCatalogHandlers {
    private static final ResearchCatalogClientSession SESSION = new ResearchCatalogClientSession();

    private ClientResearchCatalogHandlers() {}

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        requireMainThread();
        if (SESSION.begin(event.getConnection(), event.getConnection().isMemoryConnection())) {
            ClientResearchPresentation.begin(event.getConnection());
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        requireMainThread();
        // Minecraft nulls singleplayerServer BEFORE LoggingOut. The session remembers its role.
        if (event.getConnection() != null && SESSION.end(event.getConnection())) {
            ClientResearchPresentation.end(event.getConnection());
        }
    }

    public static void catalog(PacketSyncResearchCatalog packet, IPayloadContext context) {
        requireMainThread();
        var minecraft = Minecraft.getInstance();
        var listener = minecraft.getConnection();
        if (listener == null || listener.getConnection() != context.connection()) return;
        boolean memory = context.connection().isMemoryConnection();
        var integrated = minecraft.getSingleplayerServer();
        try {
            var expected = memory && integrated != null ? ResearchCatalogRuntime.currentPayload(integrated) : null;
            if (!SESSION.apply(context.connection(), memory, packet, expected)) return;
            if (Boolean.getBoolean("thaumcraft.researchStageFourNetworkAudit")) {
                var spec = SESSION.activeSpec();
                ResearchStageFourNetworkClientAudit.catalog(packet, SESSION.activeDigest(), spec.entries().size(),
                    spec.entries().stream().mapToInt(entry -> entry.stages().values().size()).sum());
            }
            if (Boolean.getBoolean("thaumcraft.researchProgressNetworkAudit")) {
                var spec = SESSION.activeSpec();
                ResearchProgressNetworkClientAudit.catalog(packet, SESSION.activeDigest(), spec.entries().size(),
                    spec.entries().stream().mapToInt(entry -> entry.stages().values().size()).sum());
            }
            if (Boolean.getBoolean("thaumcraft.researchCatalogNetworkAudit")) {
                var spec = SESSION.activeSpec();
                ResearchCatalogNetworkClientAudit.observeCatalog(packet, SESSION.activeDigest(), spec.entries().size(),
                    spec.entries().stream().mapToInt(entry -> entry.stages().values().size()).sum());
            }
        } catch (RuntimeException invalidCatalog) {
            Thaumcraft.log.error("Rejected whole server research catalog", invalidCatalog);
            context.disconnect(Component.literal("Invalid Thaumcraft research catalog; previous catalog was not replaced."));
        }
    }

    /** Catalog arrives through OnDatapackSync before the subsequent knowledge login snapshot. */
    public static boolean readyForCurrentConnection() {
        requireMainThread();
        var minecraft = Minecraft.getInstance();
        var listener = minecraft.getConnection();
        if (listener == null) return false;
        if (!SESSION.isActive(listener.getConnection())) return false;
        if (listener.getConnection().isMemoryConnection()) {
            var integrated = minecraft.getSingleplayerServer();
            // A delayed memory-connection echo may be older than the already accepted server map.
            return SESSION.borrowed() && integrated != null && ResearchCatalogRuntime.currentPayload(integrated) != null;
        }
        return SESSION.activeDigest() != null;
    }

    /** MAIN-only accepted remote SESSION revision, not an integrated-server query or a grant.
     * Null before an actual catalog apply, after logout, or for a different/memory connection. */
    public static String acceptedDigestForCurrentConnection() {
        requireMainThread();
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || listener.getConnection().isMemoryConnection()
            || !SESSION.isActive(listener.getConnection())) return null;
        return SESSION.activeDigest();
    }

    /** Actual accepted SESSION snapshot, including memory connections. A newer integrated
     * server publication does not substitute for the echo this client has actually accepted. */
    public static ResearchCatalogClientSession.AcceptedView acceptedViewForCurrentConnection() {
        requireMainThread();
        var listener = Minecraft.getInstance().getConnection();
        return listener == null ? null : SESSION.acceptedView(listener.getConnection());
    }

    private static void requireMainThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Research catalog client lifecycle must run on MAIN");
    }
}
