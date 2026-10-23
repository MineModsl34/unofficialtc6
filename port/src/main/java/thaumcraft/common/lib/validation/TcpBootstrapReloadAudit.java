package thaumcraft.common.lib.validation;

import static thaumcraft.common.lib.validation.TcpBootstrapAuditIO.*;

import com.google.gson.JsonObject;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit;

/** Default-off, read-only acceptance of a copied, fully stopped bootstrap world in new JVMs. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class TcpBootstrapReloadAudit {
    public static final String ENABLE_PROPERTY = "thaumcraft.tcpBootstrapReloadAudit";
    public static final String DIRECTORY_PROPERTY = ENABLE_PROPERTY + ".dir";
    public static final BlockPos WORKBENCH = new BlockPos(2, 65, 4);
    public static final BlockPos BOOKSHELF = new BlockPos(2, 65, 2);
    private static final List<String> SOURCE_FIELDS = List.of("sourceRunId", "sourceToken", "sourcePort",
        "sourceServerReportSha256", "sourceRuntimeSha256", "worldManifestSha256");
    private static Run active;

    private TcpBootstrapReloadAudit() {}

    public static Path directory(Path actualDirectory, String role) throws Exception {
        require(Boolean.getBoolean(ENABLE_PROPERTY) && !Boolean.getBoolean(TcpBootstrapAuditIO.ENABLE_PROPERTY),
            "Explicit reload flag required; generation and reload are exclusive");
        require(Set.of("server", "client").contains(role), "Unknown reload role");
        String property = System.getProperty(DIRECTORY_PROPERTY);
        require(property != null && !property.isBlank(), "Explicit reload directory required");
        Path path = Path.of(property).toAbsolutePath().normalize();
        require(path.equals(actualDirectory.toAbsolutePath().normalize()) && path.equals(path.toRealPath()),
            "Reload directory differs from actual game/launch directory");
        safePath(path, path);
        Path run = path.getParent(), base = run == null ? null : run.getParent();
        require(path.getFileName().toString().equals(role) && base != null && base.getParent() != null
            && base.getParent().getParent() != null && base.getFileName().toString().equals("tcp-bootstrap")
            && base.getParent().getFileName().toString().equals("build")
            && base.getParent().getParent().getFileName().toString().equals("port")
            && run.getFileName().toString().matches("[A-Za-z0-9_-]{1,80}"), "Reload must be a private bootstrap cohort");
        return path;
    }

    public static JsonObject validate(Path directory, String role) throws Exception {
        JsonObject marker = readDocument(safePath(directory, directory.resolve(MARKER_FILE)), 4096, false);
        require(marker != null, "Missing reload marker");
        verifyIdentity(marker, marker, role);
        require("reload".equals(string(marker, "auditPhase")) && "firststeps".equals(string(marker, "goal"))
            && directory.getParent().getFileName().toString().equals(string(marker, "runId")), "Wrong reload phase/goal/run");
        require(string(marker, "sourceRunId").matches("[A-Za-z0-9_-]{1,80}")
            && !string(marker, "sourceRunId").equals(string(marker, "runId")), "Distinct generation source required");
        java.util.UUID.fromString(string(marker, "sourceToken"));
        require(!string(marker, "sourceToken").equals(string(marker, "token")), "Reload must have a fresh token");
        for (String key : List.of("sourceServerReportSha256", "sourceRuntimeSha256", "worldManifestSha256"))
            require(string(marker, key).matches("[0-9a-f]{64}"), "Missing reload source hash: " + key);
        require(marker.get("sourcePort").getAsInt() >= 1024 && marker.get("sourcePort").getAsInt() <= 65535,
            "Invalid source TCP port");
        for (String name : List.of(role + "-report.json", role + "-state.json", role + "-events.jsonl"))
            require(!Files.exists(safePath(directory, directory.resolve(name)), LinkOption.NOFOLLOW_LINKS),
                "Fresh reload outputs required");
        Path peerPath = safePath(directory.getParent(), directory.getParent().resolve(role.equals("server") ? "client" : "server").resolve(MARKER_FILE));
        JsonObject peer = readDocument(peerPath, 4096, false);
        verifyIdentity(marker, peer, role.equals("server") ? "client" : "server");
        for (String key : SOURCE_FIELDS) require(marker.get(key).equals(peer.get(key)), "Reload source roles disagree: " + key);
        return marker;
    }

    public static JsonObject reloadIdentity(JsonObject marker) {
        JsonObject result = identity(marker);
        for (String key : SOURCE_FIELDS) result.add(key, marker.get(key).deepCopy());
        return result;
    }

    public static JsonObject baseline(Path directory, JsonObject marker) throws Exception {
        Path run = directory.getParent();
        Path path = safePath(run, run.resolve("preparation-inputs/generation-server-report.json"));
        require(hash(path).equals(string(marker, "sourceServerReportSha256")), "Copied generation report hash differs");
        JsonObject source = readDocument(path, MAX_DOCUMENT_BYTES, false);
        require(source != null && MARKER.equals(string(source, "marker")) && "generate".equals(string(source, "auditPhase"))
            && "firststeps".equals(string(source, "goal")) && "server".equals(string(source, "role"))
            && string(marker, "sourceRunId").equals(string(source, "runId"))
            && string(marker, "sourceToken").equals(string(source, "token"))
            && marker.get("sourcePort").equals(source.get("port")) && PROTOCOL.equals(string(source, "networkProtocolVersion"))
            && source.get("passed").getAsBoolean() && "completed".equals(string(source, "status"))
            && source.get("saveFlushed").getAsBoolean() && source.get("diskNbtVerified").getAsBoolean()
            && source.get("finalFirststepsStage").getAsInt() == 4, "Expected fully completed saved generation source");
        Path runtimePath = safePath(run, run.resolve("preparation-inputs/generation-runtime.json"));
        require(hash(runtimePath).equals(string(marker, "sourceRuntimeSha256")), "Copied source runtime hash differs");
        JsonObject runtime = readDocument(runtimePath, MAX_DOCUMENT_BYTES, false);
        require(runtime != null && "completed".equals(string(runtime, "status"))
            && string(marker, "sourceRunId").equals(string(runtime, "runId"))
            && string(marker, "sourceToken").equals(string(runtime, "token"))
            && runtime.get("buildExitCode").getAsInt() == 0 && runtime.get("serverReady").getAsBoolean(),
            "Source engines did not complete");
        require(runtime.getAsJsonArray("processes").size() == 2, "Expected one source server and client JVM");
        for (var row : runtime.getAsJsonArray("processes")) {
            var process = row.getAsJsonObject();
            require(process.get("exitCode").getAsInt() == 0 && !process.get("forcedStop").getAsBoolean(),
                "Source engine needed forced stop or failed");
        }
        require(hash(safePath(run, run.resolve("preparation-inputs/world-copy-manifest.json")))
            .equals(string(marker, "worldManifestSha256")), "World-copy manifest changed");
        JsonObject baseline = source.getAsJsonObject("persistenceBaseline");
        require(baseline != null && OWNER_UUID.toString().equals(string(baseline, "ownerUuid"))
            && baseline.get("firststepsStage").getAsInt() == 4, "Missing completed native owner baseline");
        return baseline.deepCopy();
    }

    private static String hash(Path path) throws Exception {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= MAX_DOCUMENT_BYTES,
            "Missing/oversized copied baseline file");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    public static String canonicalKnowledge(CompoundTag value, boolean removePopup) {
        CompoundTag copy = value.copy();
        for (var tag : copy.getList("research", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) tag;
            String flags = java.util.Arrays.stream(entry.getString("flags").split(","))
                .filter(flag -> !flag.isEmpty() && (!removePopup || !flag.equals("POPUP"))).sorted()
                .collect(java.util.stream.Collectors.joining(","));
            if (flags.isEmpty()) entry.remove("flags"); else entry.putString("flags", flags);
        }
        return PlayerDataNetworkAudit.canonicalKnowledge(copy);
    }

    public static ListTag inventory(JsonObject value) throws Exception {
        return TagParser.parseTag("{Inventory:" + string(value, "inventoryNbt") + "}").getList("Inventory", Tag.TAG_COMPOUND);
    }

    public static void assertPlayer(Player player, JsonObject baseline, boolean client) throws Exception {
        require(OWNER_UUID.equals(player.getUUID()) && USERNAME.equals(player.getGameProfile().getName()), "Reload owner differs");
        require(canonicalKnowledge(TagParser.parseTag(string(baseline, "knowledgeNbt")), client).equals(
            canonicalKnowledge(ThaumcraftCapabilities.getKnowledge(player).serializeNBT(), false)),
            "Reloaded full knowledge differs from saved native owner (only client POPUP clearing is expected)");
        require(TagParser.parseTag(string(baseline, "warpNbt")).equals(ThaumcraftCapabilities.getWarp(player).serializeNBT()),
            "Reloaded full warp differs");
        require(inventory(baseline).equals(player.getInventory().save(new ListTag())), "Reloaded inventory slots/components/counts differ");
        require(player.totalExperience == baseline.get("totalExperience").getAsInt()
            && player.experienceLevel == baseline.get("experienceLevel").getAsInt()
            && Float.floatToIntBits(player.experienceProgress) == Float.floatToIntBits(baseline.get("experienceProgress").getAsFloat()),
            "Reloaded native experience differs");
        if (baseline.has("selectedSlot")) require(player.getInventory().selected == baseline.get("selectedSlot").getAsInt(),
            "Reloaded selected native slot differs");
        var knowledge = ThaumcraftCapabilities.getKnowledge(player);
        require(knowledge.getResearchStage("FIRSTSTEPS") == 4 && knowledge.isResearchComplete("FIRSTSTEPS")
            && knowledge.isResearchKnown("!gotcrystals") && knowledge.isResearchKnown("!gotdream")
            && knowledge.isResearchKnown("!gotthaumonomicon"), "Full FIRSTSTEPS/acquisition did not survive restart");
        require(knowledge.getKnowledgeRaw(thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.OBSERVATION,
            thaumcraft.api.research.ResearchCategories.getResearchCategory("BASICS")) == baseline.get("basicsRaw").getAsInt(),
            "Exact saved BASICS category observations did not survive restart");
    }

    public static JsonObject playerSnapshot(Player player) {
        JsonObject result = new JsonObject();
        result.addProperty("ownerUuid", player.getUUID().toString());
        result.addProperty("knowledgeNbt", ThaumcraftCapabilities.getKnowledge(player).serializeNBT().toString());
        result.addProperty("warpNbt", ThaumcraftCapabilities.getWarp(player).serializeNBT().toString());
        result.addProperty("inventoryNbt", player.getInventory().save(new ListTag()).toString());
        result.addProperty("selectedSlot", player.getInventory().selected);
        result.addProperty("totalExperience", player.totalExperience);
        result.addProperty("experienceLevel", player.experienceLevel);
        result.addProperty("experienceProgress", player.experienceProgress);
        result.addProperty("firststepsStage", ThaumcraftCapabilities.getKnowledge(player).getResearchStage("FIRSTSTEPS"));
        return result;
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        try { active = new Run(event.getServer()); }
        catch (Exception failure) { Thaumcraft.log.error("Private TCP reload refused startup", failure); event.getServer().halt(false); }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        Run run = active;
        if (run == null || run.finished || run.server != event.getServer()) return;
        try { run.step(); } catch (Exception failure) { run.finish(false, failure.toString()); }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        Run run = active;
        if (run != null && !run.finished && run.server == event.getServer()) run.finish(false, "Stopped before reload completed");
    }

    private static final class Run {
        final MinecraftServer server;
        final Path directory, world;
        final JsonObject marker, baseline;
        final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
        ServerPlayer owner;
        net.minecraft.network.Connection connection;
        int loadedTick;
        boolean finished;

        Run(MinecraftServer server) throws Exception {
            this.server = server;
            require(server.isSameThread(), "Reload requires server MAIN");
            directory = directory(Path.of(System.getProperty("user.dir")), "server");
            marker = validate(directory, "server"); baseline = baseline(directory, marker);
            require(server.isDedicatedServer() && "127.0.0.1".equals(server.getLocalIp()) && !server.usesAuthentication()
                && server.getPort() == marker.get("port").getAsInt() && !server.isHardcore() && server.getPlayerCount() == 0,
                "Exact initially empty dedicated offline loopback reload server required");
            world = safePath(directory, server.getWorldPath(LevelResource.ROOT).toRealPath());
            require(world.getParent().equals(directory) && world.getFileName().toString().equals(WORLD), "Wrong copied private world");
            Path file = safePath(directory, world.resolve("playerdata").resolve(OWNER_UUID + ".dat"));
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) > 64 && Files.size(file) < 1024 * 1024,
                "Reload needs the exact existing generation player save");
            assertDisk(NbtIo.readCompressed(file, NbtAccounter.create(4L * 1024 * 1024)));
            assertWorld();
            publish("login");
        }

        void assertWorld() throws Exception {
            var level = server.overworld();
            require(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(WORKBENCH).getBlock()).toString()
                .equals(string(baseline, "workbenchBlock")), "Copied workbench block differs");
            require(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(BOOKSHELF).getBlock()).toString()
                .equals(string(baseline, "bookshelfBlock")), "Copied consumed bookshelf differs");
            var tile = level.getBlockEntity(WORKBENCH);
            require(tile != null && TagParser.parseTag(string(baseline, "workbenchNbt")).equals(tile.saveWithFullMetadata(server.registryAccess())),
                "Copied native workbench full NBT differs");
        }

        void assertDisk(CompoundTag saved) throws Exception {
            require(saved.hasUUID("UUID") && OWNER_UUID.equals(saved.getUUID("UUID")), "Saved native UUID differs");
            CompoundTag attachments = saved.getCompound("neoforge:attachments");
            require(canonicalKnowledge(attachments.getCompound("thaumcraft:knowledge"), false)
                .equals(canonicalKnowledge(TagParser.parseTag(string(baseline, "knowledgeNbt")), false)), "Disk full knowledge differs");
            require(attachments.getCompound("thaumcraft:warp").equals(TagParser.parseTag(string(baseline, "warpNbt"))), "Disk full warp differs");
            require(saved.getList("Inventory", Tag.TAG_COMPOUND).equals(inventory(baseline)), "Disk complete Inventory differs");
            require(saved.getInt("XpTotal") == baseline.get("totalExperience").getAsInt()
                && saved.getInt("XpLevel") == baseline.get("experienceLevel").getAsInt()
                && Float.floatToIntBits(saved.getFloat("XpP")) == Float.floatToIntBits(baseline.get("experienceProgress").getAsFloat()),
                "Disk native XP differs");
            if (baseline.has("selectedSlot")) require(saved.getInt("SelectedItemSlot") == baseline.get("selectedSlot").getAsInt(),
                "Disk selected slot differs");
        }

        void step() throws Exception {
            require(server.isSameThread() && System.nanoTime() < deadline, "Private reload timed out");
            for (ServerPlayer player : server.getPlayerList().getPlayers()) require(OWNER_UUID.equals(player.getUUID())
                && USERNAME.equals(player.getGameProfile().getName()), "Unexpected private reload player");
            JsonObject clientReport = readPeer(directory, marker, "client", true);
            if (clientReport != null) require(clientReport.has("passed") && clientReport.get("passed").getAsBoolean(), "Private reload client failed");
            if (owner == null) {
                owner = server.getPlayerList().getPlayerByName(USERNAME);
                if (owner == null) return;
                connection = owner.connection.getConnection(); loadedTick = server.getTickCount();
            }
            require(owner == server.getPlayerList().getPlayerByName(USERNAME) && connection == owner.connection.getConnection()
                && connection.isConnected() && !connection.isMemoryConnection()
                && connection.getRemoteAddress() instanceof InetSocketAddress address && address.getAddress().isLoopbackAddress()
                && owner.serverLevel() == server.overworld() && owner.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
                && !owner.getAbilities().instabuild, "Reload requires unchanged actual survival PLAY TCP owner");
            assertPlayer(owner, baseline, false); assertWorld();
            if (server.getTickCount() - loadedTick < 40) return;
            if (server.getTickCount() % 5 == 0) publish("loaded");
            if (clientReport != null) {
                require(OWNER_UUID.toString().equals(string(clientReport, "ownerUuid"))
                    && clientReport.get("firststepsStage").getAsInt() == 4 && clientReport.get("loadedWithoutGrants").getAsBoolean()
                    && clientReport.get("actualTCP").getAsBoolean() && clientReport.get("acceptedCurrentConnectionOwner").getAsBoolean(),
                    "Client must seal actual freshly accepted complete owner");
                finish(true, null);
            }
        }

        JsonObject state(String phase) {
            JsonObject result = reloadIdentity(marker);
            result.addProperty("status", finished ? "completed" : "running"); result.addProperty("phase", phase);
            result.addProperty("observedUtc", Instant.now().toString()); result.addProperty("serverTick", server.getTickCount());
            result.addProperty("ownerUuid", OWNER_UUID.toString()); result.addProperty("actualTCP", true);
            if (owner != null) result.add("loadedPlayer", playerSnapshot(owner));
            return result;
        }

        void publish(String phase) throws Exception { writeState(directory, "server", state(phase)); }

        void finish(boolean passed, String failure) {
            if (finished) return;
            finished = true;
            JsonObject result = state(passed ? "completed" : "failed");
            try {
                if (passed) {
                    server.getPlayerList().saveAll();
                    require(server.saveEverything(true, true, true), "Actual reload save did not flush");
                    Path file = safePath(directory, world.resolve("playerdata").resolve(OWNER_UUID + ".dat"));
                    assertDisk(NbtIo.readCompressed(file, NbtAccounter.create(4L * 1024 * 1024)));
                    assertWorld();
                    result.addProperty("saveFlushed", true); result.addProperty("diskNbtVerified", true);
                    result.addProperty("diskPlayerBytes", Files.size(file));
                }
                result.addProperty("passed", passed); result.addProperty("status", passed ? "completed" : "failed");
                result.addProperty("dedicatedServer", true); result.addProperty("loadedWithoutGrants", true);
                result.addProperty("noArenaOrMaterialsWritten", true); result.addProperty("researchRequests", 0);
                result.addProperty("noKnowledgeResearchFixtures", true); result.addProperty("newJvmRestartProven", passed);
                result.addProperty("firststepsStage", owner == null ? -1 : ThaumcraftCapabilities.getKnowledge(owner).getResearchStage("FIRSTSTEPS"));
                result.add("persistenceBaseline", baseline.deepCopy());
                if (failure != null) result.addProperty("failure", failure);
                writeTerminal(directory, "server", result); publish(passed ? "completed" : "failed");
                Thaumcraft.log.info("Private TCP restart acceptance passed={}: {}", passed, failure);
            } catch (Exception terminal) {
                Thaumcraft.log.error("Private TCP reload terminal failed", terminal);
                try {
                    if (!Files.exists(directory.resolve("server-report.json"))) {
                        result.addProperty("passed", false); result.addProperty("status", "failed");
                        result.addProperty("failure", terminal.toString()); writeTerminal(directory, "server", result);
                    }
                } catch (Exception ignored) { Thaumcraft.log.error("Unable to record failed TCP reload", ignored); }
            } finally { server.halt(false); }
        }
    }
}
