package thaumcraft.common.lib.research.catalog;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Snapshot acquisition precedes background I/O. AddReloadListenerEvent itself can fire on a
 * background executor (ReloadableServerRegistries:55 / ReloadableServerResources:108-114), so it
 * must not read public mutable research maps. Our apply callback only STAGES on gameExecutor; a
 * later listener may still fail, and only the engine's accepted-resources hook may publish.
 */
public final class ResearchCatalogReloadListener implements PreparableReloadListener {
    private final CompletableFuture<ResearchCatalogSources.RegistrationSnapshot> registrations;
    private final Consumer<ResearchCatalogSources.Loaded> stagePrepared;

    public ResearchCatalogReloadListener(CompletableFuture<ResearchCatalogSources.RegistrationSnapshot> registrations,
                                          Consumer<ResearchCatalogSources.Loaded> stagePrepared) {
        this.registrations = Objects.requireNonNull(registrations);
        this.stagePrepared = Objects.requireNonNull(stagePrepared);
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
        ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler, Executor backgroundExecutor, Executor gameExecutor) {
        return registrations.thenApplyAsync(snapshot -> ResearchCatalogSources.load(snapshot, resources), backgroundExecutor)
            .thenCompose(barrier::wait)
            .thenAcceptAsync(stagePrepared, gameExecutor);
    }
}
