package thaumcraft.client.lib;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;
import thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit;

/** Default-off observer AFTER real MAIN-thread application, not a connection/handler substitute. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ResearchCatalogNetworkClientAudit {
    private static Run active;
    private static boolean attempted;

    private ResearchCatalogNetworkClientAudit() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(ResearchCatalogNetworkAudit.ENABLE_PROPERTY)) return;
        Run run = initialize();
        if (run == null || run.finished) return;
        try { run.step(); }
        catch (Exception failure) { run.fail(failure.toString()); }
    }

    /** Call only after the production remote session has actually published this packet. */
    public static void observeCatalog(PacketSyncResearchCatalog packet, String activeDigest, int entryCount, int stageCount) {
        if (!Boolean.getBoolean(ResearchCatalogNetworkAudit.ENABLE_PROPERTY)) return;
        Run run = initialize();
        if (run == null || run.finished) return;
        try { run.catalog(packet, activeDigest, entryCount, stageCount); }
        catch (Exception failure) { run.fail(failure.toString()); }
    }

    /** Call only after the real LocalPlayer knowledge snapshot/toast handling has returned. */
    public static void observeKnowledge(PacketSyncKnowledge packet) { observeOwner("knowledge", packet.type().id().toString(), packet.data()); }
    public static void observeWarp(PacketSyncWarp packet) { observeOwner("warp", packet.type().id().toString(), packet.data()); }

    private static void observeOwner(String kind, String id, CompoundTag data) {
        if (!Boolean.getBoolean(ResearchCatalogNetworkAudit.ENABLE_PROPERTY)) return;
        Run run = initialize();
        if (run == null || run.finished) return;
        try { run.owner(kind, id, data); }
        catch (Exception failure) { run.fail(failure.toString()); }
    }

    private static Run initialize() {
        if (active != null) return active;
        if (attempted || !Boolean.getBoolean(ResearchCatalogNetworkAudit.ENABLE_PROPERTY)) return null;
        attempted = true;
        try {
            require(!Boolean.getBoolean(PlayerDataNetworkAudit.ENABLE_PROPERTY), "Different client network audits cannot run together");
            active = new Run(Minecraft.getInstance());
        } catch (Exception failure) {
            Thaumcraft.log.error("Research-catalog client audit refused initialization", failure);
            Minecraft.getInstance().stop();
        }
        return active;
    }

    private static void require(boolean condition, String message) { ResearchCatalogNetworkAudit.require(condition, message); }

    private static final class Run {
        private final Minecraft minecraft;
        private final String username;
        private final Path directory;
        private final JsonObject marker;
        private final JsonObject state;
        private final JsonArray packets = new JsonArray();
        private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(900);
        private long phaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(300);
        private String auditPhase = "login";
        private String activeDigest;
        private String entryKey;
        private String installedName;
        private int entryCount;
        private int stageCount;
        private int catalogHandlers;
        private int knowledgeHandlers;
        private int warpHandlers;
        private int ticks;
        private boolean catalogBeforeFirstKnowledge;
        private boolean reported;
        private boolean finished;
        private Connection actualConnection;

        private Run(Minecraft minecraft) throws Exception {
            this.minecraft = minecraft;
            require(minecraft.isSameThread(), "Client recorder must initialize on MAIN thread");
            username = minecraft.getUser().getName();
            String role = ResearchCatalogNetworkAudit.A.equals(username) ? "client-a"
                : ResearchCatalogNetworkAudit.B.equals(username) ? "client-b" : null;
            require(role != null, "Only the two fixed offline test profiles are allowed");
            require(!minecraft.hasSingleplayerServer(), "Real multiplayer TCP required, not an integrated client");
            directory = minecraft.gameDirectory.toPath().toRealPath();
            marker = ResearchCatalogNetworkAudit.validateDirectory(directory, role);
            state = ResearchCatalogNetworkAudit.identity(marker);
            state.addProperty("username", username);
            state.addProperty("startedUtc", Instant.now().toString());
            state.add("packets", packets);
            refresh();
            persist(false);
            Thaumcraft.log.info("Research-catalog real-client recorder initialized for {}", username);
        }

        private void checkConnection(boolean requirePresent) {
            require(minecraft.isSameThread(), "Actual client observations must run on MAIN");
            var listener = minecraft.getConnection();
            if (listener == null) {
                require(!requirePresent, "Applied payload requires an actual TCP listener");
                return;
            }
            Connection connection = listener.getConnection();
            require(!connection.isMemoryConnection() && connection.getRemoteAddress() instanceof InetSocketAddress address
                && address.getAddress() != null && address.getAddress().isLoopbackAddress()
                && address.getPort() == marker.get("port").getAsInt(), "Only the exact private loopback TCP connection is allowed");
            require(actualConnection == null || actualConnection == connection, "Reconnect/replacement is not part of this new-world audit");
            actualConnection = connection;
            if (minecraft.player != null) require(username.equals(minecraft.player.getGameProfile().getName())
                && ResearchCatalogNetworkAudit.offlineUuid(username).equals(minecraft.player.getUUID()), "Unexpected actual LocalPlayer identity");
        }

        private JsonObject row(String kind, String typeId) {
            require(!reported, "Unexpected payload after the terminal client transcript was sealed");
            checkConnection(true);
            JsonObject row = new JsonObject();
            row.addProperty("sequence", packets.size() + 1);
            row.addProperty("kind", kind);
            row.addProperty("typeId", typeId);
            row.addProperty("uuid", minecraft.player == null ? ResearchCatalogNetworkAudit.offlineUuid(username).toString() : minecraft.player.getUUID().toString());
            row.addProperty("localPlayerPresent", minecraft.player != null);
            row.addProperty("auditPhase", auditPhase);
            row.addProperty("observedUtc", Instant.now().toString());
            return row;
        }

        private void catalog(PacketSyncResearchCatalog packet, String acceptedDigest, int entries, int stages) throws Exception {
            JsonObject row = row("catalog", packet.type().id().toString());
            require(packet.digest().equals(acceptedDigest), "Observer is not the actually accepted client catalog");
            JsonObject packetMetadata = ResearchCatalogNetworkAudit.catalogMetadata(packet);
            require(packetMetadata.get("entryCount").getAsInt() == entries && packetMetadata.get("stageCount").getAsInt() == stages
                && entries == 148 && stages == 271, "Actual remote session/transport metadata count mismatch");
            JsonObject control = control();
            require(control != null && control.has("entryKey"), "Server control identity/key must predate a production login packet");
            entryKey = control.get("entryKey").getAsString();
            var actualEntry = ResearchCategories.getResearch(entryKey);
            require(actualEntry != null, "Production client catalog did not install the audited entry");
            installedName = actualEntry.getName();
            activeDigest = acceptedDigest;
            entryCount = entries;
            stageCount = stages;
            catalogHandlers++;
            require(catalogHandlers <= 3, "Duplicate/unexpected authoritative catalog application");
            row.add("catalog", packetMetadata);
            row.addProperty("appliedDigest", acceptedDigest);
            row.addProperty("installedName", installedName);
            packets.add(row);
            require(packets.size() <= 64, "Unbounded actual client payload transcript");
            refresh();
            persist(false);
        }

        private void owner(String kind, String typeId, CompoundTag data) throws Exception {
            JsonObject row = row(kind, typeId);
            require(minecraft.player != null, "Owner handler observation requires actual LocalPlayer");
            if (kind.equals("knowledge")) {
                require(catalogHandlers > 0 && activeDigest != null, "Actual knowledge handler preceded catalog publication");
                if (knowledgeHandlers == 0) catalogBeforeFirstKnowledge = true;
                knowledgeHandlers++;
            } else warpHandlers++;
            row.addProperty("payloadNbt", data.toString());
            packets.add(row);
            require(packets.size() <= 64, "Unbounded actual client payload transcript");
            refresh();
            persist(false);
        }

        private JsonObject control() throws Exception {
            JsonObject value = ResearchCatalogNetworkAudit.readProgress(directory.getParent(), "server", "server-state.json");
            if (value != null) ResearchCatalogNetworkAudit.verifyIdentity(marker, value, "server");
            return value;
        }

        private void step() throws Exception {
            ticks++;
            require(System.nanoTime() <= deadline && System.nanoTime() <= phaseDeadline, "Bounded actual-client timeout in " + auditPhase);
            if (!reported) checkConnection(false);
            if (ticks % 5 != 0) return;
            JsonObject server = control();
            if (server != null) {
                String next = server.get("auditPhase").getAsString();
                if (!next.equals(auditPhase)) {
                    auditPhase = next;
                    phaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(next.equals("login") ? 300 : 120);
                }
                String status = server.get("status").getAsString();
                require(!"failed".equals(status) && !"interrupted".equals(status), "Server audit terminated unsuccessfully in " + auditPhase);
                if ("finish".equals(auditPhase) && !reported) {
                    require(minecraft.player != null && catalogHandlers == server.get("expectedCatalogHandlersAtFinish").getAsInt()
                        && knowledgeHandlers >= 2 && warpHandlers >= 2 && catalogBeforeFirstKnowledge
                        && activeDigest.equals(server.get("canonicalDigest").getAsString())
                        && installedName.equals(server.get("canonicalName").getAsString()), "Actual client terminal catalog/owner evidence is incomplete");
                    refresh();
                    state.addProperty("status", "completed");
                    state.addProperty("reportSealedUtc", Instant.now().toString());
                    persist(false);
                    persist(true); // CREATE_NEW while still connected; server compares both reports.
                    reported = true;
                }
                if ("completed".equals(status)) {
                    require(reported, "Server completion preceded the actual client terminal report");
                    Path terminalPath = ResearchCatalogNetworkAudit.safePath(directory.getParent(), directory.getParent().resolve("server/server-report.json"));
                    JsonObject terminal = ResearchCatalogNetworkAudit.readTerminalDocument(terminalPath, ResearchCatalogNetworkAudit.MAX_JSON_BYTES);
                    if (terminal == null) return; // The completed journal precedes the independent CREATE_NEW report.
                    ResearchCatalogNetworkAudit.verifyIdentity(marker, terminal, "server");
                    require("completed".equals(terminal.get("status").getAsString()), "Server terminal report did not confirm completed acceptance");
                    finished = true;
                    Thaumcraft.log.info("Research-catalog real-client audit completed for {} after server terminal", username);
                    minecraft.stop();
                    return;
                }
            }
            if (!reported) { refresh(); persist(false); }
        }

        private void refresh() {
            state.addProperty("auditPhase", auditPhase);
            state.addProperty("catalogHandlers", catalogHandlers);
            state.addProperty("knowledgeHandlers", knowledgeHandlers);
            state.addProperty("warpHandlers", warpHandlers);
            state.addProperty("catalogBeforeFirstKnowledge", catalogBeforeFirstKnowledge);
            state.addProperty("remoteLoopback", actualConnection != null);
            state.addProperty("activeDigest", activeDigest);
            state.addProperty("entryCount", entryCount);
            state.addProperty("stageCount", stageCount);
            state.addProperty("installedName", installedName);
            if (entryKey != null) state.addProperty("entryKey", entryKey);
            if (minecraft.player != null) {
                state.addProperty("uuid", minecraft.player.getUUID().toString());
                state.addProperty("knowledgeNbt", ThaumcraftCapabilities.getKnowledge(minecraft.player).serializeNBT().toString());
                state.addProperty("warpNbt", ThaumcraftCapabilities.getWarp(minecraft.player).serializeNBT().toString());
            }
        }

        private void persist(boolean terminal) throws Exception {
            ResearchCatalogNetworkAudit.writeJson(directory, terminal ? "client-report.json" : "client-state.json", state, terminal);
        }

        private void fail(String detail) {
            if (finished) return;
            finished = true;
            try {
                if (!reported) refresh();
                state.addProperty("status", "failed");
                state.addProperty("failure", detail);
                state.addProperty("finishedUtc", Instant.now().toString());
                try { persist(false); }
                catch (Exception journalFailure) { Thaumcraft.log.error("Unable to publish catalog client audit failure", journalFailure); }
                if (!reported) persist(true); // Never overwrite a sealed CREATE_NEW report.
                Thaumcraft.log.error("Research-catalog real-client audit failed for {}: {}", username, detail);
            } catch (Exception reportFailure) {
                Thaumcraft.log.error("Unable to write private research-catalog client report", reportFailure);
            } finally { minecraft.stop(); }
        }
    }
}
