package thaumcraft.common.lib.research.catalog.validation;

import static thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.container.ContainerResearchTable;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.playerdata.PacketResearchReadToServer;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;
import thaumcraft.common.lib.network.research.PacketTheorycraftAction;
import thaumcraft.common.lib.network.research.PacketTheorycraftState;
import thaumcraft.common.lib.research.catalog.ResearchCatalogRuntime;
import thaumcraft.common.lib.research.theorycraft.SourceTheorycraftCard;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/** DEFAULT OFF, fresh two-owner PLAY/TCP cohort. Server fixtures are explicit: completed
 * research/flags, a physical table, paper/ink and proximity. Never calls a trusted handler
 * directly, injects a card, supplies client knowledge, or claims survival/GUI acceptance.
 * Parent hooks observers ONLY after actual production contextual work/application/send.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class ResearchStageFourNetworkAudit {
    public static final String ENABLE_PROPERTY = ResearchStageFourNetworkAuditIO.ENABLE_PROPERTY;
    public static final String READ_KEY = "FIRSTSTEPS";
    private static final BlockPos TABLE = new BlockPos(0, 6, 0);
    private static Run active;
    private ResearchStageFourNetworkAudit() {}

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        try { active = new Run(event.getServer()); }
        catch (Exception failure) { Thaumcraft.log.error("Stage-four TCP audit refused initialization", failure); event.getServer().halt(false); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        Run run = active;
        if (run == null || run.finished || run.server != event.getServer()) return;
        try { run.step(); } catch (Exception failure) { run.finish("failed", failure.toString()); }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        Run run = active;
        if (run != null && !run.finished && run.server == event.getServer()) run.finish("interrupted", "Server stopped before real TCP cohort completed");
    }
    public static void observeRead(ServerPlayer owner, PacketResearchReadToServer packet, boolean changed) {
        Run run = active;
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || run == null || run.finished) return;
        try { run.request(owner, packet, changed); } catch (Exception failure) { run.finish("failed", failure.toString()); }
    }
    public static void observeTheoryAction(ServerPlayer owner, PacketTheorycraftAction packet, boolean applied) {
        Run run = active;
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || run == null || run.finished) return;
        try { run.request(owner, packet, applied); } catch (Exception failure) { run.finish("failed", failure.toString()); }
    }
    public static void observeSent(ServerPlayer owner, CustomPacketPayload packet) {
        Run run = active;
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || run == null || run.finished) return;
        try { run.sent(owner, packet); } catch (Exception failure) { run.finish("failed", failure.toString()); }
    }

    /** Canonical transcript body, not a codec or authority seam. */
    public static JsonObject body(CustomPacketPayload packet) {
        JsonObject value = new JsonObject(); value.addProperty("typeId", packet.type().id().toString());
        if (packet instanceof PacketResearchReadToServer read) {
            value.addProperty("key", read.reference().researchKey()); value.addProperty("expectedStage", read.reference().expectedStage());
            value.addProperty("catalogDigest", read.reference().catalogDigest()); value.addProperty("readStateDigest", read.readStateDigest());
        } else if (packet instanceof PacketTheorycraftAction theory) {
            value.addProperty("menuId", theory.menuId()); value.addProperty("position", theory.position().asLong());
            value.addProperty("session", theory.session()); value.addProperty("revision", theory.revision());
            value.addProperty("sequence", theory.sequence()); value.addProperty("button", theory.button());
            JsonArray aids = new JsonArray(); theory.aids().stream().sorted().forEach(aids::add); value.add("aids", aids);
        } else throw new IllegalArgumentException("No arbitrary audit request");
        return value;
    }
    public static JsonObject theoryBody(PacketTheorycraftState state) {
        JsonObject value = new JsonObject(); value.addProperty("menuId", state.menuId()); value.addProperty("position", state.position().asLong());
        value.addProperty("session", state.session()); value.addProperty("revision", state.revision()); value.addProperty("owned", state.owned());
        value.addProperty("availableInspiration", state.availableInspiration()); JsonArray aids = new JsonArray(); state.aids().stream().sorted().forEach(aids::add);
        value.add("aids", aids); value.addProperty("note", state.note().toString()); return value;
    }

    private static final class Run {
        final MinecraftServer server; final Path directory, world; final JsonObject marker, report;
        final JsonArray sends = new JsonArray(), requests = new JsonArray(), checks = new JsonArray();
        final PacketSyncResearchCatalog canonical;
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(900);
        long phaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(600);
        ServerPlayer a, b; JsonObject baselineB, readBaseline, finalOwnerA; IPlayerKnowledge knowledge;
        TileResearchTable table; ContainerResearchTable menuA, menuB;
        String phase = "login", target; JsonObject action; int actionId, phaseTick, drawAttempts, selection = -1, inspiration;
        int lastReadRequestTick = -5;
        int expectedCopies = 1, selectedCost, paperAtStart, inkAtStart; long drawRevision, nonceA;
        CompoundTag phaseKnowledge, phaseTable, finalTable; boolean finished;

        Run(MinecraftServer server) throws Exception {
            this.server = server; require(server.isSameThread(), "Audit must start on owning server MAIN");
            directory = Path.of(System.getProperty("user.dir")).toRealPath(); marker = ResearchStageFourNetworkAuditIO.validateDirectory(directory, "server");
            require(server.isDedicatedServer() && "127.0.0.1".equals(server.getLocalIp()) && !server.usesAuthentication()
                && server.getPort() == marker.get("port").getAsInt()
                && ResearchStageFourNetworkAuditIO.NETWORK_PROTOCOL_VERSION.equals(PacketHandler.PROTOCOL_VERSION),
                "Exact prepared-protocol offline loopback dedicated server required");
            world = safePath(directory, server.getWorldPath(LevelResource.ROOT).toRealPath());
            require(world.getParent().equals(directory) && WORLD.equals(world.getFileName().toString()), "Only prepared fresh audit world allowed");
            Path players = safePath(directory, world.resolve("playerdata"));
            if (Files.isDirectory(players, LinkOption.NOFOLLOW_LINKS)) try (var files = Files.list(players)) { require(files.noneMatch(p -> p.getFileName().toString().endsWith(".dat")), "Existing player saves forbidden"); }
            require(server.getPlayerCount() == 0, "No preexisting player sessions allowed");
            canonical = ResearchCatalogRuntime.currentPayload(server); require(canonical != null, "Production startup must accept catalog");
            JsonObject catalog = catalogMetadata(canonical); require(catalog.get("sourceCount").getAsInt() == 8 && catalog.get("entryCount").getAsInt() == 148 && catalog.get("stageCount").getAsInt() == 271, "Unexpected source catalog");
            report = ResearchStageFourNetworkAuditIO.identity(marker); report.addProperty("startedUtc", Instant.now().toString()); report.add("catalog", catalog);
            report.addProperty("explicitIsolatedFixtures", true); report.addProperty("survivalAcquisitionProven", false); report.addProperty("guiProven", false);
            report.addProperty("reloadReconnectProven", false); report.addProperty("cardInjection", false); report.addProperty("cryptographicSignatureClaimed", false);
            report.add("actualProductionSends", sends); report.add("actualContextualRequests", requests); report.add("checks", checks); phaseTick = server.getTickCount(); publish("running");
        }
        void owner(ServerPlayer owner) {
            require(server.isSameThread() && owner.getServer() == server, "Real owning server MAIN required");
            String name = owner.getGameProfile().getName(); require((A.equals(name) || B.equals(name)) && offlineUuid(name).equals(owner.getUUID()), "Only exact isolated profiles allowed");
            var connection = owner.connection.getConnection(); require(connection.isConnected() && !connection.isMemoryConnection()
                && connection.getRemoteAddress() instanceof InetSocketAddress address && address.getAddress().isLoopbackAddress(), "Actual connected loopback TCP required");
            require(a == null || !A.equals(name) || a == owner, "A reconnect/respawn forbidden"); require(b == null || !B.equals(name) || b == owner, "B reconnect/respawn forbidden");
            require(ResearchCatalogRuntime.currentPayload(server) == canonical, "Catalog reload outside this cohort");
        }
        void sent(ServerPlayer owner, CustomPacketPayload packet) throws Exception {
            String kind = packet instanceof PacketSyncResearchCatalog ? "catalog" : packet instanceof PacketSyncKnowledge ? "knowledge"
                : packet instanceof PacketSyncWarp ? "warp" : packet instanceof PacketTheorycraftState ? "theory" : null;
            if (kind == null) return; owner(owner); require(sends.size() < 512, "Production send transcript bound");
            JsonObject row = new JsonObject(); row.addProperty("owner", owner.getGameProfile().getName()); row.addProperty("uuid", owner.getUUID().toString());
            row.addProperty("typeId", packet.type().id().toString()); row.addProperty("kind", kind); row.addProperty("mainThread", server.isSameThread()); row.addProperty("phase", phase);
            if (packet instanceof PacketSyncResearchCatalog catalog) { require(catalogMetadata(canonical).equals(catalogMetadata(catalog)), "Actual catalog differs from accepted"); row.add("catalog", catalogMetadata(catalog)); }
            if (packet instanceof PacketSyncKnowledge value) row.addProperty("payloadNbt", value.data().toString());
            if (packet instanceof PacketSyncWarp value) {
                row.addProperty("payloadNbt", value.data().toString());
                // PlayerWarp's source TEMP decay periodically synchronizes even empty warp.
                // Accept that genuine traffic, but never changed or foreign peer data.
                if (baselineB != null && B.equals(owner.getGameProfile().getName()))
                    require(TagParser.parseTag(baselineB.get("warpNbt").getAsString()).equals(value.data()), "Periodic peer warp snapshot differs from own baseline");
            }
            if (packet instanceof PacketTheorycraftState value) {
                row.add("theory", theoryBody(value));
                if (A.equals(owner.getGameProfile().getName())) nonceA = value.session();
                if (B.equals(owner.getGameProfile().getName()) && table != null && table.data != null)
                    require(!value.owned() && value.note().isEmpty(), "Foreign owner received private note contents");
            }
            sends.add(row);
        }
        void request(ServerPlayer owner, CustomPacketPayload packet, boolean applied) throws Exception {
            owner(owner); require(action != null && target.equals(owner.getGameProfile().getName()) && requests.size() < 256, "Unexpected/unbounded contextual audit request");
            JsonObject row = new JsonObject(); row.addProperty("owner", target); row.addProperty("uuid", owner.getUUID().toString()); row.addProperty("actionId", actionId);
            row.addProperty("phase", phase); row.add("body", body(packet)); row.addProperty("applied", applied); row.addProperty("mainThread", server.isSameThread());
            row.addProperty("serverTick", server.getTickCount());
            row.add("ownerAfter", ownerState(owner)); requests.add(row);
            require(currentRequests() <= expectedCopies, "Duplicate audit action beyond deliberate replay");
            if (packet instanceof PacketResearchReadToServer) {
                lastReadRequestTick = server.getTickCount();
                boolean expected = phase.equals("read") || phase.equals("read-recover"); require(applied == expected, "Read result differs from exact snapshot scenario");
                CompoundTag expectedKnowledge = ResearchStageFourNetworkAuditIO.expectedReadSnapshot(
                    phaseKnowledge, READ_KEY, expected, expected || phase.equals("read-stale"));
                require(normalizedKnowledge(expectedKnowledge).equals(normalizedKnowledge(knowledge.serializeNBT())), "Read altered stages, balances, unrelated flags or other owner NBT");
                if (expected) {
                    require(!knowledge.hasResearchFlag(READ_KEY, EnumResearchFlag.PAGE) && !knowledge.hasResearchFlag(READ_KEY, EnumResearchFlag.RESEARCH), "Read failed to clear source read flags");
                    require(knowledge.hasResearchFlag("THEORYRESEARCH", EnumResearchFlag.PAGE) && knowledge.hasResearchFlag("THEORYRESEARCH", EnumResearchFlag.RESEARCH), "Read altered another entry's flags");
                    if (phase.equals("read")) {
                        JsonObject snapshot = lastSend(A, "knowledge"); CompoundTag raw = TagParser.parseTag(snapshot.get("payloadNbt").getAsString());
                        require(flag(raw, READ_KEY, "POPUP") && !flag(raw, READ_KEY, "PAGE") && !flag(raw, READ_KEY, "RESEARCH"), "Read incorrectly consumed POPUP before source sync snapshot");
                    }
                } else if (phase.equals("read-stale")) require(knowledge.hasResearchFlag(READ_KEY, EnumResearchFlag.PAGE), "Stale addendum view incorrectly cleared current PAGE");
            } else {
                boolean shouldApply = Set.of("create", "draw", "select", "scrap").contains(phase) && currentRequests() == 1;
                require(applied == shouldApply, "Theory result differs from exact guarded scenario");
                if (shouldApply) {
                    if (phase.equals("create")) require(table.data != null && table.data.ownedBy(a) && table.data.cardChoices.isEmpty() && table.getItem(1).getCount() == paperAtStart && table.getItem(0).getDamageValue() == inkAtStart, "Create cost/note source mismatch");
                    if (phase.equals("draw")) {
                        require(ResearchStageFourNetworkAuditIO.hasSourceDrawCardinality(table.data.cardChoices.size(), 2),
                            "Actual source draw cardinality outside 0..2: " + table.data.cardChoices.size());
                        require(table.getItem(1).getCount() == paperAtStart - 1,
                            "Actual draw paper mismatch: before=" + paperAtStart + ", after=" + table.getItem(1).getCount());
                        require(table.getItem(0).getDamageValue() == inkAtStart,
                            "Actual draw changed ink: before=" + inkAtStart + ", after=" + table.getItem(0).getDamageValue());
                    }
                    if (phase.equals("scrap")) require(table.data == null && table.getItem(1).getCount() == paperAtStart && table.getItem(0).getDamageValue() == inkAtStart, "Scrap must not inject/refund paper or ink");
                    if (phase.equals("select")) require(table.data.cardChoices.get(selection).selected && table.data.inspiration == inspiration - selectedCost && table.getItem(0).getDamageValue() == inkAtStart + 1 && table.getItem(1).getCount() == paperAtStart, "Selection did not pay exact source ink/inspiration once");
                    phaseTable = table.saveWithoutMetadata(a.registryAccess());
                } else require(phaseTable.equals(table.saveWithoutMetadata(a.registryAccess())), "Denied/replayed action changed complete table NBT");
            }
            require(sameOwnerState(baselineB, ownerState(b)), "Peer knowledge/warp changed");
        }
        static boolean flag(CompoundTag nbt, String key, String flag) {
            for (var tag : nbt.getList("research", 10)) { CompoundTag row = (CompoundTag) tag; if (row.getString("key").equals(key)) return List.of(row.getString("flags").split(",")).contains(flag); }
            return false;
        }
        void step() throws Exception {
            require(server.isSameThread() && System.nanoTime() <= deadline && System.nanoTime() <= phaseDeadline, "Bounded TCP audit deadline in " + phase);
            for (ServerPlayer owner : server.getPlayerList().getPlayers()) owner(owner);
            if (server.getTickCount() % 5 != 0) return;
            JsonObject ca = ResearchStageFourNetworkAuditIO.readProgress(directory.getParent(), marker, "client-a"), cb = ResearchStageFourNetworkAuditIO.readProgress(directory.getParent(), marker, "client-b");
            for (JsonObject client : new JsonObject[]{ca, cb}) if (client != null) require(!"failed".equals(client.get("status").getAsString()), "Actual client failed");
            if (phase.equals("login")) {
                if (!ready(ca) || !ready(cb)) return;
                a = server.getPlayerList().getPlayerByName(A); b = server.getPlayerList().getPlayerByName(B); if (a == null || b == null) return;
                if (!received(ca, a) || !received(cb, b)) return;
                require(server.getPlayerCount() == 2 && sameOwnerState(ownerState(a), ca) && sameOwnerState(ownerState(b), cb), "Initial local owner states disagree");
                baselineB = ownerState(b); knowledge = ThaumcraftCapabilities.getKnowledge(a); require(!knowledge.isResearchKnown(READ_KEY), "New fixture preexists");
                complete("THEORYRESEARCH"); complete(READ_KEY); knowledge.setResearchFlag(READ_KEY, EnumResearchFlag.PAGE); knowledge.setResearchFlag(READ_KEY, EnumResearchFlag.RESEARCH);
                knowledge.setResearchFlag("THEORYRESEARCH", EnumResearchFlag.PAGE); knowledge.setResearchFlag("THEORYRESEARCH", EnumResearchFlag.RESEARCH);
                PlayerDataSync.syncKnowledgeNow(a); readBaseline = ownerState(a); phase = "seed"; publish("running"); return;
            }
            require(server.getPlayerCount() == 2 && server.getPlayerList().getPlayerByName(A) == a && server.getPlayerList().getPlayerByName(B) == b, "Both original live TCP owners required");
            require(sameOwnerState(baselineB, ownerState(b)) && count(B, "knowledge") == 1 && !PlayerDataSync.isKnowledgeDirty(b), "Peer owner or knowledge transport changed");
            if (phase.equals("seed")) { if (received(ca, a) && received(cb, b)) command("read-peer", B, "read", 1, -1); return; }
            if (phase.equals("menu-ready")) {
                if (ca == null || cb == null || !ca.has("theory") || !cb.has("theory")) return;
                if (!ca.get("theoryReady").getAsBoolean() || !cb.get("theoryReady").getAsBoolean()) return;
                if (!theoryCurrent(ca, A) || !theoryCurrent(cb, B)) return;
                command("create", A, "theory", 2, 0); return;
            }
            if (phase.equals("quiet")) {
                require(finalTable.equals(table.saveWithoutMetadata(a.registryAccess())) && sameOwnerState(finalOwnerA, ownerState(a)), "Replay quiet window changed owner/table");
                if (server.getTickCount() - phaseTick < 40 || !received(ca, a) || !received(cb, b) || !theoryCurrent(ca, A) || !theoryCurrent(cb, B)) return;
                if (!ResearchStageFourNetworkAuditIO.hasTerminalSealWindow(a.tickCount)
                    || !ResearchStageFourNetworkAuditIO.hasTerminalSealWindow(b.tickCount)) return;
                phase = "finish"; action = null; publish("running"); return;
            }
            if (phase.equals("finish")) {
                JsonObject ta = ResearchStageFourNetworkAuditIO.readTerminal(directory.getParent(), marker, "client-a"), tb = ResearchStageFourNetworkAuditIO.readTerminal(directory.getParent(), marker, "client-b");
                if (ta == null || tb == null) return;
                require("completed".equals(ta.get("status").getAsString()) && "completed".equals(tb.get("status").getAsString()), "Both live client reports must seal");
                compare(ta, A); compare(tb, B); require(received(ta, a) && received(tb, b), "Final client owner mismatch");
                report.add("actualClientA", ta); report.add("actualClientB", tb); report.addProperty("sourceDrawAttempts", drawAttempts);
                check("real-TCP-owner-and-transcript", "Actual outbound requests equal ordered MAIN contextual requests; actual applied catalog/owner/theory packets equal production sends");
                finish("completed", null); return;
            }
            if (action == null || currentRequests() != expectedCopies) return;
            if (phase.startsWith("read") && !ResearchStageFourNetworkAuditIO.canDispatchNextRead(server.getTickCount(), lastReadRequestTick)) return;
            JsonObject targetClient = target.equals(A) ? ca : cb;
            if (targetClient == null || targetClient.get("lastActionId").getAsInt() != actionId || !received(ca, a) || !received(cb, b)) return;
            if (table != null && (!theoryCurrent(ca, A) || !theoryCurrent(cb, B))) return;
            if (phase.equals("read-peer")) { require(sameOwnerState(readBaseline, ownerState(a)), "Peer read altered A"); knowledge.setResearchFlag(READ_KEY, EnumResearchFlag.POPUP); command("read", A, "read", 1, -1); }
            else if (phase.equals("read")) { check("read-current-owner-only", "Exact accepted snapshot clears PAGE/RESEARCH only; raw source sync retains POPUP, unrelated entry and peer unchanged"); knowledge.setResearchFlag(READ_KEY, EnumResearchFlag.PAGE); command("read-stale", A, "read", 1, -1); }
            else if (phase.equals("read-stale")) { check("read-stale-addendum-resync", "Same-stage stale read digest denied, new PAGE preserved, real owner-only knowledge recovery received"); command("read-recover", A, "read", 1, -1); }
            else if (phase.equals("read-recover")) command("read-replay", A, "read", 1, -1);
            else if (phase.equals("read-replay")) { require(count(A, "knowledge") == 5, "Unchanged read replay emitted unexpected snapshot"); prepareTable(); }
            else if (phase.equals("create")) { check("table-create-once", "Real menu/session create plus identical sequence replay: one source note, no free paper/ink cost"); command("foreign-nonce", B, "wrong-nonce", 1, 2); }
            else if (phase.equals("foreign-nonce")) command("foreign-owner", B, "theory", 1, 2);
            else if (phase.equals("foreign-owner")) { check("table-peer-private-owner", "Forged foreign session and own valid session for foreign note deny; B custom menu-state payload contains no note, personal knowledge unchanged"); command("draw", A, "theory", 2, 2); }
            else if (phase.equals("draw")) {
                drawAttempts++; require(drawAttempts <= 16, "No safe actual source card within bounded draw attempts"); drawRevision = phaseTable.getLong("theoryRevision"); selection = safeChoice();
                if (selection < 0) command("scrap", A, "theory", 1, 9);
                else { inspiration = table.data.inspiration; selectedCost = table.data.cardChoices.get(selection).card.getInspirationCost(); command("select", A, "theory", 2, selection + 4); }
            } else if (phase.equals("scrap")) command("create", A, "theory", 2, 0);
            else if (phase.equals("select")) { check("table-selection-cost-once", "An actually drawn source card pays exact inspiration and one ink; duplicate same sequence performs no second activation"); command("stale-select", A, "stale-revision", 1, selection + 4); }
            else if (phase.equals("stale-select")) { check("table-stale-revision-no-second-cost", "New sequence with pre-selection revision denied, authoritative note re-sent unchanged"); finalTable = table.saveWithoutMetadata(a.registryAccess()); finalOwnerA = ownerState(a); phase = "quiet"; action = null; phaseTick = server.getTickCount(); publish("running"); }
            else throw new IllegalStateException("Unknown stage-four phase " + phase);
        }
        void complete(String key) { knowledge.addResearch(key); require(knowledge.setResearchStage(key, ResearchCategories.getResearch(key).getStages().length + 1), "Cannot seed explicit completed fixture " + key); }
        int safeChoice() {
            for (int i = 0; i < table.data.cardChoices.size(); i++) {
                var card = table.data.cardChoices.get(i).card;
                if (card instanceof SourceTheorycraftCard source && Set.of(SourceTheorycraftCard.Kind.STUDY, SourceTheorycraftCard.Kind.EXPERIMENTATION,
                    SourceTheorycraftCard.Kind.INSPIRED, SourceTheorycraftCard.Kind.CALIBRATE, SourceTheorycraftCard.Kind.MEASURE, SourceTheorycraftCard.Kind.FOCUS).contains(source.kind())
                    && card.requirementsResolved() && card.getRequiredItems() == null && card.getInspirationCost() > 0 && card.getInspirationCost() <= table.data.inspiration) return i;
            } return -1;
        }
        void prepareTable() throws Exception {
            a.setGameMode(GameType.SURVIVAL); b.setGameMode(GameType.SURVIVAL); a.teleportTo(.5, 6, 2.5); b.teleportTo(2.5, 6, 2.5);
            var level = a.serverLevel();
            // Explicit fresh-world fixture support: Survival owners must not fall outside
            // native stillValid range during the replay/quiet window. No survival claim.
            for (BlockPos floor : List.of(TABLE.below(), new BlockPos(0, 5, 2), new BlockPos(2, 5, 2))) level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(TABLE, BlocksTC.researchTable.defaultBlockState());
            require(level.getBlockEntity(TABLE) instanceof TileResearchTable, "Actual registered table BE missing"); table = (TileResearchTable) level.getBlockEntity(TABLE);
            table.setItem(0, new ItemStack(ItemsTC.scribingTools)); table.setItem(1, new ItemStack(Items.PAPER, 64));
            require(a.openMenu(table).isPresent() && b.openMenu(table).isPresent() && a.containerMenu instanceof ContainerResearchTable && b.containerMenu instanceof ContainerResearchTable, "Actual vanilla menu open failed");
            menuA = (ContainerResearchTable) a.containerMenu; menuB = (ContainerResearchTable) b.containerMenu; menuA.broadcastChanges(); menuB.broadcastChanges();
            phase = "menu-ready"; action = null; phaseTick = server.getTickCount(); publish("running");
        }
        void command(String next, String owner, String mode, int copies, int button) throws Exception {
            phase = next; target = owner; expectedCopies = copies; actionId++; action = new JsonObject(); action.addProperty("mode", mode); action.addProperty("button", button); action.addProperty("copies", copies);
            action.addProperty("foreignSession", nonceA); action.addProperty("staleRevision", drawRevision); phaseTick = server.getTickCount(); phaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
            phaseKnowledge = knowledge.serializeNBT();
            if (table != null) { phaseTable = table.saveWithoutMetadata(a.registryAccess()); paperAtStart = table.getItem(1).getCount(); inkAtStart = table.getItem(0).getDamageValue(); }
            publish("running");
        }
        int currentRequests() { int count = 0; for (var item : requests) if (item.getAsJsonObject().get("actionId").getAsInt() == actionId) count++; return count; }
        int count(String name, String kind) { int count = 0; for (var item : sends) { var row = item.getAsJsonObject(); if (name.equals(row.get("owner").getAsString()) && kind.equals(row.get("kind").getAsString())) count++; } return count; }
        JsonObject lastSend(String name, String kind) { JsonObject found = null; for (var item : sends) { var row = item.getAsJsonObject(); if (name.equals(row.get("owner").getAsString()) && kind.equals(row.get("kind").getAsString())) found = row; } require(found != null, "Missing actual production send"); return found; }
        boolean theoryCurrent(JsonObject client, String name) throws Exception { return client != null && client.has("theory") && lastSend(name, "theory").get("theory").equals(client.get("theory")); }
        boolean ready(JsonObject client) { return client != null && ResearchStageFourNetworkAuditIO.hasInitialPersonalSnapshots(client.get("catalogCount").getAsInt(), client.get("knowledgeCount").getAsInt(), client.get("warpCount").getAsInt()) && client.has("catalogDigest") && !client.get("catalogDigest").isJsonNull() && canonical.digest().equals(client.get("catalogDigest").getAsString()); }
        boolean received(JsonObject client, ServerPlayer owner) throws Exception {
            if (client == null) return false;
            JsonObject current = ownerState(owner);
            CompoundTag raw = TagParser.parseTag(lastSend(owner.getGameProfile().getName(), "knowledge").get("payloadNbt").getAsString());
            // Server POPUP cleanup and client source deserialize/cleanup have different
            // empty-bucket representations. Check BOTH against the actual wire snapshot.
            return current.get("uuid").equals(client.get("uuid"))
                && normalizedKnowledge(ResearchStageFourNetworkAuditIO.expectedReadSnapshot(raw, READ_KEY, false, true))
                    .equals(normalizedKnowledge(TagParser.parseTag(current.get("knowledgeNbt").getAsString())))
                && normalizedKnowledge(ResearchStageFourNetworkAuditIO.expectedClientSnapshot(raw))
                    .equals(normalizedKnowledge(TagParser.parseTag(client.get("knowledgeNbt").getAsString())))
                && TagParser.parseTag(current.get("warpNbt").getAsString()).equals(TagParser.parseTag(client.get("warpNbt").getAsString()))
                && client.get("knowledgeCount").getAsInt() == count(owner.getGameProfile().getName(), "knowledge")
                && client.get("warpCount").getAsInt() == count(owner.getGameProfile().getName(), "warp");
        }
        void compare(JsonObject client, String name) throws Exception {
            var received = client.getAsJsonArray("packets"); int index = 0;
            for (var item : sends) { var row = item.getAsJsonObject(); if (!name.equals(row.get("owner").getAsString())) continue; require(index < received.size(), "Missing actual application"); var actual = received.get(index++).getAsJsonObject();
                for (String key : List.of("uuid", "kind", "typeId")) require(row.get(key).equals(actual.get(key)), "Wire owner/order mismatch " + key); require(actual.get("mainThread").getAsBoolean(), "Client application not MAIN");
                if (row.has("payloadNbt")) require(TagParser.parseTag(row.get("payloadNbt").getAsString()).equals(TagParser.parseTag(actual.get("payloadNbt").getAsString())), "Raw owner payload mismatch");
                if (row.has("catalog")) require(row.get("catalog").equals(actual.get("catalog")), "Catalog wire mismatch"); if (row.has("theory")) require(row.get("theory").equals(actual.get("theory")), "Theory wire mismatch"); }
            require(index == received.size(), "Unaccounted client applications");
            var outbound = client.getAsJsonArray("requests"); index = 0;
            for (var item : requests) { var actual = item.getAsJsonObject(); if (!name.equals(actual.get("owner").getAsString())) continue; require(index < outbound.size(), "Missing actual client request"); var sent = outbound.get(index++).getAsJsonObject();
                require(actual.get("body").equals(sent.get("body")) && actual.get("actionId").equals(sent.get("actionId")) && sent.get("afterPacketDistributor").getAsBoolean(), "Actual contextual request differs from real outbound body"); }
            require(index == outbound.size(), "Unaccounted outbound request");
        }
        void check(String name, String detail) { JsonObject value = new JsonObject(); value.addProperty("name", name); value.addProperty("detail", detail); value.addProperty("serverTick", server.getTickCount()); checks.add(value); }
        void publish(String status) throws Exception { JsonObject state = ResearchStageFourNetworkAuditIO.identity(marker); state.addProperty("status", status); state.addProperty("auditPhase", phase); state.addProperty("actionId", actionId); state.addProperty("target", target); state.addProperty("catalogDigest", canonical.digest()); state.addProperty("readKey", READ_KEY);
            state.addProperty("readStage", knowledge == null ? -1 : knowledge.getResearchStage(READ_KEY)); if (action != null) state.add("action", action); writeJson(directory, "server-state.json", state, false); }
        void finish(String status, String detail) {
            if (finished) return; finished = true;
            try {
                if (status.equals("completed")) { server.getPlayerList().saveAll(); require(server.saveEverything(true, true, true), "Audit save did not flush"); verifySaved(a); verifySaved(b); report.addProperty("saveFlushed", true); }
                phase = status; publish(status); report.addProperty("status", status); report.addProperty("finishedUtc", Instant.now().toString()); if (detail != null) report.addProperty("failure", detail); writeJson(directory, "server-report.json", report, true);
            } catch (Exception failure) { Thaumcraft.log.error("Stage-four TCP terminal failed", failure); try { phase = "failed"; publish("failed"); report.addProperty("status", "failed"); report.addProperty("failure", failure.toString()); writeJson(directory, "server-report.json", report, true); } catch (Exception journal) { Thaumcraft.log.error("Stage-four TCP failure journal failed", journal); } }
            finally { active = null; server.halt(false); }
        }
        void verifySaved(ServerPlayer player) throws Exception { Path file = safePath(directory, world.resolve("playerdata").resolve(player.getUUID() + ".dat")); require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) > 0 && Files.size(file) < 1_048_576, "Actual player save missing/unsafe");
            var saved = NbtIo.readCompressed(file, NbtAccounter.create(4L * 1024 * 1024)); var attachments = saved.getCompound("neoforge:attachments"); require(saved.getUUID("UUID").equals(player.getUUID()) && normalizedKnowledge(attachments.getCompound("thaumcraft:knowledge")).equals(normalizedKnowledge(ThaumcraftCapabilities.getKnowledge(player).serializeNBT())) && attachments.getCompound("thaumcraft:warp").equals(ThaumcraftCapabilities.getWarp(player).serializeNBT()), "Actual disk owner attachments differ"); }
    }
}
