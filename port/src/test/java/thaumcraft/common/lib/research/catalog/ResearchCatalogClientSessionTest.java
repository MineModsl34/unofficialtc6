package thaumcraft.common.lib.research.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;

/** Pure metadata/session checks, not real socket/lifecycle/UI acceptance or progression. */
class ResearchCatalogClientSessionTest {
    private LinkedHashMap<String, ResearchCategory> previous;
    private ResearchCatalogClientSession session;

    @BeforeEach
    void isolate() {
        previous = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
        session = new ResearchCatalogClientSession();
    }

    @AfterEach
    void restore() { ResearchCategories.researchCategories = previous; }

    @Test
    void constructionBeginAndEndWithoutPayloadDoNotPublishAnyGlobalMetadata() {
        Object connection = new Object();
        var original = ResearchCategories.researchCategories;
        assertFalse(session.active());
        assertFalse(session.isActive(connection));
        assertFalse(session.isActive(null));
        assertNull(session.activeDigest());
        assertTrue(session.ownedEntries().isEmpty());
        assertThrows(NullPointerException.class, () -> session.begin(null, false));
        assertTrue(session.begin(connection, false));
        assertTrue(session.active());
        assertTrue(session.isActive(connection));
        assertFalse(session.borrowed());
        assertNull(session.activeSpec());
        assertSame(original, ResearchCategories.researchCategories);
        assertTrue(session.end(connection));
        assertFalse(session.active());
        assertFalse(session.isActive(connection));
        assertSame(original, ResearchCategories.researchCategories);
    }

    @Test
    void duplicateSameIdentityBeginIsIdempotentAndRoleChangeFailsWithoutReset() {
        Object connection = new Object();
        var packet = packet(entry("ONE", "BASICS", 1, 2));
        session.begin(connection, false);
        session.apply(connection, false, packet, null);
        var global = ResearchCategories.researchCategories;
        ResearchEntry owned = ResearchCategories.getResearch("ONE");
        assertFalse(session.begin(connection, false));
        assertThrows(IllegalStateException.class, () -> session.begin(connection, true));
        assertThrows(IllegalStateException.class, () -> session.apply(connection, true, packet, packet));
        assertSame(global, ResearchCategories.researchCategories);
        assertSame(owned, ResearchCategories.getResearch("ONE"));
        assertEquals(packet.digest(), session.activeDigest());
    }

    @Test
    void staleConnectionsUseExactIdentityNotEqualsAndNeverParseOrClearNewSession() {
        Object oldConnection = new EqualConnection();
        Object newConnection = new EqualConnection();
        assertEquals(oldConnection, newConnection);
        session.begin(oldConnection, false);
        session.apply(oldConnection, false, packet(entry("OLD", "BASICS", 1, 1)), null);
        session.begin(newConnection, false);
        var next = packet(entry("NEW", "BASICS", 2, 2));
        session.apply(newConnection, false, next, null);
        var global = ResearchCategories.researchCategories;
        assertFalse(session.apply(oldConnection, true, null, null));
        assertFalse(session.end(oldConnection));
        assertFalse(session.end(null));
        assertFalse(session.isActive(oldConnection));
        assertTrue(session.isActive(newConnection));
        assertSame(global, ResearchCategories.researchCategories);
        assertNull(ResearchCategories.getResearch("OLD"));
        assertTrue(session.ownedEntries().contains(ResearchCategories.getResearch("NEW")));
        assertEquals(next.digest(), session.activeDigest());
    }

    @Test
    void remoteApplyPublishesWholeOrderedRawSnapshotWithOpaqueKeysAndNoItemResolution() {
        Object connection = new Object();
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        JsonObject first = entry("!MiXeD@2", "BASICS", -3, 4);
        JsonArray requiredItems = new JsonArray();
        requiredItems.add("addon:missing_gear;2;3");
        first.getAsJsonArray("stages").get(0).getAsJsonObject().add("required_item", requiredItems);
        String firstRaw = " \r\n" + document(first) + "\r\n";
        String secondRaw = document(entry("SECOND", "ALCHEMY", 2, -4));
        sources.put(id("addon:custom/foo.json"), firstRaw);
        sources.put(id("thaumcraft:research/bar"), secondRaw);
        PacketSyncResearchCatalog packet = new PacketSyncResearchCatalog(sources);
        session.begin(connection, false);
        var original = ResearchCategories.researchCategories;
        assertTrue(session.apply(connection, false, packet, null));
        assertNotSame(original, ResearchCategories.researchCategories);
        assertTrue(original.isEmpty());
        assertEquals(packet.digest(), session.activeDigest());
        assertEquals(List.of("addon:custom/foo.json", "thaumcraft:research/bar"), List.copyOf(session.activeSpec().sources().keySet()));
        assertEquals(firstRaw, session.activeSpec().sources().get("addon:custom/foo.json"));
        assertEquals("!MiXeD@2", ResearchCategories.getResearch("!MiXeD@2").getKey());
        assertEquals(2, session.ownedEntries().size());
        assertThrows(UnsupportedOperationException.class, () -> session.ownedEntries().clear());
        var stage = assertInstanceOf(PreparedResearchMetadata.Stage.class,
            ResearchCategories.getResearch("!MiXeD@2").getStages()[0]);
        assertEquals(1, stage.getObtain().length);
        var token = assertInstanceOf(ResearchCatalogSpec.ItemToken.class, stage.getObtain()[0]);
        assertSame(session.activeSpec().entries().getFirst().stages().values().getFirst().requiredItems().values().getFirst(), token);
        assertEquals("addon:missing_gear;2;3", token.raw());
        assertEquals("addon:missing_gear", token.name());
        assertEquals(2, token.count());
        assertEquals(3, token.damage());
        assertTrue(stage.hasUnresolved());
        assertEquals(1, stage.deferredDependencies().size());
        var dependency = stage.deferredDependencies().getFirst();
        assertEquals(PreparedResearchMetadata.DependencyKind.ITEM, dependency.kind());
        assertSame(token, dependency.token());
        assertEquals(token.unresolvedReason(), dependency.reason());
    }

    @Test
    void repeatedReloadReplacesExactlyPriorOwnedIdentitiesAndPreservesAddedAddonEntries() {
        Object connection = new Object();
        session.begin(connection, false);
        var original = packet(entry("RELOADED", "BASICS", 4, 5));
        session.apply(connection, false, original, null);
        ResearchEntry first = ResearchCategories.getResearch("RELOADED");
        ResearchEntry addon = external("ADDON", "BASICS", -7, -8);
        ResearchCategories.researchCategories.get("BASICS").research.put("ADDON", addon);
        assertTrue(session.apply(connection, false, original, null));
        ResearchEntry second = ResearchCategories.getResearch("RELOADED");
        assertNotSame(first, second);
        assertSame(addon, ResearchCategories.getResearch("ADDON"));
        assertFalse(session.ownedEntries().contains(first));
        assertTrue(session.ownedEntries().contains(second));
        var changed = packet(entry("RELOADED", "BASICS", 2, 1), entry("NEW", "BASICS", 3, 1));
        assertTrue(session.apply(connection, false, changed, null));
        assertEquals(2, session.ownedEntries().size());
        assertSame(addon, ResearchCategories.getResearch("ADDON"));
        assertBounds(ResearchCategories.researchCategories.get("BASICS"), -7, -8, 3, 1);
        assertTrue(session.end(connection));
        assertNull(ResearchCategories.getResearch("RELOADED"));
        assertNull(ResearchCategories.getResearch("NEW"));
        assertSame(addon, ResearchCategories.getResearch("ADDON"));
        assertBounds(ResearchCategories.researchCategories.get("BASICS"), -7, -8, 0, 0);
    }

    @Test
    void endPreservesAddonReplacementByIdentityAndRebuildsBoundsFromRemainingEntries() {
        Object connection = new Object();
        session.begin(connection, false);
        session.apply(connection, false, packet(entry("REPLACED", "BASICS", 20, 30), entry("REMOVED", "BASICS", -40, -50)), null);
        ResearchEntry old = ResearchCategories.getResearch("REPLACED");
        ResearchEntry replacement = external("REPLACED", "BASICS", 2, 3);
        ResearchEntry addition = external("ADDED", "BASICS", -4, -5);
        ResearchCategory basics = ResearchCategories.researchCategories.get("BASICS");
        basics.research.put("REPLACED", replacement);
        basics.research.put("ADDED", addition);
        assertTrue(session.ownedEntries().contains(old));
        assertFalse(session.ownedEntries().contains(replacement));
        assertTrue(session.end(connection));
        assertSame(replacement, ResearchCategories.getResearch("REPLACED"));
        assertSame(addition, ResearchCategories.getResearch("ADDED"));
        assertNull(ResearchCategories.getResearch("REMOVED"));
        assertBounds(ResearchCategories.researchCategories.get("BASICS"), -4, -5, 2, 3);
        assertNull(session.activeDigest());
        assertTrue(session.ownedEntries().isEmpty());
    }

    @Test
    void currentAddonCategoryDefinitionsAndEntriesSurviveRemotePublicationAndCleanup() {
        ResearchCategory addon = new ResearchCategory("Addon Mixed", "!Gate@2", new AspectList(), null, null);
        ResearchEntry external = external("API_EXISTING", "Addon Mixed", -8, -9);
        addon.research.put(external.getKey(), external);
        ResearchCategories.researchCategories.put("Addon Mixed", addon);
        Object connection = new Object();
        session.begin(connection, false);
        var packet = packet(entry("OPAQUE", "Addon Mixed", 3, 4));
        assertTrue(session.apply(connection, false, packet, null));
        assertSame(external, ResearchCategories.getResearch("API_EXISTING"));
        assertEquals("!Gate@2", ResearchCategories.researchCategories.get("Addon Mixed").researchKey);
        assertTrue(session.end(connection));
        assertSame(external, ResearchCategories.getResearch("API_EXISTING"));
        assertNull(ResearchCategories.getResearch("OPAQUE"));
        assertBounds(ResearchCategories.researchCategories.get("Addon Mixed"), -8, -9, 0, 0);
    }

    @Test
    void malformedUnknownCategoryAndWholeCrossSourceCollisionPreserveOldMapDigestAndOwnership() {
        Object connection = new Object();
        var good = packet(entry("KEEP", "BASICS", 1, 1));
        session.begin(connection, false);
        session.apply(connection, false, good, null);
        var original = ResearchCategories.researchCategories;
        ResearchEntry keep = ResearchCategories.getResearch("KEEP");
        PacketSyncResearchCatalog malformed = new PacketSyncResearchCatalog(Map.of(id("addon:broken"), "{\"entries\":TRUE}"));
        var unknown = packet(entry("UNKNOWN", "Unregistered Category", 4, 4));
        Map<ResourceLocation, String> crossSources = new LinkedHashMap<>();
        crossSources.put(id("addon:first"), document(entry("NEW", "BASICS", 2, 2)));
        crossSources.put(id("addon:last"), document(entry("NEW", "BASICS", 3, 3)));
        for (PacketSyncResearchCatalog invalid : List.of(malformed, unknown, new PacketSyncResearchCatalog(crossSources))) {
            assertThrows(ResearchCatalogParser.CatalogException.class, () -> session.apply(connection, false, invalid, null));
            assertSame(original, ResearchCategories.researchCategories);
            assertSame(keep, ResearchCategories.getResearch("KEEP"));
            assertNull(ResearchCategories.getResearch("NEW"));
            assertEquals(good.digest(), session.activeDigest());
            assertEquals(Set.of(keep), session.ownedEntries());
        }
    }

    @Test
    void addonKeyOrCoordinateCollisionPreservesOldSnapshotUntilActualEnd() {
        Object connection = new Object();
        var good = packet(entry("KEEP", "BASICS", 1, 1));
        session.begin(connection, false);
        session.apply(connection, false, good, null);
        ResearchEntry addon = external("COLLISION", "BASICS", 2, 2);
        ResearchCategories.researchCategories.get("BASICS").research.put(addon.getKey(), addon);
        var global = ResearchCategories.researchCategories;
        for (var invalid : List.of(packet(entry("COLLISION", "BASICS", 3, 3)), packet(entry("OTHER", "BASICS", 2, 2)))) {
            assertThrows(ResearchCatalogPublication.PreparationException.class, () -> session.apply(connection, false, invalid, null));
            assertSame(global, ResearchCategories.researchCategories);
            assertEquals(good.digest(), session.activeDigest());
            assertSame(addon, ResearchCategories.getResearch("COLLISION"));
            assertTrue(session.ownedEntries().contains(ResearchCategories.getResearch("KEEP")));
        }
        session.end(connection);
        assertNull(ResearchCategories.getResearch("KEEP"));
        assertSame(addon, ResearchCategories.getResearch("COLLISION"));
    }

    @Test
    void replacementOfOwnedEntryIsNotRemovedToMakeConflictingReloadSucceed() {
        Object connection = new Object();
        var packet = packet(entry("REPLACED", "BASICS", 1, 1));
        session.begin(connection, false);
        session.apply(connection, false, packet, null);
        ResearchEntry replacement = external("REPLACED", "BASICS", 5, 5);
        ResearchCategories.researchCategories.get("BASICS").research.put("REPLACED", replacement);
        var global = ResearchCategories.researchCategories;
        assertThrows(ResearchCatalogPublication.PreparationException.class, () -> session.apply(connection, false, packet, null));
        assertSame(global, ResearchCategories.researchCategories);
        assertSame(replacement, ResearchCategories.getResearch("REPLACED"));
        assertEquals(packet.digest(), session.activeDigest());
        session.end(connection);
        assertSame(replacement, ResearchCategories.getResearch("REPLACED"));
    }

    @Test
    void beginningNewRemoteSessionCleansOnlyPreviousOwnedEntriesBeforeNewPacket() {
        Object oldConnection = new Object();
        Object newConnection = new Object();
        session.begin(oldConnection, false);
        session.apply(oldConnection, false, packet(entry("OLD", "BASICS", 10, 20)), null);
        ResearchEntry addon = external("ADDON", "BASICS", -2, -3);
        ResearchCategories.researchCategories.get("BASICS").research.put("ADDON", addon);
        assertTrue(session.begin(newConnection, false));
        assertNull(ResearchCategories.getResearch("OLD"));
        assertSame(addon, ResearchCategories.getResearch("ADDON"));
        assertBounds(ResearchCategories.researchCategories.get("BASICS"), -2, -3, 0, 0);
        assertNull(session.activeDigest());
        assertTrue(session.ownedEntries().isEmpty());
        assertFalse(session.end(oldConnection));
    }

    @Test
    void integratedMemoryApplyBorrowsActualSharedCatalogWithoutPublicationOrCleanup() {
        var packet = packet(entry("SERVER", "BASICS", 1, 1));
        ResearchEntry serverEntry = publishServer(packet);
        var global = ResearchCategories.researchCategories;
        Object connection = new Object();
        session.begin(connection, true);
        assertTrue(session.borrowed());
        assertTrue(session.apply(connection, true, packet, packet));
        assertSame(global, ResearchCategories.researchCategories);
        assertSame(serverEntry, ResearchCategories.getResearch("SERVER"));
        assertTrue(session.ownedEntries().isEmpty());
        assertEquals(packet.digest(), session.activeDigest());
        assertTrue(session.apply(connection, true, packet, packet));
        assertSame(global, ResearchCategories.researchCategories);
        assertTrue(session.end(connection));
        assertSame(global, ResearchCategories.researchCategories);
        assertSame(serverEntry, ResearchCategories.getResearch("SERVER"));
        assertFalse(session.borrowed());
    }

    @Test
    void memoryNeedsExpectedAuthorityAndInvalidRawCannotPublishEvenWhenDigestMatches() {
        Object connection = new Object();
        session.begin(connection, true);
        var good = packet(entry("SERVER", "BASICS", 1, 1));
        publishServer(good);
        var global = ResearchCategories.researchCategories;
        assertThrows(NullPointerException.class, () -> session.apply(connection, true, good, null));
        assertNull(session.activeDigest());
        PacketSyncResearchCatalog malformed = new PacketSyncResearchCatalog(Map.of(id("addon:bad"), "{\"entries\":false}"));
        assertThrows(ResearchCatalogParser.CatalogException.class, () -> session.apply(connection, true, malformed, malformed));
        assertNull(session.activeDigest());
        assertSame(global, ResearchCategories.researchCategories);
        assertTrue(session.ownedEntries().isEmpty());
    }

    @Test
    void validEarlierMemoryEchoCannotRegressSessionDigestAfterServerAlreadyReloaded() {
        Object connection = new Object();
        session.begin(connection, true);
        var earlier = packet(entry("SERVER", "BASICS", 1, 1));
        publishServer(earlier);
        session.apply(connection, true, earlier, earlier);
        var newer = packet(entry("NEWER", "BASICS", 2, 2));
        // Server cleanup/reload is an independent owner operation; no client cleanup is involved.
        ResearchCategories.researchCategories = new LinkedHashMap<>();
        ResearchEntry newestServerEntry = publishServer(newer);
        var newestGlobal = ResearchCategories.researchCategories;
        assertFalse(session.apply(connection, true, earlier, newer));
        assertEquals(earlier.digest(), session.activeDigest());
        assertSame(newestGlobal, ResearchCategories.researchCategories);
        assertSame(newestServerEntry, ResearchCategories.getResearch("NEWER"));
        assertTrue(session.apply(connection, true, newer, newer));
        assertEquals(newer.digest(), session.activeDigest());
        assertSame(newestGlobal, ResearchCategories.researchCategories);
        session.end(connection);
        assertSame(newestGlobal, ResearchCategories.researchCategories);
    }

    @Test
    void beginningAfterBorrowedSessionNeverCleansServerOwnedEntries() {
        var packet = packet(entry("SERVER", "BASICS", 1, 1));
        ResearchEntry serverEntry = publishServer(packet);
        var global = ResearchCategories.researchCategories;
        Object memory = new Object();
        Object remote = new Object();
        session.begin(memory, true);
        session.apply(memory, true, packet, packet);
        assertTrue(session.begin(remote, false));
        assertSame(global, ResearchCategories.researchCategories);
        assertSame(serverEntry, ResearchCategories.getResearch("SERVER"));
        assertFalse(session.end(memory));
        session.end(remote);
        assertSame(global, ResearchCategories.researchCategories);
    }

    @Test
    void retainedEarlierSpecOwnershipAndApiMapStayUnchangedAfterReloadAndEnd() {
        Object connection = new Object();
        session.begin(connection, false);
        var first = packet(entry("OLD", "BASICS", 3, 4));
        session.apply(connection, false, first, null);
        var earlierSpec = session.activeSpec();
        var earlierOwned = session.ownedEntries();
        var earlierMap = ResearchCategories.researchCategories;
        ResearchEntry earlierEntry = ResearchCategories.getResearch("OLD");
        session.apply(connection, false, packet(entry("NEW", "BASICS", 5, 6)), null);
        assertEquals("OLD", earlierSpec.entries().getFirst().key());
        assertEquals(first.sources().values().iterator().next(), earlierSpec.sources().values().iterator().next());
        assertEquals(Set.of(earlierEntry), earlierOwned);
        assertSame(earlierEntry, earlierMap.get("BASICS").research.get("OLD"));
        assertFalse(earlierMap.get("BASICS").research.containsKey("NEW"));
        assertNull(ResearchCategories.getResearch("OLD"));
        session.end(connection);
        assertSame(earlierEntry, earlierMap.get("BASICS").research.get("OLD"));
        assertEquals("OLD", earlierSpec.entries().getFirst().key());
        assertEquals(Set.of(earlierEntry), earlierOwned);
    }

    @Test
    void failedCleanupCannotClearOwnershipOrAuthorizeNewConnectionBeforeRetry() {
        Object oldConnection = new Object();
        Object newConnection = new Object();
        var packet = packet(entry("OWNED", "BASICS", 1, 1));
        session.begin(oldConnection, false);
        session.apply(oldConnection, false, packet, null);
        var global = ResearchCategories.researchCategories;
        ResearchEntry owned = ResearchCategories.getResearch("OWNED");
        global.put("BROKEN_EXTERNAL_API", null);
        assertThrows(ResearchCatalogPublication.PreparationException.class, () -> session.end(oldConnection));
        assertThrows(ResearchCatalogPublication.PreparationException.class, () -> session.begin(newConnection, false));
        assertTrue(session.isActive(oldConnection));
        assertFalse(session.isActive(newConnection));
        assertEquals(packet.digest(), session.activeDigest());
        assertSame(global, ResearchCategories.researchCategories);
        assertSame(owned, ResearchCategories.getResearch("OWNED"));
        assertEquals(Set.of(owned), session.ownedEntries());
        global.remove("BROKEN_EXTERNAL_API");
        assertTrue(session.end(oldConnection));
        assertNull(ResearchCategories.getResearch("OWNED"));
        assertFalse(session.active());
    }

    @Test
    void acceptedViewRequiresSuccessfulApplyAndExactCurrentConnectionIdentity() {
        Object oldConnection = new EqualConnection();
        Object newConnection = new EqualConnection();
        assertEquals(oldConnection, newConnection);
        assertNull(session.acceptedView(oldConnection));
        assertNull(session.acceptedView(null));
        session.begin(oldConnection, false);
        assertNull(session.acceptedView(oldConnection));
        var first = packet(entry("OLD_VIEW", "BASICS", 1, 1));
        assertTrue(session.apply(oldConnection, false, first, null));
        var oldView = session.acceptedView(oldConnection);
        assertSame(oldConnection, oldView.connection());
        assertFalse(oldView.memory());
        assertEquals(first.digest(), oldView.digest());
        assertSame(session.activeSpec(), oldView.spec());
        var originalMap = ResearchCategories.researchCategories;
        assertSame(oldView, session.acceptedView(oldConnection));
        assertNull(session.acceptedView(newConnection));
        assertSame(originalMap, ResearchCategories.researchCategories);
        assertFalse(session.begin(oldConnection, false));
        assertSame(oldView, session.acceptedView(oldConnection));

        assertTrue(session.begin(newConnection, false));
        assertNull(session.acceptedView(oldConnection));
        assertNull(session.acceptedView(newConnection));
        var next = packet(entry("NEW_VIEW", "BASICS", 2, 2));
        assertTrue(session.apply(newConnection, false, next, null));
        var newView = session.acceptedView(newConnection);
        assertNotSame(oldView, newView);
        assertSame(newConnection, newView.connection());
        assertEquals(next.digest(), newView.digest());
        assertFalse(session.end(oldConnection));
        assertSame(newView, session.acceptedView(newConnection));
        assertTrue(session.end(newConnection));
        assertNull(session.acceptedView(newConnection));
        assertNull(session.acceptedView(oldConnection));
        assertEquals("OLD_VIEW", oldView.spec().entries().getFirst().key());
        assertEquals("NEW_VIEW", newView.spec().entries().getFirst().key());
    }

    @Test
    void equalRemotePayloadStillCreatesNewOpaqueAcceptedViewGenerationOnlyOnApply() {
        Object connection = new Object();
        var packet = packet(entry("SAME", "BASICS", 1, 1));
        session.begin(connection, false);
        assertTrue(session.apply(connection, false, packet, null));
        var earlier = session.acceptedView(connection);
        assertSame(earlier, session.acceptedView(connection));
        assertFalse(session.begin(connection, false));
        assertSame(earlier, session.acceptedView(connection));
        assertTrue(session.apply(connection, false, packet, null));
        var newer = session.acceptedView(connection);
        assertNotSame(earlier, newer);
        assertSame(connection, newer.connection());
        assertEquals(earlier.digest(), newer.digest());
        assertEquals(earlier.spec().sources(), newer.spec().sources());
        assertSame(newer, session.acceptedView(connection));
    }

    @Test
    void failedRemoteApplyOrCleanupRetainsTheSameAcceptedViewGeneration() {
        Object connection = new Object();
        session.begin(connection, false);
        var packet = packet(entry("KEEP_VIEW", "BASICS", 1, 1));
        assertTrue(session.apply(connection, false, packet, null));
        var view = session.acceptedView(connection);
        var map = ResearchCategories.researchCategories;
        var malformed = new PacketSyncResearchCatalog(Map.of(id("addon:bad_view"), "{\"entries\":false}"));
        assertThrows(ResearchCatalogParser.CatalogException.class,
            () -> session.apply(connection, false, malformed, null));
        assertSame(view, session.acceptedView(connection));
        assertSame(map, ResearchCategories.researchCategories);
        assertThrows(IllegalStateException.class, () -> session.apply(connection, true, packet, packet));
        assertSame(view, session.acceptedView(connection));
        map.put("BROKEN_VIEW_EXTERNAL", null);
        assertThrows(ResearchCatalogPublication.PreparationException.class, () -> session.end(connection));
        assertThrows(ResearchCatalogPublication.PreparationException.class, () -> session.begin(new Object(), false));
        assertSame(view, session.acceptedView(connection));
        map.remove("BROKEN_VIEW_EXTERNAL");
        assertTrue(session.end(connection));
        assertNull(session.acceptedView(connection));
    }

    @Test
    void memoryAcceptedViewNeverReadsNewSharedServerRevisionAndWaitsForMatchingEcho() {
        Object connection = new Object();
        var earlier = packet(entry("BORROWED_OLD", "BASICS", 1, 1));
        var newer = packet(entry("BORROWED_NEW", "BASICS", 2, 2));
        session.begin(connection, true);
        assertNull(session.acceptedView(connection));
        publishServer(newer);
        var initialShared = ResearchCategories.researchCategories;
        assertFalse(session.apply(connection, true, earlier, newer));
        assertNull(session.acceptedView(connection));
        assertSame(initialShared, ResearchCategories.researchCategories);
        assertTrue(session.apply(connection, true, newer, newer));
        var oldView = session.acceptedView(connection);
        assertTrue(oldView.memory());
        assertSame(connection, oldView.connection());
        assertEquals(newer.digest(), oldView.digest());

        var latest = packet(entry("BORROWED_LATEST", "BASICS", 3, 3));
        ResearchCategories.researchCategories = new LinkedHashMap<>();
        var actualServerEntry = publishServer(latest);
        var shared = ResearchCategories.researchCategories;
        assertSame(oldView, session.acceptedView(connection));
        assertEquals(newer.digest(), session.acceptedView(connection).digest());
        assertEquals("BORROWED_NEW", oldView.spec().entries().getFirst().key());
        assertFalse(session.apply(connection, true, newer, latest));
        assertSame(oldView, session.acceptedView(connection));
        assertSame(shared, ResearchCategories.researchCategories);
        assertSame(actualServerEntry, ResearchCategories.getResearch("BORROWED_LATEST"));
        assertTrue(session.apply(connection, true, latest, latest));
        var latestView = session.acceptedView(connection);
        assertNotSame(oldView, latestView);
        assertEquals(latest.digest(), latestView.digest());
        assertSame(session.activeSpec(), latestView.spec());
        assertSame(shared, ResearchCategories.researchCategories);
        assertSame(actualServerEntry, ResearchCategories.getResearch("BORROWED_LATEST"));
        assertTrue(session.end(connection));
        assertNull(session.acceptedView(connection));
        assertSame(shared, ResearchCategories.researchCategories);
        assertEquals("BORROWED_NEW", oldView.spec().entries().getFirst().key());
        assertEquals("BORROWED_LATEST", latestView.spec().entries().getFirst().key());
    }

    @Test
    void acceptedViewRetainsDeeplyImmutableSpecAcrossPublicationReloadAndLogout() {
        Object connection = new Object();
        var firstEntry = entry("IMMUTABLE_VIEW", "BASICS", 1, 1);
        firstEntry.addProperty("external_field", "retained");
        var stageJson = firstEntry.getAsJsonArray("stages").get(0).getAsJsonObject();
        stageJson.addProperty("external_stage_field", "retained stage");
        JsonArray required = new JsonArray();
        required.add("addon:missing_gear;2;3");
        stageJson.add("required_item", required);
        var packet = packet(firstEntry);
        session.begin(connection, false);
        assertTrue(session.apply(connection, false, packet, null));
        var view = session.acceptedView(connection);
        var rawEntry = view.spec().entries().getFirst();
        var stage = rawEntry.stages().values().getFirst();
        var token = stage.requiredItems().values().getFirst();
        assertThrows(UnsupportedOperationException.class, () -> view.spec().sources().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.spec().entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.spec().diagnostics().clear());
        assertThrows(UnsupportedOperationException.class, () -> rawEntry.stages().values().clear());
        assertThrows(UnsupportedOperationException.class, () -> rawEntry.unknownFields().clear());
        assertThrows(UnsupportedOperationException.class, () -> stage.requiredItems().values().clear());
        assertThrows(UnsupportedOperationException.class, () -> stage.unknownFields().clear());
        assertThrows(UnsupportedOperationException.class, () -> token.ignoredSegments().add("new"));
        assertTrue(session.apply(connection, false, packet(entry("RELOAD_VIEW", "BASICS", 2, 2)), null));
        assertTrue(session.end(connection));
        assertEquals(packet.digest(), view.digest());
        assertEquals(packet.sources().values().iterator().next(), view.spec().sources().values().iterator().next());
        assertEquals("IMMUTABLE_VIEW", rawEntry.key());
        assertEquals("addon:missing_gear;2;3", token.raw());
        assertEquals("\"retained\"", rawEntry.unknownFields().get("external_field"));
        assertEquals("\"retained stage\"", stage.unknownFields().get("external_stage_field"));
    }

    @Test
    void acceptedViewReadBeforeBeginDoesNotBindAndBoundReadsRequireOwningMainThread() throws InterruptedException {
        Object connection = new Object();
        assertNull(offThreadFailure(() -> assertNull(session.acceptedView(connection))));
        assertTrue(session.begin(connection, false));
        assertInstanceOf(IllegalStateException.class, offThreadFailure(() -> session.acceptedView(connection)));
        assertTrue(session.apply(connection, false, packet(entry("THREAD_VIEW", "BASICS", 1, 1)), null));
        var view = session.acceptedView(connection);
        var map = ResearchCategories.researchCategories;
        assertInstanceOf(IllegalStateException.class, offThreadFailure(() -> session.acceptedView(connection)));
        assertInstanceOf(IllegalStateException.class, offThreadFailure(() -> session.acceptedView(new Object())));
        assertSame(view, session.acceptedView(connection));
        assertSame(map, ResearchCategories.researchCategories);
        session.end(connection);
        assertNull(session.acceptedView(connection));
        assertInstanceOf(IllegalStateException.class, offThreadFailure(() -> session.acceptedView(connection)));
    }

    private static Throwable offThreadFailure(Runnable action) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try { action.run(); }
            catch (Throwable rejected) { failure.set(rejected); }
        }, "research-accepted-view-test-reader");
        reader.setDaemon(true);
        reader.start();
        reader.join(5000L);
        assertFalse(reader.isAlive(), "Bounded metadata-only reader did not finish");
        return failure.get();
    }

    private static PacketSyncResearchCatalog packet(JsonObject... entries) {
        return new PacketSyncResearchCatalog(Map.of(id("thaumcraft:research/test"), document(entries)));
    }

    private static String document(JsonObject... entries) {
        JsonArray array = new JsonArray();
        for (JsonObject entry : entries) array.add(entry);
        JsonObject root = new JsonObject();
        root.add("entries", array);
        return root.toString();
    }

    private static JsonObject entry(String key, String category, int column, int row) {
        JsonObject entry = new JsonObject();
        entry.addProperty("key", key);
        entry.addProperty("name", "research.title");
        entry.addProperty("category", category);
        JsonArray location = new JsonArray();
        location.add(column);
        location.add(row);
        entry.add("location", location);
        JsonArray stages = new JsonArray();
        JsonObject stage = new JsonObject();
        stage.addProperty("text", "stage.text");
        stages.add(stage);
        entry.add("stages", stages);
        return entry;
    }

    private static ResearchEntry external(String key, String category, int column, int row) {
        ResearchEntry entry = new ResearchEntry();
        entry.setKey(key);
        entry.setCategory(category);
        entry.setDisplayColumn(column);
        entry.setDisplayRow(row);
        return entry;
    }

    private static ResearchEntry publishServer(PacketSyncResearchCatalog packet) {
        Map<String, String> raw = new LinkedHashMap<>();
        packet.sources().forEach((location, text) -> raw.put(location.toString(), text));
        var spec = ResearchCatalogParser.parse(raw, ResearchCategoryDefinitions.newBuiltinCategories().keySet());
        var publisher = new ResearchCatalogPublication();
        var candidate = ResearchCatalogPublication.prepareWhole(spec, ResearchCategories.researchCategories, Set.of());
        publisher.publish(candidate);
        return ResearchCategories.getResearch(spec.entries().getFirst().key());
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }

    private static void assertBounds(ResearchCategory category, int minColumn, int minRow, int maxColumn, int maxRow) {
        assertEquals(minColumn, category.minDisplayColumn);
        assertEquals(minRow, category.minDisplayRow);
        assertEquals(maxColumn, category.maxDisplayColumn);
        assertEquals(maxRow, category.maxDisplayRow);
    }

    private static final class EqualConnection {
        @Override public boolean equals(Object other) { return other instanceof EqualConnection; }
        @Override public int hashCode() { return 1; }
    }
}
