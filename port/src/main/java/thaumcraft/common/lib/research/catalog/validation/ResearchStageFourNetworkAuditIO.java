package thaumcraft.common.lib.research.catalog.validation;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit;
import thaumcraft.common.lib.network.PacketHandler;

/**
 * Isolation/identity adapter for a NEW, default-off stage-four TCP audit. The old catalog
 * recorder stays disabled: only its checked paths, bounded readers and JSONL writer are reused.
 * This class creates no directories, sends no packets and changes no player/catalog data.
 */
public final class ResearchStageFourNetworkAuditIO {
    public static final String ENABLE_PROPERTY = "thaumcraft.researchStageFourNetworkAudit";
    public static final String DIRECTORY_PROPERTY = ResearchCatalogNetworkAudit.DIRECTORY_PROPERTY;
    public static final String PURPOSE = "stage4-read-theory";
    public static final String NETWORK_PROTOCOL_VERSION = "6";
    public static final String COMPANION_FILE = ".research-stage-four-network-audit.json";
    private static final Set<String> ROLES = Set.of("server", "client-a", "client-b");
    private static final List<String> BASE_FIELDS =
        List.of("marker", "schemaVersion", "runId", "token", "role", "port", "phase");

    private ResearchStageFourNetworkAuditIO() {}

    /** Readiness is receipt, not exactly-once delivery of periodic source warp snapshots.
     * Every duplicate remains in the ordered transcript and is checked against its send. */
    public static boolean hasInitialPersonalSnapshots(int catalogs, int knowledge, int warp) {
        return catalogs == 1 && knowledge == 1 && warp >= 1 && warp <= 512;
    }

    static boolean canDispatchNextRead(int serverTick, int lastReadTick) {
        return (long) serverTick - lastReadTick >= 5;
    }

    /** Start terminal sealing with >=1000 owner ticks before the next native warp sync.
     * Late/unaccounted packets still fail strictly; no production event is suppressed. */
    static boolean hasTerminalSealWindow(int ownerTick) {
        return ownerTick >= 0 && ownerTick % 2000 <= 1000;
    }

    /** Original 10000-attempt failsafe and unique-key rejection permit partial draws.
     * A later actual safe-card selection is mandatory before this cohort can complete. */
    static boolean hasSourceDrawCardinality(int actualCards, int requestedCards) {
        return requestedCards >= 2 && requestedCards <= 3
            && actualCards >= 0 && actualCards <= requestedCards;
    }

    /** Expected server NBT after READ/sync; preserve an existing empty flag set exactly.
     * PlayerKnowledge serializes a retained empty EnumSet as flags:"", not an absent tag. */
    static CompoundTag expectedReadSnapshot(CompoundTag before, String readKey,
                                           boolean applied, boolean clearsPopup) {
        CompoundTag expected = before.copy();
        for (Tag tag : expected.getList("research", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) tag;
            if (!entry.contains("flags", Tag.TAG_STRING)) continue;
            String flags = Arrays.stream(entry.getString("flags").split(","))
                .filter(value -> !value.isEmpty()
                    && !(applied && entry.getString("key").equals(readKey)
                        && (value.equals("PAGE") || value.equals("RESEARCH")))
                    && !(clearsPopup && value.equals("POPUP")))
                .reduce((x, y) -> x + "," + y).orElse("");
            entry.putString("flags", flags);
        }
        return expected;
    }

    /** Audit-local projection of canonical wire flags through source client load/POPUP clear.
     * No global catalog, attachment or shared normalization is changed. Raw wire comparisons
     * remain exact; notably raw flags:"" loads WITHOUT a bucket, while POPUP loads a bucket
     * and its later removal leaves that bucket empty. */
    static CompoundTag expectedClientSnapshot(CompoundTag raw) {
        CompoundTag expected = raw.copy();
        for (Tag tag : expected.getList("research", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) tag;
            TreeSet<String> flags = new TreeSet<>();
            for (String name : entry.getString("flags").split(",")) {
                try { flags.add(EnumResearchFlag.valueOf(name).name()); }
                catch (IllegalArgumentException ignored) { /* Source ignores unknown enum names. */ }
            }
            if (flags.isEmpty()) entry.remove("flags");
            else {
                flags.remove(EnumResearchFlag.POPUP.name());
                entry.putString("flags", String.join(",", flags));
            }
        }
        return expected;
    }

    /** Called ONCE at recorder initialization; the unchanged legacy guard requires fresh files. */
    public static JsonObject validateDirectory(Path roleDirectory, String role) throws Exception {
        require(Boolean.getBoolean(ENABLE_PROPERTY), "Stage-four audit must be explicitly enabled");
        require(!Boolean.getBoolean(ResearchCatalogNetworkAudit.ENABLE_PROPERTY)
            && !Boolean.getBoolean(PlayerDataNetworkAudit.ENABLE_PROPERTY)
            && !Boolean.getBoolean(ResearchProgressNetworkAuditIO.ENABLE_PROPERTY),
            "Other network audit recorders must remain disabled");
        JsonObject base = ResearchCatalogNetworkAudit.validateDirectory(roleDirectory, role);
        Path companionPath = ResearchCatalogNetworkAudit.safePath(roleDirectory,
            roleDirectory.resolve(COMPANION_FILE));
        JsonObject companion = ResearchCatalogNetworkAudit.readDocument(companionPath, 4096);
        require(companion != null, "Missing NEW stage-four audit purpose marker");
        for (String field : BASE_FIELDS) {
            require(base.has(field) && companion.has(field)
                && base.get(field).equals(companion.get(field)),
                "Stage-four companion disagrees with isolation marker: " + field);
        }
        verifyIdentity(companion, companion, role);
        return companion;
    }

    /** New states/reports keep the legacy identity AND the explicit purpose/current protocol. */
    public static JsonObject identity(JsonObject marker) {
        require(marker != null && string(marker, "role") != null, "Missing stage-four audit identity");
        verifyIdentity(marker, marker, string(marker, "role"));
        JsonObject result = ResearchCatalogNetworkAudit.identity(marker);
        result.addProperty("purpose", PURPOSE);
        result.addProperty("networkProtocolVersion", NETWORK_PROTOCOL_VERSION);
        return result;
    }

    /** The peer role may differ from the local marker role; run/token/port must not differ. */
    public static void verifyIdentity(JsonObject marker, JsonObject value, String role) {
        requireRole(role);
        verifyPurpose(marker);
        ResearchCatalogNetworkAudit.verifyIdentity(marker, value, role);
        verifyPurpose(value);
    }

    /** Latest complete JSONL frame, or null while no complete frame is available. */
    public static JsonObject readProgress(Path runDirectory, JsonObject marker, String role) throws Exception {
        checkRead(runDirectory, marker, role);
        JsonObject value = ResearchCatalogNetworkAudit.readProgress(runDirectory, role,
            role.equals("server") ? "server-state.json" : "client-state.json");
        if (value != null) verifyIdentity(marker, value, role);
        return value;
    }

    /** CREATE_NEW reports may still be incomplete; retain the caller's bounded deadline. */
    public static JsonObject readTerminal(Path runDirectory, JsonObject marker, String role) throws Exception {
        checkRead(runDirectory, marker, role);
        Path path = ResearchCatalogNetworkAudit.safePath(runDirectory,
            runDirectory.resolve(role).resolve(role.equals("server") ? "server-report.json" : "client-report.json"));
        JsonObject value = ResearchCatalogNetworkAudit.readTerminalDocument(path,
            ResearchCatalogNetworkAudit.MAX_JSON_BYTES);
        if (value != null) verifyIdentity(marker, value, role);
        return value;
    }

    private static void checkRead(Path runDirectory, JsonObject marker, String role) {
        requireRole(role);
        verifyPurpose(marker);
        require(runDirectory != null && runDirectory.toAbsolutePath().normalize().getFileName() != null
            && runDirectory.toAbsolutePath().normalize().getFileName().toString().equals(string(marker, "runId")),
            "Read directory disagrees with stage-four run identity");
    }

    private static void verifyPurpose(JsonObject value) {
        require(NETWORK_PROTOCOL_VERSION.equals(PacketHandler.PROTOCOL_VERSION),
            "Stage-four audit protocol must be deliberately updated with production");
        require(value != null && PURPOSE.equals(string(value, "purpose"))
            && NETWORK_PROTOCOL_VERSION.equals(string(value, "networkProtocolVersion"))
            && "generate".equals(string(value, "phase")), "Stage-four audit purpose/protocol/phase mismatch");
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()
            || !object.getAsJsonPrimitive(key).isString()) return null;
        return object.get(key).getAsString();
    }

    private static void requireRole(String role) {
        require(role != null && ROLES.contains(role), "Unknown stage-four audit role");
    }
    private static void require(boolean condition, String message) {
        ResearchCatalogNetworkAudit.require(condition, message);
    }
}
