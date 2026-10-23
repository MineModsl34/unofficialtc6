package thaumcraft.common.lib.research.catalog.validation;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCatalogResourceLoader;
import thaumcraft.common.lib.research.catalog.ResearchCatalogRuntime;
import thaumcraft.common.lib.research.catalog.ResearchCategoryDefinitions;
import thaumcraft.datagen.TCResearchDataProvider;

/**
 * DEFAULT OFF private real-TCP/reload acceptance recorder. Never replaces a sender, handler,
 * reload listener or publication. Its only fixture writes/mutations are inside a fresh marker-
 * guarded world and two fixed offline test profiles. No ordinary save or original JSON is read.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class ResearchCatalogNetworkAudit {
    public static final String ENABLE_PROPERTY = "thaumcraft.researchCatalogNetworkAudit";
    public static final String DIRECTORY_PROPERTY = "thaumcraft.researchCatalogNetworkAudit.dir";
    public static final String MARKER = "TC_PORT_RESEARCH_CATALOG_NETWORK_AUDIT_V1";
    public static final String A = "TC_Audit_A";
    public static final String B = "TC_Audit_B";
    public static final String WORLD = "research-catalog-network-audit";
    public static final int MAX_JSON_BYTES = 1_048_576;
    public static final int MAX_PROGRESS_FRAME_BYTES = 262_144;
    private static final String PACK_NAME = "tc_research_catalog_audit";
    private static Run active;

    private ResearchCatalogNetworkAudit() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        try {
            require(!Boolean.getBoolean(PlayerDataNetworkAudit.ENABLE_PROPERTY), "Two different network audits cannot run together");
            active = new Run(event.getServer());
        } catch (Exception failure) {
            Thaumcraft.log.error("Research-catalog network audit refused initialization", failure);
            event.getServer().halt(false);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Run run = active;
        if (run == null || run.finished || run.server != event.getServer()) return;
        try { run.step(); }
        catch (Exception failure) { run.finish("failed", failure.toString()); }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        Run run = active;
        if (run != null && !run.finished && run.server == event.getServer()) {
            run.finish("interrupted", "Server stopped before real-client/reload evidence completed");
        }
    }

    /** Production calls only AFTER the actual PacketDistributor.sendToPlayer, never GameTest capture. */
    public static void observeSent(ServerPlayer owner, CustomPacketPayload payload) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        Run run = active;
        if (run == null || run.finished || owner.getServer() != run.server) return;
        try { run.observe(owner, payload); }
        catch (Exception failure) { run.finish("failed", failure.toString()); }
    }

    /** Shared server/client guard. The preparer, not this recorder, creates role directories. */
    public static JsonObject validateDirectory(Path actualGameDirectory, String role) throws Exception {
        require(Set.of("server", "client-a", "client-b").contains(role), "Unknown audit role");
        String requested = System.getProperty(DIRECTORY_PROPERTY);
        require(requested != null && !requested.isBlank(), "Explicit private audit directory is required");
        Path directory = Path.of(requested).toAbsolutePath().normalize();
        require(directory.equals(actualGameDirectory.toAbsolutePath().normalize())
            && directory.equals(actualGameDirectory.toRealPath()), "Audit directory must equal the real game/launch directory");
        safePath(directory, directory);
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS), "Missing prepared role directory");
        Path run = directory.getParent();
        Path root = run == null ? null : run.getParent();
        require(directory.getFileName().toString().equals(role) && root != null && root.getParent() != null
            && root.getParent().getParent() != null
            && root.getFileName().toString().equals("research-catalog-network")
            && root.getParent().getFileName().toString().equals("build")
            && root.getParent().getParent().getFileName().toString().equals("port")
            && run.getFileName().toString().matches("[A-Za-z0-9_-]{1,80}"),
            "Expected port/build/research-catalog-network/<newRunId>/<role>");
        JsonObject marker = readDocument(safePath(directory, directory.resolve(".research-catalog-network-audit.json")), 4096);
        require(marker != null && marker.get("schemaVersion").getAsInt() == 1
            && MARKER.equals(marker.get("marker").getAsString()) && role.equals(marker.get("role").getAsString())
            && run.getFileName().toString().equals(marker.get("runId").getAsString())
            && (!marker.has("phase") || "generate".equals(marker.get("phase").getAsString())), "Private isolation marker mismatch");
        UUID.fromString(marker.get("token").getAsString());
        int port = marker.get("port").getAsInt();
        require(port >= 1024 && port <= 65535, "Invalid private TCP port");
        for (String file : List.of(role.equals("server") ? "server-report.json" : "client-report.json",
            role.equals("server") ? "server-state.json" : "client-state.json")) {
            Path path = safePath(directory, directory.resolve(file));
            require(!Files.exists(path, LinkOption.NOFOLLOW_LINKS), "Use a fresh role directory: " + file);
        }
        return marker;
    }

    public static JsonObject identity(JsonObject marker) {
        JsonObject result = new JsonObject();
        for (String key : List.of("schemaVersion", "marker", "runId", "token", "role", "port")) result.add(key, marker.get(key).deepCopy());
        result.addProperty("phase", "generate");
        return result;
    }

    /** Returns the exact checked path subsequently used for I/O, never an unchecked alias. */
    public static Path safePath(Path privateRoot, Path target) throws Exception {
        Path root = privateRoot.toAbsolutePath().normalize();
        Path path = target.toAbsolutePath().normalize();
        require(path.startsWith(root), "Audit I/O escaped its private root: " + path);
        for (Path part = path; part != null; part = part.getParent()) {
            BasicFileAttributes attributes;
            try { attributes = Files.readAttributes(part, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
            catch (NoSuchFileException missing) { continue; }
            boolean reparse = false;
            if (part.getFileSystem().getSeparator().equals("\\")) {
                Object raw = Files.getAttribute(part, "dos:attributes", LinkOption.NOFOLLOW_LINKS);
                require(raw instanceof Number, "Windows raw DOS attributes are required");
                reparse = (((Number) raw).intValue() & 0x400) != 0;
            }
            require(!attributes.isSymbolicLink() && !attributes.isOther() && !reparse
                && part.equals(part.toRealPath()), "Linked/reparse audit I/O path: " + part);
            require(part.equals(path) || attributes.isDirectory(), "Audit path ancestor is not a directory");
        }
        return path;
    }

    public static void writeJson(Path roleDirectory, String name, JsonObject value, boolean terminal) throws Exception {
        int bytes = new Gson().toJson(value).getBytes(StandardCharsets.UTF_8).length;
        require(bytes <= (terminal ? MAX_JSON_BYTES : MAX_PROGRESS_FRAME_BYTES - 1), "Unbounded audit JSON");
        PlayerDataNetworkAudit.writeJson(safePath(roleDirectory, roleDirectory.resolve(name)), value, terminal);
    }

    public static JsonObject readProgress(Path runDirectory, String role, String name) throws Exception {
        Path path = safePath(runDirectory, runDirectory.resolve(role).resolve(name));
        return PlayerDataNetworkAudit.readProgressJson(path, MAX_PROGRESS_FRAME_BYTES);
    }

    public static JsonObject readDocument(Path path, int maxBytes) throws Exception {
        return readDocument(path, maxBytes, false);
    }

    /** CREATE_NEW exposes a final file before its single writer finishes; callers retain their deadline. */
    public static JsonObject readTerminalDocument(Path path, int maxBytes) throws Exception {
        return readDocument(path, maxBytes, true);
    }

    private static JsonObject readDocument(Path path, int maxBytes, boolean waitForCompleteJson) throws Exception {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= maxBytes, "Unsafe/unbounded private JSON");
        byte[] bytes = Files.readAllBytes(path);
        require(bytes.length <= maxBytes, "Private JSON grew beyond bound");
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        try {
            var value = JsonParser.parseString(text);
            if (waitForCompleteJson && value.isJsonNull()) return null;
            return value.getAsJsonObject();
        } catch (com.google.gson.JsonSyntaxException incomplete) {
            if (waitForCompleteJson) return null;
            throw incomplete;
        }
    }

    public static void verifyIdentity(JsonObject marker, JsonObject value, String role) {
        require(value != null && value.get("schemaVersion").getAsInt() == 1 && MARKER.equals(value.get("marker").getAsString())
            && role.equals(value.get("role").getAsString()) && marker.get("runId").equals(value.get("runId"))
            && marker.get("token").equals(value.get("token")) && marker.get("port").equals(value.get("port")), "Peer audit identity mismatch");
    }

    /** Transport-only evidence; no raw documents or owner attachment NBT are put into catalog rows. */
    public static JsonObject catalogMetadata(PacketSyncResearchCatalog packet) {
        Map<String, String> raw = new LinkedHashMap<>();
        packet.sources().forEach((id, text) -> raw.put(id.toString(), text));
        var spec = ResearchCatalogParser.parse(raw, ResearchCategoryDefinitions.newBuiltinCategories().keySet());
        JsonObject value = new JsonObject();
        value.addProperty("typeId", packet.type().id().toString());
        value.addProperty("digest", packet.digest());
        value.addProperty("protocolVersion", packet.protocolVersion());
        value.addProperty("encodedBytes", packet.encodedBytes());
        value.addProperty("sourceCount", packet.sources().size());
        value.addProperty("sourceByteCount", packet.sources().values().stream().mapToInt(text -> text.getBytes(StandardCharsets.UTF_8).length).sum());
        value.addProperty("entryCount", spec.entries().size());
        value.addProperty("stageCount", spec.entries().stream().mapToInt(entry -> entry.stages().values().size()).sum());
        JsonArray ids = new JsonArray();
        packet.sources().keySet().forEach(id -> ids.add(id.toString()));
        value.add("orderedIds", ids);
        value.addProperty("ownerAttachmentFieldsInCatalogWire", 0);
        return value;
    }

    /** Canonical representation of installed public presentation metadata, not registry resolution. */
    public static JsonObject installedMetadata() {
        JsonObject result = new JsonObject();
        JsonArray categories = new JsonArray();
        JsonObject entries = new JsonObject();
        int stages = 0;
        for (var category : ResearchCategories.researchCategories.values()) {
            JsonObject cat = new JsonObject();
            cat.addProperty("key", category.key);
            cat.addProperty("researchKey", category.researchKey);
            cat.addProperty("minColumn", category.minDisplayColumn);
            cat.addProperty("maxColumn", category.maxDisplayColumn);
            cat.addProperty("minRow", category.minDisplayRow);
            cat.addProperty("maxRow", category.maxDisplayRow);
            categories.add(cat);
            for (ResearchEntry entry : new TreeMap<>(category.research).values()) {
                JsonObject row = new JsonObject();
                row.addProperty("name", entry.getName());
                row.addProperty("category", entry.getCategory());
                row.addProperty("column", entry.getDisplayColumn());
                row.addProperty("row", entry.getDisplayRow());
                row.add("parents", new Gson().toJsonTree(entry.getParents()));
                row.add("siblings", new Gson().toJsonTree(entry.getSiblings()));
                row.add("meta", new Gson().toJsonTree(entry.getMeta()));
                JsonArray stageRows = new JsonArray();
                if (entry.getStages() != null) for (var stage : entry.getStages()) {
                    JsonObject stageRow = new JsonObject();
                    stageRow.addProperty("text", stage.getText());
                    stageRow.addProperty("warp", stage.getWarp());
                    stageRow.add("recipes", new Gson().toJsonTree(stage.getRecipes() == null ? null
                        : java.util.Arrays.stream(stage.getRecipes()).map(Object::toString).toArray(String[]::new)));
                    stageRow.add("research", new Gson().toJsonTree(stage.getResearch()));
                    stageRow.add("researchIcon", new Gson().toJsonTree(stage.getResearchIcon()));
                    stageRows.add(stageRow);
                    stages++;
                }
                row.add("stages", entry.getStages() == null ? com.google.gson.JsonNull.INSTANCE : stageRows);
                entries.add(entry.getKey(), row);
            }
        }
        result.add("categories", categories);
        result.add("entries", entries);
        result.addProperty("entryCount", entries.size());
        result.addProperty("stageCount", stages);
        return result;
    }

    public static JsonObject ownerState(ServerPlayer player) {
        JsonObject result = new JsonObject();
        result.addProperty("uuid", player.getUUID().toString());
        result.addProperty("knowledgeNbt", ThaumcraftCapabilities.getKnowledge(player).serializeNBT().toString());
        result.addProperty("warpNbt", ThaumcraftCapabilities.getWarp(player).serializeNBT().toString());
        return result;
    }

    /** Preserves ALL keys/values; only storage's unordered research/knowledge lists are sorted. */
    public static CompoundTag normalizedKnowledge(CompoundTag data) {
        CompoundTag result = data.copy();
        for (String name : List.of("research", "knowledge")) {
            if (!result.contains(name, Tag.TAG_LIST)) continue;
            ListTag original = (ListTag) result.get(name);
            if (!original.isEmpty() && original.getElementType() != Tag.TAG_COMPOUND) continue;
            List<CompoundTag> rows = new ArrayList<>();
            for (Tag tag : result.getList(name, Tag.TAG_COMPOUND)) {
                CompoundTag row = ((CompoundTag) tag).copy();
                if (row.contains("flags", Tag.TAG_STRING)) {
                    String[] flags = row.getString("flags").split(",", -1);
                    java.util.Arrays.sort(flags);
                    row.putString("flags", String.join(",", flags));
                }
                rows.add(row);
            }
            rows.sort(java.util.Comparator.comparing(row -> row.getString("key")));
            ListTag sorted = new ListTag();
            rows.forEach(sorted::add);
            result.put(name, sorted);
        }
        return result;
    }

    public static boolean sameOwnerState(JsonObject expected, JsonObject observed) throws Exception {
        return expected.get("uuid").equals(observed.get("uuid"))
            && normalizedKnowledge(TagParser.parseTag(expected.get("knowledgeNbt").getAsString()))
                .equals(normalizedKnowledge(TagParser.parseTag(observed.get("knowledgeNbt").getAsString())))
            && TagParser.parseTag(expected.get("warpNbt").getAsString()).equals(TagParser.parseTag(observed.get("warpNbt").getAsString()));
    }

    public static UUID offlineUuid(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)); }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Run {
        private final MinecraftServer server;
        private final Path directory;
        private final Path world;
        private final JsonObject marker;
        private final JsonObject report;
        private final JsonArray trace = new JsonArray();
        private final JsonArray checks = new JsonArray();
        private final PacketSyncResearchCatalog canonical;
        private final net.minecraft.resources.ResourceLocation firstSource;
        private final net.minecraft.resources.ResourceLocation lastSource;
        private final String entryKey;
        private final String canonicalName;
        private final String changedName;
        private final Path fixture;
        private final Set<Path> writtenFixtureFiles = new HashSet<>();
        private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(900);
        private long phaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(300);
        private String phase = "login";
        private int phaseTick;
        private int revision;
        private CompletableFuture<Void> reloadFuture;
        private JsonObject baselineOwners;
        private JsonObject acceptedMetadata;
        private Map<String, thaumcraft.api.research.ResearchCategory> acceptedMap;
        private String acceptedDigest;
        private int invalidSendCount;
        private int invalidClientACount;
        private int invalidClientBCount;
        private int baselineKnowledgeSends;
        private int baselineWarpSends;
        private boolean finished;

        private Run(MinecraftServer server) throws Exception {
            this.server = server;
            directory = Path.of(System.getProperty("user.dir")).toRealPath();
            marker = validateDirectory(directory, "server");
            require(server.isDedicatedServer() && "127.0.0.1".equals(server.getLocalIp())
                && !server.usesAuthentication() && server.getPort() == marker.get("port").getAsInt(), "Exact offline loopback dedicated server required");
            world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            safePath(directory, world);
            require(world.getParent().equals(directory) && world.getFileName().toString().equals(WORLD), "Only the prepared new audit world is allowed");
            Path players = safePath(directory, world.resolve("playerdata"));
            if (Files.isDirectory(players)) try (var existing = Files.list(players)) {
                require(existing.noneMatch(path -> path.getFileName().toString().endsWith(".dat")), "Existing player saves are forbidden");
            }
            require(server.getPlayerCount() == 0, "No connected player may predate the audit");
            canonical = ResearchCatalogRuntime.currentPayload(server);
            require(canonical != null, "Production startup must accept a catalog before the audit starts");
            JsonObject summary = catalogMetadata(canonical);
            require(summary.get("sourceCount").getAsInt() == 8 && summary.get("entryCount").getAsInt() == 148
                && summary.get("stageCount").getAsInt() == 271, "Unexpected default catalog dimensions");
            require(canonical.sources().size() == TCResearchDataProvider.CANONICAL_DEFAULTS.size(), "Exactly eight canonical source registrations required");
            for (var expected : TCResearchDataProvider.CANONICAL_DEFAULTS) {
                String raw = canonical.sources().get(expected.registration());
                require(raw != null, "Missing canonical runtime source " + expected.registration());
                byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
                require(bytes.length == expected.byteCount() && Hashing.sha256().hashBytes(bytes).toString().equals(expected.sha256()), "Runtime defaults differ from executed source manifest");
            }
            firstSource = canonical.sources().keySet().iterator().next();
            lastSource = new ArrayList<>(canonical.sources().keySet()).getLast();
            JsonArray firstEntries = JsonParser.parseString(canonical.sources().get(firstSource)).getAsJsonObject().getAsJsonArray("entries");
            require(firstEntries != null && !firstEntries.isEmpty() && !firstSource.equals(lastSource), "First registered source must contain a real entry");
            JsonObject entry = firstEntries.get(0).getAsJsonObject();
            entryKey = entry.get("key").getAsString();
            canonicalName = entry.get("name").getAsString();
            changedName = canonicalName + " [PRIVATE TCP CATALOG AUDIT]";
            require(ResearchCategories.getResearch(entryKey) != null && canonicalName.equals(ResearchCategories.getResearch(entryKey).getName()), "Installed initial metadata differs from transport");
            fixture = safePath(directory, world.resolve("datapacks").resolve(PACK_NAME));
            require(!Files.exists(fixture, LinkOption.NOFOLLOW_LINKS), "Fixture datapack must not already exist");
            report = identity(marker);
            report.addProperty("startedUtc", Instant.now().toString());
            report.addProperty("entryKey", entryKey);
            report.addProperty("canonicalName", canonicalName);
            report.addProperty("changedName", changedName);
            report.add("canonicalCatalog", summary);
            report.add("actualProductionSendTrace", trace);
            report.add("checks", checks);
            report.addProperty("coversProgressionOrRegistryResolution", false);
            phaseTick = server.getTickCount();
            publish("running");
            Thaumcraft.log.info("Research-catalog TCP audit waiting for {} and {} on localhost:{}", A, B, server.getPort());
        }

        private void observe(ServerPlayer owner, CustomPacketPayload payload) throws Exception {
            String kind = payload instanceof PacketSyncResearchCatalog ? "catalog"
                : payload instanceof PacketSyncKnowledge ? "knowledge" : payload instanceof PacketSyncWarp ? "warp" : null;
            if (kind == null) return;
            String name = owner.getGameProfile().getName();
            require((A.equals(name) || B.equals(name)) && offlineUuid(name).equals(owner.getUUID()), "Unexpected private owner/UUID");
            require(owner.connection.getConnection().getRemoteAddress() instanceof InetSocketAddress address
                && address.getAddress() != null && address.getAddress().isLoopbackAddress(), "Actual loopback TCP owner required");
            if (kind.equals("knowledge")) require(sentCount(name, "catalog") > 0, "Production knowledge send preceded catalog for " + name);
            JsonObject row = new JsonObject();
            row.addProperty("sequence", trace.size() + 1);
            row.addProperty("serverTick", server.getTickCount());
            row.addProperty("ownerTick", owner.tickCount);
            row.addProperty("auditPhase", phase);
            row.addProperty("owner", name);
            row.addProperty("uuid", owner.getUUID().toString());
            row.addProperty("kind", kind);
            row.addProperty("typeId", payload.type().id().toString());
            if (payload instanceof PacketSyncResearchCatalog catalog) row.add("catalog", catalogMetadata(catalog));
            else row.addProperty("payloadNbt", payload instanceof PacketSyncKnowledge knowledge ? knowledge.data().toString() : ((PacketSyncWarp) payload).data().toString());
            trace.add(row);
            require(trace.size() <= 64, "Unbounded production packet transcript");
        }

        private void step() throws Exception {
            require(System.nanoTime() <= deadline && System.nanoTime() <= phaseDeadline, "Bounded TCP audit timeout in " + phase);
            if (server.getTickCount() % 5 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) require(A.equals(player.getGameProfile().getName()) || B.equals(player.getGameProfile().getName()), "Unexpected player in private audit");
            JsonObject clientA = client("client-a", false);
            JsonObject clientB = client("client-b", false);
            require(clientA == null || !clientA.has("failure"), "Client A reported failure");
            require(clientB == null || !clientB.has("failure"), "Client B reported failure");
            if (phase.equals("finish")) {
                JsonObject finalA = client("client-a", true);
                JsonObject finalB = client("client-b", true);
                if (finalA == null || finalB == null) return;
                require("completed".equals(finalA.get("status").getAsString()) && "completed".equals(finalB.get("status").getAsString()), "Both actual clients must report completed");
                compareTranscript(finalA, A);
                compareTranscript(finalB, B);
                require(sameOwnerState(baselineOwners.getAsJsonObject(A), finalA) && sameOwnerState(baselineOwners.getAsJsonObject(B), finalB), "Terminal client full owner NBT differs from baseline");
                ServerPlayer finalOwnerA = server.getPlayerList().getPlayerByName(A);
                ServerPlayer finalOwnerB = server.getPlayerList().getPlayerByName(B);
                require(finalOwnerA != null && finalOwnerB != null, "Both actual owners must remain connected until server terminal");
                assertOwnerDataUnchanged(finalOwnerA, finalOwnerB);
                report.add("clientA", finalA);
                report.add("clientB", finalB);
                check("real-tcp-transcripts", "All ordered production catalog/knowledge/warp sends equal actual applied client transcripts");
                finish("completed", null);
                return;
            }
            ServerPlayer a = server.getPlayerList().getPlayerByName(A);
            ServerPlayer b = server.getPlayerList().getPlayerByName(B);
            if (a == null || b == null || clientA == null || clientB == null) return;
            require(offlineUuid(A).equals(a.getUUID()) && offlineUuid(B).equals(b.getUUID()), "Expected distinct offline profiles");
            if (baselineOwners != null) assertOwnerDataUnchanged(a, b);
            switch (phase) {
                case "login" -> {
                    if (!connected(clientA, a) || !connected(clientB, b) || count(clientA, "catalog") != 1 || count(clientB, "catalog") != 1
                        || count(clientA, "knowledge") < 1 || count(clientB, "knowledge") < 1 || count(clientA, "warp") < 1 || count(clientB, "warp") < 1) return;
                    require(clientA.get("activeDigest").getAsString().equals(canonical.digest()) && clientB.get("activeDigest").getAsString().equals(canonical.digest()), "Initial real clients lack default catalog");
                    assertCountsAndName(canonicalName);
                    check("catalog-before-knowledge", "Both actual clients applied default8/148/271 before their first knowledge handler");
                    advance("seed");
                    seed(a, "A", 11);
                    seed(b, "B", 71);
                }
                case "seed" -> {
                    if (server.getTickCount() - phaseTick < 40 || PlayerDataSync.isKnowledgeDirty(a) || PlayerDataSync.isKnowledgeDirty(b)
                        || !sameOwnerState(ownerState(a), clientA) || !sameOwnerState(ownerState(b), clientB)) return;
                    for (String owner : List.of(A, B)) {
                        require(sentCount(owner, "knowledge") == 2 && sentCount(owner, "warp") == 2,
                            "Expected one initial and one private sentinel snapshot per owner");
                        boolean dirty20 = false;
                        for (var item : trace) {
                            JsonObject row = item.getAsJsonObject();
                            if (owner.equals(row.get("owner").getAsString()) && "knowledge".equals(row.get("kind").getAsString())
                                && "seed".equals(row.get("auditPhase").getAsString())) {
                                dirty20 = row.get("ownerTick").getAsInt() % 20 == 0;
                            }
                        }
                        require(dirty20, "Private sentinel knowledge did not use the production dirty20 flush");
                    }
                    require(count(clientA, "knowledge") == 2 && count(clientB, "knowledge") == 2
                        && count(clientA, "warp") == 2 && count(clientB, "warp") == 2,
                        "Actual seeded owner handlers do not match production counts");
                    baselineKnowledgeSends = totalSent("knowledge");
                    baselineWarpSends = totalSent("warp");
                    assertPrivacy(clientA, "B");
                    assertPrivacy(clientB, "A");
                    baselineOwners = new JsonObject();
                    baselineOwners.add(A, ownerState(a));
                    baselineOwners.add(B, ownerState(b));
                    report.add("baselineFullOwnerNbt", baselineOwners.deepCopy());
                    check("distinct-owner-sentinels", "Additive opaque research plus permanent/normal warp went through actual dirty20/warp sends; no clear/reseed of existing data");
                    createFixture();
                    JsonObject changed = JsonParser.parseString(canonical.sources().get(firstSource)).getAsJsonObject();
                    changed.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("name", changedName);
                    writeFixtureSource(firstSource, changed.toString());
                    beginReload("valid-reload");
                }
                case "valid-reload" -> {
                    if (!reloadDone(false)) return;
                    PacketSyncResearchCatalog payload = current();
                    require(!canonical.digest().equals(payload.digest()), "Valid override did not change accepted digest");
                    assertCountsAndName(changedName);
                    if (!clientsHaveCatalog(clientA, clientB, payload.digest(), 2, changedName)) return;
                    acceptedDigest = payload.digest();
                    acceptedMap = ResearchCategories.researchCategories;
                    acceptedMetadata = installedMetadata();
                    report.add("validAcceptedCatalog", catalogMetadata(payload));
                    check("actual-valid-reload", "Engine reload future succeeded; changed real entry name is installed and applied by both TCP clients");
                    invalidSendCount = totalSent("catalog");
                    invalidClientACount = count(clientA, "catalog");
                    invalidClientBCount = count(clientB, "catalog");
                    writeFixtureSource(lastSource, "{\"entries\":[");
                    beginReload("invalid-reload");
                }
                case "invalid-reload" -> {
                    if (!reloadDone(true)) return;
                    assertRejectedReload(clientA, clientB);
                    advance("invalid-quiet");
                }
                case "invalid-quiet" -> {
                    assertRejectedReload(clientA, clientB);
                    if (server.getTickCount() - phaseTick < 40) return;
                    check("actual-invalid-reload-rollback", "Late registered source malformed JSON caused real reload failure; map identity, metadata, digest, full owner NBT and client packet counts stayed unchanged");
                    writeFixtureSource(firstSource, canonical.sources().get(firstSource));
                    writeFixtureSource(lastSource, canonical.sources().get(lastSource));
                    beginReload("recovery-reload");
                }
                case "recovery-reload" -> {
                    if (!reloadDone(false)) return;
                    require(current().digest().equals(canonical.digest()), "Canonical recovery changed exact ordered source bytes");
                    assertCountsAndName(canonicalName);
                    if (!clientsHaveCatalog(clientA, clientB, canonical.digest(), 3, canonicalName)) return;
                    assertPrivacy(clientA, "B");
                    assertPrivacy(clientB, "A");
                    check("actual-canonical-recovery", "Successful real reload recovered exact baseline digest and name; both authoritative TCP deliveries observed");
                    report.add("expectedSavedPlayers", baselineOwners.deepCopy());
                    server.getPlayerList().saveAll();
                    require(server.saveEverything(true, true, true), "Private world flush failed");
                    report.addProperty("saveFlushedBeforeClientReports", true);
                    advance("finish");
                }
                default -> throw new IllegalStateException("Unknown audit phase " + phase);
            }
        }

        private void seed(ServerPlayer player, String role, int warp) {
            String sentinel = sentinel(role);
            require(ResearchCategories.getResearch(sentinel) == null, "Sentinel must not grant a real research definition");
            ThaumcraftCapabilities.getKnowledge(player).addResearch(sentinel);
            PlayerDataSync.markKnowledgeDirty(player);
            ThaumcraftCapabilities.getWarp(player).set(EnumWarpType.PERMANENT, warp);
            ThaumcraftCapabilities.getWarp(player).set(EnumWarpType.NORMAL, warp + 1);
            PlayerDataSync.syncWarpNow(player);
        }

        private String sentinel(String role) { return "[#]TC_CATALOG_NETWORK_AUDIT_" + marker.get("token").getAsString() + "_" + role; }

        private void assertPrivacy(JsonObject client, String foreignRole) {
            require(!client.get("knowledgeNbt").getAsString().contains(sentinel(foreignRole)), "Client contains foreign owner research sentinel");
            for (var item : client.getAsJsonArray("packets")) {
                JsonObject row = item.getAsJsonObject();
                if (row.has("payloadNbt")) require(!row.get("payloadNbt").getAsString().contains(sentinel(foreignRole)), "Wire transcript contains foreign owner sentinel");
            }
        }

        private void assertOwnerDataUnchanged(ServerPlayer a, ServerPlayer b) throws Exception {
            require(totalSent("knowledge") == baselineKnowledgeSends && totalSent("warp") == baselineWarpSends,
                "Metadata reload emitted an unexpected owner knowledge/warp snapshot");
            for (ServerPlayer player : List.of(a, b)) {
                JsonObject expected = baselineOwners.getAsJsonObject(player.getGameProfile().getName());
                JsonObject actual = ownerState(player);
                require(expected.get("uuid").equals(actual.get("uuid"))
                    && TagParser.parseTag(expected.get("knowledgeNbt").getAsString()).equals(TagParser.parseTag(actual.get("knowledgeNbt").getAsString()))
                    && TagParser.parseTag(expected.get("warpNbt").getAsString()).equals(TagParser.parseTag(actual.get("warpNbt").getAsString())), "Reload changed full server owner NBT");
            }
        }

        private void assertCountsAndName(String name) {
            JsonObject metadata = installedMetadata();
            require(metadata.get("entryCount").getAsInt() == 148 && metadata.get("stageCount").getAsInt() == 271
                && ResearchCategories.getResearch(entryKey) != null && name.equals(ResearchCategories.getResearch(entryKey).getName()), "Installed catalog count/name mismatch");
        }

        private void assertRejectedReload(JsonObject a, JsonObject b) {
            require(ResearchCategories.researchCategories == acceptedMap && installedMetadata().equals(acceptedMetadata)
                && current().digest().equals(acceptedDigest), "Failed reload replaced/mutated the accepted public catalog");
            require(totalSent("catalog") == invalidSendCount && count(a, "catalog") == invalidClientACount && count(b, "catalog") == invalidClientBCount,
                "Failed reload emitted/applied a new catalog packet");
            require(clientsHaveCatalog(a, b, acceptedDigest, 2, changedName), "Client lost accepted metadata after invalid reload");
            report.addProperty("invalidGlobalMapIdentityRetained", true);
            report.addProperty("invalidInstalledMetadataSha256", Hashing.sha256().hashString(acceptedMetadata.toString(), StandardCharsets.UTF_8).toString());
        }

        private boolean clientsHaveCatalog(JsonObject a, JsonObject b, String digest, int expectedCount, String name) {
            for (JsonObject value : List.of(a, b)) {
                if (count(value, "catalog") < expectedCount || !value.has("activeDigest") || !digest.equals(value.get("activeDigest").getAsString())) return false;
                require(count(value, "catalog") == expectedCount && value.get("entryCount").getAsInt() == 148
                    && value.get("stageCount").getAsInt() == 271 && name.equals(value.get("installedName").getAsString()), "Unexpected duplicate/client metadata catalog snapshot");
            }
            require(sentCount(A, "catalog") == expectedCount && sentCount(B, "catalog") == expectedCount, "Expected exactly one authoritative catalog per accepted generation/owner");
            return true;
        }

        private PacketSyncResearchCatalog current() {
            var payload = ResearchCatalogRuntime.currentPayload(server);
            require(payload != null, "Authoritative catalog unexpectedly disappeared");
            return payload;
        }

        private void createFixture() throws Exception {
            Path path = safePath(directory, fixture.resolve("pack.mcmeta"));
            Files.createDirectories(safePath(directory, fixture));
            safePath(directory, path);
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
            pack.addProperty("description", "Private generated TC catalog TCP reload fixture; not mod defaults");
            JsonObject document = new JsonObject();
            document.add("pack", pack);
            Files.writeString(path, document.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            writtenFixtureFiles.add(path);
        }

        private void writeFixtureSource(net.minecraft.resources.ResourceLocation registration, String raw) throws Exception {
            require(canonical.sources().containsKey(registration), "Fixture may override only one of the actual eight default sources");
            var physical = ResearchCatalogResourceLoader.physicalLocation(registration);
            Path path = safePath(directory, fixture.resolve("data").resolve(physical.getNamespace()).resolve(physical.getPath()));
            byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
            require(bytes.length <= ResearchCatalogResourceLoader.MAX_UTF8_BYTES, "Unbounded fixture source");
            Files.createDirectories(safePath(directory, path.getParent()));
            safePath(directory, path);
            if (writtenFixtureFiles.contains(path)) {
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Own fixture source disappeared");
                Files.write(path, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
                writtenFixtureFiles.add(path);
            }
        }

        private void beginReload(String next) throws Exception {
            advance(next);
            server.getPackRepository().reload();
            String id = "file/" + PACK_NAME;
            require(server.getPackRepository().isAvailable(id), "Engine did not discover the real fixture datapack");
            List<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
            if (!selected.contains(id)) selected.add(id);
            report.addProperty("actualFixturePackId", id);
            // MinecraftServer.reloadResources invokes managedBlock on the server thread. Calling
            // the REAL API from a worker returns its future, while its resource/accept tasks still
            // run on the engine executors. Main-thread polling retains our strict phase timeout.
            reloadFuture = CompletableFuture.supplyAsync(() -> server.reloadResources(List.copyOf(selected))).thenCompose(value -> value);
        }

        private boolean reloadDone(boolean mustFail) {
            if (!reloadFuture.isDone()) return false;
            try {
                reloadFuture.join();
                require(!mustFail, "Malformed late source was incorrectly accepted by real reload");
            } catch (CompletionException rejected) {
                require(mustFail, "Valid/recovery engine reload failed: " + rejected);
                report.addProperty("actualInvalidReloadException", rejected.toString());
            }
            return true;
        }

        private boolean connected(JsonObject state, ServerPlayer player) {
            return state.has("uuid") && state.get("uuid").getAsString().equals(player.getUUID().toString())
                && state.has("remoteLoopback") && state.get("remoteLoopback").getAsBoolean();
        }

        private JsonObject client(String role, boolean terminal) throws Exception {
            JsonObject value = terminal ? readTerminalDocument(safePath(directory.getParent(), directory.getParent().resolve(role).resolve("client-report.json")), MAX_JSON_BYTES)
                : readProgress(directory.getParent(), role, "client-state.json");
            if (value != null) verifyIdentity(marker, value, role);
            return value;
        }

        private int count(JsonObject state, String kind) { return state.get(kind + "Handlers").getAsInt(); }
        private int sentCount(String owner, String kind) {
            int count = 0;
            for (var item : trace) { JsonObject row = item.getAsJsonObject(); if (owner.equals(row.get("owner").getAsString()) && kind.equals(row.get("kind").getAsString())) count++; }
            return count;
        }
        private int totalSent(String kind) { return sentCount(A, kind) + sentCount(B, kind); }

        private void compareTranscript(JsonObject client, String owner) throws Exception {
            JsonArray received = client.getAsJsonArray("packets");
            int index = 0;
            int catalogs = 0;
            for (var item : trace) {
                JsonObject sent = item.getAsJsonObject();
                if (!owner.equals(sent.get("owner").getAsString())) continue;
                require(index < received.size(), "Missing actual client payload for " + owner);
                JsonObject actual = received.get(index++).getAsJsonObject();
                String kind = sent.get("kind").getAsString();
                require(sent.get("kind").equals(actual.get("kind")) && sent.get("uuid").equals(actual.get("uuid"))
                    && sent.get("typeId").equals(actual.get("typeId")), "Per-owner actual packet order/identity mismatch");
                if (kind.equals("catalog")) {
                    require(sent.get("catalog").equals(actual.get("catalog")), "Actual TCP catalog digest/order/bytes differ from production send");
                    catalogs++;
                } else require(TagParser.parseTag(sent.get("payloadNbt").getAsString()).equals(TagParser.parseTag(actual.get("payloadNbt").getAsString())), "Actual TCP owner snapshot differs from production packet");
            }
            require(index == received.size() && catalogs == 3 && count(client, "catalog") == 3, "Unaccounted/duplicate actual client payload");
            require(count(client, "knowledge") == 2 && count(client, "warp") == 2,
                "Reload must not resynchronize or reapply player knowledge/warp");
            require(client.get("catalogBeforeFirstKnowledge").getAsBoolean(), "Actual catalog did not precede knowledge application");
        }

        private void check(String name, String detail) {
            JsonObject value = new JsonObject();
            value.addProperty("name", name);
            value.addProperty("detail", detail);
            value.addProperty("serverTick", server.getTickCount());
            value.addProperty("digest", current().digest());
            checks.add(value);
            Thaumcraft.log.info("Research-catalog TCP audit passed {}", name);
        }

        private void advance(String next) throws Exception {
            phase = next;
            phaseTick = server.getTickCount();
            phaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
            revision++;
            publish("running");
            Thaumcraft.log.info("Research-catalog TCP audit phase {}", phase);
        }

        private void publish(String status) throws Exception {
            JsonObject value = identity(marker);
            value.addProperty("status", status);
            value.addProperty("auditPhase", phase);
            value.addProperty("revision", revision);
            value.addProperty("entryKey", entryKey);
            value.addProperty("expectedCatalogHandlersAtFinish", 3);
            value.addProperty("canonicalDigest", canonical.digest());
            value.addProperty("canonicalName", canonicalName);
            writeJson(directory, "server-state.json", value, false);
        }

        private void finish(String status, String detail) {
            if (finished) return;
            finished = true;
            active = null;
            try {
                try {
                    server.getPlayerList().saveAll();
                    report.addProperty("saveFlushed", server.saveEverything(true, true, true));
                    if ("completed".equals(status)) {
                        require(report.get("saveFlushed").getAsBoolean(), "Final private world save did not flush");
                        verifySavedPlayers();
                    }
                } catch (Exception saveFailure) {
                    status = "failed";
                    detail = (detail == null ? "" : detail + "; ") + "Final private save/disk verification failed: " + saveFailure;
                    Thaumcraft.log.error("Private research-catalog audit save verification failed", saveFailure);
                }
                try {
                    publish(status);
                } catch (Exception progressFailure) {
                    status = "failed";
                    detail = (detail == null ? "" : detail + "; ") + "Terminal progress publish failed: " + progressFailure;
                    Thaumcraft.log.error("Unable to publish terminal catalog-audit progress", progressFailure);
                }
                report.addProperty("status", status);
                report.addProperty("auditPhase", phase);
                report.addProperty("finishedUtc", Instant.now().toString());
                if (detail != null) report.addProperty("failure", detail);
                // CREATE_NEW is attempted independently even after save/progress failure. A failed
                // terminal journal can never leave a sealed falsely completed server report.
                writeJson(directory, "server-report.json", report, true);
                Thaumcraft.log.info("Research-catalog TCP audit {}: {}", status, detail);
            } catch (Exception failure) {
                Thaumcraft.log.error("Unable to finish private research-catalog TCP audit", failure);
                try { publish("failed"); } catch (Exception publishFailure) { Thaumcraft.log.error("Unable to publish terminal audit failure", publishFailure); }
            } finally { server.halt(false); }
        }

        private void verifySavedPlayers() throws Exception {
            JsonObject verified = new JsonObject();
            for (String name : List.of(A, B)) {
                ServerPlayer live = server.getPlayerList().getPlayerByName(name);
                require(live != null && offlineUuid(name).equals(live.getUUID()), "Saved owner must remain the expected connected profile");
                Path file = safePath(directory, world.resolve("playerdata").resolve(live.getUUID() + ".dat"));
                require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) > 0 && Files.size(file) <= 1_048_576,
                    "Missing/unsafe/unbounded actual private player save");
                CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.create(4L * 1024 * 1024));
                require(saved.contains("UUID", Tag.TAG_INT_ARRAY) && saved.getIntArray("UUID").length == 4
                    && saved.getUUID("UUID").equals(live.getUUID()), "Actual saved UUID differs from live owner");
                require(saved.contains("neoforge:attachments", Tag.TAG_COMPOUND), "Actual disk player lacks attachment compound");
                CompoundTag attachments = saved.getCompound("neoforge:attachments");
                require(attachments.contains("thaumcraft:knowledge", Tag.TAG_COMPOUND) && attachments.contains("thaumcraft:warp", Tag.TAG_COMPOUND),
                    "Actual disk player lacks complete knowledge/warp attachment tags");
                JsonObject baseline = baselineOwners.getAsJsonObject(name);
                CompoundTag knowledge = attachments.getCompound("thaumcraft:knowledge");
                CompoundTag warp = attachments.getCompound("thaumcraft:warp");
                require(knowledge.equals(TagParser.parseTag(baseline.get("knowledgeNbt").getAsString()))
                    && knowledge.equals(ThaumcraftCapabilities.getKnowledge(live).serializeNBT())
                    && warp.equals(TagParser.parseTag(baseline.get("warpNbt").getAsString()))
                    && warp.equals(ThaumcraftCapabilities.getWarp(live).serializeNBT()), "Actual saved full attachment NBT differs from baseline/live owner");
                JsonObject row = new JsonObject();
                row.addProperty("uuid", live.getUUID().toString());
                row.addProperty("compressedBytes", Files.size(file));
                row.addProperty("knowledgeNbt", knowledge.toString());
                row.addProperty("warpNbt", warp.toString());
                verified.add(name, row);
            }
            report.add("actualDiskPlayerAttachments", verified);
            report.addProperty("actualDiskPlayerNbtVerified", true);
        }
    }
}
