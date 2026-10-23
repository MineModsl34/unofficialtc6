package thaumcraft.client.gui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchStatus;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;
import thaumcraft.common.lib.research.ResearchBrowserAvailability.Reason;
import thaumcraft.common.lib.research.catalog.ResearchCatalogClientSession;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCatalogPublication;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;
import thaumcraft.common.lib.research.catalog.ResearchClientView;

/**
 * Pure Session -> received owner Snapshot -> source page projection integration. Identity tokens
 * stand for connection/LocalPlayer objects; there is no Minecraft facade, GUI, socket, packet
 * application handler, cost evaluator, research event or grant in these tests. Fixture storage is
 * explicitly prepared, and all mutable global metadata is restored synchronously after each test.
 */
class ResearchClientPresentationProjectionTest {
    private LinkedHashMap<String, ResearchCategory> previous;
    private ResearchCatalogClientSession session;
    private ResearchClientView client;
    private Object connection;
    private Object owner;

    @BeforeEach
    void isolate() {
        previous = ResearchCategories.researchCategories;
        ResearchCategories.researchCategories = new LinkedHashMap<>();
        session = new ResearchCatalogClientSession();
        client = new ResearchClientView();
        connection = new Object();
        owner = new Object();
    }

    @AfterEach
    void restore() { ResearchCategories.researchCategories = previous; }

    @Test
    void actualAcceptedMemoryCatalogMakesPageStrictAddendaIndependentOfMutableGlobalLookup() {
        var packet = packet(pageEntry(2), entry("GOOD", 1, 0, 1));
        acceptMemory(packet);
        PlayerKnowledge knowledge = completedPageAndGood();
        CompoundTag before = knowledge.serializeNBT().copy();
        assertTrue(client.knowledgeApplied(connection, owner, knowledge));
        var snapshot = client.capture(session.acceptedView(connection), owner);
        assertNotNull(snapshot);
        var projected = page(snapshot, "PAGE");
        assertEquals(1, projected.stageIndex());
        assertTrue(projected.addendaPhaseComplete());
        assertEquals(List.of("raw.PAGE.addendum.good"),
            projected.eligibleAddenda().stream().map(ResearchPageProjection.VisibleAddendum::rawText).toList());
        assertEquals(1, projected.eligibleAddenda().getFirst().sourceIndex());
        assertEquals(1, projected.eligibleAddenda().getFirst().number());
        assertEquals(EnumResearchStatus.COMPLETE, snapshot.knowledgeStatus("GOOD"));

        // Live storage's source status query DOES depend on the global map. This establishes a
        // real differing oracle, rather than merely asserting that the new snapshot is unchanged.
        ResearchCategories.getResearch("GOOD").setStages(new ResearchStage[3]);
        ResearchCategories.getResearch("PAGE").setStages(new ResearchStage[5]);
        assertEquals(EnumResearchStatus.IN_PROGRESS, knowledge.getResearchStatus("GOOD"));
        assertEquals(EnumResearchStatus.IN_PROGRESS, knowledge.getResearchStatus("PAGE"));
        assertFalse(knowledge.isResearchComplete("GOOD"));
        assertTrue(snapshot.knowsStrict("GOOD"));
        assertEquals(EnumResearchStatus.COMPLETE, snapshot.knowledgeStatus("PAGE"));
        assertEquals(projected, page(snapshot, "PAGE"));

        ResearchCategories.researchCategories.clear();
        assertNull(ResearchCategories.getResearch("PAGE"));
        assertEquals(projected, page(snapshot, "PAGE"));
        assertEquals(packet.digest(), snapshot.digest());
        assertSame(snapshot, client.capture(session.acceptedView(connection), owner));
        assertEquals(before, knowledge.serializeNBT());
    }

    @Test
    void actualAcceptedReloadInvalidatesOldGenerationAndReprojectsSameRawOwnerStage() {
        var first = packet(pageEntry(2), entry("GOOD", 1, 0, 1));
        acceptMemory(first);
        PlayerKnowledge knowledge = completedPageAndGood();
        CompoundTag before = knowledge.serializeNBT().copy();
        assertTrue(client.knowledgeApplied(connection, owner, knowledge));
        var oldAccepted = session.acceptedView(connection);
        var oldSnapshot = client.capture(oldAccepted, owner);
        assertNotNull(oldSnapshot);
        var oldPage = page(oldSnapshot, "PAGE");
        assertTrue(oldPage.addendaPhaseComplete());

        // A new actual server publication and matching memory echo, not a forged AcceptedView.
        var changed = packet(pageEntry(4), entry("GOOD", 1, 0, 1));
        ResearchCategories.researchCategories = new LinkedHashMap<>();
        publishServer(changed);
        assertTrue(session.apply(connection, true, changed, changed));
        var newAccepted = session.acceptedView(connection);
        assertNotSame(oldAccepted, newAccepted);
        assertFalse(client.isCurrent(oldSnapshot, newAccepted, owner));
        var refreshed = client.capture(newAccepted, owner);
        assertNotNull(refreshed);
        assertNotSame(oldSnapshot, refreshed);
        assertTrue(client.isCurrent(refreshed, newAccepted, owner));
        assertEquals(3, oldSnapshot.storedStage("PAGE"));
        assertEquals(3, refreshed.storedStage("PAGE"));
        assertEquals(3, knowledge.getResearchStage("PAGE"));
        var refreshedPage = page(refreshed, "PAGE");
        assertEquals(2, refreshedPage.stageIndex());
        assertEquals("raw.PAGE.stage.3", refreshedPage.rawText());
        assertFalse(refreshedPage.addendaPhaseComplete());
        assertTrue(refreshedPage.eligibleAddenda().isEmpty());
        assertEquals(EnumResearchStatus.IN_PROGRESS, refreshed.knowledgeStatus("PAGE"));
        assertEquals(oldPage, page(oldSnapshot, "PAGE")); // Historical queries are immutable.
        assertEquals(before, knowledge.serializeNBT()); // Reload did not re-deserialize or grant.
    }

    @Test
    void unknownAndKnownZeroSelectSameSourcePageButPrepareDifferentExactStoredStages() {
        String exactKey = "MiXeD_unstarted";
        var packet = packet(entry(exactKey, 0, 0, 2));
        acceptMemory(packet);
        PlayerKnowledge knowledge = new PlayerKnowledge();
        CompoundTag unknownBefore = knowledge.serializeNBT().copy();
        assertTrue(client.knowledgeApplied(connection, owner, knowledge));
        var unknown = client.capture(session.acceptedView(connection), owner);
        assertNotNull(unknown);
        assertEquals(-1, unknown.storedStage(exactKey));
        assertEquals(Reason.START_UNKNOWN, unknown.availability(exactKey).reason());
        assertRequest(unknown.prepare(exactKey), exactKey, -1, packet.digest());
        var unknownPage = page(unknown, exactKey);
        assertEquals(0, unknownPage.stageIndex());
        assertFalse(unknownPage.addendaPhaseComplete());
        assertEquals(unknownBefore, knowledge.serializeNBT());

        // An explicit second received-storage fixture, not a client-side optimistic request grant.
        assertTrue(knowledge.addResearch(exactKey));
        assertEquals(0, knowledge.getResearchStage(exactKey));
        CompoundTag zeroBefore = knowledge.serializeNBT().copy();
        assertTrue(client.knowledgeApplied(connection, owner, knowledge));
        var zero = client.capture(session.acceptedView(connection), owner);
        assertNotNull(zero);
        assertFalse(client.isCurrent(unknown, session.acceptedView(connection), owner));
        assertEquals(0, zero.storedStage(exactKey));
        assertEquals(Reason.RESUME_KNOWN_ZERO, zero.availability(exactKey).reason());
        assertRequest(zero.prepare(exactKey), exactKey, 0, packet.digest());
        var zeroPage = page(zero, exactKey);
        assertEquals(0, zeroPage.stageIndex());
        assertSame(unknownPage.stage(), zeroPage.stage());
        assertFalse(zeroPage.addendaPhaseComplete());
        assertNull(zero.prepare("mixed_unstarted")); // No key case normalization.
        assertEquals(zeroBefore, knowledge.serializeNBT());
    }

    @Test
    void deferredFutureItemDoesNotBlockPreparingSafeStartOrBecomeAClaimOfPaidRequirements() {
        var deferred = entry("FUTURE_ITEM", 0, 0, 2);
        JsonArray items = new JsonArray();
        items.add("addon:unported_gear;4;3");
        deferred.getAsJsonArray("stages").get(0).getAsJsonObject().add("required_item", items);
        var packet = packet(deferred);
        acceptMemory(packet);
        PlayerKnowledge knowledge = new PlayerKnowledge();
        CompoundTag before = knowledge.serializeNBT().copy();
        assertTrue(client.knowledgeApplied(connection, owner, knowledge));
        var initial = client.capture(session.acceptedView(connection), owner);
        assertNotNull(initial);
        assertEquals(Reason.START_UNKNOWN, initial.availability("FUTURE_ITEM").reason());
        assertRequest(initial.prepare("FUTURE_ITEM"), "FUTURE_ITEM", -1, packet.digest());
        var descriptor = page(initial, "FUTURE_ITEM").stage().requiredItems().values().getFirst();
        assertEquals("addon:unported_gear;4;3", descriptor.raw());
        assertEquals("addon:unported_gear", descriptor.name());
        assertEquals(4, descriptor.count());
        assertEquals(3, descriptor.damage());
        assertNotNull(descriptor.unresolvedReason());
        assertEquals(-1, knowledge.getResearchStage("FUTURE_ITEM"));
        assertEquals(before, knowledge.serializeNBT());

        knowledge.addResearch("FUTURE_ITEM");
        knowledge.setResearchStage("FUTURE_ITEM", 1);
        CompoundTag exposedBefore = knowledge.serializeNBT().copy();
        assertTrue(client.knowledgeApplied(connection, owner, knowledge));
        var exposed = client.capture(session.acceptedView(connection), owner);
        assertNotNull(exposed);
        assertEquals(Reason.ADVANCE_KNOWN, exposed.availability("FUTURE_ITEM").reason());
        // prepare deliberately permits an ATTEMPT. It neither resolves this item nor approves,
        // consumes or pays the active requirement; the checked server service may reject it.
        assertRequest(exposed.prepare("FUTURE_ITEM"), "FUTURE_ITEM", 1, packet.digest());
        assertEquals(descriptor, page(exposed, "FUTURE_ITEM").stage().requiredItems().values().getFirst());
        assertEquals(1, knowledge.getResearchStage("FUTURE_ITEM"));
        assertEquals(exposedBefore, knowledge.serializeNBT());
    }

    @Test
    void acceptedCatalogAloneOrReplacementOwnerWithoutReceivedDataCannotProducePage() {
        acceptMemory(packet(pageEntry(2), entry("GOOD", 1, 0, 1)));
        assertNull(currentPage(owner, "PAGE"));
        PlayerKnowledge knowledge = completedPageAndGood();
        assertTrue(client.knowledgeApplied(connection, owner, knowledge));
        var initial = client.capture(session.acceptedView(connection), owner);
        assertNotNull(initial);
        assertNotNull(currentPage(owner, "PAGE"));

        Object replacementOwner = new Object();
        PlayerKnowledge replacement = new PlayerKnowledge();
        replacement.deserializeNBT(knowledge.serializeNBT().copy());
        CompoundTag before = replacement.serializeNBT().copy();
        assertEquals(knowledge.serializeNBT(), before);
        assertFalse(client.isCurrent(initial, session.acceptedView(connection), replacementOwner));
        assertNull(currentPage(replacementOwner, "PAGE")); // Equal NBT is not a received epoch.
        assertTrue(client.knowledgeApplied(connection, replacementOwner, replacement));
        assertNotNull(currentPage(replacementOwner, "PAGE"));
        assertNull(currentPage(owner, "PAGE"));
        assertEquals(before, replacement.serializeNBT());
    }

    private ResearchPageProjection.View currentPage(Object actualOwner, String key) {
        var snapshot = client.capture(session.acceptedView(connection), actualOwner);
        return snapshot == null ? null : page(snapshot, key);
    }

    /** Same pure composition as the facade, without loading that Minecraft/client class. */
    private static ResearchPageProjection.View page(ResearchClientView.Snapshot snapshot, String key) {
        return ResearchPageProjection.project(snapshot.spec().entriesByKey().get(key),
            snapshot.storedStage(key), snapshot::knowsStrict);
    }

    private void acceptMemory(PacketSyncResearchCatalog packet) {
        publishServer(packet);
        assertTrue(session.begin(connection, true));
        assertTrue(session.apply(connection, true, packet, packet));
        assertTrue(client.begin(connection));
        assertNotNull(session.acceptedView(connection));
    }

    private static void publishServer(PacketSyncResearchCatalog packet) {
        Map<String, String> raw = new LinkedHashMap<>();
        packet.sources().forEach((location, text) -> raw.put(location.toString(), text));
        var spec = ResearchCatalogParser.parse(raw, ResearchCategoryDefinitions.newBuiltinCategories().keySet());
        var candidate = ResearchCatalogPublication.prepareWhole(spec, ResearchCategories.researchCategories, Set.of());
        new ResearchCatalogPublication().publish(candidate);
    }

    private static PlayerKnowledge completedPageAndGood() {
        PlayerKnowledge knowledge = new PlayerKnowledge();
        knowledge.addResearch("PAGE");
        knowledge.setResearchStage("PAGE", 3);
        knowledge.setResearchFlag("PAGE", EnumResearchFlag.RESEARCH);
        knowledge.addResearch("GOOD");
        knowledge.setResearchStage("GOOD", 2);
        knowledge.addKnowledge(EnumKnowledgeType.OBSERVATION, ResearchCategories.getResearchCategory("BASICS"), 7);
        return knowledge;
    }

    private static JsonObject pageEntry(int stages) {
        JsonObject page = entry("PAGE", 0, 0, stages);
        JsonArray addenda = new JsonArray();
        addenda.add(addendum("raw.PAGE.addendum.missing", "MISSING"));
        addenda.add(addendum("raw.PAGE.addendum.good", "GOOD"));
        page.add("addenda", addenda);
        return page;
    }

    private static JsonObject addendum(String text, String required) {
        JsonObject addendum = new JsonObject();
        addendum.addProperty("text", text);
        JsonArray research = new JsonArray();
        research.add(required);
        addendum.add("required_research", research);
        return addendum;
    }

    private static JsonObject entry(String key, int column, int row, int count) {
        JsonObject entry = new JsonObject();
        entry.addProperty("key", key);
        entry.addProperty("name", "raw." + key + ".title");
        entry.addProperty("category", "BASICS");
        JsonArray location = new JsonArray();
        location.add(column);
        location.add(row);
        entry.add("location", location);
        JsonArray stages = new JsonArray();
        for (int i = 1; i <= count; i++) {
            JsonObject stage = new JsonObject();
            stage.addProperty("text", "raw." + key + ".stage." + i);
            stages.add(stage);
        }
        entry.add("stages", stages);
        return entry;
    }

    private static PacketSyncResearchCatalog packet(JsonObject... entries) {
        JsonArray array = new JsonArray();
        for (JsonObject entry : entries) array.add(entry);
        JsonObject document = new JsonObject();
        document.add("entries", array);
        return new PacketSyncResearchCatalog(Map.of(ResourceLocation.parse("thaumcraft:research/page-integration"), document.toString()));
    }

    private static void assertRequest(PacketSyncProgressToServer request, String key, int stage, String digest) {
        assertNotNull(request);
        assertEquals(key, request.researchKey());
        assertEquals(stage, request.expectedStage());
        assertEquals(digest, request.catalogDigest());
    }
}
