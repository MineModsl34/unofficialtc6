package thaumcraft.client.lib;

import static thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.container.ContainerResearchTable;
import thaumcraft.client.gui.GuiResearchTable;
import thaumcraft.common.lib.network.playerdata.PacketResearchReadToServer;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;
import thaumcraft.common.lib.network.research.PacketTheorycraftAction;
import thaumcraft.common.lib.network.research.PacketTheorycraftState;
import thaumcraft.common.lib.research.catalog.validation.ResearchStageFourNetworkAudit;
import thaumcraft.common.lib.research.catalog.validation.ResearchStageFourNetworkAuditIO;

/** DEFAULT OFF remote TCP sender/after-application observer. The guarded server alone creates
 * fixtures; the client never grants research, inserts cards, changes NBT/slots or calls a server
 * helper. Native actual menus and received nonce/revision determine every theory request.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ResearchStageFourNetworkClientAudit {
    private static Run active;
    private static boolean attempted;
    private static long initializationDeadline;
    private ResearchStageFourNetworkClientAudit() {}
    /** Pure audit-fixture gate, not a close-menu action or production GUI condition. */
    public static boolean shouldDismissTheoryScreen(boolean enabled, Object screenMenu, Object liveMenu, boolean ready) {
        return enabled && ready && screenMenu != null && screenMenu == liveMenu;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(ResearchStageFourNetworkAuditIO.ENABLE_PROPERTY)) return;
        Run run = initialize(); if (run == null || run.finished) return;
        try { run.step(); } catch (Exception failure) { run.fail(failure.toString()); }
    }
    public static void catalog(PacketSyncResearchCatalog packet, String digest, int entries, int stages) {
        Run run = initialize(); if (run == null || run.finished) return;
        try { run.catalog(packet, digest, entries, stages); } catch (Exception failure) { run.fail(failure.toString()); }
    }
    public static void knowledge(PacketSyncKnowledge packet) { personal(packet, "knowledge", packet.data()); }
    public static void warp(PacketSyncWarp packet) { personal(packet, "warp", packet.data()); }
    private static void personal(CustomPacketPayload packet, String kind, CompoundTag nbt) {
        Run run = initialize(); if (run == null || run.finished) return;
        try { run.personal(packet, kind, nbt); } catch (Exception failure) { run.fail(failure.toString()); }
    }
    /** Hook ONLY after an actual matching menu accepted state and incremented its generation. */
    public static void theoryState(PacketTheorycraftState packet, ContainerResearchTable menu) {
        Run run = initialize(); if (run == null || run.finished) return;
        try { run.theory(packet, menu); } catch (Exception failure) { run.fail(failure.toString()); }
    }
    private static Run initialize() {
        if (!Boolean.getBoolean(ResearchStageFourNetworkAuditIO.ENABLE_PROPERTY)) return null;
        if (active != null) return active; if (attempted) return null;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            require(minecraft.isSameThread() && !minecraft.hasSingleplayerServer(), "Private client audit requires remote MAIN");
            if (initializationDeadline == 0) initializationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(600);
            require(System.nanoTime() <= initializationDeadline, "Remote login deadline exceeded");
            if (minecraft.getConnection() == null || minecraft.player == null) return null;
            attempted = true; active = new Run(minecraft);
        } catch (Exception failure) { attempted = true; Thaumcraft.log.error("Stage-four client audit refused initialization", failure); minecraft.stop(); }
        return active;
    }
    private static final class Run {
        final Minecraft minecraft; final LocalPlayer player; final Connection connection; final Path directory; final JsonObject marker, state;
        final String name, role; final JsonArray packets = new JsonArray(), requests = new JsonArray();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(900);
        int catalogCount, knowledgeCount, warpCount, ticks, lastActionId; boolean finished, sealed, terminalAttempted;
        String digest; JsonObject latestTheory; long lastTheoryGeneration; Object theoryMenu;
        Run(Minecraft minecraft) throws Exception {
            this.minecraft = minecraft; player = minecraft.player; connection = minecraft.getConnection().getConnection(); name = player.getGameProfile().getName();
            role = A.equals(name) ? "client-a" : B.equals(name) ? "client-b" : null; require(role != null, "Only explicit audit profiles allowed");
            directory = minecraft.gameDirectory.toPath().toRealPath(); marker = ResearchStageFourNetworkAuditIO.validateDirectory(directory, role); checkConnection();
            state = ResearchStageFourNetworkAuditIO.identity(marker); state.addProperty("status", "running"); state.addProperty("startedUtc", Instant.now().toString());
            state.addProperty("explicitIsolatedFixtures", true); state.addProperty("survivalAcquisitionProven", false); state.addProperty("guiProven", false);
            state.addProperty("clientGrantsOrOptimisticChanges", false); state.add("packets", packets); state.add("requests", requests); refresh(); persist(false);
        }
        void checkConnection() {
            require(minecraft.isSameThread() && minecraft.player == player && !minecraft.hasSingleplayerServer() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == connection && connection.isConnected() && !connection.isMemoryConnection(), "Actual original remote MAIN session required");
            require(connection.getRemoteAddress() instanceof InetSocketAddress address && address.getAddress().isLoopbackAddress()
                && address.getPort() == marker.get("port").getAsInt() && offlineUuid(name).equals(player.getUUID()), "Unexpected isolated TCP owner/port");
        }
        JsonObject row(CustomPacketPayload packet, String kind) {
            checkConnection(); require(!sealed && packets.size() < 512, "Bounded live after-application transcript required");
            JsonObject row = new JsonObject(); row.addProperty("typeId", packet.type().id().toString()); row.addProperty("uuid", player.getUUID().toString());
            row.addProperty("kind", kind); row.addProperty("mainThread", minecraft.isSameThread()); row.addProperty("clientTick", ticks); return row;
        }
        void catalog(PacketSyncResearchCatalog packet, String accepted, int entries, int stages) throws Exception {
            JsonObject row = row(packet, "catalog"); require(catalogCount == 0 && entries == 148 && stages == 271 && packet.sources().size() == 8
                && packet.digest().equals(accepted) && accepted.equals(ClientResearchCatalogHandlers.acceptedDigestForCurrentConnection()), "Actual remote SESSION/catalog acceptance missing");
            catalogCount++; digest = accepted; row.add("catalog", catalogMetadata(packet)); packets.add(row); refresh(); persist(false);
        }
        void personal(CustomPacketPayload packet, String kind, CompoundTag nbt) throws Exception {
            JsonObject row = row(packet, kind); require(catalogCount == 1 && digest.equals(ClientResearchCatalogHandlers.acceptedDigestForCurrentConnection()), "Owner application before accepted SESSION");
            require(nbt.toString().length() < 65536, "Bounded private owner fixture required"); row.addProperty("payloadNbt", nbt.toString());
            if (kind.equals("knowledge")) knowledgeCount++; else warpCount++; packets.add(row); refresh(); persist(false);
        }
        void theory(PacketTheorycraftState packet, ContainerResearchTable menu) throws Exception {
            JsonObject row = row(packet, "theory"); require(player.containerMenu == menu && menu.containerId == packet.menuId() && menu.theoryReady()
                && menu.viewRevision() == packet.revision() && menu.theoryOwned() == packet.owned(), "Actual native menu did not apply this state");
            if (theoryMenu != menu) { theoryMenu = menu; lastTheoryGeneration = 0; }
            require(menu.receivedStateGeneration() == lastTheoryGeneration + 1, "Observer missing/duplicating actual menu state application"); lastTheoryGeneration = menu.receivedStateGeneration();
            if (packet.note().isEmpty()) require(menu.theoryData() == null, "Empty/private note failed closed");
            else require(menu.theoryData() != null && menu.theoryData().serialize(player.registryAccess()).equals(packet.note()), "Actually applied note differs from decoded packet");
            dismissTheoryScreenOnly(menu);
            latestTheory = ResearchStageFourNetworkAudit.theoryBody(packet); row.add("theory", latestTheory.deepCopy()); packets.add(row); refresh(); persist(false);
        }
        void dismissTheoryScreenOnly(ContainerResearchTable menu) {
            if (!(minecraft.screen instanceof GuiResearchTable screen)
                || !shouldDismissTheoryScreen(Boolean.getBoolean(ResearchStageFourNetworkAuditIO.ENABLE_PROPERTY), screen.getMenu(), player.containerMenu, menu.theoryReady())) return;
            require(player.containerMenu == menu && menu.getCarried().isEmpty(), "Only the actual empty-carried audit menu may hide its screen");
            long generation = menu.receivedStateGeneration(), revision = menu.viewRevision();
            // MC1.21.1 setScreen -> AbstractContainerScreen.removed -> menu.removed(LocalPlayer):
            // native menu.removed only cleans carried for ServerPlayer. Unlike onClose/ESC,
            // this API never calls LocalPlayer.closeContainer or sends a close-menu packet.
            // Hide the screen so its source 350ms autoarchive cannot race this transport-only
            // scripted cohort. Keep the real menu, nonce, sequence and received state intact.
            minecraft.setScreen(null);
            require(minecraft.screen == null && player.containerMenu == menu && menu.receivedStateGeneration() == generation
                && menu.viewRevision() == revision && menu.theoryReady(), "Screen-only dismissal changed actual synchronized menu state");
            state.addProperty("theoryScreenDismissedForTransportOnly", true);
        }
        void step() throws Exception {
            ticks++; require(System.nanoTime() <= deadline, "Bounded actual TCP client deadline"); if (!sealed) checkConnection(); if (ticks % 5 != 0) return;
            JsonObject control = ResearchStageFourNetworkAuditIO.readProgress(directory.getParent(), marker, "server"); if (control == null) return;
            String status = control.get("status").getAsString(); require(!status.equals("failed") && !status.equals("interrupted"), "Server TCP cohort failed");
            if (status.equals("completed")) { require(sealed, "Server completed before client terminal"); var terminal = ResearchStageFourNetworkAuditIO.readTerminal(directory.getParent(), marker, "server"); if (terminal == null) return; require("completed".equals(terminal.get("status").getAsString()), "Unconfirmed server terminal"); finished = true; minecraft.stop(); return; }
            if (sealed) return;
            if (control.get("auditPhase").getAsString().equals("finish")) {
                refresh(); state.addProperty("status", "completed"); state.addProperty("sealedWhileConnected", connection.isConnected()); persist(false); terminalAttempted = true; persist(true); sealed = true; return;
            }
            int nextAction = control.get("actionId").getAsInt(); require(nextAction >= lastActionId, "Control action went backwards");
            if (control.has("action") && name.equals(control.get("target").getAsString()) && nextAction > lastActionId) act(control);
            refresh(); persist(false);
        }
        void act(JsonObject control) throws Exception {
            checkConnection(); require(catalogCount == 1 && digest != null && digest.equals(control.get("catalogDigest").getAsString())
                && digest.equals(ClientResearchCatalogHandlers.acceptedDigestForCurrentConnection()), "Action needs actual current accepted remote digest");
            JsonObject action = control.getAsJsonObject("action"); String mode = action.get("mode").getAsString(); CustomPacketPayload packet;
            if (mode.equals("read")) {
                var knowledge = ThaumcraftCapabilities.getKnowledge(player); String key = control.get("readKey").getAsString(); require(key.equals(ResearchStageFourNetworkAudit.READ_KEY), "Arbitrary research action forbidden");
                // Peer deliberately uses the A fixture stage, but its OWN received owner fingerprint.
                int stage = A.equals(name) ? knowledge.getResearchStage(key) : control.get("readStage").getAsInt();
                var view = ClientResearchPresentation.view();
                require(view != null && view.readStateDigest() != null, "Read needs the actual received owner snapshot");
                packet = new PacketResearchReadToServer(new PacketSyncProgressToServer(key, stage, digest), view.readStateDigest());
            } else {
                require(player.containerMenu instanceof ContainerResearchTable && latestTheory != null, "Action requires actual synchronized table menu");
                ContainerResearchTable menu = (ContainerResearchTable) player.containerMenu; var prepared = menu.prepareTheoryAction(action.get("button").getAsInt(), Set.of()); require(prepared != null, "Native menu cannot prepare current received revision");
                long session = mode.equals("wrong-nonce") ? action.get("foreignSession").getAsLong() : prepared.session();
                if (mode.equals("wrong-nonce")) require(session != prepared.session(), "Accidental random session collision cannot prove a forged nonce rejection");
                long revision = mode.equals("stale-revision") ? action.get("staleRevision").getAsLong() : prepared.revision();
                require(mode.equals("theory") || mode.equals("wrong-nonce") || mode.equals("stale-revision"), "No arbitrary audit action mode");
                packet = new PacketTheorycraftAction(prepared.menuId(), prepared.position(), session, revision, prepared.sequence(), prepared.button(), prepared.aids());
            }
            int copies = action.get("copies").getAsInt(); require(copies >= 1 && copies <= 2 && requests.size() + copies <= 256, "Bounded intentional replay only");
            int id = control.get("actionId").getAsInt();
            for (int i = 0; i < copies; i++) {
                PacketDistributor.sendToServer(packet); // REAL production PLAY send, never tryHandle/receive.
                JsonObject row = new JsonObject(); row.addProperty("actionId", id); row.addProperty("owner", name); row.addProperty("uuid", player.getUUID().toString());
                row.add("body", ResearchStageFourNetworkAudit.body(packet)); row.addProperty("mainThread", minecraft.isSameThread()); row.addProperty("afterPacketDistributor", true); row.addProperty("sentUtc", Instant.now().toString()); requests.add(row);
                refresh(); persist(false); // Preserve a genuine sent prefix on failure; never retry uncertain sends.
            }
            lastActionId = id;
        }
        void refresh() {
            state.addProperty("catalogCount", catalogCount); state.addProperty("knowledgeCount", knowledgeCount); state.addProperty("warpCount", warpCount); state.addProperty("catalogDigest", digest);
            state.addProperty("lastActionId", lastActionId); state.addProperty("uuid", player.getUUID().toString()); state.addProperty("knowledgeNbt", ThaumcraftCapabilities.getKnowledge(player).serializeNBT().toString());
            state.addProperty("warpNbt", ThaumcraftCapabilities.getWarp(player).serializeNBT().toString()); state.addProperty("mainThread", minecraft.isSameThread()); state.addProperty("remoteLoopback", !connection.isMemoryConnection());
            state.addProperty("connected", connection.isConnected()); if (latestTheory != null) state.add("theory", latestTheory.deepCopy());
            state.addProperty("theoryReady", player.containerMenu instanceof ContainerResearchTable menu && menu.theoryReady());
            state.addProperty("theoryStateGeneration", lastTheoryGeneration);
        }
        void persist(boolean terminal) throws Exception { writeJson(directory, terminal ? "client-report.json" : "client-state.json", state, terminal); }
        void fail(String detail) {
            if (finished) return; finished = true;
            try { refresh(); state.addProperty("status", "failed"); state.addProperty("failure", detail); persist(false); if (!terminalAttempted) { terminalAttempted = true; persist(true); } }
            catch (Exception failure) { Thaumcraft.log.error("Stage-four client failure journal failed", failure); }
            Thaumcraft.log.error("Stage-four actual TCP client failed for {}: {}", name, detail); minecraft.stop();
        }
    }
}
