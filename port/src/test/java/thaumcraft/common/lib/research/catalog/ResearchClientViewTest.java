package thaumcraft.common.lib.research.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchStatus;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer;
import thaumcraft.common.lib.research.ResearchBrowserAvailability.Reason;
import thaumcraft.common.lib.research.catalog.ResearchCatalogClientSession.AcceptedView;

/** Detached view/state tests only: no screen, packet send, registry bootstrap or server grant. */
class ResearchClientViewTest {
    private static final String DIGEST = "1".repeat(64);
    private LinkedHashMap<String, ResearchCategory> previous;
    private ResearchClientView view;
    private Object connection;
    private Object owner;
    private PlayerKnowledge live;

    @BeforeEach
    void isolate() {
        previous = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
        view = new ResearchClientView();
        connection = new Object();
        owner = new Object();
        live = new PlayerKnowledge();
    }

    @AfterEach
    void restore() { ResearchCategories.researchCategories = previous; }

    @Test
    void ownerGenerationComparisonDoesNotTurnCatalogOnlyReloadIntoKnowledgeResponse() {
        var catalog = accepted(entry("NODE", "BASICS", 1, 1));
        var initial = receive(catalog);
        assertFalse(initial.sameOwnerKnowledgeGeneration(null));
        assertTrue(initial.sameOwnerKnowledgeGeneration(initial));
        var reload = new AcceptedView(connection, false, "2".repeat(64), catalog.spec());
        var reloaded = view.capture(reload, owner);
        assertNotSame(initial, reloaded);
        assertTrue(reloaded.sameOwnerKnowledgeGeneration(initial));
        assertTrue(view.knowledgeApplied(connection, owner, live)); // Equal actual packets still have distinct epochs.
        var receivedAgain = view.capture(reload, owner);
        assertFalse(receivedAgain.sameOwnerKnowledgeGeneration(initial));
        assertFalse(receivedAgain.sameOwnerKnowledgeGeneration(reloaded));
        var other = new ResearchClientView();
        other.begin(connection);
        other.knowledgeApplied(connection, owner, live);
        assertFalse(other.capture(reload, owner).sameOwnerKnowledgeGeneration(receivedAgain));
    }

    @Test
    void flagsAndRawKnowledgeQueriesAreDetachedAndFingerprintUsesActualWireBody() {
        var catalog = accepted(entry("NODE", "BASICS", 1, 1));
        live.addResearch("NODE"); live.setResearchFlag("NODE", EnumResearchFlag.PAGE);
        var category = ResearchCategoryDefinitions.newBuiltinCategories().get("BASICS");
        live.addKnowledge(thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, category, 17);
        var wire = live.serializeNBT();
        view.begin(connection);
        // Simulate a legitimate local AUTOUNLOCK normalization after the packet body was received.
        live.addResearch("LOCAL_AUTO");
        assertTrue(view.knowledgeApplied(connection, owner, live, wire));
        var snapshot = view.capture(catalog, owner);
        assertEquals(thaumcraft.common.lib.research.ResearchReadFingerprint.of(wire), snapshot.readStateDigest());
        assertTrue(snapshot.hasResearchFlag("NODE", EnumResearchFlag.PAGE));
        assertEquals(17, snapshot.getKnowledgeRaw(thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, "BASICS"));
        assertEquals(1, snapshot.getKnowledge(thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, "BASICS"));
        live.clearResearchFlag("NODE", EnumResearchFlag.PAGE);
        live.addKnowledge(thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, category, 16);
        assertTrue(snapshot.hasResearchFlag("NODE", EnumResearchFlag.PAGE));
        assertEquals(17, snapshot.getKnowledgeRaw(thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, "BASICS"));
    }

    @Test
    void pendingDataCannotProduceAViewBeforeBothActualCatalogAndOwnerKnowledgeExist() {
        var catalog = accepted(entry("NODE", "BASICS", 1, 1));
        assertNull(view.capture(catalog, owner));
        assertFalse(view.knowledgeApplied(connection, owner, live));
        assertFalse(view.end(connection));
        assertThrows(NullPointerException.class, () -> view.begin(null));
        assertTrue(view.begin(connection));
        assertNull(view.capture(catalog, owner));
        assertTrue(view.knowledgeApplied(connection, owner, live));
        assertNull(view.capture(null, owner));
        var snapshot = view.capture(catalog, owner);
        assertNotNull(snapshot);
        assertSame(catalog, snapshot.accepted());
        assertSame(catalog.spec(), snapshot.spec());
        assertEquals(DIGEST, snapshot.digest());
        assertTrue(view.isCurrent(snapshot, catalog, owner));
        assertNull(view.capture(catalog, null));
    }

    @Test
    void exactIdentityNotEqualsAndLateConnectionEventsProtectTheCurrentSession() {
        var oldConnection = new EqualToken();
        var nextConnection = new EqualToken();
        var exactOwner = new EqualToken();
        var equalButOtherOwner = new EqualToken();
        assertEquals(oldConnection, nextConnection);
        assertEquals(exactOwner, equalButOtherOwner);
        view.begin(oldConnection);
        view.knowledgeApplied(oldConnection, exactOwner, live);
        var old = new AcceptedView(oldConnection, false, DIGEST, spec(entry("OLD", "BASICS", 1, 1)));
        var oldSnapshot = view.capture(old, exactOwner);
        assertNull(view.capture(old, equalButOtherOwner));
        view.begin(nextConnection);
        var next = new AcceptedView(nextConnection, false, DIGEST, spec(entry("NEXT", "BASICS", 1, 1)));
        assertFalse(view.end(oldConnection));
        assertFalse(view.knowledgeApplied(oldConnection, exactOwner, null)); // Stale packet is not dereferenced.
        assertNull(view.capture(next, exactOwner));
        assertNull(view.capture(old, exactOwner));
        assertFalse(view.isCurrent(oldSnapshot, old, exactOwner));
        assertTrue(view.knowledgeApplied(nextConnection, exactOwner, live));
        var nextSnapshot = view.capture(next, exactOwner);
        assertTrue(view.isCurrent(nextSnapshot, next, exactOwner));
        assertFalse(view.isCurrent(nextSnapshot, next, equalButOtherOwner));
        assertTrue(view.end(nextConnection));
        assertNull(view.capture(next, exactOwner));
        assertFalse(view.isCurrent(nextSnapshot, next, exactOwner));
    }

    @Test
    void replacementOwnerIsUnreceivedUntilItsActualPacketAndInvalidatesPriorTickets() {
        var catalog = accepted(entry("NODE", "BASICS", 1, 1));
        var old = receive(catalog);
        var replacement = new Object();
        assertNull(view.capture(catalog, replacement));
        assertFalse(view.isCurrent(old, catalog, replacement));
        var replacementData = new PlayerKnowledge();
        replacementData.addResearch("NODE");
        assertTrue(view.knowledgeApplied(connection, replacement, replacementData));
        var next = view.capture(catalog, replacement);
        assertNotNull(next);
        assertEquals(0, next.storedStage("NODE"));
        assertEquals(-1, old.storedStage("NODE"));
        assertFalse(view.isCurrent(old, catalog, owner));
        assertNull(view.capture(catalog, owner));
        assertTrue(view.isCurrent(next, catalog, replacement));
    }

    @Test
    void sameIdentityBeginAndEveryReceivedKnowledgePacketReplaceTheEpochEvenForEqualData() {
        var catalog = accepted(entry("NODE", "BASICS", 1, 1));
        var old = receive(catalog);
        assertSame(old, view.capture(catalog, owner)); // No repeated private metadata preparation.
        assertTrue(view.knowledgeApplied(connection, owner, live));
        var next = view.capture(catalog, owner);
        assertNotSame(old, next);
        assertFalse(view.isCurrent(old, catalog, owner));
        assertTrue(view.isCurrent(next, catalog, owner));
        assertTrue(view.begin(connection));
        assertNull(view.capture(catalog, owner));
        assertFalse(view.isCurrent(next, catalog, owner));
        view.knowledgeApplied(connection, owner, live);
        assertNotSame(next, view.capture(catalog, owner));
    }

    @Test
    void equalCatalogValueWithNewAcceptedIdentityInvalidatesOldTicketsAndTheCaptureCache() {
        var catalog = accepted(entry("NODE", "BASICS", 1, 1));
        var old = receive(catalog);
        var newGeneration = new AcceptedView(connection, false, DIGEST, catalog.spec());
        assertEquals(catalog, newGeneration);
        assertFalse(view.isCurrent(old, newGeneration, owner));
        var next = view.capture(newGeneration, owner);
        assertNotSame(old, next);
        assertSame(next, view.capture(newGeneration, owner));
        assertTrue(view.isCurrent(next, newGeneration, owner));
        var otherCoordinator = new ResearchClientView();
        otherCoordinator.begin(connection);
        otherCoordinator.knowledgeApplied(connection, owner, live);
        assertFalse(otherCoordinator.isCurrent(next, newGeneration, owner));
    }

    @Test
    void snapshotIsDetachedFromLaterLiveChangesAndQueriesNeverClearFlagsOrGrantAnything() {
        live.addResearch("NODE");
        live.setResearchStage("NODE", 1);
        live.setResearchFlag("NODE", EnumResearchFlag.POPUP);
        live.setResearchFlag("NODE", EnumResearchFlag.RESEARCH);
        var catalog = accepted(entry("NODE", "BASICS", 1, 1));
        CompoundTag before = live.serializeNBT().copy();
        var snapshot = receive(catalog);
        assertTrue(snapshot.knows("NODE"));
        assertFalse(snapshot.knowsStrict("NODE"));
        assertEquals(EnumResearchStatus.IN_PROGRESS, snapshot.knowledgeStatus("NODE"));
        assertEquals(new PacketSyncProgressToServer("NODE", 1, DIGEST), snapshot.prepare("NODE"));
        assertEquals(before, live.serializeNBT());
        live.setResearchStage("NODE", 2);
        live.addResearch("!new_marker");
        live.clearResearchFlag("NODE", EnumResearchFlag.POPUP);
        CompoundTag changed = live.serializeNBT().copy();
        assertEquals(1, snapshot.storedStage("NODE"));
        assertFalse(snapshot.knows("!new_marker"));
        assertFalse(snapshot.knowsStrict("NODE"));
        assertEquals(changed, live.serializeNBT());
        assertTrue(view.isCurrent(snapshot, catalog, owner)); // Epoch, NOT a live stage/cost authorization.
        view.knowledgeApplied(connection, owner, live);
        var next = view.capture(catalog, owner);
        assertTrue(next.knowsStrict("NODE"));
        assertNull(next.prepare("NODE"));
        assertFalse(view.isCurrent(snapshot, catalog, owner));
    }

    @Test
    void acceptedSpecOnlyDeterminesCompletionAndEvenMalformedGlobalMapsAreNeverReadOrPublished() {
        live.addResearch("NODE"); live.setResearchStage("NODE", 2);
        var catalog = accepted(entry("NODE", "BASICS", 1, 1));
        var snapshot = receive(catalog);
        assertTrue(snapshot.knowsStrict("NODE"));
        var global = new ResearchCategory("BASICS", "UNKNOWN_GATE", null, null, null);
        var globalEntry = new ResearchEntry();
        globalEntry.setKey("NODE"); globalEntry.setCategory("BASICS");
        globalEntry.setMeta(new ResearchEntry.EnumResearchMeta[]{ResearchEntry.EnumResearchMeta.AUTOUNLOCK});
        global.research.put("NODE", globalEntry);
        ResearchCategories.researchCategories.put("BASICS", global);
        global.research = null;
        var replacement = new AcceptedView(connection, false, DIGEST, catalog.spec());
        var capturedAgain = view.capture(replacement, owner);
        assertNotNull(capturedAgain);
        assertTrue(capturedAgain.knowsStrict("NODE"));
        assertTrue(capturedAgain.availability("NODE").categoryAccessible());
        assertSame(global, ResearchCategories.researchCategories.get("BASICS"));
        assertNull(global.research);
        ResearchCategories.researchCategories = null;
        var whileNull = view.capture(new AcceptedView(connection, false, DIGEST, catalog.spec()), owner);
        assertNotNull(whileNull);
        assertTrue(whileNull.knowsStrict("NODE"));
        assertNull(ResearchCategories.researchCategories);
    }

    @Test
    void unknownKnownZeroAbsentStagesAndOpaqueMarkersKeepDistinctSourceStatuses() {
        live.addResearch("KNOWN_ZERO");
        live.addResearch("NO_STAGES");
        live.addResearch("!raw_marker");
        var snapshot = receive(accepted(entry("UNKNOWN", "BASICS", 1, 1),
            entry("KNOWN_ZERO", "BASICS", 1, 2), entry("NO_STAGES", "BASICS", 0, 3)));
        assertEquals(EnumResearchStatus.UNKNOWN, snapshot.knowledgeStatus("UNKNOWN"));
        assertEquals(-1, snapshot.storedStage("UNKNOWN"));
        assertEquals(EnumResearchStatus.IN_PROGRESS, snapshot.knowledgeStatus("KNOWN_ZERO"));
        assertEquals(0, snapshot.storedStage("KNOWN_ZERO"));
        assertEquals(EnumResearchStatus.COMPLETE, snapshot.knowledgeStatus("NO_STAGES"));
        assertEquals(EnumResearchStatus.COMPLETE, snapshot.knowledgeStatus("!raw_marker"));
        assertEquals(Reason.RESUME_KNOWN_ZERO, snapshot.availability("KNOWN_ZERO").reason());
        assertEquals(-1, snapshot.prepare("UNKNOWN").expectedStage());
        assertEquals(0, snapshot.prepare("KNOWN_ZERO").expectedStage());
        assertNull(snapshot.prepare("NO_STAGES"));
        assertNull(snapshot.prepare("!raw_marker")); // Known is not registered request authority.
        assertNull(snapshot.prepare("not_registered"));
        assertTrue(snapshot.knows(""));
        assertEquals(EnumResearchStatus.UNKNOWN, snapshot.knowledgeStatus(null));
        assertThrows(NullPointerException.class, () -> snapshot.knows((String) null));
    }

    @Test
    void qualifiedStatusUsesLiteralAcceptedEntryLookupRatherThanUnqualifyingItsStageCount() {
        live.addResearch("BASE"); live.setResearchStage("BASE", 1);
        var withoutLiteral = accepted(entry("BASE", "BASICS", 2, 1));
        var first = receive(withoutLiteral);
        assertEquals(EnumResearchStatus.IN_PROGRESS, first.knowledgeStatus("BASE"));
        assertEquals(EnumResearchStatus.COMPLETE, first.knowledgeStatus("BASE@1"));
        assertEquals(EnumResearchStatus.UNKNOWN, first.knowledgeStatus("BASE@2"));
        assertEquals(-1, first.storedStage("BASE@1"));
        assertTrue(first.knowsStrict("BASE@1"));
        var withLiteral = accepted(entry("BASE", "BASICS", 2, 1), entry("BASE@1", "BASICS", 1, 2));
        var second = view.capture(withLiteral, owner);
        assertEquals(EnumResearchStatus.IN_PROGRESS, second.knowledgeStatus("BASE@1"));
        assertEquals(-1, second.storedStage("BASE@1"));
        assertTrue(second.knows("BASE@invalid", "BASE@-4", "BASE@1@ignored"));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> second.knows("@"));
    }

    @Test
    void expressionsReuseCapabilityRegexOrFallthroughAndCompletionAgainstOnlyTheAcceptedSpec() {
        var node = entry("NODE", "BASICS", 1, 1);
        node.add("parents", strings("ALPHA||BETA", "MISSING"));
        node.add("meta", strings("HIDDEN"));
        live.addResearch("A");
        var snapshot = receive(accepted(node));
        assertTrue(snapshot.knowsStrict("ALPHA||BETA", "MISSING")); // Source early regex-OR return.
        assertTrue(snapshot.availability("NODE").parentsSatisfied());
        assertTrue(snapshot.availability("NODE").mayRequestProgress());
        assertNotNull(snapshot.prepare("NODE"));
        assertFalse(snapshot.knows("A&&MISSING"));
        assertTrue(snapshot.knows("NEITHER||NOPE")); // Failed regex-OR falls through.
    }

    @Test
    void categoryGateBlocksUnknownButNotKnownSearchActionsAndStrictParentsStillLockThem() {
        var unknown = entry("UNKNOWN", "AUROMANCY", 1, 1);
        var known = entry("KNOWN", "AUROMANCY", 1, 2);
        var child = entry("CHILD", "BASICS", 1, 3);
        child.add("parents", strings("!missing_marker"));
        live.addResearch("KNOWN");
        var snapshot = receive(accepted(unknown, known, child));
        assertEquals(Reason.CATEGORY_LOCKED, snapshot.availability("UNKNOWN").reason());
        assertNull(snapshot.prepare("UNKNOWN"));
        assertFalse(snapshot.availability("KNOWN").categoryAccessible());
        assertEquals(Reason.RESUME_KNOWN_ZERO, snapshot.availability("KNOWN").reason());
        assertEquals(0, snapshot.prepare("KNOWN").expectedStage());
        assertEquals(Reason.MISSING_PARENTS, snapshot.availability("CHILD").reason());
        assertNull(snapshot.prepare("CHILD"));
    }

    @Test
    void memorySnapshotNeverBorrowsANewerSharedServerDigestOrStageMetadata() {
        live.addResearch("NODE"); live.setResearchStage("NODE", 2);
        var oldEcho = new AcceptedView(connection, true, DIGEST, spec(entry("NODE", "BASICS", 1, 1)));
        var old = receive(oldEcho);
        var newestServerOnly = new AcceptedView(connection, true, "2".repeat(64), spec(entry("NODE", "BASICS", 5, 1)));
        ResearchCategories.researchCategories = ResearchCategoryDefinitions.newBuiltinCategories();
        var mutable = new ResearchEntry(); mutable.setKey("NODE"); mutable.setCategory("BASICS");
        ResearchCategories.researchCategories.get("BASICS").research.put("NODE", mutable);
        assertEquals(DIGEST, old.digest());
        assertEquals(EnumResearchStatus.COMPLETE, old.knowledgeStatus("NODE"));
        assertSame(old, view.capture(oldEcho, owner));
        assertFalse(view.isCurrent(old, newestServerOnly, owner));
        // Only an explicitly supplied newer ACCEPTED view creates the newer detached query.
        var newlyAccepted = view.capture(newestServerOnly, owner);
        assertEquals("2".repeat(64), newlyAccepted.digest());
        assertEquals(EnumResearchStatus.IN_PROGRESS, newlyAccepted.knowledgeStatus("NODE"));
        assertSame(mutable, ResearchCategories.getResearch("NODE"));
    }

    @Test
    void customCategoriesMalformedMetadataAndCyclicOrMalformedGatesFailClosedWithoutPublication() {
        var builtin = accepted(entry("GOOD", "BASICS", 1, 1));
        var good = receive(builtin);
        var global = ResearchCategories.researchCategories;
        CompoundTag before = live.serializeNBT().copy();
        var custom = new AcceptedView(connection, false, DIGEST,
            ResearchCatalogParser.parse(Map.of("test:custom", root(entry("CUSTOM", "NATIVE_ADDON", 1, 1))),
                Set.of("NATIVE_ADDON")));
        assertNull(view.capture(custom, owner));
        assertFalse(view.isCurrent(good, custom, owner));
        var valid = builtin.spec().entries().getFirst();
        var malformed = new ResearchCatalogSpec.Entry(valid.source(), valid.path(), valid.key(), valid.name(),
            valid.category(), valid.icons(), valid.parents(), valid.siblings(), valid.meta(), valid.location(),
            valid.rewardItems(), valid.rewardKnowledge(), null, valid.addenda(), valid.unknownFields());
        assertNull(view.capture(new AcceptedView(connection, false, DIGEST,
            new ResearchCatalogSpec(Map.of(), List.of(malformed), List.of())), owner));
        var badGate = entry("BAD_GATE", "BASICS", 1, 1); badGate.add("parents", strings("@"));
        var bad = view.capture(accepted(badGate), owner);
        assertEquals(Reason.MALFORMED, bad.availability("BAD_GATE").reason());
        assertNull(bad.prepare("BAD_GATE"));
        var a = entry("A", "BASICS", 1, 1); a.add("parents", strings("B"));
        var b = entry("B", "BASICS", 1, 2); b.add("parents", strings("A"));
        var cyclic = view.capture(accepted(a, b), owner);
        assertEquals(Reason.VISIBILITY_CYCLE, cyclic.availability("A").reason());
        assertNull(cyclic.prepare("A"));
        assertSame(global, ResearchCategories.researchCategories);
        assertTrue(global.isEmpty());
        assertEquals(before, live.serializeNBT());
    }

    @Test
    void requestPreparationIsBoundedNotADeferredItemResolverOrCostCheck() {
        var node = entry("PAID", "BASICS", 1, 1);
        node.getAsJsonArray("stages").get(0).getAsJsonObject().add("required_item", strings("addon:missing;2;3"));
        var longKey = "K".repeat(PacketSyncProgressToServer.MAX_KEY_CHARS + 1);
        var snapshot = receive(accepted(node, entry(longKey, "BASICS", 1, 2)));
        CompoundTag before = live.serializeNBT().copy();
        assertTrue(snapshot.availability("PAID").mayRequestProgress());
        assertEquals(new PacketSyncProgressToServer("PAID", -1, DIGEST), snapshot.prepare("PAID"));
        assertTrue(snapshot.availability(longKey).mayRequestProgress());
        assertNull(snapshot.prepare(longKey));
        var badDigest = view.capture(new AcceptedView(connection, false, "invalid", snapshot.spec()), owner);
        assertNull(badDigest.prepare("PAID"));
        assertEquals(before, live.serializeNBT());
        assertEquals("addon:missing;2;3", snapshot.spec().entries().getFirst().stages().values().getFirst()
            .requiredItems().values().getFirst().raw());
    }

    @Test
    void malformedActualOwnerApplyDoesNotRetainAnOlderReceivedEpoch() {
        var catalog = accepted(entry("NODE", "BASICS", 1, 1));
        var old = receive(catalog);
        var malformed = new PlayerKnowledge();
        assertTrue(malformed.addResearch(null)); // Native model permits this, but cannot serialize the null key.
        assertFalse(view.knowledgeApplied(connection, owner, malformed));
        assertFalse(view.isCurrent(old, catalog, owner));
        assertNull(view.capture(catalog, owner));
        assertTrue(view.knowledgeApplied(connection, owner, live));
        var captured = view.capture(catalog, owner);
        assertThrows(NullPointerException.class, () -> view.knowledgeApplied(connection, owner, null));
        assertNull(view.capture(catalog, owner));
        assertFalse(view.isCurrent(captured, catalog, owner));
    }

    @Test
    void coordinatorAndHistoricalSnapshotQueriesEnforceTheBoundOwningThread() throws InterruptedException {
        var catalog = accepted(entry("NODE", "BASICS", 1, 1));
        var snapshot = receive(catalog);
        var coordinatorFailure = new AtomicReference<Throwable>();
        var snapshotFailure = new AtomicReference<Throwable>();
        Thread other = new Thread(() -> {
            try { view.capture(catalog, owner); } catch (Throwable failure) { coordinatorFailure.set(failure); }
            try { snapshot.storedStage("NODE"); } catch (Throwable failure) { snapshotFailure.set(failure); }
        });
        other.start(); other.join(2000);
        assertFalse(other.isAlive());
        assertInstanceOf(IllegalStateException.class, coordinatorFailure.get());
        assertInstanceOf(IllegalStateException.class, snapshotFailure.get());
        assertTrue(view.isCurrent(snapshot, catalog, owner));
    }

    private ResearchClientView.Snapshot receive(AcceptedView catalog) {
        view.begin(connection);
        assertTrue(view.knowledgeApplied(connection, owner, live));
        var snapshot = view.capture(catalog, owner);
        assertNotNull(snapshot);
        return snapshot;
    }

    private AcceptedView accepted(JsonObject... entries) {
        return new AcceptedView(connection, false, DIGEST, spec(entries));
    }

    private static ResearchCatalogSpec spec(JsonObject... entries) {
        return ResearchCatalogParser.parse(Map.of("test:research", root(entries)),
            ResearchCategoryDefinitions.newBuiltinCategories().keySet());
    }

    private static String root(JsonObject... entries) {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (JsonObject entry : entries) array.add(entry);
        root.add("entries", array);
        return root.toString();
    }

    private static JsonObject entry(String key, String category, int stages, int column) {
        JsonObject entry = new JsonObject();
        entry.addProperty("key", key); entry.addProperty("name", "research.title");
        entry.addProperty("category", category);
        JsonArray location = new JsonArray(); location.add(column); location.add(0); entry.add("location", location);
        JsonArray stageArray = new JsonArray();
        for (int i = 0; i < stages; i++) {
            JsonObject stage = new JsonObject(); stage.addProperty("text", "stage.text"); stageArray.add(stage);
        }
        entry.add("stages", stageArray);
        return entry;
    }

    private static JsonArray strings(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }

    private static final class EqualToken {
        @Override public boolean equals(Object other) { return other instanceof EqualToken; }
        @Override public int hashCode() { return 1; }
    }
}
