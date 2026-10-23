package thaumcraft.common.lib.research.catalog.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit;

/** Bootstrap-free guards/I/O only: no actual TCP, progression, reload or reconnect acceptance. */
class ResearchStageFourNetworkAuditIOTest {
    private static final String RUN = "stage4-test";
    private static final String TOKEN = "d5d22267-8170-45ba-8a73-1d623bdb26f0";
    private static final List<String> ROLES = List.of("server", "client-a", "client-b");
    private static final List<String> PROPERTIES = List.of(
        ResearchStageFourNetworkAuditIO.ENABLE_PROPERTY,
        ResearchStageFourNetworkAuditIO.DIRECTORY_PROPERTY,
        ResearchCatalogNetworkAudit.ENABLE_PROPERTY,
        PlayerDataNetworkAudit.ENABLE_PROPERTY,
        ResearchProgressNetworkAuditIO.ENABLE_PROPERTY);
    @TempDir Path temporary;
    private final Map<String, String> previousProperties = new LinkedHashMap<>();
    private Path run;

    @BeforeEach
    void prepareFreshIsolatedRoleMarkers() throws Exception {
        for (String property : PROPERTIES) {
            previousProperties.put(property, System.getProperty(property));
            System.clearProperty(property);
        }
        System.setProperty(ResearchStageFourNetworkAuditIO.ENABLE_PROPERTY, "true");
        run = temporary.resolve("port/build/research-catalog-network").resolve(RUN);
        for (String role : ROLES) {
            Path directory = Files.createDirectories(run.resolve(role));
            Files.writeString(directory.resolve(".research-catalog-network-audit.json"), base(role).toString(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            writeCompanion(role, marker(role));
        }
    }

    @AfterEach
    void restoreSystemProperties() {
        previousProperties.forEach((key, value) -> {
            if (value == null) System.clearProperty(key); else System.setProperty(key, value);
        });
    }

    @Test
    void initialReceiptAllowsGenuinePeriodicWarpDuplicatesWithoutClaimingTranscriptAcceptance() {
        assertTrue(ResearchStageFourNetworkAuditIO.hasInitialPersonalSnapshots(1, 1, 1));
        assertTrue(ResearchStageFourNetworkAuditIO.hasInitialPersonalSnapshots(1, 1, 4));
        assertTrue(ResearchStageFourNetworkAuditIO.hasInitialPersonalSnapshots(1, 1, 512));
        assertFalse(ResearchStageFourNetworkAuditIO.hasInitialPersonalSnapshots(0, 1, 1));
        assertFalse(ResearchStageFourNetworkAuditIO.hasInitialPersonalSnapshots(2, 1, 1));
        assertFalse(ResearchStageFourNetworkAuditIO.hasInitialPersonalSnapshots(1, 0, 1));
        assertFalse(ResearchStageFourNetworkAuditIO.hasInitialPersonalSnapshots(1, 2, 1));
        assertFalse(ResearchStageFourNetworkAuditIO.hasInitialPersonalSnapshots(1, 1, 0));
        assertFalse(ResearchStageFourNetworkAuditIO.hasInitialPersonalSnapshots(1, 1, -1));
        assertFalse(ResearchStageFourNetworkAuditIO.hasInitialPersonalSnapshots(1, 1, 513));
    }

    @Test
    void readDispatchUsesActualServerTicksNotClientPollingTime() {
        assertTrue(ResearchStageFourNetworkAuditIO.canDispatchNextRead(0, -5));
        assertFalse(ResearchStageFourNetworkAuditIO.canDispatchNextRead(100, 100));
        assertFalse(ResearchStageFourNetworkAuditIO.canDispatchNextRead(104, 100));
        assertTrue(ResearchStageFourNetworkAuditIO.canDispatchNextRead(105, 100));
        assertTrue(ResearchStageFourNetworkAuditIO.canDispatchNextRead(110, 100));
        assertFalse(ResearchStageFourNetworkAuditIO.canDispatchNextRead(99, 100));
    }

    @Test
    void terminalSealingWaitsForBothOwnersSafeNativePeriodicWindow() {
        for (int tick : new int[]{0, 1, 999, 1000, 2000, 3000, 6000})
            assertTrue(ResearchStageFourNetworkAuditIO.hasTerminalSealWindow(tick));
        for (int tick : new int[]{-1, 1001, 1999, 3001, 3999})
            assertFalse(ResearchStageFourNetworkAuditIO.hasTerminalSealWindow(tick));
    }

    @Test
    void sourceFailsafeAllowsPartialDrawWithoutClaimingCardSelectionOrCosts() {
        for (int cards : new int[]{0, 1, 2})
            assertTrue(ResearchStageFourNetworkAuditIO.hasSourceDrawCardinality(cards, 2));
        assertFalse(ResearchStageFourNetworkAuditIO.hasSourceDrawCardinality(-1, 2));
        assertFalse(ResearchStageFourNetworkAuditIO.hasSourceDrawCardinality(3, 2));
        assertTrue(ResearchStageFourNetworkAuditIO.hasSourceDrawCardinality(3, 3));
        assertFalse(ResearchStageFourNetworkAuditIO.hasSourceDrawCardinality(4, 3));
        assertFalse(ResearchStageFourNetworkAuditIO.hasSourceDrawCardinality(1, 1));
        assertFalse(ResearchStageFourNetworkAuditIO.hasSourceDrawCardinality(1, 4));
    }

    @Test
    void readExpectationPreservesEmptyFlagSetAbsentTagsOtherFlagsAndOriginalSnapshot() {
        CompoundTag before = new CompoundTag();
        ListTag research = new ListTag();
        for (String key : List.of("FIRSTSTEPS", "THEORYRESEARCH", "NOFLAGS", "EMPTY")) {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", key);
            entry.putInt("stage", 4);
            if (key.equals("FIRSTSTEPS")) entry.putString("flags", "PAGE,POPUP,RESEARCH");
            if (key.equals("THEORYRESEARCH")) entry.putString("flags", "PAGE,POPUP,RESEARCH");
            if (key.equals("EMPTY")) entry.putString("flags", "");
            research.add(entry);
        }
        before.put("research", research);
        before.putInt("unrelatedBalance", 17);
        CompoundTag original = before.copy();
        CompoundTag applied = ResearchStageFourNetworkAuditIO.expectedReadSnapshot(before, "FIRSTSTEPS", true, true);
        ListTag rows = applied.getList("research", Tag.TAG_COMPOUND);
        assertTrue(rows.getCompound(0).contains("flags", Tag.TAG_STRING));
        assertEquals("", rows.getCompound(0).getString("flags"));
        assertEquals("PAGE,RESEARCH", rows.getCompound(1).getString("flags"));
        assertFalse(rows.getCompound(2).contains("flags"));
        assertTrue(rows.getCompound(3).contains("flags", Tag.TAG_STRING));
        assertEquals("", rows.getCompound(3).getString("flags"));
        assertEquals(17, applied.getInt("unrelatedBalance"));
        for (Tag tag : rows) assertEquals(4, ((CompoundTag) tag).getInt("stage"));
        assertEquals(original, before);
        assertEquals(before, ResearchStageFourNetworkAuditIO.expectedReadSnapshot(before, "FIRSTSTEPS", false, false));
        CompoundTag stale = ResearchStageFourNetworkAuditIO.expectedReadSnapshot(before, "FIRSTSTEPS", false, true);
        assertEquals("PAGE,RESEARCH", stale.getList("research", Tag.TAG_COMPOUND).getCompound(0).getString("flags"));
        CompoundTag unsynced = ResearchStageFourNetworkAuditIO.expectedReadSnapshot(before, "FIRSTSTEPS", true, false);
        assertEquals("POPUP", unsynced.getList("research", Tag.TAG_COMPOUND).getCompound(0).getString("flags"));
    }

    @Test
    void clientProjectionDistinguishesRawEmptyFromPopupClearedEmptyWithoutChangingWireNbt() {
        CompoundTag raw = new CompoundTag();
        ListTag research = new ListTag();
        String[] sourceFlags = {null, "", "POPUP", "PAGE", "PAGE,POPUP", "RESEARCH,POPUP,PAGE", "invalid,page", "invalid,POPUP"};
        for (int i = 0; i < sourceFlags.length; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", "KEY" + i);
            if (i % 2 == 0) entry.putInt("stage", 4);
            if (sourceFlags[i] != null) entry.putString("flags", sourceFlags[i]);
            research.add(entry);
        }
        raw.put("research", research);
        raw.putInt("unrelatedBalance", 17);
        CompoundTag wire = raw.copy();
        CompoundTag projected = ResearchStageFourNetworkAuditIO.expectedClientSnapshot(raw);
        ListTag rows = projected.getList("research", Tag.TAG_COMPOUND);
        assertFalse(rows.getCompound(0).contains("flags"));
        assertFalse(rows.getCompound(1).contains("flags"));
        assertTrue(rows.getCompound(2).contains("flags", Tag.TAG_STRING));
        assertEquals("", rows.getCompound(2).getString("flags"));
        assertEquals("PAGE", rows.getCompound(3).getString("flags"));
        assertEquals("PAGE", rows.getCompound(4).getString("flags"));
        assertEquals("PAGE,RESEARCH", rows.getCompound(5).getString("flags"));
        assertFalse(rows.getCompound(6).contains("flags"));
        assertEquals("", rows.getCompound(7).getString("flags"));
        for (int i = 0; i < rows.size(); i++) {
            assertEquals("KEY" + i, rows.getCompound(i).getString("key"));
            assertEquals(i % 2 == 0, rows.getCompound(i).contains("stage", Tag.TAG_INT));
            if (i % 2 == 0) assertEquals(4, rows.getCompound(i).getInt("stage"));
        }
        assertEquals(17, projected.getInt("unrelatedBalance"));
        assertEquals(wire, raw);
        assertNotEquals(rows.getCompound(1), rows.getCompound(2));
    }

    @Test
    void allThreeFreshRoleGuardsReturnPurposeMarkerAndIndependentIdentity() throws Exception {
        for (String role : ROLES) {
            JsonObject accepted = validate(role);
            assertEquals(marker(role), accepted);
            JsonObject state = ResearchStageFourNetworkAuditIO.identity(accepted);
            assertEquals(ResearchStageFourNetworkAuditIO.PURPOSE, state.get("purpose").getAsString());
            assertEquals("6", state.get("networkProtocolVersion").getAsString());
            assertTrue(state.getAsJsonPrimitive("networkProtocolVersion").isString());
            state.addProperty("token", UUID.randomUUID().toString());
            assertEquals(TOKEN, accepted.get("token").getAsString());
            assertEquals(base(role).toString(), Files.readString(run.resolve(role).resolve(".research-catalog-network-audit.json")));
        }
    }

    @Test
    void explicitEnableAndMutuallyExclusiveRecorderFlagsAreRequired() {
        System.clearProperty(ResearchStageFourNetworkAuditIO.ENABLE_PROPERTY);
        assertThrows(IllegalStateException.class, () -> validate("server"));
        System.setProperty(ResearchStageFourNetworkAuditIO.ENABLE_PROPERTY, "true");
        for (String conflicting : List.of(ResearchCatalogNetworkAudit.ENABLE_PROPERTY, PlayerDataNetworkAudit.ENABLE_PROPERTY, ResearchProgressNetworkAuditIO.ENABLE_PROPERTY)) {
            System.setProperty(conflicting, "true");
            assertThrows(IllegalStateException.class, () -> validate("server"), conflicting);
            System.clearProperty(conflicting);
        }
    }

    @Test
    void legacyIsolationMarkerAloneNeverAuthorizesNewPurpose() throws Exception {
        Files.delete(run.resolve("server").resolve(ResearchStageFourNetworkAuditIO.COMPANION_FILE));
        assertThrows(IllegalStateException.class, () -> validate("server"));
        assertEquals(base("server").toString(), Files.readString(run.resolve("server").resolve(".research-catalog-network-audit.json")));
    }

    @Test
    void companionMustMatchEveryLegacyBaseFieldIncludingPhase() throws Exception {
        for (String field : List.of("marker", "schemaVersion", "runId", "token", "role", "port", "phase")) {
            JsonObject mismatched = marker("server");
            if (field.equals("schemaVersion") || field.equals("port")) mismatched.addProperty(field, 2);
            else mismatched.addProperty(field, "different");
            writeCompanion("server", mismatched);
            assertThrows(IllegalStateException.class, () -> validate("server"), field);
        }
    }

    @Test
    void companionPurposeAndProtocolAreExactStringsNotLegacyOrCoercibleValues() throws Exception {
        for (String field : List.of("purpose", "networkProtocolVersion")) {
            JsonObject absent = marker("server");
            absent.remove(field);
            writeCompanion("server", absent);
            assertThrows(IllegalStateException.class, () -> validate("server"), field);
            JsonObject wrong = marker("server");
            wrong.addProperty(field, field.equals("purpose") ? "catalog" : "2");
            writeCompanion("server", wrong);
            assertThrows(IllegalStateException.class, () -> validate("server"), field);
        }
        JsonObject numericProtocol = marker("server");
        numericProtocol.addProperty("networkProtocolVersion", 5);
        writeCompanion("server", numericProtocol);
        assertThrows(IllegalStateException.class, () -> validate("server"));
    }

    @Test
    void boundedStrictCompanionReaderRejectsHugeOrInvalidUtf8Marker() throws Exception {
        Path path = run.resolve("server").resolve(ResearchStageFourNetworkAuditIO.COMPANION_FILE);
        Files.writeString(path, " ".repeat(4097));
        assertThrows(IllegalStateException.class, () -> validate("server"));
        Files.write(path, new byte[]{'{', '"', 'x', '"', ':', '"', (byte) 0xc3, '"', '}'});
        assertThrows(Exception.class, () -> validate("server"));
    }

    @Test
    void launchDirectoryMustBeExactAndRoleMustBeFresh() throws Exception {
        System.setProperty(ResearchStageFourNetworkAuditIO.DIRECTORY_PROPERTY, run.resolve("client-a").toString());
        assertThrows(IllegalStateException.class,
            () -> ResearchStageFourNetworkAuditIO.validateDirectory(run.resolve("server"), "server"));
        validate("server");
        ResearchCatalogNetworkAudit.writeJson(run.resolve("server"), "server-state.json", marker("server"), false);
        assertThrows(IllegalStateException.class, () -> validate("server"));
    }

    @Test
    void peerIdentityAllowsDifferentLocalRoleButRejectsStaleOrMissingFields() {
        JsonObject local = marker("server");
        JsonObject peer = marker("client-a");
        assertDoesNotThrow(() -> ResearchStageFourNetworkAuditIO.verifyIdentity(local, peer, "client-a"));
        assertThrows(IllegalStateException.class,
            () -> ResearchStageFourNetworkAuditIO.verifyIdentity(local, peer, "client-b"));
        for (String field : List.of("marker", "schemaVersion", "runId", "token", "port", "purpose", "networkProtocolVersion", "phase")) {
            JsonObject stale = peer.deepCopy();
            if (field.equals("schemaVersion") || field.equals("port")) stale.addProperty(field, 2);
            else stale.addProperty(field, "stale");
            assertThrows(IllegalStateException.class,
                () -> ResearchStageFourNetworkAuditIO.verifyIdentity(local, stale, "client-a"), field);
            JsonObject missing = peer.deepCopy();
            missing.remove(field);
            assertThrows(RuntimeException.class,
                () -> ResearchStageFourNetworkAuditIO.verifyIdentity(local, missing, "client-a"), field);
        }
    }

    @Test
    void readRoleAndRunAreCheckedBeforeAccessingAPath() {
        for (String invalid : new String[]{null, "client-c", "../client-a", "client-a/client-b"}) {
            assertThrows(IllegalStateException.class,
                () -> ResearchStageFourNetworkAuditIO.readProgress(run, marker("server"), invalid));
            assertThrows(IllegalStateException.class,
                () -> ResearchStageFourNetworkAuditIO.readTerminal(run, marker("server"), invalid));
        }
        Path otherRun = run.resolveSibling("other-run");
        assertThrows(IllegalStateException.class,
            () -> ResearchStageFourNetworkAuditIO.readProgress(otherRun, marker("server"), "client-a"));
        assertThrows(IllegalStateException.class,
            () -> ResearchStageFourNetworkAuditIO.readTerminal(otherRun, marker("server"), "client-a"));
    }

    @Test
    void progressReturnsLatestCompletePeerFrameAndWaitsThroughIncompleteTail() throws Exception {
        JsonObject local = marker("server");
        assertNull(ResearchStageFourNetworkAuditIO.readProgress(run, local, "client-a"));
        Path path = run.resolve("client-a/client-state.json");
        JsonObject frame = marker("client-a");
        frame.addProperty("revision", 1);
        Files.writeString(path, frame.toString(), StandardOpenOption.CREATE_NEW);
        assertNull(ResearchStageFourNetworkAuditIO.readProgress(run, local, "client-a"));
        Files.writeString(path, "\n", StandardOpenOption.APPEND);
        frame.addProperty("revision", 2);
        ResearchCatalogNetworkAudit.writeJson(run.resolve("client-a"), "client-state.json", frame, false);
        Files.writeString(path, "{\"incomplete\":", StandardOpenOption.APPEND);
        assertEquals(frame, ResearchStageFourNetworkAuditIO.readProgress(run, local, "client-a"));
        assertNull(ResearchStageFourNetworkAuditIO.readProgress(run, marker("client-a"), "server"));
    }

    @Test
    void completeProgressWithWrongPurposeFailsWithoutFallingBackToOlderFrame() throws Exception {
        JsonObject local = marker("server");
        JsonObject valid = marker("client-b");
        ResearchCatalogNetworkAudit.writeJson(run.resolve("client-b"), "client-state.json", valid, false);
        JsonObject wrongPurpose = valid.deepCopy();
        wrongPurpose.addProperty("purpose", "catalog");
        ResearchCatalogNetworkAudit.writeJson(run.resolve("client-b"), "client-state.json", wrongPurpose, false);
        assertThrows(IllegalStateException.class,
            () -> ResearchStageFourNetworkAuditIO.readProgress(run, local, "client-b"));
    }

    @Test
    void terminalWaitsForCompleteDocumentAndCannotOverwriteAnExistingReport() throws Exception {
        JsonObject local = marker("client-a");
        assertNull(ResearchStageFourNetworkAuditIO.readTerminal(run, local, "server"));
        Path path = run.resolve("server/server-report.json");
        Files.writeString(path, "{\"marker\":", StandardOpenOption.CREATE_NEW);
        assertNull(ResearchStageFourNetworkAuditIO.readTerminal(run, local, "server"));
        // Complete the same fixture writer; no real audit/report is edited by this TempDir test.
        JsonObject report = marker("server");
        report.addProperty("status", "completed");
        Files.writeString(path, report.toString(), StandardOpenOption.TRUNCATE_EXISTING);
        assertEquals(report, ResearchStageFourNetworkAuditIO.readTerminal(run, local, "server"));
        String original = Files.readString(path);
        assertThrows(Exception.class,
            () -> ResearchCatalogNetworkAudit.writeJson(run.resolve("server"), "server-report.json", report, true));
        assertEquals(original, Files.readString(path));
        JsonObject client = marker("client-b");
        ResearchCatalogNetworkAudit.writeJson(run.resolve("client-b"), "client-report.json", client, true);
        assertEquals(client, ResearchStageFourNetworkAuditIO.readTerminal(run, local, "client-b"));
    }

    @Test
    void terminalCompleteStaleIdentityIsFailureNotAnIncompleteDocument() throws Exception {
        JsonObject stale = marker("server");
        stale.addProperty("token", UUID.randomUUID().toString());
        ResearchCatalogNetworkAudit.writeJson(run.resolve("server"), "server-report.json", stale, true);
        assertThrows(IllegalStateException.class,
            () -> ResearchStageFourNetworkAuditIO.readTerminal(run, marker("client-b"), "server"));
    }

    private JsonObject validate(String role) throws Exception {
        Path directory = run.resolve(role);
        System.setProperty(ResearchStageFourNetworkAuditIO.DIRECTORY_PROPERTY, directory.toString());
        return ResearchStageFourNetworkAuditIO.validateDirectory(directory, role);
    }

    private void writeCompanion(String role, JsonObject value) throws Exception {
        Files.writeString(run.resolve(role).resolve(ResearchStageFourNetworkAuditIO.COMPANION_FILE), value.toString(), StandardCharsets.UTF_8);
    }

    private static JsonObject base(String role) {
        JsonObject value = new JsonObject();
        value.addProperty("marker", ResearchCatalogNetworkAudit.MARKER);
        value.addProperty("schemaVersion", 1);
        value.addProperty("runId", RUN);
        value.addProperty("token", TOKEN);
        value.addProperty("role", role);
        value.addProperty("phase", "generate");
        value.addProperty("port", 25573);
        return value;
    }

    private static JsonObject marker(String role) {
        JsonObject value = base(role);
        value.addProperty("purpose", ResearchStageFourNetworkAuditIO.PURPOSE);
        value.addProperty("networkProtocolVersion", ResearchStageFourNetworkAuditIO.NETWORK_PROTOCOL_VERSION);
        return value;
    }
}
