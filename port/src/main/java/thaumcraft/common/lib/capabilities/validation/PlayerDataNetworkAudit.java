package thaumcraft.common.lib.capabilities.validation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;

/**
 * Opt-in real TCP/lifecycle audit, never a packet sender substitute. It mutates only two fixed
 * players in a marker-guarded private world. Client evidence is read from separately written
 * local reports, not trusted C2S NBT. No warning, option or save-confirmation flag is changed.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class PlayerDataNetworkAudit {
    public static final String ENABLE_PROPERTY = "thaumcraft.playerDataNetworkAudit";
    public static final String DIRECTORY_PROPERTY = "thaumcraft.playerDataNetworkAudit.dir";
    public static final String MARKER = "TC_PORT_PLAYER_DATA_NETWORK_AUDIT_V1";
    public static final String A = "TC_Audit_A";
    public static final String B = "TC_Audit_B";
    static final BlockPos END_PLATFORM_CENTER = new BlockPos(1024, 80, 1024);
    static final BlockPos END_PORTAL_POSITION = END_PLATFORM_CENTER.above();
    private static final int MAX_PROGRESS_FRAME_BYTES = 1_048_576;
    private static final long MAX_PROGRESS_JOURNAL_BYTES = 33_554_432L;
    private static Run active;

    private PlayerDataNetworkAudit() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        try {
            active = new Run(event.getServer());
        } catch (Exception failure) {
            Thaumcraft.log.error("Player-data network audit refused initialization", failure);
            event.getServer().halt(false);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (active == null || active.server != event.getServer()) return;
        try {
            active.step();
        } catch (Exception failure) {
            active.finish("failed", failure.toString());
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        if (active != null && active.server == event.getServer()) {
            active.finish("interrupted", "Server stopped before both real-client reports completed");
        }
    }

    @SubscribeEvent
    public static void cloned(PlayerEvent.Clone event) {
        Run run = active;
        if (run == null || run.finished || !(event.getEntity() instanceof ServerPlayer player)
            || player.getServer() != run.server) return;
        JsonObject row = new JsonObject();
        row.addProperty("name", player.getGameProfile().getName());
        row.addProperty("wasDeath", event.isWasDeath());
        row.addProperty("phase", run.phase);
        row.addProperty("uuid", player.getUUID().toString());
        row.addProperty("originalIdentity", System.identityHashCode(event.getOriginal()));
        row.addProperty("newIdentity", System.identityHashCode(player));
        run.clones.add(row);
    }

    /** Called only AFTER PacketDistributor.sendToPlayer; GameTest capture never calls this. */
    public static void observeSent(ServerPlayer owner, CustomPacketPayload payload) {
        Run run = active;
        if (run == null || owner.getServer() != run.server || run.finished) return;
        try {
            run.observe(owner, payload);
        } catch (Exception failure) {
            run.finish("failed", failure.toString());
        }
    }

    /** Shared guard for server and client recorders; no directories are created by the Java audit. */
    public static JsonObject validateDirectory(Path launchDirectory, String role) throws Exception {
        String requested = System.getProperty(DIRECTORY_PROPERTY);
        if (requested == null || requested.isBlank()) throw new IllegalStateException("Audit directory is required");
        Path directory = Path.of(requested).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)
            || !directory.equals(directory.toRealPath()) || !directory.equals(launchDirectory.toRealPath())) {
            throw new IllegalStateException("Audit directory must be the prepared real launch/game directory");
        }
        Path runDirectory = directory.getParent();
        if (!directory.getFileName().toString().equals(role) || runDirectory == null
            || runDirectory.getParent() == null
            || !runDirectory.getParent().getFileName().toString().equals("player-data-network")
            || !runDirectory.getFileName().toString().matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalStateException("Expected player-data-network/<runId>/<role> layout");
        }
        Path markerPath = directory.resolve(".player-data-network-audit.json");
        if (Files.isSymbolicLink(markerPath) || Files.size(markerPath) > 4096) {
            throw new IllegalStateException("Invalid isolation marker");
        }
        JsonObject marker = JsonParser.parseString(Files.readString(markerPath)).getAsJsonObject();
        if (!MARKER.equals(marker.get("marker").getAsString())
            || !role.equals(marker.get("role").getAsString())
            || !runDirectory.getFileName().toString().equals(marker.get("runId").getAsString())
            || !Set.of("generate", "reload").contains(marker.get("phase").getAsString())) {
            throw new IllegalStateException("Isolation marker mismatch");
        }
        UUID.fromString(marker.get("token").getAsString());
        int port = marker.get("port").getAsInt();
        if (port < 1024 || port > 65535) throw new IllegalStateException("Invalid private test port");
        if ("reload".equals(marker.get("phase").getAsString())) readBaseline(directory, marker);
        String reportName = role.equals("server") ? "server-report.json" : "client-report.json";
        if (Files.exists(directory.resolve(reportName)) || Files.exists(directory.resolve(role.equals("server")
            ? "server-state.json" : "client-state.json"))) {
            throw new IllegalStateException("Use a fresh prepared directory; reports/state already exist");
        }
        return marker;
    }

    private static JsonObject readBaseline(Path directory, JsonObject marker) throws Exception {
        Path baseline = Path.of(marker.get("baseline").getAsString()).toAbsolutePath().normalize();
        Path root = directory.getParent().getParent();
        if (!baseline.getFileName().toString().equals("server-report.json") || baseline.getParent() == null
            || !baseline.getParent().getFileName().toString().equals("server")
            || baseline.getParent().getParent() == null || baseline.getParent().getParent().equals(directory.getParent())
            || !root.equals(baseline.getParent().getParent().getParent())
            || Files.isSymbolicLink(baseline) || !baseline.equals(baseline.toRealPath()) || Files.size(baseline) > 1_000_000) {
            throw new IllegalStateException("Baseline must be a completed sibling generate report inside the same private root");
        }
        JsonObject previous = JsonParser.parseString(Files.readString(baseline)).getAsJsonObject();
        if (!MARKER.equals(previous.get("marker").getAsString()) || !marker.get("token").equals(previous.get("token"))
            || !"generate".equals(previous.get("phase").getAsString()) || !"completed".equals(previous.get("status").getAsString())
            || !previous.has("expectedSavedPlayers")) throw new IllegalStateException("Invalid completed generate baseline");
        return previous;
    }

    public static JsonObject identity(JsonObject marker) {
        JsonObject identity = new JsonObject();
        for (String key : List.of("marker", "runId", "token", "role", "phase", "port")) {
            identity.add(key, marker.get(key).deepCopy());
        }
        identity.addProperty("schemaVersion", 1);
        if (marker.has("baseline")) identity.add("baseline", marker.get("baseline").deepCopy());
        return identity;
    }

    /** Append-only newline-framed progress; final reports remain one CREATE_NEW JSON document. */
    public static void writeJson(Path path, JsonObject value, boolean finalReport) throws Exception {
        if (Files.isSymbolicLink(path)) throw new IllegalStateException("Refusing linked audit output");
        if (finalReport) {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(value);
            Files.writeString(path, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } else {
            byte[] frame = (new Gson().toJson(value) + "\n").getBytes(StandardCharsets.UTF_8);
            if (frame.length > MAX_PROGRESS_FRAME_BYTES) throw new IllegalStateException("Unbounded audit progress frame");
            // A single role's server/render thread is the only writer. No replacement/deletion:
            // Windows readers may retain the file handle without granting delete-sharing.
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                if (channel.size() + frame.length > MAX_PROGRESS_JOURNAL_BYTES) {
                    throw new IllegalStateException("Unbounded audit progress journal");
                }
                ByteBuffer buffer = ByteBuffer.wrap(frame);
                while (buffer.hasRemaining()) {
                    if (channel.write(buffer) <= 0) throw new IllegalStateException("Unable to append complete audit progress frame");
                }
            }
        }
    }

    /**
     * Reads a captured end position, never a moving EOF. A partially appended last frame is ignored
     * until its LF arrives; a malformed COMPLETE frame fails explicitly. UTF-8 byte bounds include
     * the LF. The maximum tail covers one previous complete plus one incomplete maximum frame.
     */
    public static JsonObject readProgressJson(Path path, int maxFrameBytes) throws Exception {
        if (maxFrameBytes < 1 || maxFrameBytes > MAX_PROGRESS_FRAME_BYTES) throw new IllegalArgumentException("Invalid progress frame bound");
        if (!Files.exists(path)) return null;
        if (Files.isSymbolicLink(path)) throw new IllegalStateException("Refusing linked audit progress input");
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long end = channel.size();
            if (end > MAX_PROGRESS_JOURNAL_BYTES) throw new IllegalStateException("Unbounded audit progress journal");
            if (end == 0) return null;
            int length = (int)Math.min(end, 2L * (maxFrameBytes + 1));
            long start = end - length;
            ByteBuffer buffer = ByteBuffer.allocate(length);
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, start + buffer.position());
                if (read <= 0) throw new IllegalStateException("Audit append-only journal was truncated during read");
            }
            byte[] tail = buffer.array();
            int lastLf = length - 1;
            while (lastLf >= 0 && tail[lastLf] != '\n') lastLf--;
            int incompleteBytes = length - lastLf - 1;
            if (incompleteBytes >= maxFrameBytes) throw new IllegalStateException("Unbounded incomplete audit progress frame");
            if (lastLf < 0) {
                if (start != 0) throw new IllegalStateException("Unbounded incomplete audit progress frame");
                return null;
            }
            int precedingLf = lastLf - 1;
            while (precedingLf >= 0 && tail[precedingLf] != '\n') precedingLf--;
            if (precedingLf < 0 && start != 0) throw new IllegalStateException("Unbounded complete audit progress frame");
            int frameStart = precedingLf + 1;
            int jsonLength = lastLf - frameStart;
            if (jsonLength + 1 > maxFrameBytes) throw new IllegalStateException("Unbounded complete audit progress frame");
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(tail, frameStart, jsonLength)).toString();
            return JsonParser.parseString(json).getAsJsonObject();
        }
    }

    /** Normalize only unordered output lists/flag sets, without relying on SNBT iteration order. */
    public static String canonicalKnowledge(CompoundTag data) {
        JsonObject result = new JsonObject();
        JsonArray research = new JsonArray();
        List<CompoundTag> entries = new ArrayList<>();
        for (Tag value : data.getList("research", Tag.TAG_COMPOUND)) entries.add((CompoundTag)value);
        entries.sort(Comparator.comparing(value -> value.getString("key")));
        for (CompoundTag entry : entries) {
            JsonObject normalized = new JsonObject();
            normalized.addProperty("key", entry.getString("key"));
            if (entry.contains("stage")) normalized.addProperty("stage", entry.getInt("stage"));
            if (entry.contains("flags")) {
                JsonArray flags = new JsonArray();
                java.util.Arrays.stream(entry.getString("flags").split(","))
                    .filter(value -> !value.isEmpty()).sorted().forEach(flags::add);
                normalized.add("flags", flags);
            }
            research.add(normalized);
        }
        JsonArray knowledge = new JsonArray();
        entries.clear();
        for (Tag value : data.getList("knowledge", Tag.TAG_COMPOUND)) entries.add((CompoundTag)value);
        entries.sort(Comparator.comparing(value -> value.getString("key")));
        for (CompoundTag entry : entries) {
            JsonObject normalized = new JsonObject();
            normalized.addProperty("key", entry.getString("key"));
            normalized.addProperty("amount", entry.getInt("amount"));
            knowledge.add(normalized);
        }
        result.add("research", research);
        result.add("knowledge", knowledge);
        return new Gson().toJson(result);
    }

    private static final class Run {
        private final MinecraftServer server;
        private final Path directory;
        private final JsonObject marker;
        private final JsonObject report;
        private final JsonArray trace = new JsonArray();
        private final JsonArray checks = new JsonArray();
        private final JsonArray clones = new JsonArray();
        private final boolean reload;
        private final JsonObject baseline;
        private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(900);
        private String phase = "login";
        private int phaseTick;
        private int revision;
        private int baselineBKnowledge;
        private int baselineBWarp;
        private int baselineAKnowledge;
        private int baselineAWarp;
        private int baselineEpochA;
        private ServerPlayer beforeDeath;
        private ServerPlayer beforeEndReturn;
        private boolean endFixturePrepared;
        private boolean finished;

        private Run(MinecraftServer server) throws Exception {
            this.server = server;
            directory = Path.of(System.getProperty("user.dir")).toRealPath();
            marker = validateDirectory(directory, "server");
            reload = "reload".equals(marker.get("phase").getAsString());
            baseline = reload ? readBaseline(directory, marker) : null;
            if (!server.isDedicatedServer() || !"127.0.0.1".equals(server.getLocalIp())
                || server.usesAuthentication() || server.getPort() != marker.get("port").getAsInt()
                || server.isHardcore() || server.overworld().getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN)) {
                throw new IllegalStateException("Audit requires non-hardcore offline dedicated server bound to exact 127.0.0.1 private port");
            }
            Path world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            if (!world.getParent().equals(directory) || !world.getFileName().toString().equals("player-data-network-audit")) {
                throw new IllegalStateException("Refusing a non-audit world");
            }
            Path playerData = world.resolve("playerdata");
            if (!reload && Files.isDirectory(playerData)) {
                try (var existing = Files.list(playerData)) {
                    if (existing.anyMatch(path -> path.getFileName().toString().endsWith(".dat"))) {
                        throw new IllegalStateException("Generate refuses existing player saves; use a fresh world or explicit reload");
                    }
                }
            }
            report = identity(marker);
            report.addProperty("startedUtc", Instant.now().toString());
            report.addProperty("coverageEndReturn", "pending: actual EndPortal/WinScreen callback and non-death Clone in generate");
            report.addProperty("coverageRelogRestart", reload ? "pending: real new login and actual copied-world player NBT reload"
                : "uncovered in generate: requires completed separate reload run");
            report.add("actualProductionSendTrace", trace);
            report.add("actualCloneEvents", clones);
            report.add("checks", checks);
            phaseTick = server.getTickCount();
            publish("running");
            Thaumcraft.log.info("Player-data network audit waiting for real clients {} and {} on localhost:{}", A, B, server.getPort());
        }

        private void observe(ServerPlayer owner, CustomPacketPayload packet) throws Exception {
            String name = owner.getGameProfile().getName();
            if (!A.equals(name) && !B.equals(name)) throw new IllegalStateException("Unexpected audit player " + name);
            if (!(owner.connection.getConnection().getRemoteAddress() instanceof InetSocketAddress address)
                || !address.getAddress().isLoopbackAddress()) throw new IllegalStateException("Non-loopback audit connection");
            CompoundTag data;
            String kind;
            if (packet instanceof PacketSyncKnowledge knowledge) {
                data = knowledge.data();
                kind = "knowledge";
            } else if (packet instanceof PacketSyncWarp warp) {
                data = warp.data();
                kind = "warp";
            } else return;
            JsonObject row = new JsonObject();
            row.addProperty("sequence", trace.size() + 1);
            row.addProperty("serverTick", server.getTickCount());
            row.addProperty("ownerTick", owner.tickCount);
            row.addProperty("phase", phase);
            row.addProperty("owner", name);
            row.addProperty("uuid", owner.getUUID().toString());
            row.addProperty("kind", kind);
            row.addProperty("payloadNbt", data.toString());
            row.addProperty("dimension", owner.level().dimension().location().toString());
            trace.add(row);
            if (trace.size() > 128) throw new IllegalStateException("Unexpected/unbounded audit packet count");
        }

        private void step() throws Exception {
            if (operatorAbortRequested()) {
                finish("interrupted", "Marker-matched private operator abort requested");
                return;
            }
            int elapsed = server.getTickCount() - phaseTick;
            if (System.nanoTime() > deadline || elapsed > (phase.equals("login") ? 6000 : 2400)) {
                throw new IllegalStateException("Bounded timeout in phase " + phase);
            }
            if (server.getTickCount() % 5 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!A.equals(player.getGameProfile().getName()) && !B.equals(player.getGameProfile().getName())) {
                    throw new IllegalStateException("Unexpected player in private audit server");
                }
            }
            if (phase.equals("finish")) {
                JsonObject finalA = readClient("client-a", "client-report.json");
                JsonObject finalB = readClient("client-b", "client-report.json");
                if (finalA != null && finalB != null) {
                    require("completed".equals(finalA.get("status").getAsString())
                        && "completed".equals(finalB.get("status").getAsString()), "Both client recorders must complete");
                    compareTranscript(finalA, A);
                    compareTranscript(finalB, B);
                    check("socket-transcripts", "Every actual per-owner production send matches the client's ordered applied payload transcript and final data");
                    report.add("clientA", finalA);
                    report.add("clientB", finalB);
                    finish("completed", null);
                }
                return;
            }
            ServerPlayer a = server.getPlayerList().getPlayerByName(A);
            ServerPlayer b = server.getPlayerList().getPlayerByName(B);
            JsonObject clientA = readClient("client-a", "client-state.json");
            JsonObject clientB = readClient("client-b", "client-state.json");
            require((clientA == null || !clientA.has("failure")) && (clientB == null || !clientB.has("failure")), "Client-side audit failed");
            if (a == null || b == null || clientA == null || clientB == null) return;
            require(offlineUuid(A).equals(a.getUUID()) && offlineUuid(B).equals(b.getUUID()), "Distinct expected offline UUIDs");
            if (!phase.equals("login")) {
                require(noForeignPayload(clientA, "B") && noForeignPayload(clientB, "A"), "Owner-only sentinel privacy");
            }
            switch (phase) {
                case "login" -> {
                    if (!connected(clientA, a) || !connected(clientB, b)
                        || count(clientA, "knowledge") < 1 || count(clientB, "knowledge") < 1
                        || count(clientA, "warp") < 1 || count(clientB, "warp") < 1) return;
                    if (reload) {
                        if (elapsed < 80 || !matches(clientA, a) || !matches(clientB, b)) return;
                        check("real-login", "Both real socket clients applied initial production knowledge/warp snapshots");
                        assertSavedPlayer(a);
                        assertSavedPlayer(b);
                        check("restart-relogin", "Actual loaded server attachments and new client login snapshots exactly match completed generate saved NBT/UUID");
                        report.addProperty("coverageRelogRestart", "passed: server restart and real new A/B logins from private world copy");
                        report.addProperty("coverageEndReturn", "covered by referenced completed generate, not re-executed in reload");
                        report.add("expectedSavedPlayers", baseline.get("expectedSavedPlayers").deepCopy());
                        advance("finish");
                        return;
                    }
                    check("real-login", "Both real socket clients applied initial production knowledge/warp snapshots");
                    advance("baseline");
                    ServerLevel overworld = server.overworld();
                    platform(overworld);
                    a.teleportTo(overworld, 0.5, 81, 0.5, Set.of(), 0, 0);
                    b.teleportTo(overworld, 2.5, 81, 0.5, Set.of(), 0, 0);
                    a.setRespawnPosition(Level.OVERWORLD, new BlockPos(0, 81, 0), 0, true, false);
                    b.setRespawnPosition(Level.OVERWORLD, new BlockPos(2, 81, 0), 0, true, false);
                    a.setGameMode(GameType.CREATIVE);
                    b.setGameMode(GameType.CREATIVE);
                    seed(a, "A", 11);
                    seed(b, "B", 71);
                    PlayerDataSync.syncAllNow(a);
                    PlayerDataSync.syncAllNow(b);
                }
                case "baseline" -> {
                    if (elapsed < 80 || !matches(clientA, a) || !matches(clientB, b)
                        || !a.serverLevel().getChunkSource().chunkMap.getPlayersWatching(a).contains(b)
                        || !b.serverLevel().getChunkSource().chunkMap.getPlayersWatching(b).contains(a)) return;
                    baselineBKnowledge = count(clientB, "knowledge");
                    baselineBWarp = count(clientB, "warp");
                    baselineAKnowledge = count(clientA, "knowledge");
                    baselineAWarp = count(clientA, "warp");
                    baselineEpochA = clientA.get("entityEpoch").getAsInt();
                    check("tracking-baseline", "Both mutually tracking clients retain different sentinel/warp data");
                    advance("privacy-update");
                    ThaumcraftCapabilities.getKnowledge(a).addResearch(sentinel("A", "DIRTY"));
                    PlayerDataSync.markKnowledgeDirty(a);
                    ThaumcraftCapabilities.getWarp(a).set(EnumWarpType.NORMAL, 33);
                    PlayerDataSync.syncWarpNow(a);
                }
                case "privacy-update" -> {
                    if (elapsed < 80 || !matches(clientA, a) || !matches(clientB, b)) return;
                    require(count(clientB, "knowledge") == baselineBKnowledge && count(clientB, "warp") == baselineBWarp,
                        "Tracking B must receive no private A packet");
                    require(count(clientA, "knowledge") == baselineAKnowledge + 1
                        && count(clientA, "warp") == baselineAWarp + 1, "A receives exactly one dirty knowledge and immediate warp update");
                    boolean due = false;
                    for (var value : trace) {
                        JsonObject row = value.getAsJsonObject();
                        if ("privacy-update".equals(row.get("phase").getAsString())
                            && A.equals(row.get("owner").getAsString()) && "knowledge".equals(row.get("kind").getAsString())) {
                            due = row.get("ownerTick").getAsInt() % 20 == 0;
                        }
                    }
                    require(due && !PlayerDataSync.isKnowledgeDirty(a), "Dirty knowledge flush follows actual 20-tick rule");
                    check("privacy-dirty20", "A-only updates passed while B was tracking A; no interception or broadcast");
                    advance("dimension-nether");
                    ServerLevel nether = server.getLevel(Level.NETHER);
                    require(nether != null, "Nether is required");
                    platform(nether);
                    a.teleportTo(nether, 0.5, 81, 0.5, Set.of(), 0, 0);
                }
                case "dimension-nether" -> {
                    if (elapsed < 40 || a.level().dimension() != Level.NETHER || !matches(clientA, a)) return;
                    require(clientA.get("entityEpoch").getAsInt() > baselineEpochA, "Dimension transition recreates actual LocalPlayer");
                    assertBUnchanged(clientB, b);
                    baselineEpochA = clientA.get("entityEpoch").getAsInt();
                    check("dimension-nether", "ChangedDimension production owner resync applied to new LocalPlayer");
                    advance("dimension-overworld");
                    a.teleportTo(server.overworld(), 0.5, 81, 0.5, Set.of(), 0, 0);
                }
                case "dimension-overworld" -> {
                    if (elapsed < 40 || a.level().dimension() != Level.OVERWORLD || !matches(clientA, a)) return;
                    require(clientA.get("entityEpoch").getAsInt() > baselineEpochA, "Return transition creates another LocalPlayer");
                    assertBUnchanged(clientB, b);
                    check("dimension-overworld", "Return owner snapshots restored persistent data without client/server aliases");
                    baselineEpochA = clientA.get("entityEpoch").getAsInt();
                    beforeDeath = a;
                    advance("death");
                    a.setGameMode(GameType.SURVIVAL);
                    a.hurt(a.damageSources().genericKill(), Float.MAX_VALUE);
                    require(a.isDeadOrDying(), "Real player must actually die");
                }
                case "death" -> {
                    if (a == beforeDeath || a.isDeadOrDying() || elapsed < 40 || !matches(clientA, a)) return;
                    require(clientA.get("respawnRequests").getAsInt() == 1, "Client must send one real PERFORM_RESPAWN");
                    require(clientA.get("entityEpoch").getAsInt() > baselineEpochA, "Actual respawn LocalPlayer replacement required");
                    assertBUnchanged(clientB, b);
                    require(cloneCount("death", true) == 1, "Exactly one actual PlayerEvent.Clone.wasDeath=true is required");
                    check("death-respawn", "Real C2S PERFORM_RESPAWN replaced ServerPlayer and preserved attachments through vanilla/NeoForge clone");
                    baselineEpochA = clientA.get("entityEpoch").getAsInt();
                    advance("dimension-end");
                    ServerLevel end = server.getLevel(Level.END);
                    require(end != null, "End is required");
                    // The origin overlaps EndDragonFight's automatically generated exit podium.
                    // This private fixture is outside its 192-block player scan and arena chunks.
                    platform(end, END_PLATFORM_CENTER);
                    endFixturePrepared = true;
                    a.setGameMode(GameType.CREATIVE);
                    a.teleportTo(end, END_PLATFORM_CENTER.getX() + 0.5, END_PLATFORM_CENTER.getY() + 1,
                        END_PLATFORM_CENTER.getZ() + 0.5, Set.of(), 0, 0);
                }
                case "dimension-end" -> {
                    if (elapsed < 40 || a.level().dimension() != Level.END || !matches(clientA, a)) return;
                    require(clientA.get("entityEpoch").getAsInt() > baselineEpochA, "Actual End LocalPlayer replacement required");
                    baselineEpochA = clientA.get("entityEpoch").getAsInt();
                    beforeEndReturn = a;
                    require(!a.seenCredits, "Fresh generate player must not have altered/previous credits state");
                    check("dimension-end", "Actual End owner resync preserved personal data");
                    advance("end-return");
                    // Vanilla EndPortalBlock.entityInside must initiate the actual credits/C2S path.
                    require(a.serverLevel().setBlock(END_PORTAL_POSITION, Blocks.END_PORTAL.defaultBlockState(), 3)
                        && a.serverLevel().getBlockState(END_PORTAL_POSITION).is(Blocks.END_PORTAL),
                        "Private End portal placement must succeed");
                    check("end-portal-fixture", "Placed genuine End portal outside the arena; actual collision/credits path remains unmodified");
                }
                case "end-return" -> {
                    if (a == beforeEndReturn || elapsed < 40 || a.level().dimension() != Level.OVERWORLD || !matches(clientA, a)) return;
                    require(clientA.get("creditsCallbacks").getAsInt() == 1, "Actual WinScreen and vanilla callback required");
                    require(clientA.get("entityEpoch").getAsInt() > baselineEpochA, "End return must replace actual LocalPlayer");
                    require(cloneCount("end-return", false) == 1, "Exactly one actual non-death PlayerEvent.Clone required");
                    assertBUnchanged(clientB, b);
                    check("end-return", "Actual EndPortal -> WinScreen.onClose -> vanilla C2S PERFORM_RESPAWN -> non-death clone preserves data");
                    report.addProperty("coverageEndReturn", "passed: actual EndPortal, identified WinScreen vanilla callback, non-death Clone and new LocalPlayer");
                    JsonObject expected = new JsonObject();
                    expected.add(A, savedPlayer(a));
                    expected.add(B, savedPlayer(b));
                    report.add("expectedSavedPlayers", expected);
                    // Persist the actual current player objects before automatic clients quit.
                    server.getPlayerList().saveAll();
                    advance("finish");
                }
                default -> throw new IllegalStateException("Unknown audit phase " + phase);
            }
        }

        private void assertBUnchanged(JsonObject client, ServerPlayer b) throws Exception {
            require(matches(client, b) && count(client, "knowledge") == baselineBKnowledge
                && count(client, "warp") == baselineBWarp, "B data/handler counts remain unchanged during A lifecycle");
        }

        private int cloneCount(String auditPhase, boolean wasDeath) {
            int result = 0;
            for (var value : clones) {
                JsonObject row = value.getAsJsonObject();
                if (auditPhase.equals(row.get("phase").getAsString()) && A.equals(row.get("name").getAsString())
                    && wasDeath == row.get("wasDeath").getAsBoolean()) result++;
            }
            return result;
        }

        private void compareTranscript(JsonObject client, String owner) throws Exception {
            JsonArray received = client.getAsJsonArray("packets");
            int index = 0;
            int knowledge = 0;
            int warp = 0;
            for (var value : trace) {
                JsonObject sent = value.getAsJsonObject();
                if (!owner.equals(sent.get("owner").getAsString())) continue;
                require(index < received.size(), "Client missed a production payload for " + owner);
                JsonObject actual = received.get(index++).getAsJsonObject();
                String kind = sent.get("kind").getAsString();
                require(kind.equals(actual.get("kind").getAsString()) && sent.get("uuid").equals(actual.get("uuid")), "Per-owner ordered payload identity");
                CompoundTag expectedData = TagParser.parseTag(sent.get("payloadNbt").getAsString());
                CompoundTag actualData = TagParser.parseTag(actual.get("payloadNbt").getAsString());
                require(kind.equals("knowledge") ? canonicalKnowledge(expectedData).equals(canonicalKnowledge(actualData))
                    : expectedData.equals(actualData), "Actual socket payload differs from server snapshot for " + owner);
                if (kind.equals("knowledge")) knowledge++;
                else warp++;
            }
            require(index == received.size() && knowledge == count(client, "knowledge") && warp == count(client, "warp"),
                "No unaccounted private payloads/handler counts for " + owner);
            JsonObject saved = report.getAsJsonObject("expectedSavedPlayers").getAsJsonObject(owner);
            require(saved.get("uuid").equals(client.get("uuid")), "Final real-client UUID matches saved owner");
            require(canonicalKnowledge(TagParser.parseTag(saved.get("knowledgeNbt").getAsString()))
                .equals(canonicalKnowledge(TagParser.parseTag(client.get("knowledgeNbt").getAsString()))), "Final client knowledge matches saved full data");
            require(TagParser.parseTag(saved.get("warpNbt").getAsString()).equals(TagParser.parseTag(client.get("warpNbt").getAsString())),
                "Final client warp matches saved full data");
        }

        private JsonObject savedPlayer(ServerPlayer player) {
            JsonObject saved = new JsonObject();
            saved.addProperty("uuid", player.getUUID().toString());
            saved.addProperty("knowledgeNbt", ThaumcraftCapabilities.getKnowledge(player).serializeNBT().toString());
            saved.addProperty("warpNbt", ThaumcraftCapabilities.getWarp(player).serializeNBT().toString());
            return saved;
        }

        private void assertSavedPlayer(ServerPlayer player) throws Exception {
            JsonObject expected = baseline.getAsJsonObject("expectedSavedPlayers").getAsJsonObject(player.getGameProfile().getName());
            require(expected.get("uuid").getAsString().equals(player.getUUID().toString()), "Reload keeps original offline UUID");
            require(canonicalKnowledge(TagParser.parseTag(expected.get("knowledgeNbt").getAsString()))
                .equals(canonicalKnowledge(ThaumcraftCapabilities.getKnowledge(player).serializeNBT())), "Actual disk-loaded knowledge matches baseline");
            require(TagParser.parseTag(expected.get("warpNbt").getAsString()).equals(ThaumcraftCapabilities.getWarp(player).serializeNBT()),
                "Actual disk-loaded warp matches baseline");
        }

        private boolean noForeignPayload(JsonObject client, String foreignRole) {
            for (var value : client.getAsJsonArray("packets")) {
                if (value.getAsJsonObject().get("payloadNbt").getAsString().contains(sentinel(foreignRole, ""))) return false;
            }
            return true;
        }

        private String sentinel(String role, String suffix) {
            return "[#]TC_NETWORK_AUDIT_" + marker.get("token").getAsString() + "_" + role + "_" + suffix;
        }

        private void seed(ServerPlayer player, String role, int warp) {
            var knowledge = ThaumcraftCapabilities.getKnowledge(player);
            knowledge.clear();
            knowledge.addResearch(sentinel(role, "BASE"));
            var data = ThaumcraftCapabilities.getWarp(player);
            data.clear();
            data.set(EnumWarpType.PERMANENT, warp);
            data.set(EnumWarpType.NORMAL, warp + 1);
            data.set(EnumWarpType.TEMPORARY, warp + 2);
            data.setCounter(warp + 100);
        }

        private boolean matches(JsonObject client, ServerPlayer player) throws Exception {
            if (!connected(client, player)) return false;
            CompoundTag knowledge = TagParser.parseTag(client.get("knowledgeNbt").getAsString());
            CompoundTag warp = TagParser.parseTag(client.get("warpNbt").getAsString());
            return canonicalKnowledge(knowledge).equals(canonicalKnowledge(ThaumcraftCapabilities.getKnowledge(player).serializeNBT()))
                && warp.equals(ThaumcraftCapabilities.getWarp(player).serializeNBT());
        }

        private boolean connected(JsonObject client, ServerPlayer player) {
            return client.has("uuid") && player.getUUID().toString().equals(client.get("uuid").getAsString())
                && player.level().dimension().location().toString().equals(client.get("dimension").getAsString())
                && client.get("remoteLoopback").getAsBoolean();
        }

        private int count(JsonObject client, String kind) {
            return client.get(kind + "Handlers").getAsInt();
        }

        private JsonObject readClient(String role, String file) throws Exception {
            Path path = directory.getParent().resolve(role).resolve(file);
            if (!Files.exists(path)) return null;
            JsonObject state;
            if (file.equals("client-state.json")) {
                state = readProgressJson(path, MAX_PROGRESS_FRAME_BYTES);
                if (state == null) return null;
            } else {
                if (Files.isSymbolicLink(path) || Files.size(path) > 1_000_000) throw new IllegalStateException("Unsafe/unbounded client report");
                state = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            }
            require(MARKER.equals(state.get("marker").getAsString()) && role.equals(state.get("role").getAsString())
                && marker.get("runId").equals(state.get("runId")) && marker.get("token").equals(state.get("token")), "Client isolation identity");
            return state;
        }

        private boolean operatorAbortRequested() throws Exception {
            Path path = directory.resolve("abort-request.json");
            if (!Files.exists(path)) return false;
            if (Files.isSymbolicLink(path) || Files.size(path) > 4096) throw new IllegalStateException("Unsafe operator abort marker");
            JsonObject request = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            require(MARKER.equals(request.get("marker").getAsString())
                && marker.get("token").equals(request.get("token"))
                && marker.get("runId").equals(request.get("runId"))
                && "operator".equals(request.get("role").getAsString()), "Operator abort isolation identity");
            String reason = request.get("reason").getAsString();
            require(!reason.isBlank() && reason.length() <= 512, "Bounded operator abort reason required");
            report.addProperty("operatorAbortReason", reason);
            return true;
        }

        private void check(String name, String detail) {
            JsonObject row = new JsonObject();
            row.addProperty("name", name);
            row.addProperty("detail", detail);
            row.addProperty("serverTick", server.getTickCount());
            ServerPlayer a = server.getPlayerList().getPlayerByName(A);
            ServerPlayer b = server.getPlayerList().getPlayerByName(B);
            if (a != null) row.add("serverOwnerA", savedPlayer(a));
            if (b != null) row.add("serverOwnerB", savedPlayer(b));
            if (a != null) row.add("ownerADiagnostics", playerDiagnostics(a));
            if (b != null) row.add("ownerBDiagnostics", playerDiagnostics(b));
            checks.add(row);
            Thaumcraft.log.info("Player-data network audit passed {}", name);
        }

        private void advance(String next) throws Exception {
            phase = next;
            phaseTick = server.getTickCount();
            revision++;
            publish("running");
            Thaumcraft.log.info("Player-data network audit phase {}", phase);
        }

        private void publish(String status) throws Exception {
            JsonObject state = identity(marker);
            state.addProperty("auditPhase", phase);
            state.addProperty("revision", revision);
            state.addProperty("status", status);
            writeJson(directory.resolve("server-state.json"), state, false);
        }

        private void finish(String status, String detail) {
            if (finished) return;
            finished = true;
            active = null;
            try {
                try {
                    publish(status);
                } catch (Exception progressFailure) {
                    // A stuck progress-file handle must not suppress the final CREATE_NEW report.
                    Thaumcraft.log.error("Unable to publish terminal network-audit progress", progressFailure);
                    if ("completed".equals(status)) status = "failed";
                    detail = (detail == null ? "" : detail + "; ") + "Terminal progress publish failed: " + progressFailure;
                }
                report.addProperty("status", status);
                report.addProperty("auditPhase", phase);
                report.addProperty("finishedUtc", Instant.now().toString());
                report.addProperty("actualDeathCloneCount", cloneCount("death", true));
                report.addProperty("actualNonDeathEndCloneCount", cloneCount("end-return", false));
                ServerPlayer a = server.getPlayerList().getPlayerByName(A);
                ServerPlayer b = server.getPlayerList().getPlayerByName(B);
                if (a != null) report.add("finalOwnerADiagnostics", playerDiagnostics(a));
                if (b != null) report.add("finalOwnerBDiagnostics", playerDiagnostics(b));
                if (detail != null) report.addProperty("detail", detail);
                report.addProperty("saveFlushed", server.saveEverything(true, true, true));
                writeJson(directory.resolve("server-report.json"), report, true);
                Thaumcraft.log.info("Player-data network audit {}: {}", status, detail);
            } catch (Exception failure) {
                Thaumcraft.log.error("Unable to finish player-data network audit", failure);
            } finally {
                server.halt(false);
            }
        }

        private JsonObject playerDiagnostics(ServerPlayer player) {
            JsonObject result = new JsonObject();
            result.addProperty("dimension", player.level().dimension().location().toString());
            result.add("position", vector(player.position()));
            result.add("velocity", vector(player.getDeltaMovement()));
            result.addProperty("onGround", player.onGround());
            result.addProperty("seenCredits", player.seenCredits);
            result.addProperty("dead", player.isDeadOrDying());
            result.add("fixturePortalPosition", vector(Vec3.atLowerCornerOf(END_PORTAL_POSITION)));
            result.addProperty("endFixturePrepared", endFixturePrepared);
            ServerLevel end = server.getLevel(Level.END);
            if (endFixturePrepared && end != null) {
                var portal = end.getBlockState(END_PORTAL_POSITION);
                result.addProperty("actualFixturePortalState", portal.toString());
                result.addProperty("actualPortalShapeOverlapsOwner", player.level() == end && portal.is(Blocks.END_PORTAL)
                    && Shapes.joinIsNotEmpty(Shapes.create(player.getBoundingBox().move(-END_PORTAL_POSITION.getX(),
                        -END_PORTAL_POSITION.getY(), -END_PORTAL_POSITION.getZ())),
                        portal.getShape(end, END_PORTAL_POSITION), BooleanOp.AND));
            }
            return result;
        }
    }

    private static void platform(ServerLevel level) {
        platform(level, new BlockPos(0, 80, 0));
    }

    private static void platform(ServerLevel level, BlockPos center) {
        // A bounded test fixture, only in the guarded private world, not normal worldgen.
        for (int x = -3; x <= 4; x++) for (int z = -3; z <= 3; z++) {
            BlockPos floor = center.offset(x, 0, z);
            placeFixtureBlock(level, floor, Blocks.STONE.defaultBlockState());
            for (int y = 1; y <= 4; y++) placeFixtureBlock(level, floor.above(y), Blocks.AIR.defaultBlockState());
        }
    }

    private static void placeFixtureBlock(ServerLevel level, BlockPos position,
                                          net.minecraft.world.level.block.state.BlockState state) {
        boolean changed = level.setBlock(position, state, 3);
        require(changed || level.getBlockState(position).equals(state), "Private platform placement failed at " + position);
        require(level.getBlockState(position).equals(state), "Private platform state mismatch at " + position);
    }

    private static JsonArray vector(Vec3 vector) {
        JsonArray result = new JsonArray();
        result.add(vector.x);
        result.add(vector.y);
        result.add(vector.z);
        return result;
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
