package thaumcraft.common.lib.validation;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit;
import thaumcraft.common.lib.network.PacketHandler;

/** Default-off, private native alchemy cohort. Source manifests are immutable preparation inputs. */
public final class TcpAlchemyAuditIO {
    public static final String ENABLE_PROPERTY = "thaumcraft.tcpAlchemyAudit";
    public static final String DIRECTORY_PROPERTY = ENABLE_PROPERTY + ".dir";
    public static final String MARKER_FILE = ".tcp-alchemy-audit.json";
    public static final String MARKER = "TC_PORT_TCP_ALCHEMY_AUDIT_V1";
    public static final String PURPOSE = "native-alchemy";
    public static final String PROTOCOL = "6";
    public static final String WORLD = "tcp-alchemy-audit";
    public static final String USERNAME = "TC_Bootstrap";
    public static final UUID OWNER_UUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + USERNAME).getBytes(StandardCharsets.UTF_8));
    public static final int MAX_DOCUMENT_BYTES = 4 * 1024 * 1024;
    public static final List<String> IDENTITY_FIELDS = List.of("marker", "schemaVersion", "purpose", "networkProtocolVersion",
        "runId", "token", "port", "auditPhase", "goal", "sourceKind", "sourceRunId", "sourceToken", "sourcePort",
        "sourceMarker", "sourcePurpose", "sourceGoal", "sourceAuditPhase", "sourceServerReportSha256", "sourceClientReportSha256",
        "sourceRuntimeSha256", "sourceVerificationSha256", "worldManifestSha256");
    private TcpAlchemyAuditIO() {}
    public static void require(boolean condition, String detail) { TcpBootstrapAuditIO.require(condition, detail); }
    public static String string(JsonObject value, String key) { return TcpBootstrapAuditIO.string(value, key); }
    public static Path safePath(Path root, Path target) throws Exception { return TcpBootstrapAuditIO.safePath(root, target); }
    public static JsonObject readDocument(Path path, int limit, boolean mayWait) throws Exception {
        return TcpBootstrapAuditIO.readDocument(path, limit, mayWait);
    }
    public static Path directory(Path actualDirectory, String role) throws Exception {
        require(Boolean.getBoolean(ENABLE_PROPERTY), "Private native alchemy must be explicitly enabled");
        require(!Boolean.getBoolean(TcpBootstrapAuditIO.ENABLE_PROPERTY)
            && !Boolean.getBoolean("thaumcraft.tcpBootstrapReloadAudit"), "Mutually exclusive TCP audit flags required");
        require(Set.of("server", "client").contains(role), "Unknown native alchemy role");
        String property = System.getProperty(DIRECTORY_PROPERTY);
        require(property != null && !property.isBlank(), "Explicit private alchemy directory required");
        Path directory = Path.of(property).toAbsolutePath().normalize();
        require(directory.equals(actualDirectory.toAbsolutePath().normalize()) && directory.equals(actualDirectory.toRealPath()),
            "Alchemy role directory must be the actual game directory");
        safePath(directory, directory);
        Path run = directory.getParent(), root = run == null ? null : run.getParent();
        require(directory.getFileName().toString().equals(role) && root != null && root.getParent() != null
            && root.getParent().getParent() != null && root.getFileName().toString().equals("tcp-alchemy")
            && root.getParent().getFileName().toString().equals("build")
            && root.getParent().getParent().getFileName().toString().equals("port")
            && run.getFileName().toString().matches("[A-Za-z0-9_-]{1,80}"),
            "Expected private port/build/tcp-alchemy/<runId>/<role>");
        return directory;
    }
    public static JsonObject validateDirectory(Path actualDirectory, String role) throws Exception {
        Path directory = directory(actualDirectory, role);
        JsonObject marker = readDocument(safePath(directory, directory.resolve(MARKER_FILE)), 16 * 1024, false);
        require(marker != null, "Missing private native alchemy marker");
        verifyIdentity(marker, marker, role);
        require(directory.getParent().getFileName().toString().equals(string(marker, "runId")), "Alchemy marker runId differs");
        for (String name : List.of(role + "-report.json", role + "-state.json", role + "-events.jsonl"))
            require(!Files.exists(safePath(directory, directory.resolve(name)), LinkOption.NOFOLLOW_LINKS), "Fresh alchemy role required");
        return marker;
    }
    public static void verifyIdentity(JsonObject marker, JsonObject value, String role) {
        require(marker != null && value != null && Set.of("server", "client").contains(role), "Missing alchemy identity");
        require(PROTOCOL.equals(PacketHandler.PROTOCOL_VERSION), "Alchemy protocol must match reviewed production");
        require(MARKER.equals(string(value, "marker")) && value.has("schemaVersion") && value.get("schemaVersion").getAsInt() == 1
            && PURPOSE.equals(string(value, "purpose")) && PROTOCOL.equals(string(value, "networkProtocolVersion"))
            && "alchemy".equals(string(value, "goal")) && role.equals(string(value, "role")), "Alchemy schema/purpose/protocol/role differs");
        require(Set.of("generate", "resume", "reload").contains(string(value, "auditPhase")), "Unsupported alchemy phase");
        UUID.fromString(string(value, "token")); UUID.fromString(string(value, "sourceToken"));
        require(string(value, "runId").matches("[A-Za-z0-9_-]{1,80}") && string(value, "sourceRunId").matches("[A-Za-z0-9_-]{1,80}"), "Invalid alchemy runId");
        int port = value.get("port").getAsInt(); require(port >= 1024 && port <= 65535, "Invalid alchemy port");
        require(Set.of("bootstrap", "alchemy").contains(string(value, "sourceKind")), "Unsupported source kind");
        require(("generate".equals(string(value, "auditPhase")) ? "bootstrap" : "alchemy").equals(string(value, "sourceKind")), "Wrong phase source kind");
        for (String field : IDENTITY_FIELDS)
            require(marker.has(field) && value.has(field) && marker.get(field).equals(value.get(field)), "Alchemy peer identity differs: " + field);
        for (String field : List.of("sourceServerReportSha256", "sourceClientReportSha256", "sourceRuntimeSha256", "sourceVerificationSha256", "worldManifestSha256"))
            require(string(value, field).matches("[0-9a-f]{64}"), "Invalid source hash: " + field);
    }
    public static JsonObject identity(JsonObject marker) {
        JsonObject result = new JsonObject();
        for (String field : IDENTITY_FIELDS) result.add(field, marker.get(field).deepCopy());
        result.add("role", marker.get("role").deepCopy()); return result;
    }
    public static JsonObject readPeer(Path directory, JsonObject marker, String role, boolean terminal) throws Exception {
        Path run = directory.getParent(), path = safePath(run, run.resolve(role).resolve(role + (terminal ? "-report.json" : "-state.json")));
        JsonObject value = terminal ? readDocument(path, MAX_DOCUMENT_BYTES, true) : PlayerDataNetworkAudit.readProgressJson(path, 256 * 1024);
        if (value != null) verifyIdentity(marker, value, role); return value;
    }
    public static void writeState(Path directory, String role, JsonObject value) throws Exception { TcpBootstrapAuditIO.writeState(directory, role, value); }
    public static void appendEvent(Path directory, String role, JsonObject value) throws Exception { TcpBootstrapAuditIO.appendEvent(directory, role, value); }
    public static void writeTerminal(Path directory, String role, JsonObject value) throws Exception { TcpBootstrapAuditIO.writeTerminal(directory, role, value); }

    public static String hash(Path path) throws Exception {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= MAX_DOCUMENT_BYTES,
            "Missing/oversized source preparation input");
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    public static JsonObject sourceReport(Path directory, JsonObject marker) throws Exception {
        Path inputs = safePath(directory.getParent(), directory.getParent().resolve("preparation-inputs"));
        for (String[] pair : List.of(new String[]{"source-server-report.json", "sourceServerReportSha256"},
                new String[]{"source-client-report.json", "sourceClientReportSha256"}, new String[]{"source-runtime.json", "sourceRuntimeSha256"},
                new String[]{"source-verification.json", "sourceVerificationSha256"}, new String[]{"world-copy-manifest.json", "worldManifestSha256"}))
            require(hash(safePath(inputs, inputs.resolve(pair[0]))).equals(string(marker, pair[1])), "Immutable source input differs: " + pair[0]);
        JsonObject source = readDocument(inputs.resolve("source-server-report.json"), MAX_DOCUMENT_BYTES, false);
        require(source != null && source.get("passed").getAsBoolean() && "completed".equals(string(source, "status"))
            && source.get("saveFlushed").getAsBoolean() && source.get("diskNbtVerified").getAsBoolean()
            && string(marker, "sourceRunId").equals(string(source, "runId")) && string(marker, "sourceToken").equals(string(source, "token"))
            && string(marker, "sourceMarker").equals(string(source, "marker")) && string(marker, "sourcePurpose").equals(string(source, "purpose"))
            && string(marker, "sourceGoal").equals(string(source, "goal")) && string(marker, "sourceAuditPhase").equals(string(source, "auditPhase")),
            "Incomplete or mismatched actual source server report");
        JsonObject runtime = readDocument(inputs.resolve("source-runtime.json"), MAX_DOCUMENT_BYTES, false);
        require(runtime != null && "completed".equals(string(runtime, "status")) && runtime.get("buildExitCode").getAsInt() == 0
            && runtime.get("serverReady").getAsBoolean() && runtime.getAsJsonArray("processes").size() == 2, "Source JVM cohort did not complete");
        for (var row : runtime.getAsJsonArray("processes")) require(row.getAsJsonObject().get("exitCode").getAsInt() == 0
            && !row.getAsJsonObject().get("forcedStop").getAsBoolean(), "Source JVM failed/was killed");
        JsonObject verification = readDocument(inputs.resolve("source-verification.json"), MAX_DOCUMENT_BYTES, false);
        require(verification != null && verification.get("passed").getAsBoolean(), "Source independent verification did not pass");
        if ("resume".equals(string(marker, "auditPhase"))) require(source.get("checkpointedInfusion").getAsBoolean(), "Resume source is not a paid infusion checkpoint");
        if ("reload".equals(string(marker, "auditPhase"))) require(source.get("alchemyComplete").getAsBoolean()
            && source.get("reconnectProven").getAsBoolean(), "Final reload source is not complete/reconnected alchemy");
        return source;
    }
    public static JsonObject baseline(Path directory, JsonObject marker) throws Exception {
        JsonObject source = sourceReport(directory, marker), baseline = source.getAsJsonObject("persistenceBaseline");
        require(baseline != null && OWNER_UUID.toString().equals(string(baseline, "ownerUuid"))
            && baseline.get("firststepsStage").getAsInt() == 4, "Missing native completed FIRSTSTEPS owner baseline");
        return baseline.deepCopy();
    }
    public static com.google.gson.JsonArray vector(net.minecraft.core.BlockPos pos) {
        var value = new com.google.gson.JsonArray(); value.add(pos.getX()); value.add(pos.getY()); value.add(pos.getZ()); return value;
    }
    public static net.minecraft.core.BlockPos position(com.google.gson.JsonArray value) {
        require(value != null && value.size() == 3, "Invalid reviewed block position");
        return new net.minecraft.core.BlockPos(value.get(0).getAsInt(), value.get(1).getAsInt(), value.get(2).getAsInt());
    }
    public static void assertPlayer(net.minecraft.world.entity.player.Player player, JsonObject baseline, boolean client) throws Exception {
        TcpBootstrapReloadAudit.assertPlayer(player, baseline, client);
    }
    public static JsonObject playerSnapshot(net.minecraft.world.entity.player.Player player) {
        JsonObject value = TcpBootstrapReloadAudit.playerSnapshot(player);
        value.addProperty("basicsRaw", thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player).getKnowledgeRaw(
            thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.OBSERVATION,
            thaumcraft.api.research.ResearchCategories.getResearchCategory("BASICS")));
        return value;
    }
}
