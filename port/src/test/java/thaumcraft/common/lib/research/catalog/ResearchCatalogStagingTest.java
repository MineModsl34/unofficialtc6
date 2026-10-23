package thaumcraft.common.lib.research.catalog;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.capabilities.PlayerWarp;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;
import thaumcraft.common.lib.research.catalog.ResearchCatalogStaging.Status;

class ResearchCatalogStagingTest {
    private LinkedHashMap<String, ResearchCategory> previous;
    private final Object server = new Object();

    @BeforeEach void isolate() {
        previous = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
    }
    @AfterEach void restore() { ResearchCategories.researchCategories = previous; }

    @Test
    void stageDoesNotPublishUntilExactAcceptedResourcesCommit() {
        var store = new ResearchCatalogStaging<Object, Object>();
        Object resources = new Object(); var original = ResearchCategories.researchCategories;
        stage(store, resources, "ONE", 1);
        assertEquals(1, store.pendingCount());
        assertSame(original, ResearchCategories.researchCategories);
        assertNull(store.currentPayload(server));
        assertEquals(Status.COMMITTED, store.accept(resources, server).status());
        assertNotSame(original, ResearchCategories.researchCategories);
        assertNotNull(ResearchCategories.getResearch("ONE"));
        assertEquals(0, store.pendingCount());
        assertEquals(1, store.ownedEntries().size());
        assertEquals(List.of("addon:source"), List.copyOf(store.currentSnapshot(server).spec().sources().keySet()));
    }

    @Test
    void equalityCannotSubstituteAnotherResourcesIdentity() {
        var store = new ResearchCatalogStaging<EqualKey, Object>();
        EqualKey staged = new EqualKey(), other = new EqualKey();
        var loaded = loaded("ONE", 1);
        store.stage(staged, candidate(store, loaded), loaded.payload());
        assertEquals(Status.NOT_STAGED, store.accept(other, server).status());
        assertNull(ResearchCategories.getResearch("ONE"));
        assertEquals(Status.COMMITTED, store.accept(staged, server).status());
    }

    @Test
    void laterListenerFailureLeavesOldCatalogAndPayloadUntouched() {
        var store = new ResearchCatalogStaging<Object, Object>();
        Object accepted = new Object(); stage(store, accepted, "OLD", 1); store.accept(accepted, server);
        var oldMap = ResearchCategories.researchCategories; var oldPayload = store.currentPayload(server);
        Object failedResources = new Object(); stage(store, failedResources, "UNACCEPTED", 2);
        // Simulates the engine refusing a reload after our apply: NO acceptance call for that key.
        assertSame(oldMap, ResearchCategories.researchCategories);
        assertSame(oldPayload, store.currentPayload(server));
        assertNotNull(ResearchCategories.getResearch("OLD"));
        assertNull(ResearchCategories.getResearch("UNACCEPTED"));
        assertEquals(Status.ALREADY_CURRENT, store.accept(accepted, server).status());
        assertEquals(1, store.pendingCount());
    }

    @Test
    void duplicateStageAndMismatchedPayloadAreRejectedWithoutMutation() {
        var store = new ResearchCatalogStaging<Object, Object>();
        Object resources = new Object(); var loaded = loaded("ONE", 1);
        var candidate = candidate(store, loaded);
        assertThrows(IllegalArgumentException.class, () -> store.stage(resources, candidate, loaded("OTHER", 2).payload()));
        assertEquals(0, store.pendingCount());
        store.stage(resources, candidate, loaded.payload());
        assertThrows(IllegalStateException.class, () -> store.stage(resources, candidate, loaded.payload()));
        assertTrue(ResearchCategories.researchCategories.isEmpty());
    }

    @Test
    void payloadSourceOrderMustAlsoMatchCandidateOrder() {
        var store = new ResearchCatalogStaging<Object, Object>();
        var sources = new LinkedHashMap<ResourceLocation, String>();
        sources.put(id("addon:a"), ResearchCatalogSourcesTest.entry("ONE", 1, "name"));
        sources.put(id("addon:b"), ResearchCatalogSourcesTest.entry("TWO", 2, "name"));
        var labels = new LinkedHashMap<String, String>(); sources.forEach((id, raw) -> labels.put(id.toString(), raw));
        var spec = ResearchCatalogParser.parse(labels, Set.of("BASICS"));
        var candidate = ResearchCatalogPublication.prepareWhole(spec, ResearchCategories.researchCategories, Set.of());
        var reversed = new LinkedHashMap<ResourceLocation, String>();
        reversed.put(id("addon:b"), sources.get(id("addon:b"))); reversed.put(id("addon:a"), sources.get(id("addon:a")));
        assertThrows(IllegalArgumentException.class, () -> store.stage(new Object(), candidate, new PacketSyncResearchCatalog(reversed)));
        assertEquals(0, store.pendingCount());
    }

    @Test
    void staleApiAdditionFailsClosedAndDoesNotReprepareAfterVanillaAcceptance() {
        var store = new ResearchCatalogStaging<Object, Object>();
        Object first = new Object(); stage(store, first, "OLD", 1); store.accept(first, server);
        var oldMap = ResearchCategories.researchCategories; var oldPayload = store.currentPayload(server);
        Object second = new Object(); stage(store, second, "NEW", 2);
        ResearchEntry addon = external("LATE_ADDON", 9); ResearchCategories.getResearchCategory("BASICS").research.put(addon.getKey(), addon);
        var result = store.accept(second, server);
        assertEquals(Status.STALE, result.status()); assertNotNull(result.failure());
        assertSame(oldMap, ResearchCategories.researchCategories);
        assertSame(oldPayload, store.currentPayload(server));
        assertSame(addon, ResearchCategories.getResearch("LATE_ADDON"));
        assertNotNull(ResearchCategories.getResearch("OLD")); assertNull(ResearchCategories.getResearch("NEW"));
        assertEquals(Status.NOT_STAGED, store.accept(second, server).status());
    }

    @Test
    void replacedGlobalMapBeforeCommitIsNotOverwritten() {
        var store = new ResearchCatalogStaging<Object, Object>();
        Object resources = new Object(); stage(store, resources, "ONE", 1);
        var replacement = new LinkedHashMap<String, ResearchCategory>();
        ResearchCategories.researchCategories = replacement;
        assertEquals(Status.STALE, store.accept(resources, server).status());
        assertSame(replacement, ResearchCategories.researchCategories); assertNull(store.currentPayload(server));
    }

    @Test
    void otherServerCannotAcceptOrClearCurrentAuthority() {
        var store = new ResearchCatalogStaging<Object, Object>();
        Object first = new Object(); stage(store, first, "OLD", 1); store.accept(first, server);
        Object otherServer = new Object(), second = new Object(); stage(store, second, "NEW", 2);
        assertEquals(Status.OWNER_CONFLICT, store.accept(second, otherServer).status());
        assertNull(store.currentPayload(otherServer)); assertFalse(store.clearOwner(otherServer));
        assertNotNull(ResearchCategories.getResearch("OLD"));
        assertEquals(Status.COMMITTED, store.accept(second, server).status());
        assertNull(ResearchCategories.getResearch("OLD")); assertNotNull(ResearchCategories.getResearch("NEW"));
    }

    @Test
    void repeatedAcceptedIdentityDoesNotReplaceMapOrDropLaterAddon() {
        var store = new ResearchCatalogStaging<Object, Object>();
        Object resources = new Object(); stage(store, resources, "ONE", 1); store.accept(resources, server);
        var map = ResearchCategories.researchCategories;
        ResearchEntry addon = external("ADDON", 8); map.get("BASICS").research.put(addon.getKey(), addon);
        assertEquals(Status.ALREADY_CURRENT, store.accept(resources, server).status());
        assertSame(map, ResearchCategories.researchCategories); assertSame(addon, ResearchCategories.getResearch("ADDON"));
    }

    @Test
    void stopRemovesExactOwnedEntriesButKeepsAddonReplacementAndRebuildsBounds() {
        var store = new ResearchCatalogStaging<Object, Object>();
        Object resources = new Object(); stage(store, resources, "ONE", 1); store.accept(resources, server);
        ResearchEntry replacement = external("ONE", -7), addon = external("EXTERNAL", 8);
        var basics = ResearchCategories.getResearchCategory("BASICS");
        basics.research.put("ONE", replacement); basics.research.put("EXTERNAL", addon);
        // Add a second real owned entry by accepting a new whole candidate while preserving replacements.
        Object next = new Object(); stage(store, next, "TWO", 2); store.accept(next, server);
        assertTrue(store.clearOwner(server));
        assertSame(replacement, ResearchCategories.getResearch("ONE")); assertSame(addon, ResearchCategories.getResearch("EXTERNAL"));
        assertNull(ResearchCategories.getResearch("TWO")); assertNull(store.currentPayload(server)); assertTrue(store.ownedEntries().isEmpty());
        var remaining = ResearchCategories.getResearchCategory("BASICS");
        assertEquals(-7, remaining.minDisplayColumn); assertEquals(8, remaining.maxDisplayColumn);
        assertEquals(0, remaining.minDisplayRow); assertEquals(1, remaining.maxDisplayRow);
    }

    @Test
    void cleanupWithNoRemainingOwnedIdentityDoesNotReplaceAddonMap() {
        var store = new ResearchCatalogStaging<Object, Object>();
        Object resources = new Object(); stage(store, resources, "ONE", 1); store.accept(resources, server);
        var map = ResearchCategories.researchCategories;
        ResearchEntry replacement = external("ONE", 3); map.get("BASICS").research.put("ONE", replacement);
        assertTrue(store.clearOwner(server));
        assertSame(map, ResearchCategories.researchCategories); assertSame(replacement, ResearchCategories.getResearch("ONE"));
    }

    @Test
    void staleOldStopCannotRemoveNewServerCatalog() {
        var store = new ResearchCatalogStaging<Object, Object>();
        Object first = new Object(); stage(store, first, "OLD", 1); store.accept(first, server); store.clearOwner(server);
        Object newerServer = new Object(), second = new Object(); stage(store, second, "NEW", 2); store.accept(second, newerServer);
        var map = ResearchCategories.researchCategories;
        assertFalse(store.clearOwner(server)); assertSame(map, ResearchCategories.researchCategories);
        assertNotNull(ResearchCategories.getResearch("NEW")); assertNotNull(store.currentPayload(newerServer));
    }

    @Test
    void failedReloadAndCleanupDoNotReDeserializeOrGrantPlayerData() {
        var knowledge = new PlayerKnowledge(); var warp = new PlayerWarp();
        knowledge.addResearch("orphan@3"); knowledge.setResearchStage("orphan@3", 17);
        warp.setCounter(42);
        var oldKnowledge = knowledge.serializeNBT().copy(); var oldWarp = warp.serializeNBT().copy();
        var store = new ResearchCatalogStaging<Object, Object>();
        var raw = ResearchCatalogSourcesTest.entry("AUTO", 1, "name").replace("\"stages\":[]", "\"meta\":[\"AUTOUNLOCK\"],\"stages\":[]");
        var payload = new PacketSyncResearchCatalog(Map.of(id("addon:source"), raw));
        var spec = ResearchCatalogParser.parse(Map.of("addon:source", raw), Set.of("BASICS"));
        Object resources = new Object(); store.stage(resources, ResearchCatalogPublication.prepareWhole(spec, ResearchCategories.researchCategories, Set.of()), payload);
        store.accept(resources, server);
        assertFalse(knowledge.isResearchKnown("AUTO"));
        Object rejected = new Object(); stage(store, rejected, "NOT_ACCEPTED", 2);
        store.clearOwner(server);
        assertEquals(oldKnowledge, knowledge.serializeNBT()); assertEquals(oldWarp, warp.serializeNBT());
    }

    @Test
    void weakIdentityKeyQueueReleasesUnacceptedResourcesWithoutOwnerOrKeyInValue() throws ReflectiveOperationException {
        var store = new ResearchCatalogStaging<Object, Object>();
        Object resources = new Object(); stage(store, resources, "ONE", 1);
        var field = ResearchCatalogStaging.class.getDeclaredField("pending"); field.setAccessible(true);
        var pending = (Map<?, ?>) field.get(store);
        Object key = pending.keySet().iterator().next(), value = pending.values().iterator().next();
        assertInstanceOf(WeakReference.class, key);
        // Prepared stores candidate+payload only; neither the resources key nor its server owner.
        assertEquals(List.of("candidate", "payload"), java.util.Arrays.stream(value.getClass().getRecordComponents()).map(c -> c.getName()).toList());
        WeakReference<?> reference = (WeakReference<?>) key;
        assertSame(resources, reference.get()); reference.clear(); reference.enqueue();
        assertEquals(0, store.pendingCount()); assertNull(store.currentPayload(server));
    }

    private static final class EqualKey {
        @Override public boolean equals(Object other) { return other instanceof EqualKey; }
        @Override public int hashCode() { return 1; }
    }
    private static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
    private static ResearchEntry external(String key, int x) {
        var entry = new ResearchEntry(); entry.setKey(key); entry.setCategory("BASICS"); entry.setDisplayColumn(x); entry.setDisplayRow(1); return entry;
    }
    private static ResearchCatalogSources.Loaded loaded(String key, int x) {
        return ResearchCatalogSources.load(ResearchCatalogSourcesTest.snapshot("addon:source"),
            physical -> ResearchCatalogSourcesTest.stream(ResearchCatalogSourcesTest.entry(key, x, "name")));
    }
    private static <K> ResearchCatalogPublication.Candidate candidate(ResearchCatalogStaging<K, Object> store, ResearchCatalogSources.Loaded loaded) {
        return ResearchCatalogPublication.prepareWhole(loaded.spec(), ResearchCategories.researchCategories, store.ownedEntries());
    }
    private static void stage(ResearchCatalogStaging<Object, Object> store, Object resources, String key, int x) {
        var loaded = loaded(key, x); store.stage(resources, candidate(store, loaded), loaded.payload());
    }
}
