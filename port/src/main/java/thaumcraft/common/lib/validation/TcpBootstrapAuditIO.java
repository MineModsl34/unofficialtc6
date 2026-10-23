package thaumcraft.common.lib.validation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit;
import thaumcraft.common.lib.network.PacketHandler;

/** Default-off private TCP scenario I/O. Does not create a cohort or accept an EULA. */
public final class TcpBootstrapAuditIO {
    public static final String ENABLE_PROPERTY = "thaumcraft.tcpBootstrapAudit";
    public static final String DIRECTORY_PROPERTY = ENABLE_PROPERTY + ".dir";
    public static final String MARKER_FILE = ".tcp-bootstrap-audit.json";
    public static final String MARKER = "TC_PORT_TCP_BOOTSTRAP_AUDIT_V1";
    public static final String PURPOSE = "native-bootstrap";
    public static final String PROTOCOL = "6";
    public static final String WORLD = "tcp-bootstrap-audit";
    public static final String USERNAME = "TC_Bootstrap";
    public static final UUID OWNER_UUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + USERNAME).getBytes(StandardCharsets.UTF_8));
    public static final int MAX_DOCUMENT_BYTES = 4 * 1024 * 1024;
    private static final int MAX_STATE_FRAME_BYTES = 256 * 1024;
    private static final Gson COMPACT = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private TcpBootstrapAuditIO() {}

    public static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }

    public static Path directory(Path actualDirectory, String role) throws Exception {
        require(Boolean.getBoolean(ENABLE_PROPERTY), "Private TCP bootstrap must be explicitly enabled");
        require(Set.of("server", "client").contains(role), "Unknown bootstrap role");
        String property = System.getProperty(DIRECTORY_PROPERTY);
        require(property != null && !property.isBlank(), "Explicit private bootstrap directory required");
        Path directory = Path.of(property).toAbsolutePath().normalize();
        require(directory.equals(actualDirectory.toAbsolutePath().normalize()) && directory.equals(actualDirectory.toRealPath()),
            "Bootstrap directory must equal actual game/launch directory");
        safePath(directory, directory);
        Path run = directory.getParent(), root = run == null ? null : run.getParent();
        require(directory.getFileName().toString().equals(role) && root != null && root.getParent() != null
            && root.getParent().getParent() != null && root.getFileName().toString().equals("tcp-bootstrap")
            && root.getParent().getFileName().toString().equals("build")
            && root.getParent().getParent().getFileName().toString().equals("port")
            && run.getFileName().toString().matches("[A-Za-z0-9_-]{1,80}"),
            "Expected freshly prepared port/build/tcp-bootstrap/<runId>/<role>");
        return directory;
    }

    public static JsonObject validateDirectory(Path actualDirectory, String role) throws Exception {
        Path directory = directory(actualDirectory, role);
        JsonObject marker = readDocument(safePath(directory, directory.resolve(MARKER_FILE)), 4096, false);
        require(marker != null, "Missing private bootstrap marker");
        verifyIdentity(marker, marker, role);
        require(directory.getParent().getFileName().toString().equals(marker.get("runId").getAsString()),
            "Marker runId differs from real directory");
        for (String name : List.of(role + "-report.json", role + "-state.json", role + "-events.jsonl")) {
            require(!Files.exists(safePath(directory, directory.resolve(name)), LinkOption.NOFOLLOW_LINKS),
                "Fresh bootstrap role required; report/state/event file already exists");
        }
        return marker;
    }

    public static void verifyIdentity(JsonObject marker, JsonObject value, String role) {
        require(marker != null && value != null && Set.of("server", "client").contains(role), "Missing/unknown bootstrap identity");
        require(PROTOCOL.equals(PacketHandler.PROTOCOL_VERSION), "Bootstrap protocol must deliberately match production");
        require(string(value, "marker").equals(MARKER) && value.has("schemaVersion") && value.get("schemaVersion").getAsInt() == 1
            && string(value, "purpose").equals(PURPOSE) && string(value, "networkProtocolVersion").equals(PROTOCOL)
            && string(value, "role").equals(role), "Bootstrap purpose/schema/protocol/role mismatch");
        UUID.fromString(string(value, "token"));
        require(string(value, "runId").matches("[A-Za-z0-9_-]{1,80}"), "Invalid private runId");
        int port = value.get("port").getAsInt();
        require(port >= 1024 && port <= 65535, "Invalid private TCP port");
        for (String field : List.of("marker", "schemaVersion", "purpose", "networkProtocolVersion", "runId", "token", "port")) {
            require(marker.has(field) && marker.get(field).equals(value.get(field)), "Bootstrap peer identity differs: " + field);
        }
        for (String field : List.of("auditPhase", "goal", "sourceRunId", "sourceToken")) {
            require(marker.has(field) == value.has(field) && (!marker.has(field) || marker.get(field).equals(value.get(field))),
                "Bootstrap phase/source identity differs: " + field);
        }
    }

    public static JsonObject identity(JsonObject marker) {
        JsonObject result = new JsonObject();
        for (String field : List.of("marker", "schemaVersion", "purpose", "networkProtocolVersion", "runId", "token", "role", "port")) {
            result.add(field, marker.get(field).deepCopy());
        }
        for (String field : List.of("auditPhase", "goal", "sourceRunId", "sourceToken")) {
            if (marker.has(field)) result.add(field, marker.get(field).deepCopy());
        }
        return result;
    }

    public static String string(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    public static Path safePath(Path privateRoot, Path target) throws Exception {
        Path root = privateRoot.toAbsolutePath().normalize(), path = target.toAbsolutePath().normalize();
        require(path.startsWith(root), "Bootstrap I/O escaped its private root");
        for (Path part = path; part != null; part = part.getParent()) {
            BasicFileAttributes attributes;
            try { attributes = Files.readAttributes(part, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
            catch (NoSuchFileException missing) { continue; }
            boolean reparse = part.getFileSystem().getSeparator().equals("\\")
                && (((Number) Files.getAttribute(part, "dos:attributes", LinkOption.NOFOLLOW_LINKS)).intValue() & 0x400) != 0;
            require(!attributes.isSymbolicLink() && !attributes.isOther() && !reparse && part.equals(part.toRealPath()),
                "Linked/reparse bootstrap path forbidden");
            require(part.equals(path) || attributes.isDirectory(), "Bootstrap path ancestor must be a directory");
        }
        return path;
    }

    /** Only a potentially incomplete document is retried; all path/size/identity checks remain strict. */
    public static JsonObject readDocument(Path path, int maxBytes, boolean incompleteMayWait) throws Exception {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= maxBytes, "Unsafe/oversized private document");
        byte[] bytes = Files.readAllBytes(path);
        require(bytes.length <= maxBytes, "Private document grew beyond bound");
        String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        try {
            var parsed = JsonParser.parseString(text);
            if (parsed.isJsonNull() && incompleteMayWait) return null;
            require(parsed.isJsonObject(), "Private document must be an object");
            return parsed.getAsJsonObject();
        } catch (JsonSyntaxException incomplete) {
            if (incompleteMayWait) return null;
            throw incomplete;
        }
    }

    public static JsonObject readPeer(Path roleDirectory, JsonObject marker, String role, boolean terminal) throws Exception {
        Path run = roleDirectory.getParent();
        Path path = safePath(run, run.resolve(role).resolve(role + (terminal ? "-report.json" : "-state.json")));
        JsonObject value = terminal ? readDocument(path, MAX_DOCUMENT_BYTES, true)
            : PlayerDataNetworkAudit.readProgressJson(path, MAX_STATE_FRAME_BYTES);
        if (value != null) verifyIdentity(marker, value, role);
        return value;
    }

    public static void writeState(Path roleDirectory, String role, JsonObject value) throws Exception {
        byte[] bytes = (COMPACT.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8);
        require(bytes.length < MAX_STATE_FRAME_BYTES, "Private state exceeded bound");
        Path path = safePath(roleDirectory, roleDirectory.resolve(role + "-state.json"));
        require(!Files.exists(path) || Files.size(path) + bytes.length <= 32L * 1024 * 1024, "Private state journal exceeded bound");
        Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
    }

    public static void appendEvent(Path roleDirectory, String role, JsonObject value) throws Exception {
        byte[] bytes = (COMPACT.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8);
        require(bytes.length <= 128 * 1024, "Private event frame exceeded bound");
        Path path = safePath(roleDirectory, roleDirectory.resolve(role + "-events.jsonl"));
        require(!Files.exists(path) || Files.size(path) + bytes.length <= 16L * 1024 * 1024, "Private event journal exceeded bound");
        Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
    }

    public static void writeTerminal(Path roleDirectory, String role, JsonObject value) throws Exception {
        byte[] bytes = PRETTY.toJson(value).getBytes(StandardCharsets.UTF_8);
        require(bytes.length < MAX_DOCUMENT_BYTES, "Private terminal exceeded bound");
        Files.write(safePath(roleDirectory, roleDirectory.resolve(role + "-report.json")), bytes,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
