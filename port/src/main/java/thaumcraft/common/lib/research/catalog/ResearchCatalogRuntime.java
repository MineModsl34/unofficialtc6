package thaumcraft.common.lib.research.catalog;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import thaumcraft.api.internal.CommonInternals;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;

/**
 * Server-authoritative metadata lifecycle, not research progression. Public registrations and
 * category definitions must be changed on the owning setup/server thread; arbitrary concurrent
 * writes to source-compatible public HashMaps cannot be made safe by this adapter. The initial
 * immutable snapshot is captured explicitly AFTER all mod setup (FMLLoadComplete enqueueWork).
 * No player attachment is loaded, deserialized, granted, rewritten or synchronized on reload.
 */
public final class ResearchCatalogRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResearchCatalogStaging<ReloadableServerResources, MinecraftServer> STAGING =
        new ResearchCatalogStaging<>();
    private static volatile ResearchCatalogSources.RegistrationSnapshot setupSnapshot;

    private ResearchCatalogRuntime() {}

    /** Startup wiring calls once AFTER all common setup work and addon registrations finish. */
    public static void captureSetupSnapshot() { setupSnapshot = snapshotRegistrationsOnOwnerThread(); }

    /** Only immutable lists/sets escape; source HashMap.values() iteration is not sorted. */
    public static ResearchCatalogSources.RegistrationSnapshot snapshotRegistrationsOnOwnerThread() {
        var registrations = new ArrayList<>(CommonInternals.jsonLocs.values());
        var categories = new LinkedHashSet<>(ResearchCategoryDefinitions.newBuiltinCategories().keySet());
        categories.addAll(ResearchCategories.researchCategories.keySet());
        return new ResearchCatalogSources.RegistrationSnapshot(registrations, categories);
    }

    public static ResearchCatalogReloadListener listener(ReloadableServerResources resources) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        CompletableFuture<ResearchCatalogSources.RegistrationSnapshot> future;
        if (server != null) {
            future = server.submit(() -> {
                requireOwnerThread(server);
                return snapshotRegistrationsOnOwnerThread();
            });
        } else {
            var immutable = setupSnapshot;
            future = immutable == null ? CompletableFuture.failedFuture(new IllegalStateException(
                "Research setup snapshot was not captured after common setup")) : CompletableFuture.completedFuture(immutable);
        }
        return new ResearchCatalogReloadListener(future, loaded -> {
            // An active-server resource preview must not inspect mutable API metadata on a
            // client's render executor. Reject that unsupported context before reading it.
            if (server != null) requireOwnerThread(server);
            var candidate = ResearchCatalogPublication.prepareWhole(loaded.spec(), ResearchCategories.researchCategories,
                STAGING.ownedEntries());
            STAGING.stage(resources, candidate, loaded.payload());
        });
    }

    /** Exact engine-accepted managers identity: initial startup OR successful datapack sync. */
    public static ResearchCatalogStaging.Acceptance commitAccepted(MinecraftServer server) {
        requireOwnerThread(server);
        var outcome = STAGING.accept(server.getServerResources().managers(), server);
        if (!outcome.accepted()) {
            LOGGER.error("Research catalog acceptance rejected ({}); retaining last accepted catalog and player data",
                outcome.status(), outcome.failure());
        }
        return outcome;
    }

    /** Immutable authoritative transport; integrated clients must borrow this, not republish it. */
    public static PacketSyncResearchCatalog currentPayload(MinecraftServer server) { return STAGING.currentPayload(server); }

    public static ResearchCatalogPublication.Snapshot currentSnapshot(MinecraftServer server) { return STAGING.currentSnapshot(server); }

    public static void clearServer(MinecraftServer server) {
        requireOwnerThread(server);
        // Keep registrations/category additions made by addons during this server session for the
        // next world's initial load. A stale older server must not replace a newer setup snapshot.
        if (STAGING.clearOwner(server)) captureSetupSnapshot();
    }

    private static void requireOwnerThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Research catalog metadata must use the server thread");
    }
}
