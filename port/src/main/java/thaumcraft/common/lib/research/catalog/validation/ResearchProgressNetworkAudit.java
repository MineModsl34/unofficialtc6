package thaumcraft.common.lib.research.catalog.validation;

import static thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.A;
import static thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.B;
import static thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.catalogMetadata;
import static thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.normalizedKnowledge;
import static thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.offlineUuid;
import static thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.ownerState;
import static thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.require;
import static thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.safePath;
import static thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.sameOwnerState;
import static thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.writeJson;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;
import thaumcraft.common.lib.network.playerdata.ServerResearchRequestHandler;
import thaumcraft.common.lib.research.ServerResearchProgression;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;
import thaumcraft.common.lib.research.catalog.ResearchCatalogPublication;
import thaumcraft.common.lib.research.catalog.ResearchCatalogRuntime;
import thaumcraft.datagen.TCResearchDataProvider;

/**
 * DEFAULT OFF, private first-cohort real PLAY/TCP progression recorder. Observers are called
 * only after the production contextual C2S handler or real production S2C send. No sender,
 * handler, research event or catalog is replaced. Fresh marker-guarded A alone receives an
 * explicit stage/knowledge fixture, not a claim of ordinary book/research acquisition.
 * This cohort proves neither a GUI nor progression reload/reconnect (separate future cohorts).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class ResearchProgressNetworkAudit {
    public static final String ENABLE_PROPERTY = ResearchProgressNetworkAuditIO.ENABLE_PROPERTY;
    public static final String REQUESTED_KEY = "CELESTIALSCANNING";
    public static final int EXPECTED_STAGE = 1;
    private static final List<String> OBSERVATION_POOLS = List.of("BASICS", "ARTIFICE", "AUROMANCY");
    private static Run active;

    private ResearchProgressNetworkAudit() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        try {
            require(!Boolean.getBoolean(PlayerDataNetworkAudit.ENABLE_PROPERTY)
                && !Boolean.getBoolean(ResearchCatalogNetworkAudit.ENABLE_PROPERTY), "Network audit cohorts are mutually exclusive");
            active = new Run(event.getServer());
        } catch (Exception failure) {
            Thaumcraft.log.error("Research-progression TCP audit refused initialization", failure);
            event.getServer().halt(false);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Run run = active;
        if (run == null || run.finished || event.getServer() != run.server) return;
        try { run.step(); }
        catch (Exception failure) { run.finish("failed", failure.toString()); }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        Run run = active;
        if (run != null && !run.finished && event.getServer() == run.server) {
            run.finish("interrupted", "Server stopped before real progression-client evidence completed");
        }
    }

    /** ONLY the actual receive/enqueueWork route calls this after authoritative tryHandle. */
    public static void observeRequest(ServerPlayer owner, PacketSyncProgressToServer packet,
                                      ServerResearchRequestHandler.Result result) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        Run run = active;
        if (run == null || run.finished || owner.getServer() != run.server) return;
        try { run.request(owner, packet, result); }
        catch (Exception failure) { run.finish("failed", failure.toString()); }
    }

    /** ONLY after PacketDistributor.sendToPlayer, never a GameTest capture or mock handler. */
    public static void observeSent(ServerPlayer owner, CustomPacketPayload packet) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        Run run = active;
        if (run == null || run.finished || owner.getServer() != run.server) return;
        try { run.sent(owner, packet); }
        catch (Exception failure) { run.finish("failed", failure.toString()); }
    }

    private static final class Run {
        final MinecraftServer server;
        final Path directory;
        final Path world;
        final JsonObject marker;
        final PacketSyncResearchCatalog canonical;
        final ResearchCatalogPublication.Snapshot accepted;
        final JsonObject report;
        final JsonArray sends = new JsonArray();
        final JsonArray requests = new JsonArray();
        final JsonArray checks = new JsonArray();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(900);
        long phaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(600);
        String phase = "login";
        int phaseTick;
        int revision;
        int paidTick;
        int appliedCount;
        boolean finished;
        ServerPlayer actualA;
        ServerPlayer actualB;
        IPlayerKnowledge knowledgeA;
        IPlayerKnowledge knowledgeB;
        IPlayerWarp warpA;
        IPlayerWarp warpB;
        JsonObject seedA;
        JsonObject baselineB;
        JsonObject paidRawA;
        JsonObject paidLocalA;
        JsonObject finalA;
        JsonObject seedExperienceA;
        JsonObject baselineExperienceB;
        JsonObject paidExperienceA;

        Run(MinecraftServer server) throws Exception {
            this.server = server;
            require(server.isSameThread(), "Audit must initialize on the owning server thread");
            directory = Path.of(System.getProperty("user.dir")).toRealPath();
            marker = ResearchProgressNetworkAuditIO.validateDirectory(directory, "server");
            require(server.isDedicatedServer() && "127.0.0.1".equals(server.getLocalIp())
                && !server.usesAuthentication() && server.getPort() == marker.get("port").getAsInt(), "Exact offline loopback dedicated server required");
            require(ResearchProgressNetworkAuditIO.NETWORK_PROTOCOL_VERSION.equals(PacketHandler.PROTOCOL_VERSION),
                "Actual registered network protocol must match the prepared audit protocol");
            world = safePath(directory, server.getWorldPath(LevelResource.ROOT).toRealPath());
            require(world.getParent().equals(directory)
                && ResearchCatalogNetworkAudit.WORLD.equals(world.getFileName().toString()), "Only the prepared fresh audit world is allowed");
            Path players = safePath(directory, world.resolve("playerdata"));
            if (Files.isDirectory(players, LinkOption.NOFOLLOW_LINKS)) try (var files = Files.list(players)) {
                require(files.noneMatch(path -> path.getFileName().toString().endsWith(".dat")), "Existing player saves are forbidden");
            }
            require(server.getPlayerCount() == 0, "No player may predate this new-world audit");
            canonical = ResearchCatalogRuntime.currentPayload(server);
            accepted = ResearchCatalogRuntime.currentSnapshot(server);
            require(canonical != null && accepted != null, "Production startup must accept a catalog first");
            JsonObject metadata = catalogMetadata(canonical);
            require(metadata.get("sourceCount").getAsInt() == 8 && metadata.get("entryCount").getAsInt() == 148
                && metadata.get("stageCount").getAsInt() == 271, "Unexpected source catalog dimensions");
            require(canonical.sources().size() == TCResearchDataProvider.CANONICAL_DEFAULTS.size(), "Exactly eight canonical registrations required");
            for (var expected : TCResearchDataProvider.CANONICAL_DEFAULTS) {
                String source = canonical.sources().get(expected.registration());
                require(source != null, "Missing canonical runtime source " + expected.registration());
                byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
                require(bytes.length == expected.byteCount() && Hashing.sha256().hashBytes(bytes).toString().equals(expected.sha256()),
                    "Runtime catalog differs from the executed-source manifest");
            }
            require(accepted.entries().get(REQUESTED_KEY) == ResearchCategories.getResearch(REQUESTED_KEY)
                && accepted.entries().get("THEORYRESEARCH") == ResearchCategories.getResearch("THEORYRESEARCH")
                && ResearchCategories.getResearch(REQUESTED_KEY).getStages().length == 1,
                "Current accepted fixture entries must retain actual source identities/stages");
            report = ResearchProgressNetworkAuditIO.identity(marker);
            report.addProperty("startedUtc", Instant.now().toString());
            report.addProperty("networkProtocolVersion", PacketHandler.PROTOCOL_VERSION);
            report.addProperty("requestedKey", REQUESTED_KEY);
            report.addProperty("expectedStage", EXPECTED_STAGE);
            report.add("canonicalCatalog", metadata);
            report.add("actualProductionSendTrace", sends);
            report.add("actualContextualRequestTrace", requests);
            report.add("checks", checks);
            report.addProperty("ordinaryAcquisitionProven", false);
            report.addProperty("guiProven", false);
            report.addProperty("progressionReloadProven", false);
            report.addProperty("progressionReconnectProven", false);
            phaseTick = server.getTickCount();
            publish("running");
            Thaumcraft.log.info("Research-progression TCP audit waiting for {} and {} on localhost:{}", A, B, server.getPort());
        }

        void owner(ServerPlayer player) {
            require(server.isSameThread() && player.getServer() == server, "Actual observer must run on owning MAIN server thread");
            String name = player.getGameProfile().getName();
            require((A.equals(name) || B.equals(name)) && offlineUuid(name).equals(player.getUUID()), "Unexpected private TCP owner");
            var connection = player.connection.getConnection();
            require(connection.isConnected() && !connection.isMemoryConnection()
                && connection.getRemoteAddress() instanceof InetSocketAddress address
                && address.getAddress() != null && address.getAddress().isLoopbackAddress(), "Actual connected loopback TCP required");
            require((actualA == null || !A.equals(name) || player == actualA)
                && (actualB == null || !B.equals(name) || player == actualB), "Reconnect/respawn is outside this cohort");
        }

        void currentCatalog() {
            require(ResearchCatalogRuntime.currentPayload(server) == canonical
                && ResearchCatalogRuntime.currentSnapshot(server) == accepted, "Reload/catalog replacement is outside this cohort");
            for (var entry : accepted.entries().entrySet()) require(ResearchCategories.getResearch(entry.getKey()) == entry.getValue(),
                "Accepted public entry identity changed during progression audit");
        }

        void sent(ServerPlayer player, CustomPacketPayload packet) throws Exception {
            String kind = packet instanceof PacketSyncResearchCatalog ? "catalog"
                : packet instanceof PacketSyncKnowledge ? "knowledge" : packet instanceof PacketSyncWarp ? "warp" : null;
            if (kind == null) return;
            owner(player);
            currentCatalog();
            String name = player.getGameProfile().getName();
            require(sends.size() < 64, "Unbounded production send transcript");
            if (kind.equals("catalog")) require(sentCount(name, kind) == 0, "Unexpected duplicate/reloaded catalog");
            else require(sentCount(name, "catalog") == 1, "Personal data preceded authoritative catalog");
            if (!phase.equals("login")) {
                require(A.equals(name) && kind.equals("knowledge") && isExpectedDirtyPhase(phase), "Unexpected personal-data send outside source dirty20 path");
                require(player.tickCount % 20 == 0, "Actual seeded/paid snapshot was not sent at owner tick20");
                int previous = sentCount(name, kind);
                require((previous == 1 && seedA != null) || (previous == 2 && paidRawA != null), "Unexpected additional personal-data snapshot");
                CompoundTag expected = TagParser.parseTag((previous == 1 ? seedA : paidRawA).get("knowledgeNbt").getAsString());
                require(normalizedKnowledge(((PacketSyncKnowledge) packet).data()).equals(normalizedKnowledge(expected)), "Actual dirty20 raw packet differs from full expected owner NBT");
            }
            JsonObject row = observedRow(player, sends.size() + 1);
            row.addProperty("kind", kind);
            row.addProperty("typeId", packet.type().id().toString());
            if (packet instanceof PacketSyncResearchCatalog catalog) {
                require(catalogMetadata(canonical).equals(catalogMetadata(catalog)), "Actual sent catalog differs from accepted runtime payload");
                row.add("catalog", catalogMetadata(catalog));
            } else row.addProperty("payloadNbt", packet instanceof PacketSyncKnowledge knowledge ? knowledge.data().toString() : ((PacketSyncWarp) packet).data().toString());
            sends.add(row);
        }

        boolean isExpectedDirtyPhase(String value) { return value.equals("seed") || value.equals("pay") || value.equals("paid-sync"); }

        void request(ServerPlayer player, PacketSyncProgressToServer packet, ServerResearchRequestHandler.Result result) throws Exception {
            owner(player);
            currentCatalog();
            require(packet != null && result != null && requests.size() < 64, "Missing/unbounded actual contextual request evidence");
            String name = player.getGameProfile().getName();
            // Retain actual evidence even if a later scenario assertion rejects this request.
            JsonObject row = observedRow(player, requests.size() + 1);
            row.addProperty("phase", phase);
            row.addProperty("key", packet.researchKey());
            row.addProperty("expectedStage", packet.expectedStage());
            row.addProperty("catalogDigest", packet.catalogDigest());
            row.addProperty("ownerUUID", player.getUUID().toString());
            row.addProperty("status", result.status().name());
            row.addProperty("detail", result.detail());
            if (result.progression() != null) row.addProperty("progressionStatus", result.progression().status().name());
            row.add("actualOwnerAfterRequest", ownerState(player));
            requests.add(row);
            require(List.of("negative", "stale-stage", "pay", "replay").contains(phase), "Unexpected request phase " + phase);
            String expectedKey = B.equals(name) ? "FIRSTSTEPS" : REQUESTED_KEY;
            int expectedStage = B.equals(name) ? -1 : phase.equals("stale-stage") ? 0 : EXPECTED_STAGE;
            String expectedDigest = A.equals(name) && phase.equals("negative") ? staleDigest() : canonical.digest();
            require(packet.researchKey().equals(expectedKey) && packet.expectedStage() == expectedStage
                && packet.catalogDigest().equals(expectedDigest), "Actual contextual request body differs from controlled client scenario");
            require(A.equals(name) || phase.equals("negative"), "B may send only its own missing-parent negative request");
            int limit = phase.equals("negative") && A.equals(name) || phase.equals("pay") ? 8 : 1;
            require(requestCount(name, phase) <= limit, "Duplicate/unbounded phase request burst");
            if (phase.equals("negative")) {
                if (A.equals(name)) require(result.status() == ServerResearchRequestHandler.Status.STALE_CATALOG
                    || result.status() == ServerResearchRequestHandler.Status.RATE_LIMITED, "Stale digest did not reject before research mutation");
                else require(result.status() == ServerResearchRequestHandler.Status.BROWSER_UNAVAILABLE
                    && result.detail().equals("MISSING_PARENTS"), "B missing-parent attempt was not independently rejected");
                unchangedSeed();
            } else if (phase.equals("stale-stage")) {
                require(result.status() == ServerResearchRequestHandler.Status.PROGRESSION_REJECTED && result.progression() != null
                    && result.progression().status() == ServerResearchProgression.Status.STALE_STAGE, "Actual current-digest stale stage was not rejected by checked progression");
                unchangedSeed();
            } else if (phase.equals("pay")) {
                if (result.applied()) {
                    require(appliedCount++ == 0, "Identical burst paid more than once");
                    paidTick = server.getTickCount();
                    assertData(paidRawA, actualA);
                    assertData(baselineB, actualB);
                    observation(actualA, 0);
                    require(actualA.totalExperience == seedExperienceA.get("totalExperience").getAsInt() + 5,
                        "Paid source transition did not grant exactly five experience points");
                    paidExperienceA = experience(actualA);
                    require(PlayerDataSync.isKnowledgeDirty(actualA), "Actual paid transition did not queue source knowledge dirty20 sync");
                    report.add("actualPaidOwnerBeforePopupClear", ownerState(actualA));
                    report.addProperty("paidServerTick", paidTick);
                    report.addProperty("paidOwnerTick", actualA.tickCount);
                } else require(result.status() == ServerResearchRequestHandler.Status.RATE_LIMITED
                    || result.status() == ServerResearchRequestHandler.Status.BROWSER_UNAVAILABLE && result.detail().equals("COMPLETE"),
                    "Duplicate paid burst was not rejected by rate/complete boundary");
                require(appliedCount == 1, "Valid first payable request unexpectedly rejected");
                unchangedPaid(sentCount(A, "knowledge") >= 3);
            } else {
                require(result.status() == ServerResearchRequestHandler.Status.BROWSER_UNAVAILABLE && result.detail().equals("COMPLETE"),
                    "Completed-entry replay should reject at the browser gate before checked progression");
                unchangedFinal();
                noPending();
            }
        }

        JsonObject observedRow(ServerPlayer player, int sequence) {
            JsonObject row = new JsonObject();
            row.addProperty("sequence", sequence);
            row.addProperty("serverTick", server.getTickCount());
            row.addProperty("ownerTick", player.tickCount);
            row.addProperty("auditPhase", phase);
            row.addProperty("owner", player.getGameProfile().getName());
            row.addProperty("uuid", player.getUUID().toString());
            row.addProperty("mainThread", server.isSameThread());
            return row;
        }

        void step() throws Exception {
            require(server.isSameThread() && System.nanoTime() <= deadline && System.nanoTime() <= phaseDeadline, "Bounded actual-TCP audit timeout in " + phase);
            currentCatalog();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) owner(player);
            if (server.getTickCount() % 5 != 0) return;
            JsonObject clientA = ResearchProgressNetworkAuditIO.readProgress(directory.getParent(), marker, "client-a");
            JsonObject clientB = ResearchProgressNetworkAuditIO.readProgress(directory.getParent(), marker, "client-b");
            for (JsonObject client : new JsonObject[]{clientA, clientB}) if (client != null) require(!client.has("status")
                || !List.of("failed", "interrupted").contains(client.get("status").getAsString()), "Actual client audit failed");
            if (phase.equals("login")) {
                ServerPlayer a = server.getPlayerList().getPlayerByName(A);
                ServerPlayer b = server.getPlayerList().getPlayerByName(B);
                if (a == null || b == null || !ready(clientA) || !ready(clientB)) return;
                owner(a); owner(b);
                if (!sameOwnerState(ownerState(a), clientA) || !sameOwnerState(ownerState(b), clientB)) return;
                require(server.getPlayerCount() == 2, "Exactly two actual TCP players required");
                actualA = a; actualB = b;
                knowledgeA = ThaumcraftCapabilities.getKnowledge(a); knowledgeB = ThaumcraftCapabilities.getKnowledge(b);
                warpA = ThaumcraftCapabilities.getWarp(a); warpB = ThaumcraftCapabilities.getWarp(b);
                require(!knowledgeA.isResearchKnown(REQUESTED_KEY) && !knowledgeA.isResearchKnown("THEORYRESEARCH")
                    && !knowledgeB.isResearchKnown("!gotthaumonomicon") && !knowledgeB.isResearchKnown("FIRSTSTEPS"), "New private player fixtures must not preexist");
                noPending();
                comparePackets(clientA, A, 1); comparePackets(clientB, B, 1);
                baselineB = ownerState(b); baselineExperienceB = experience(b);
                JsonObject initial = new JsonObject(); initial.add(A, ownerState(a)); initial.add(B, baselineB.deepCopy());
                report.add("actualInitialOwnerStates", initial);
                check("actual-login-catalog-before-personal-data", "Two real offline TCP owners, authoritative eight-source catalog before knowledge/warp handlers");
                seed();
                advance("seed");
                return;
            }
            require(actualA != null && actualB != null && server.getPlayerList().getPlayerByName(A) == actualA
                && server.getPlayerList().getPlayerByName(B) == actualB && server.getPlayerCount() == 2, "Both original TCP sessions must remain connected");
            owner(actualA); owner(actualB);
            if (phase.equals("seed")) {
                assertData(seedA, actualA); assertData(baselineB, actualB); assertExperience(baselineExperienceB, actualB);
                if (!receivedOwners(clientA, clientB, seedA, 2)) return;
                noPending(); assertCounts(2);
                check("explicit-owner-a-fixture-dirty20", "Server-only THEORY complete/CELESTIAL stage1/raw OBS16x3 fixture reached A through actual owner tick20; B unchanged");
                advance("negative");
            } else if (phase.equals("negative")) {
                unchangedSeed(); assertCounts(2); noPending();
                if (!seal(clientA, "negativeSent") || !seal(clientB, "negativeSent")
                    || requestCount(A, phase) != 8 || requestCount(B, phase) != 1) return;
                require(statusCount(A, phase, "STALE_CATALOG") >= 1 && statusCount(A, phase, "RATE_LIMITED") >= 1,
                    "Actual stale-digest burst must demonstrate both content rejection and server rate limiting");
                check("negative-owner-and-stale-digest", "A stale digest burst and B missing-parent request reject with unchanged full NBT/raw balances/XP and no dirty sends");
                advance("negative-quiet");
            } else if (phase.equals("negative-quiet")) {
                unchangedSeed(); assertCounts(2); noPending();
                if (elapsed(phaseTick) < 10) return;
                advance("stale-stage");
            } else if (phase.equals("stale-stage")) {
                unchangedSeed(); assertCounts(2); noPending();
                if (!seal(clientA, "staleStageSent") || requestCount(A, phase) != 1) return;
                check("actual-current-catalog-stale-stage", "Current digest/owner/parents accepted, expected stage0 rejected against server stage1 before any payment");
                advance("stale-stage-quiet");
            } else if (phase.equals("stale-stage-quiet")) {
                unchangedSeed(); assertCounts(2); noPending();
                if (elapsed(phaseTick) < 10) return;
                advance("pay");
            } else if (phase.equals("pay")) {
                if (!seal(clientA, "paySent") || requestCount(A, phase) != 8) return;
                require(appliedCount == 1 && statusCount(A, phase, "APPLIED") == 1, "Exactly one identical valid burst request must pay");
                unchangedPaid(sentCount(A, "knowledge") >= 3);
                check("paid-exactly-once", "Eight identical actual C2S requests, one applied stage2 transition, OBS16x3 paid once, XP+5, remaining rate/complete rejects");
                advance("paid-sync");
            } else if (phase.equals("paid-sync")) {
                unchangedPaid(sentCount(A, "knowledge") >= 3);
                if (!receivedOwners(clientA, clientB, paidLocalA, 3)) return;
                noPending(); assertCounts(3);
                finalA = ownerState(actualA);
                report.add("actualFinalOwnerA", finalA);
                report.add("actualUnchangedOwnerB", ownerState(actualB));
                report.add("actualPaidExperienceA", paidExperienceA);
                check("actual-paid-dirty20-owner-only", "Raw paid snapshot precedes source POPUP clear; actual client/local and server full NBT agree afterward; no B knowledge/warp resync");
                if (elapsed(paidTick) < 20) return;
                advance("replay");
            } else if (phase.equals("replay")) {
                unchangedFinal(); assertCounts(3); noPending();
                if (!seal(clientA, "replaySent") || requestCount(A, phase) != 1) return;
                check("actual-completed-entry-replay", "Same expected stage1 body rejects as BROWSER_UNAVAILABLE/COMPLETE; source browser gate precedes checked stage gate");
                advance("replay-quiet");
            } else if (phase.equals("replay-quiet")) {
                unchangedFinal(); assertCounts(3); noPending();
                if (elapsed(phaseTick) < 40) return;
                require(receivedOwners(clientA, clientB, finalA, 3), "Client full owner state changed during forty-tick replay quiet window");
                check("rejected-replay-no-dirty20", "Forty subsequent server ticks: no debit/progression/XP/full NBT change or duplicate personal snapshot");
                advance("finish");
            } else if (phase.equals("finish")) {
                unchangedFinal(); assertCounts(3); noPending();
                JsonObject terminalA = ResearchProgressNetworkAuditIO.readTerminal(directory.getParent(), marker, "client-a");
                JsonObject terminalB = ResearchProgressNetworkAuditIO.readTerminal(directory.getParent(), marker, "client-b");
                if (terminalA == null || terminalB == null) return;
                require("completed".equals(terminalA.get("status").getAsString())
                    && "completed".equals(terminalB.get("status").getAsString()), "Actual client terminal reports must both complete while connected");
                comparePackets(terminalA, A, 3); comparePackets(terminalB, B, 1);
                compareRequests(terminalA, A, 18); compareRequests(terminalB, B, 1);
                require(sameOwnerState(finalA, terminalA) && sameOwnerState(baselineB, terminalB), "Terminal local full owner NBT differs from actual server owner");
                unchangedFinal(); // Recheck live owner state immediately before saving, not just client reports.
                report.add("actualClientA", terminalA); report.add("actualClientB", terminalB);
                check("actual-context-owner-and-transcripts", "Exact ordered raw request bodies and S2C payloads match real contextual receive/actual client handlers; no foreign owner data");
                phase = "completed";
                finish("completed", null);
            } else throw new IllegalStateException("Unknown private audit phase " + phase);
        }

        void seed() throws Exception {
            int theoryStages = ResearchCategories.getResearch("THEORYRESEARCH").getStages().length;
            require(knowledgeA.addResearch("THEORYRESEARCH") && knowledgeA.setResearchStage("THEORYRESEARCH", theoryStages + 1)
                && knowledgeA.addResearch(REQUESTED_KEY) && knowledgeA.setResearchStage(REQUESTED_KEY, EXPECTED_STAGE), "Cannot create explicit new-player stage fixture");
            for (String category : OBSERVATION_POOLS) {
                var pool = ResearchCategories.getResearchCategory(category);
                require(pool != null && knowledgeA.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, pool) == 0
                    && knowledgeA.addKnowledge(EnumKnowledgeType.OBSERVATION, pool, 16), "Cannot create explicit raw observation fixture");
            }
            seedA = ownerState(actualA);
            seedExperienceA = experience(actualA);
            observation(actualA, 16);
            paidRawA = expectedPayment(seedA);
            paidLocalA = clearPopupCopy(paidRawA);
            report.add("explicitSeedOwnerA", seedA);
            report.add("explicitSeedExperienceA", seedExperienceA);
            report.add("expectedPaidOwnerBeforePopupClear", paidRawA);
            report.add("expectedPaidOwnerAfterPopupClear", paidLocalA);
            PlayerDataSync.markKnowledgeDirty(actualA); // Fixture only: ordinary payment later must mark itself.
        }

        JsonObject expectedPayment(JsonObject before) throws Exception {
            JsonObject result = before.deepCopy();
            CompoundTag knowledge = TagParser.parseTag(before.get("knowledgeNbt").getAsString());
            boolean found = false;
            for (Tag item : knowledge.getList("research", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) item;
                if (REQUESTED_KEY.equals(row.getString("key"))) {
                    require(row.getInt("stage") == 1 && !row.contains("flags"), "Unexpected fixture research flags/stage");
                    row.putInt("stage", 2);
                    row.putString("flags", "POPUP,RESEARCH");
                    found = true;
                }
            }
            require(found, "Missing fixture entry in full source NBT");
            ListTag retained = new ListTag();
            int removed = 0;
            for (Tag item : knowledge.getList("knowledge", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) item;
                if (OBSERVATION_POOLS.stream().anyMatch(pool -> ("O_" + pool).equals(row.getString("key")))) {
                    require(row.getInt("amount") == 16, "Unexpected observation fixture raw amount");
                    removed++;
                } else retained.add(row.copy());
            }
            require(removed == 3, "Expected exactly three paid raw knowledge rows");
            knowledge.put("knowledge", retained); // Source zero pools are intentionally absent from serialized NBT.
            result.addProperty("knowledgeNbt", knowledge.toString());
            return result;
        }

        JsonObject clearPopupCopy(JsonObject raw) throws Exception {
            JsonObject result = raw.deepCopy();
            CompoundTag knowledge = TagParser.parseTag(raw.get("knowledgeNbt").getAsString());
            for (Tag item : knowledge.getList("research", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) item;
                if (row.contains("flags", Tag.TAG_STRING)) row.putString("flags", String.join(",",
                    java.util.Arrays.stream(row.getString("flags").split(",")).filter(flag -> !flag.equals("POPUP")).toList()));
            }
            result.addProperty("knowledgeNbt", knowledge.toString());
            return result;
        }

        boolean ready(JsonObject client) {
            return client != null && client.has("uuid") && client.has("knowledgeNbt") && client.has("warpNbt")
                && count(client, "catalog") == 1 && count(client, "knowledge") == 1 && count(client, "warp") == 1
                && client.has("catalogBeforeFirstKnowledge") && client.get("catalogBeforeFirstKnowledge").getAsBoolean()
                && client.has("activeDigest") && canonical.digest().equals(client.get("activeDigest").getAsString());
        }

        boolean receivedOwners(JsonObject a, JsonObject b, JsonObject expectedA, int knowledgeCount) throws Exception {
            if (a == null || b == null || !a.has("uuid") || !b.has("uuid") || count(a, "knowledge") < knowledgeCount) return false;
            require(count(a, "catalog") == 1 && count(b, "catalog") == 1 && count(a, "warp") == 1
                && count(b, "warp") == 1 && count(b, "knowledge") == 1 && count(a, "knowledge") == knowledgeCount,
                "Duplicate/foreign actual handler application");
            return sameOwnerState(expectedA, a) && sameOwnerState(baselineB, b);
        }

        void attachmentIdentity() {
            require(ThaumcraftCapabilities.getKnowledge(actualA) == knowledgeA && ThaumcraftCapabilities.getKnowledge(actualB) == knowledgeB
                && ThaumcraftCapabilities.getWarp(actualA) == warpA && ThaumcraftCapabilities.getWarp(actualB) == warpB,
                "Private owner attachment identity unexpectedly replaced");
        }

        void unchangedSeed() throws Exception {
            attachmentIdentity();
            assertData(seedA, actualA); assertData(baselineB, actualB);
            observation(actualA, 16); assertExperience(seedExperienceA, actualA); assertExperience(baselineExperienceB, actualB);
            noPending();
        }

        void unchangedPaid(boolean popupCleared) throws Exception {
            attachmentIdentity();
            assertData(popupCleared ? paidLocalA : paidRawA, actualA); assertData(baselineB, actualB);
            observation(actualA, 0); assertExperience(paidExperienceA, actualA); assertExperience(baselineExperienceB, actualB);
        }

        void unchangedFinal() throws Exception {
            require(finalA != null, "Final actual paid owner baseline must exist");
            unchangedPaid(true); assertData(finalA, actualA);
        }

        void assertData(JsonObject expected, ServerPlayer player) throws Exception {
            require(expected != null && sameOwnerState(expected, ownerState(player)), "Actual full knowledge/warp NBT changed for " + player.getGameProfile().getName());
        }

        void observation(ServerPlayer player, int amount) {
            for (String key : OBSERVATION_POOLS) require(ThaumcraftCapabilities.getKnowledge(player)
                .getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, ResearchCategories.getResearchCategory(key)) == amount,
                "Raw observation balance mismatch for " + key);
        }

        JsonObject experience(ServerPlayer player) {
            JsonObject result = new JsonObject();
            result.addProperty("totalExperience", player.totalExperience);
            result.addProperty("experienceLevel", player.experienceLevel);
            result.addProperty("experienceProgressBits", Float.floatToIntBits(player.experienceProgress));
            return result;
        }
        void assertExperience(JsonObject expected, ServerPlayer player) { require(expected != null && expected.equals(experience(player)), "Actual owner XP changed outside one paid source transition"); }
        void noPending() { require(!PlayerDataSync.isKnowledgeDirty(actualA) && !PlayerDataSync.isKnowledgeDirty(actualB), "Rejected/quiet request unexpectedly marked owner knowledge dirty"); }
        int count(JsonObject client, String kind) { return client.has(kind + "Count") ? client.get(kind + "Count").getAsInt() : 0; }
        boolean seal(JsonObject client, String key) { return client != null && client.has(key) && client.get(key).getAsBoolean(); }
        long elapsed(int before) { return Integer.toUnsignedLong(server.getTickCount() - before); }
        String staleDigest() { return (canonical.digest().charAt(0) == '0' ? "1" : "0") + canonical.digest().substring(1); }

        int sentCount(String name, String kind) {
            int result = 0;
            for (var item : sends) { JsonObject row = item.getAsJsonObject(); if (name.equals(row.get("owner").getAsString()) && kind.equals(row.get("kind").getAsString())) result++; }
            return result;
        }
        int requestCount(String name, String requestPhase) {
            int result = 0;
            for (var item : requests) { JsonObject row = item.getAsJsonObject(); if (name.equals(row.get("owner").getAsString()) && requestPhase.equals(row.get("phase").getAsString())) result++; }
            return result;
        }
        int statusCount(String name, String requestPhase, String status) {
            int result = 0;
            for (var item : requests) { JsonObject row = item.getAsJsonObject(); if (name.equals(row.get("owner").getAsString())
                && requestPhase.equals(row.get("phase").getAsString()) && status.equals(row.get("status").getAsString())) result++; }
            return result;
        }
        void assertCounts(int knowledgeA) {
            require(sentCount(A, "catalog") == 1 && sentCount(B, "catalog") == 1 && sentCount(A, "warp") == 1 && sentCount(B, "warp") == 1
                && sentCount(A, "knowledge") == knowledgeA && sentCount(B, "knowledge") == 1, "Unexpected production resync/foreign-owner snapshot");
        }

        void comparePackets(JsonObject client, String name, int expectedKnowledge) throws Exception {
            JsonArray received = client.getAsJsonArray("packets");
            require(received != null && received.size() <= 64, "Missing/unbounded actual client transcript");
            int index = 0;
            for (var item : sends) {
                JsonObject sent = item.getAsJsonObject();
                if (!name.equals(sent.get("owner").getAsString())) continue;
                require(index < received.size(), "Missing actual handler for production owner packet");
                JsonObject actual = received.get(index++).getAsJsonObject();
                require(sent.get("kind").equals(actual.get("kind")) && sent.get("uuid").equals(actual.get("uuid"))
                    && sent.get("typeId").equals(actual.get("typeId")) && actual.get("mainThread").getAsBoolean(), "Actual per-owner payload order/identity/MAIN mismatch");
                if (sent.get("kind").getAsString().equals("catalog")) require(sent.get("catalog").equals(actual.get("catalog")), "Actual catalog wire metadata/digest/order differs from production send");
                else require(TagParser.parseTag(sent.get("payloadNbt").getAsString()).equals(TagParser.parseTag(actual.get("payloadNbt").getAsString())),
                    "Actual raw owner packet differs from actual server send (before POPUP clear)");
            }
            require(index == received.size() && count(client, "catalog") == 1 && count(client, "knowledge") == expectedKnowledge
                && count(client, "warp") == 1 && client.get("catalogBeforeFirstKnowledge").getAsBoolean(), "Duplicate/unaccounted actual client application");
        }

        void compareRequests(JsonObject client, String name, int expectedCount) {
            JsonArray outbound = client.getAsJsonArray("outboundRequests");
            require(outbound != null && outbound.size() == expectedCount, "Incomplete actual client request transcript");
            int index = 0;
            for (var item : requests) {
                JsonObject actual = item.getAsJsonObject();
                if (!name.equals(actual.get("owner").getAsString())) continue;
                require(index < outbound.size(), "Unaccounted contextual server request");
                JsonObject sent = outbound.get(index++).getAsJsonObject();
                for (String key : List.of("key", "expectedStage", "catalogDigest", "ownerUUID", "phase")) require(actual.get(key).equals(sent.get(key)),
                    "Actual client/context owner request body/order mismatch: " + key);
            }
            require(index == expectedCount, "Missing actual contextual server request");
        }

        void check(String name, String detail) {
            JsonObject check = new JsonObject();
            check.addProperty("name", name); check.addProperty("detail", detail);
            check.addProperty("serverTick", server.getTickCount()); check.addProperty("catalogDigest", canonical.digest());
            checks.add(check);
            Thaumcraft.log.info("Research-progression TCP audit passed {}", name);
        }

        void advance(String next) throws Exception {
            phase = next; phaseTick = server.getTickCount(); revision++;
            phaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
            publish("running");
            Thaumcraft.log.info("Research-progression TCP audit phase {}", next);
        }

        void publish(String status) throws Exception {
            JsonObject value = ResearchProgressNetworkAuditIO.identity(marker);
            value.addProperty("status", status); value.addProperty("auditPhase", phase); value.addProperty("revision", revision);
            value.addProperty("serverTick", server.getTickCount()); value.addProperty("currentDigest", canonical.digest());
            value.addProperty("requestedKey", REQUESTED_KEY); value.addProperty("expectedStage", EXPECTED_STAGE);
            value.addProperty("expectedCatalogCount", 1); value.addProperty("expectedKnowledgeCountA", 3);
            value.addProperty("expectedKnowledgeCountB", 1); value.addProperty("expectedWarpCount", 1);
            value.addProperty("expectedRequestCountA", 18); value.addProperty("expectedRequestCountB", 1);
            value.addProperty("actualRequestCount", requests.size()); value.addProperty("actualSendCount", sends.size());
            writeJson(directory, "server-state.json", value, false);
        }

        void finish(String status, String detail) {
            if (finished) return;
            finished = true; active = null;
            try {
                try {
                    if (status.equals("completed")) { unchangedFinal(); noPending(); }
                    server.getPlayerList().saveAll();
                    report.addProperty("saveFlushed", server.saveEverything(true, true, true));
                    if (status.equals("completed")) {
                        require(report.get("saveFlushed").getAsBoolean(), "Final private world save did not flush");
                        unchangedFinal(); verifySavedPlayers();
                    }
                } catch (Exception saveFailure) {
                    status = "failed";
                    detail = (detail == null ? "" : detail + "; ") + "Private save/disk verification failed: " + saveFailure;
                    Thaumcraft.log.error("Research-progression TCP save verification failed", saveFailure);
                }
                try { publish(status); }
                catch (Exception progressFailure) {
                    status = "failed";
                    detail = (detail == null ? "" : detail + "; ") + "Terminal journal failed: " + progressFailure;
                    Thaumcraft.log.error("Research-progression TCP terminal journal failed", progressFailure);
                }
                report.addProperty("status", status); report.addProperty("auditPhase", phase);
                report.addProperty("finishedUtc", Instant.now().toString());
                if (detail != null) report.addProperty("failure", detail);
                // Journal first; an independently attempted CREATE_NEW terminal can never seal a
                // false completed result after failed save/progress. Clients require BOTH artifacts.
                writeJson(directory, "server-report.json", report, true);
                Thaumcraft.log.info("Research-progression TCP audit {}: {}", status, detail);
            } catch (Exception reportFailure) {
                Thaumcraft.log.error("Unable to seal private progression TCP report", reportFailure);
                try { publish("failed"); }
                catch (Exception progressFailure) { Thaumcraft.log.error("Unable to record private progression TCP failure", progressFailure); }
            } finally { server.halt(false); }
        }

        void verifySavedPlayers() throws Exception {
            JsonObject verified = new JsonObject();
            for (String name : List.of(A, B)) {
                ServerPlayer live = server.getPlayerList().getPlayerByName(name);
                require(live == (name.equals(A) ? actualA : actualB), "Expected actual TCP session must remain connected until disk proof");
                owner(live);
                Path file = safePath(directory, world.resolve("playerdata").resolve(live.getUUID() + ".dat"));
                require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) > 0 && Files.size(file) <= 1_048_576,
                    "Missing/unsafe/unbounded actual private player save");
                CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.create(4L * 1024 * 1024));
                require(saved.contains("UUID", Tag.TAG_INT_ARRAY) && saved.getIntArray("UUID").length == 4
                    && saved.getUUID("UUID").equals(live.getUUID()), "Actual saved UUID differs from live owner");
                require(saved.contains("neoforge:attachments", Tag.TAG_COMPOUND), "Actual disk player lacks attachments");
                CompoundTag attachments = saved.getCompound("neoforge:attachments");
                require(attachments.contains("thaumcraft:knowledge", Tag.TAG_COMPOUND) && attachments.contains("thaumcraft:warp", Tag.TAG_COMPOUND),
                    "Actual disk player lacks full knowledge/warp compounds");
                JsonObject actual = new JsonObject();
                actual.addProperty("uuid", live.getUUID().toString());
                actual.addProperty("knowledgeNbt", attachments.getCompound("thaumcraft:knowledge").toString());
                actual.addProperty("warpNbt", attachments.getCompound("thaumcraft:warp").toString());
                require(sameOwnerState(name.equals(A) ? finalA : baselineB, actual) && sameOwnerState(ownerState(live), actual),
                    "Actual saved full owner attachment NBT differs from final/live owner");
                require(saved.contains("XpTotal", Tag.TAG_INT) && saved.contains("XpLevel", Tag.TAG_INT) && saved.contains("XpP", Tag.TAG_FLOAT)
                    && saved.getInt("XpTotal") == live.totalExperience && saved.getInt("XpLevel") == live.experienceLevel
                    && Float.floatToIntBits(saved.getFloat("XpP")) == Float.floatToIntBits(live.experienceProgress), "Actual saved XP differs from final paid/bystander owner");
                actual.addProperty("compressedBytes", Files.size(file));
                actual.add("experience", experience(live));
                verified.add(name, actual);
            }
            report.add("actualDiskPlayerAttachments", verified);
            report.addProperty("actualDiskPlayerNbtVerified", true);
        }
    }
}
