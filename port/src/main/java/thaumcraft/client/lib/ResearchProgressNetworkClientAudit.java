package thaumcraft.client.lib;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;
import thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit;
import thaumcraft.common.lib.research.catalog.validation.ResearchProgressNetworkAuditIO;

/** DEFAULT OFF private real-TCP browser-request acceptance driver. Only after-production
 * observers and the real PacketDistributor sender are used. No direct handler/service calls,
 * client grants, optimistic NBT changes, GUI actions, reloads or reconnects. Fixture knowledge
 * is seeded ONLY by the guarded server. Both reports are sealed while still connected; clients
 * wait for the actual matching server terminal before stopping. This is not normal acquisition,
 * GUI, reload, reconnect, or a complete research/gameplay port acceptance claim.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ResearchProgressNetworkClientAudit {
    public static final String ENABLE_PROPERTY = "thaumcraft.researchProgressNetworkAudit";
    private static final String RESEARCH = "CELESTIALSCANNING";
    private static final List<String> PHASES = List.of("login", "seed", "negative", "negative-quiet",
        "stale-stage", "stale-stage-quiet", "pay", "paid-sync", "replay", "replay-quiet", "finish");
    private static final List<String> POOLS = List.of("BASICS", "AUROMANCY", "ARTIFICE");
    private static Run active;
    private static boolean attempted;
    private static long initializationDeadline;

    private ResearchProgressNetworkClientAudit() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        Run run = initialize();
        if (run == null || run.finished) return;
        try { run.step(); }
        catch (Exception failure) { run.fail(failure.toString()); }
    }

    /** Root hooks this ONLY after the actual remote SESSION accepted the production payload. */
    public static void catalog(PacketSyncResearchCatalog packet, String acceptedDigest, int entries, int stages) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        Run run = initialize();
        if (run == null || run.finished) return;
        try { run.catalog(packet, acceptedDigest, entries, stages); }
        catch (Exception failure) { run.fail(failure.toString()); }
    }

    /** Root hooks these ONLY after actual LocalPlayer application and source POPUP handling. */
    public static void knowledge(PacketSyncKnowledge packet) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        observe("knowledge", packet.type().id().toString(), packet.data());
    }
    public static void warp(PacketSyncWarp packet) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        observe("warp", packet.type().id().toString(), packet.data());
    }
    private static void observe(String kind, String type, CompoundTag data) {
        Run run = initialize();
        if (run == null || run.finished) return;
        try { run.owner(kind, type, data); }
        catch (Exception failure) { run.fail(failure.toString()); }
    }

    private static Run initialize() {
        if (active != null) return active;
        if (attempted || !Boolean.getBoolean(ENABLE_PROPERTY)) return null;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            require(minecraft.isSameThread(), "Private client audit must initialize on MAIN");
            require(!Boolean.getBoolean(PlayerDataNetworkAudit.ENABLE_PROPERTY)
                && !Boolean.getBoolean(ResearchCatalogNetworkAudit.ENABLE_PROPERTY), "Network audit cohorts are exclusive");
            require(!minecraft.hasSingleplayerServer(), "A remote TCP client is required, never integrated/memory");
            if (initializationDeadline == 0) initializationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(600);
            require(System.nanoTime() <= initializationDeadline, "No actual remote client connection within bounded login timeout");
            // A startup tick is not a fake connection. Do not consume the one initialization
            // until an actual PLAY listener/LocalPlayer exists (first observer may initialize it).
            if (minecraft.getConnection() == null || minecraft.player == null) return null;
            attempted = true;
            active = new Run(minecraft);
        } catch (Exception failure) {
            attempted = true;
            Thaumcraft.log.error("Research-progress client audit refused initialization", failure);
            minecraft.stop();
        }
        return active;
    }

    private static void require(boolean condition, String message) { ResearchCatalogNetworkAudit.require(condition, message); }

    private static final class Run {
        final Minecraft minecraft;
        final String username;
        final String role;
        final boolean clientA;
        final Path directory;
        final JsonObject marker;
        final JsonObject state;
        final Connection connection;
        final LocalPlayer player;
        final JsonArray packets = new JsonArray();
        final JsonArray catalogPackets = new JsonArray();
        final JsonArray knowledgePackets = new JsonArray();
        final JsonArray warpPackets = new JsonArray();
        final JsonArray requests = new JsonArray();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(900);
        long phaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(600);
        String auditPhase = "login";
        String catalogDigest;
        String latestKnowledge;
        String latestWarp;
        int catalogCount;
        int knowledgeCount;
        int warpCount;
        int entryCount;
        int stageCount;
        int sourceCount;
        int ticks;
        int negativeRequestCount;
        int staleStageRequestCount;
        int payRequestCount;
        int replayRequestCount;
        boolean negativeSent;
        boolean staleStageSent;
        boolean paySent;
        boolean replaySent;
        boolean catalogBeforeFirstKnowledge;
        boolean terminalAttempted;
        boolean reported;
        boolean finished;

        Run(Minecraft minecraft) throws Exception {
            this.minecraft = minecraft;
            require(minecraft.isSameThread(), "Actual client observations require MAIN");
            username = minecraft.getUser().getName();
            role = ResearchCatalogNetworkAudit.A.equals(username) ? "client-a"
                : ResearchCatalogNetworkAudit.B.equals(username) ? "client-b" : null;
            require(role != null, "Only the two fixed offline private profiles are allowed");
            clientA = role.equals("client-a");
            require(!minecraft.hasSingleplayerServer() && minecraft.getConnection() != null && minecraft.player != null,
                "Initialization requires an actual remote PLAY client");
            directory = minecraft.gameDirectory.toPath().toRealPath();
            marker = ResearchProgressNetworkAuditIO.validateDirectory(directory, role);
            connection = minecraft.getConnection().getConnection();
            player = minecraft.player;
            checkConnection();
            state = ResearchProgressNetworkAuditIO.identity(marker);
            state.addProperty("username", username);
            state.addProperty("startedUtc", Instant.now().toString());
            state.addProperty("status", "running");
            state.addProperty("guiTested", false);
            state.addProperty("reloadTested", false);
            state.addProperty("reconnectTested", false);
            state.addProperty("clientGrantsOrOptimisticMutations", false);
            state.add("packets", packets);
            state.add("catalogPackets", catalogPackets);
            state.add("knowledgePackets", knowledgePackets);
            state.add("warpPackets", warpPackets);
            state.add("requests", requests);
            state.add("outboundRequests", requests);
            refresh();
            persist(false);
            Thaumcraft.log.info("Private research-progress TCP client observer initialized for {}", username);
        }

        void checkConnection() {
            require(minecraft.isSameThread(), "Payload observations and requests require MAIN");
            require(!minecraft.hasSingleplayerServer() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == connection && minecraft.player == player,
                "Reconnect/LocalPlayer replacement is outside this fresh cohort");
            require(!connection.isMemoryConnection() && connection.isConnected()
                && connection.getRemoteAddress() instanceof InetSocketAddress address
                && address.getAddress() != null && address.getAddress().isLoopbackAddress()
                && address.getPort() == marker.get("port").getAsInt(), "Only the exact guarded loopback TCP port is allowed");
            require(username.equals(player.getGameProfile().getName())
                && ResearchCatalogNetworkAudit.offlineUuid(username).equals(player.getUUID()), "Unexpected actual LocalPlayer owner");
        }

        JsonObject row(String kind, String type) throws Exception {
            require(!reported, "Unexpected applied payload after sealed client transcript");
            checkConnection();
            JsonObject control = control();
            if (control != null) phase(control);
            JsonObject row = new JsonObject();
            row.addProperty("sequence", packets.size() + 1);
            row.addProperty("kind", kind);
            row.addProperty("typeId", type);
            row.addProperty("uuid", player.getUUID().toString());
            row.addProperty("ownerIdentityObserved", true);
            row.addProperty("localPlayerPresent", true);
            row.addProperty("mainThread", minecraft.isSameThread());
            row.addProperty("clientTick", ticks);
            row.addProperty("auditPhase", auditPhase);
            row.addProperty("catalogDigest", ClientResearchCatalogHandlers.acceptedDigestForCurrentConnection());
            row.addProperty("observedUtc", Instant.now().toString());
            return row;
        }

        void catalog(PacketSyncResearchCatalog packet, String accepted, int entries, int stages) throws Exception {
            JsonObject row = row("catalog", packet.type().id().toString());
            String actual = ClientResearchCatalogHandlers.acceptedDigestForCurrentConnection();
            require(packet.digest().equals(accepted) && accepted.equals(actual), "Only the actually applied remote SESSION digest is evidence");
            JsonObject metadata = ResearchCatalogNetworkAudit.catalogMetadata(packet);
            require(entries == 148 && stages == 271 && packet.sources().size() == 8
                && metadata.get("entryCount").getAsInt() == entries && metadata.get("stageCount").getAsInt() == stages,
                "Expected actual unmodified original default catalog 8/148/271");
            require(catalogPackets.size() < 64, "Catalog observations exceed bound");
            catalogDigest = accepted;
            entryCount = entries; stageCount = stages; sourceCount = packet.sources().size(); catalogCount++;
            row.add("catalog", metadata);
            row.addProperty("appliedDigest", accepted);
            catalogPackets.add(row); packets.add(row);
            refresh(); persist(false);
        }

        void owner(String kind, String type, CompoundTag data) throws Exception {
            JsonObject row = row(kind, type);
            require(catalogCount > 0 && catalogDigest != null && catalogDigest.equals(
                ClientResearchCatalogHandlers.acceptedDigestForCurrentConnection()), "Owner application preceded accepted catalog");
            String raw = data.toString();
            require(raw.length() <= 16_384, "Private fixture owner payload exceeds bounded transcript");
            if (kind.equals("knowledge")) {
                require(knowledgePackets.size() < 64, "Knowledge observations exceed bound");
                if (knowledgeCount == 0) catalogBeforeFirstKnowledge = true;
                knowledgeCount++; latestKnowledge = raw; knowledgePackets.add(row);
            } else {
                require(warpPackets.size() < 64, "Warp observations exceed bound");
                warpCount++; latestWarp = raw; warpPackets.add(row);
            }
            row.addProperty("payloadNbt", raw);
            packets.add(row);
            refresh(); persist(false);
        }

        JsonObject control() throws Exception {
            return ResearchProgressNetworkAuditIO.readProgress(directory.getParent(), marker, "server");
        }

        void phase(JsonObject control) {
            String status = control.get("status").getAsString();
            require(!status.equals("failed") && !status.equals("interrupted"), "Server audit terminated unsuccessfully");
            String next = control.get("auditPhase").getAsString();
            // completed can be a terminal phase; it is never a request action.
            if (status.equals("completed")) return;
            int nextIndex = PHASES.indexOf(next), currentIndex = PHASES.indexOf(auditPhase);
            require(nextIndex >= 0 && nextIndex >= currentIndex, "Unknown or backwards guarded audit phase: " + next);
            if (!next.equals(auditPhase)) {
                auditPhase = next;
                phaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(next.equals("login") ? 600 : 120);
            }
        }

        void step() throws Exception {
            ticks++;
            require(System.nanoTime() <= deadline && System.nanoTime() <= phaseDeadline, "Bounded client timeout in " + auditPhase);
            // The server may close TCP after publishing its terminal. The sealed report already
            // proves the live connection; afterwards only the matching bounded terminal is read.
            if (!reported) checkConnection();
            if (ticks % 5 != 0) return;
            JsonObject control = control();
            if (control != null) {
                phase(control);
                String status = control.get("status").getAsString();
                if (status.equals("completed")) {
                    require(reported, "Server completed before both live client transcripts were sealed");
                    JsonObject terminal = ResearchProgressNetworkAuditIO.readTerminal(directory.getParent(), marker, "server");
                    if (terminal == null) return; // A terminal file can exist while its single writer is still writing.
                    require("completed".equals(terminal.get("status").getAsString()), "Actual server terminal did not confirm completion");
                    finished = true;
                    Thaumcraft.log.info("Private research-progress TCP client {} completed after actual server terminal", username);
                    minecraft.stop();
                    return;
                }
                if (!reported) {
                    if (auditPhase.equals("negative") && !negativeSent) negative(control);
                    if (clientA && auditPhase.equals("stale-stage") && !staleStageSent) staleStage(control);
                    if (clientA && auditPhase.equals("pay") && !paySent) pay(control);
                    if (clientA && auditPhase.equals("replay") && !replaySent) replay(control);
                    if (auditPhase.equals("finish")) seal(control);
                }
            }
            if (!reported) { refresh(); persist(false); }
        }

        String actionDigest(JsonObject control) {
            checkConnection();
            String actual = ClientResearchCatalogHandlers.acceptedDigestForCurrentConnection();
            require(catalogCount == 1 && entryCount == 148 && stageCount == 271 && sourceCount == 8
                && actual != null && actual.equals(catalogDigest) && actual.equals(control.get("currentDigest").getAsString()),
                "Requests must use the actual currently applied client SESSION/catalog, not a server query");
            require(RESEARCH.equals(control.get("requestedKey").getAsString()) && control.get("expectedStage").getAsInt() == 1,
                "Unexpected guarded research/stage fixture");
            return actual;
        }

        void requireSeeded() {
            var knowledge = ThaumcraftCapabilities.getKnowledge(player);
            require(knowledgeCount >= 2 && knowledge.getResearchStage(RESEARCH) == 1 && knowledge.isResearchComplete("THEORYRESEARCH"),
                "A may request only after actually applying server seed, not client grants");
            for (String pool : POOLS) require(knowledge.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION,
                ResearchCategories.getResearchCategory(pool)) == 16,
                "Actual server-seeded observation cost missing in " + pool);
        }

        void negative(JsonObject control) throws Exception {
            String actual = actionDigest(control);
            if (clientA) {
                requireSeeded();
                String stale = (actual.charAt(0) == '0' ? "1" : "0") + actual.substring(1);
                for (int i = 0; i < 8; i++) send(RESEARCH, 1, stale, "negative");
            } else {
                require(ThaumcraftCapabilities.getKnowledge(player).getResearchStage("FIRSTSTEPS") == -1,
                    "B must remain the unseeded contextual-owner fixture");
                send("FIRSTSTEPS", -1, actual, "negative");
            }
            negativeSent = true; refresh(); persist(false);
        }

        void staleStage(JsonObject control) throws Exception {
            require(negativeSent && negativeRequestCount == 8, "Stale-stage action must follow actual negative sends");
            String actual = actionDigest(control); requireSeeded();
            send(RESEARCH, 0, actual, "stale-stage");
            staleStageSent = true; refresh(); persist(false);
        }

        void pay(JsonObject control) throws Exception {
            require(negativeSent && staleStageSent && staleStageRequestCount == 1, "Payment must follow negative/stale-stage quiet windows");
            String actual = actionDigest(control); requireSeeded();
            // Capture the legitimate first expectation from the ACTUAL local snapshot. Repeated
            // bodies intentionally retain that same stage; no optimistic local progression occurs.
            int stage = ThaumcraftCapabilities.getKnowledge(player).getResearchStage(RESEARCH);
            for (int i = 0; i < 8; i++) send(RESEARCH, stage, actual, "pay");
            paySent = true; refresh(); persist(false);
        }

        void replay(JsonObject control) throws Exception {
            require(paySent && payRequestCount == 8, "Replay requires actual prior payment sends");
            String actual = actionDigest(control);
            var knowledge = ThaumcraftCapabilities.getKnowledge(player);
            require(knowledgeCount == 3 && knowledge.getResearchStage(RESEARCH) == 2 && knowledge.isResearchComplete(RESEARCH),
                "Replay must follow actual paid COMPLETE owner snapshot, never optimistic mutation");
            for (String pool : POOLS) require(knowledge.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION,
                ResearchCategories.getResearchCategory(pool)) == 0, "Paid raw observation balance not actually applied");
            send(RESEARCH, 1, actual, "replay");
            replaySent = true; refresh(); persist(false);
        }

        void send(String key, int expected, String digest, String phase) throws Exception {
            require(requests.size() < 64, "Bounded private outbound request transcript");
            checkConnection();
            int beforeStage = ThaumcraftCapabilities.getKnowledge(player).getResearchStage(key);
            PacketSyncProgressToServer packet = new PacketSyncProgressToServer(key, expected, digest);
            PacketDistributor.sendToServer(packet); // Actual registered PLAY sender, not direct receive/tryHandle.
            JsonObject row = new JsonObject();
            row.addProperty("sequence", requests.size() + 1);
            row.addProperty("phase", phase);
            row.addProperty("auditPhase", auditPhase);
            row.addProperty("key", packet.researchKey());
            row.addProperty("expectedStage", packet.expectedStage());
            row.addProperty("catalogDigest", packet.catalogDigest());
            row.addProperty("ownerUUID", player.getUUID().toString());
            row.addProperty("typeId", packet.type().id().toString());
            row.addProperty("mainThread", minecraft.isSameThread());
            row.addProperty("clientTick", ticks);
            row.addProperty("localStageBefore", beforeStage);
            row.addProperty("afterActualPacketDistributorCall", true);
            row.addProperty("sentUtc", Instant.now().toString());
            requests.add(row);
            switch (phase) {
                case "negative" -> negativeRequestCount++;
                case "stale-stage" -> staleStageRequestCount++;
                case "pay" -> payRequestCount++;
                case "replay" -> replayRequestCount++;
                default -> throw new IllegalStateException("No arbitrary private request actions");
            }
            refresh(); persist(false); // Partial-burst failures still retain the actual already-sent prefix.
        }

        void seal(JsonObject control) throws Exception {
            actionDigest(control);
            require(catalogBeforeFirstKnowledge && catalogCount == control.get("expectedCatalogCount").getAsInt()
                && knowledgeCount == control.get(clientA ? "expectedKnowledgeCountA" : "expectedKnowledgeCountB").getAsInt()
                && warpCount == control.get("expectedWarpCount").getAsInt(), "Actual final owner/catalog application counts incomplete");
            require(negativeSent && negativeRequestCount == (clientA ? 8 : 1)
                && staleStageRequestCount == (clientA ? 1 : 0) && payRequestCount == (clientA ? 8 : 0)
                && replayRequestCount == (clientA ? 1 : 0) && requests.size() == (clientA ? 18 : 1)
                && (!clientA || staleStageSent && paySent && replaySent), "Actual request actions not sealed exactly once");
            refresh();
            state.addProperty("status", "completed");
            state.addProperty("reportSealedUtc", Instant.now().toString());
            persist(false);
            terminalAttempted = true;
            persist(true); // CREATE_NEW before disconnect; server verifies both reports/live owners/disk state.
            reported = true;
        }

        void refresh() {
            state.addProperty("auditPhase", auditPhase);
            state.addProperty("catalogCount", catalogCount); state.addProperty("catalogHandlers", catalogCount);
            state.addProperty("knowledgeCount", knowledgeCount); state.addProperty("knowledgeHandlers", knowledgeCount);
            state.addProperty("warpCount", warpCount); state.addProperty("warpHandlers", warpCount);
            state.addProperty("catalogBeforeFirstKnowledge", catalogBeforeFirstKnowledge);
            state.addProperty("catalogDigest", catalogDigest); state.addProperty("activeDigest", catalogDigest);
            state.addProperty("currentDigest", ClientResearchCatalogHandlers.acceptedDigestForCurrentConnection());
            state.addProperty("entryCount", entryCount); state.addProperty("stageCount", stageCount); state.addProperty("sourceCount", sourceCount);
            state.addProperty("latestKnowledge", latestKnowledge); state.addProperty("latestWarp", latestWarp);
            state.addProperty("negativeSent", negativeSent); state.addProperty("staleStageSent", staleStageSent);
            state.addProperty("paySent", paySent); state.addProperty("replaySent", replaySent);
            state.addProperty("negativeRequestCount", negativeRequestCount); state.addProperty("staleStageRequestCount", staleStageRequestCount);
            state.addProperty("payRequestCount", payRequestCount); state.addProperty("replayRequestCount", replayRequestCount);
            state.addProperty("requestCount", requests.size()); state.addProperty("mainThread", minecraft.isSameThread());
            state.addProperty("clientTick", ticks); state.addProperty("remoteLoopback", connection != null && !connection.isMemoryConnection());
            state.addProperty("connectionIdentity", System.identityHashCode(connection));
            state.addProperty("connected", connection.isConnected());
            if (minecraft.player != null) {
                var actual = minecraft.player;
                var knowledge = ThaumcraftCapabilities.getKnowledge(actual);
                var rawKnowledge = knowledge.serializeNBT();
                state.addProperty("uuid", actual.getUUID().toString());
                state.addProperty("knowledgeNbt", rawKnowledge.toString());
                state.addProperty("normalizedKnowledgeNbt", ResearchCatalogNetworkAudit.normalizedKnowledge(rawKnowledge).toString());
                state.addProperty("warpNbt", ThaumcraftCapabilities.getWarp(actual).serializeNBT().toString());
                state.addProperty("celestialStage", knowledge.getResearchStage(RESEARCH));
                JsonObject raw = new JsonObject();
                for (String pool : POOLS) raw.addProperty(pool, knowledge.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION,
                    ResearchCategories.getResearchCategory(pool)));
                state.add("rawObservation", raw);
            }
        }

        void persist(boolean terminal) throws Exception {
            ResearchCatalogNetworkAudit.writeJson(directory, terminal ? "client-report.json" : "client-state.json", state, terminal);
        }

        void fail(String detail) {
            if (finished) return;
            finished = true;
            try { refresh(); }
            catch (Exception refreshFailure) { Thaumcraft.log.error("Unable to refresh failing private client audit", refreshFailure); }
            state.addProperty("status", "failed");
            state.addProperty("failure", detail);
            state.addProperty("finishedUtc", Instant.now().toString());
            try { persist(false); }
            catch (Exception journalFailure) { Thaumcraft.log.error("Unable to publish private client failure journal", journalFailure); }
            if (!terminalAttempted) {
                terminalAttempted = true;
                try { persist(true); }
                catch (Exception reportFailure) { Thaumcraft.log.error("Unable to create private client terminal report", reportFailure); }
            }
            Thaumcraft.log.error("Private research-progress TCP client audit failed for {}: {}", username, detail);
            minecraft.stop();
        }
    }
}
