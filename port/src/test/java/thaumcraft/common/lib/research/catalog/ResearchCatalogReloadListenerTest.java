package thaumcraft.common.lib.research.catalog;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;

class ResearchCatalogReloadListenerTest {
    @Test
    void snapshotThenBackgroundIoThenBarrierThenGameApplyAreStrictlyOrdered() {
        var snapshot = new CompletableFuture<ResearchCatalogSources.RegistrationSnapshot>();
        var background = new QueueExecutor(); var game = new QueueExecutor(); var barrier = new Barrier();
        var stages = new java.util.ArrayList<ResearchCatalogSources.Loaded>(); AtomicInteger opened = new AtomicInteger();
        var resources = ResearchCatalogSourcesTest.manager(physical -> {
            opened.incrementAndGet(); return Optional.of(new Resource(null,
                () -> ResearchCatalogSourcesTest.stream(ResearchCatalogSourcesTest.entry("ONE", 1, "name"))));
        });
        var listener = new ResearchCatalogReloadListener(snapshot, stages::add);
        var done = listener.reload(barrier, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, background, game);
        assertFalse(done.isDone()); assertEquals(0, opened.get()); assertTrue(background.queue.isEmpty());
        snapshot.complete(ResearchCatalogSourcesTest.snapshot("addon:source"));
        assertEquals(1, background.queue.size()); assertTrue(game.queue.isEmpty());
        background.runNext();
        assertEquals(1, opened.get()); assertNotNull(barrier.prepared); assertTrue(game.queue.isEmpty()); assertTrue(stages.isEmpty());
        barrier.release.complete(null); assertEquals(1, game.queue.size()); assertTrue(stages.isEmpty());
        game.runNext();
        assertTrue(done.isDone()); assertFalse(done.isCompletedExceptionally()); assertEquals(1, stages.size());
        assertEquals("ONE", stages.getFirst().spec().entries().getFirst().key());
    }

    @Test
    void failedRegistrationSnapshotNeverOpensSourcesOrApplies() {
        var background = new QueueExecutor(); var game = new QueueExecutor(); var barrier = new Barrier();
        AtomicInteger opened = new AtomicInteger(), applied = new AtomicInteger();
        var resources = ResearchCatalogSourcesTest.manager(physical -> { opened.incrementAndGet(); return Optional.empty(); });
        var listener = new ResearchCatalogReloadListener(CompletableFuture.failedFuture(new IllegalStateException("setup failure")),
            loaded -> applied.incrementAndGet());
        var done = listener.reload(barrier, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, background, game);
        assertThrows(CompletionException.class, done::join);
        assertEquals(0, opened.get()); assertEquals(0, applied.get()); assertTrue(background.queue.isEmpty()); assertTrue(game.queue.isEmpty());
        assertNull(barrier.prepared);
    }

    @Test
    void ioOrParserFailureNeverReachesApplyBarrier() {
        var background = new QueueExecutor(); var game = new QueueExecutor(); var barrier = new Barrier();
        AtomicInteger applied = new AtomicInteger();
        var resources = ResearchCatalogSourcesTest.manager(physical -> Optional.of(new Resource(null,
            () -> ResearchCatalogSourcesTest.stream("{broken JSON"))));
        var listener = new ResearchCatalogReloadListener(CompletableFuture.completedFuture(ResearchCatalogSourcesTest.snapshot("addon:source")),
            loaded -> applied.incrementAndGet());
        var done = listener.reload(barrier, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, background, game);
        background.runNext();
        assertThrows(CompletionException.class, done::join); assertNull(barrier.prepared);
        assertEquals(0, applied.get()); assertTrue(game.queue.isEmpty());
    }

    @Test
    void metadataStageFailurePropagatesToGlobalReloadRatherThanBeingSwallowed() {
        var background = new QueueExecutor(); var game = new QueueExecutor(); var barrier = new Barrier();
        var resources = ResearchCatalogSourcesTest.manager(physical -> Optional.of(new Resource(null,
            () -> ResearchCatalogSourcesTest.stream(ResearchCatalogSourcesTest.entry("ONE", 1, "name")))));
        var expected = new IllegalArgumentException("metadata collision");
        var listener = new ResearchCatalogReloadListener(CompletableFuture.completedFuture(ResearchCatalogSourcesTest.snapshot("addon:source")),
            loaded -> { throw expected; });
        var done = listener.reload(barrier, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, background, game);
        background.runNext(); barrier.release.complete(null); game.runNext();
        assertSame(expected, assertThrows(CompletionException.class, done::join).getCause());
    }

    @Test
    void failedEarlierListenerBarrierPreventsStagingEvenWithValidPreparedData() {
        var background = new QueueExecutor(); var game = new QueueExecutor(); var barrier = new Barrier();
        AtomicInteger applied = new AtomicInteger();
        var resources = ResearchCatalogSourcesTest.manager(physical -> Optional.of(new Resource(null,
            () -> ResearchCatalogSourcesTest.stream(ResearchCatalogSourcesTest.entry("ONE", 1, "name")))));
        var listener = new ResearchCatalogReloadListener(CompletableFuture.completedFuture(ResearchCatalogSourcesTest.snapshot("addon:source")),
            loaded -> applied.incrementAndGet());
        var done = listener.reload(barrier, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, background, game);
        background.runNext(); barrier.release.completeExceptionally(new IllegalStateException("earlier listener failed"));
        assertThrows(CompletionException.class, done::join); assertEquals(0, applied.get()); assertTrue(game.queue.isEmpty());
    }

    private static final class QueueExecutor implements Executor {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        @Override public void execute(Runnable command) { queue.add(command); }
        void runNext() { assertFalse(queue.isEmpty()); queue.remove().run(); }
    }
    private static final class Barrier implements PreparableReloadListener.PreparationBarrier {
        final CompletableFuture<Void> release = new CompletableFuture<>();
        Object prepared;
        @Override public <T> CompletableFuture<T> wait(T prepared) {
            this.prepared = prepared; return release.thenApply(ignored -> prepared);
        }
    }
}
