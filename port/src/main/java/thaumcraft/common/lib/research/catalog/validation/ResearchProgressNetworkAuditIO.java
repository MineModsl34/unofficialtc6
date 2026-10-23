package thaumcraft.common.lib.research.catalog.validation;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit;
import thaumcraft.common.lib.network.PacketHandler;

/**
 * Isolation/identity adapter for a NEW, default-off browser-request TCP audit. The old catalog
 * recorder stays disabled: only its checked paths, bounded readers and JSONL writer are reused.
 * This class creates no directories, sends no packets and changes no player/catalog data.
 */
public final class ResearchProgressNetworkAuditIO {
    public static final String ENABLE_PROPERTY = "thaumcraft.researchProgressNetworkAudit";
    public static final String DIRECTORY_PROPERTY = ResearchCatalogNetworkAudit.DIRECTORY_PROPERTY;
    public static final String PURPOSE = "browser-c2s";
    public static final String NETWORK_PROTOCOL_VERSION = "6";
    public static final String COMPANION_FILE = ".research-progress-network-audit.json";
    private static final Set<String> ROLES = Set.of("server", "client-a", "client-b");
    private static final List<String> BASE_FIELDS =
        List.of("marker", "schemaVersion", "runId", "token", "role", "port", "phase");

    private ResearchProgressNetworkAuditIO() {}

    /** Called ONCE at recorder initialization; the unchanged legacy guard requires fresh files. */
    public static JsonObject validateDirectory(Path roleDirectory, String role) throws Exception {
        require(Boolean.getBoolean(ENABLE_PROPERTY), "Browser-request audit must be explicitly enabled");
        require(!Boolean.getBoolean(ResearchCatalogNetworkAudit.ENABLE_PROPERTY)
            && !Boolean.getBoolean(PlayerDataNetworkAudit.ENABLE_PROPERTY)
            && !Boolean.getBoolean(ResearchStageFourNetworkAuditIO.ENABLE_PROPERTY),
            "Other network audit recorders must remain disabled");
        JsonObject base = ResearchCatalogNetworkAudit.validateDirectory(roleDirectory, role);
        Path companionPath = ResearchCatalogNetworkAudit.safePath(roleDirectory,
            roleDirectory.resolve(COMPANION_FILE));
        JsonObject companion = ResearchCatalogNetworkAudit.readDocument(companionPath, 4096);
        require(companion != null, "Missing NEW browser-request audit purpose marker");
        for (String field : BASE_FIELDS) {
            require(base.has(field) && companion.has(field)
                && base.get(field).equals(companion.get(field)),
                "Browser-request companion disagrees with isolation marker: " + field);
        }
        verifyIdentity(companion, companion, role);
        return companion;
    }

    /** New states/reports keep the legacy identity AND the explicit purpose/current protocol. */
    public static JsonObject identity(JsonObject marker) {
        require(marker != null && string(marker, "role") != null, "Missing browser-request audit identity");
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
            "Read directory disagrees with browser-request run identity");
    }

    private static void verifyPurpose(JsonObject value) {
        require(NETWORK_PROTOCOL_VERSION.equals(PacketHandler.PROTOCOL_VERSION),
            "Browser-request audit protocol must be deliberately updated with production");
        require(value != null && PURPOSE.equals(string(value, "purpose"))
            && NETWORK_PROTOCOL_VERSION.equals(string(value, "networkProtocolVersion"))
            && "generate".equals(string(value, "phase")), "Browser-request audit purpose/protocol/phase mismatch");
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()
            || !object.getAsJsonPrimitive(key).isString()) return null;
        return object.get(key).getAsString();
    }

    private static void requireRole(String role) {
        require(role != null && ROLES.contains(role), "Unknown browser-request audit role");
    }
    private static void require(boolean condition, String message) {
        ResearchCatalogNetworkAudit.require(condition, message);
    }
}
