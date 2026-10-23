package thaumcraft.common.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Isolated stores never register additions in the mod-global policy. */
class WorldgenBlacklistsTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("example:dimension");
    private static final ResourceLocation BIOME = ResourceLocation.parse("example:biome");

    @Test
    void configurationOverridesPersistedAdditionsAndRemovingItRevealsLatestAddition() {
        var store = new WorldgenBlacklists.Store();
        store.registerDimension(DIMENSION, 0);
        store.registerBiome(BIOME, 2);
        store.setConfig(WorldgenPolicy.fromEntries(
            List.of("example:dimension=-1"), List.of("example:biome=1")));
        assertEquals(-1, store.policy().dimensionMode(DIMENSION));
        assertEquals(1, store.policy().biomeMode(BIOME));

        store.registerDimension(DIMENSION, 7);
        store.registerBiome(BIOME, 0);
        assertEquals(-1, store.policy().dimensionMode(DIMENSION), "explicit mode -1 is still an override");
        assertEquals(1, store.policy().biomeMode(BIOME));

        store.setConfig(WorldgenPolicy.EMPTY);
        assertEquals(7, store.policy().dimensionMode(DIMENSION));
        assertEquals(0, store.policy().biomeMode(BIOME));
    }

    @Test
    void lastAdditionWinsWithoutMutatingAlreadyPublishedSnapshots() {
        var store = new WorldgenBlacklists.Store();
        WorldgenPolicy empty = store.policy();
        WorldgenPolicy first = store.registerDimension(DIMENSION, 0);
        WorldgenPolicy second = store.registerDimension(DIMENSION, 2);
        WorldgenPolicy third = store.registerBiome(BIOME, 7);
        assertSame(third, store.policy());
        assertEquals(-1, empty.dimensionMode(DIMENSION));
        assertEquals(0, first.dimensionMode(DIMENSION));
        assertEquals(-1, first.biomeMode(BIOME));
        assertEquals(2, second.dimensionMode(DIMENSION));
        assertEquals(-1, second.biomeMode(BIOME));
        assertEquals(2, third.dimensionMode(DIMENSION));
        assertEquals(7, third.biomeMode(BIOME));
    }

    @Test
    void invalidApiArgumentsLeaveThePublishedPolicyUnchanged() {
        var store = new WorldgenBlacklists.Store();
        WorldgenPolicy before = store.registerDimension(DIMENSION, 0);
        assertThrows(NullPointerException.class, () -> store.setConfig(null));
        assertThrows(NullPointerException.class, () -> store.registerDimension(null, 1));
        assertThrows(NullPointerException.class, () -> store.registerBiome(null, 2));
        assertSame(before, store.policy());
        assertEquals(0, store.policy().dimensionMode(DIMENSION));
        assertEquals(-1, store.policy().biomeMode(BIOME));
    }

    @Test
    void concurrentAddonUpdatesDoNotLoseEitherMapAndConfigurationKeepsPriority() throws Exception {
        var store = new WorldgenBlacklists.Store();
        store.setConfig(WorldgenPolicy.EMPTY.withDimension(DIMENSION, -1).withBiome(BIOME, -1));
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(4);
        List<Future<?>> updates = new ArrayList<>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                int workerId = worker;
                updates.add(executor.submit(() -> {
                    start.await();
                    for (int index = 0; index < 20; index++) {
                        ResourceLocation key = ResourceLocation.parse("example:worker_" + workerId + "/" + index);
                        store.registerDimension(key, workerId);
                        store.registerBiome(key, index);
                        store.registerDimension(DIMENSION, workerId);
                        store.registerBiome(BIOME, index);
                        WorldgenPolicy snapshot = store.policy();
                        assertEquals(-1, snapshot.dimensionMode(DIMENSION));
                        assertEquals(-1, snapshot.biomeMode(BIOME));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> update : updates) {
                update.get(10, TimeUnit.SECONDS);
            }
            WorldgenPolicy complete = store.policy();
            for (int worker = 0; worker < 4; worker++) {
                for (int index = 0; index < 20; index++) {
                    ResourceLocation key = ResourceLocation.parse("example:worker_" + worker + "/" + index);
                    assertEquals(worker, complete.dimensionMode(key));
                    assertEquals(index, complete.biomeMode(key));
                }
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
