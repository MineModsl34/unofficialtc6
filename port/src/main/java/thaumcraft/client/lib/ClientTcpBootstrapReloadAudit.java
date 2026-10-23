package thaumcraft.client.lib;

import static thaumcraft.common.lib.validation.TcpBootstrapAuditIO.*;

import com.google.gson.JsonObject;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.validation.TcpBootstrapReloadAudit;

/** New client JVM observes a real PLAY login to the copied completed private world. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientTcpBootstrapReloadAudit {
    private static Run active;
    private static boolean stopped;
    private ClientTcpBootstrapReloadAudit() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(TcpBootstrapReloadAudit.ENABLE_PROPERTY) || stopped) return;
        Minecraft client = Minecraft.getInstance();
        try { if (active == null) active = new Run(client); active.step(); }
        catch (Exception failure) {
            Thaumcraft.log.error("Private TCP restart client failed", failure);
            if (active == null) { stopped = true; client.stop(); } else active.fail(failure.toString());
        }
    }

    private static final class Run {
        final Minecraft client;
        final Path directory;
        final JsonObject marker, baseline;
        final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
        Connection connection;
        LocalPlayer owner;
        int stableTicks;
        boolean sealed;

        Run(Minecraft client) throws Exception {
            this.client = client;
            require(client.isSameThread() && !ClientJeiReviewScenario.enabled(), "Reload client requires exclusive native MAIN");
            directory = TcpBootstrapReloadAudit.directory(client.gameDirectory.toPath(), "client");
            marker = TcpBootstrapReloadAudit.validate(directory, "client");
            baseline = TcpBootstrapReloadAudit.baseline(directory, marker);
            require(USERNAME.equals(client.getUser().getName()) && !client.hasSingleplayerServer(), "Exact private remote profile required");
            writeState(directory, "client", state("login"));
        }

        void step() throws Exception {
            require(System.nanoTime() < deadline && client.isSameThread() && !client.hasSingleplayerServer(), "Restart client timed out or acquired an integrated server");
            JsonObject terminal = readPeer(directory, marker, "server", true);
            if (terminal != null) {
                require(sealed && terminal.has("passed") && terminal.get("passed").getAsBoolean(), "Server restart rejected or finished before client acceptance");
                stopped = true; client.stop(); return;
            }
            if (connection == null) {
                if (client.getConnection() == null || client.player == null || client.gameMode == null) return;
                connection = client.getConnection().getConnection(); owner = client.player;
            }
            require(client.getConnection() != null && client.player == owner && client.getConnection().getConnection() == connection
                && connection.isConnected() && !connection.isMemoryConnection()
                && connection.getRemoteAddress() instanceof InetSocketAddress remote && remote.getAddress().isLoopbackAddress()
                && remote.getPort() == marker.get("port").getAsInt() && OWNER_UUID.equals(owner.getUUID())
                && client.gameMode.getPlayerMode() == GameType.SURVIVAL && !owner.getAbilities().instabuild,
                "Restart requires a stable separate actual survival PLAY TCP owner");
            if (sealed) return;
            var view = ClientResearchPresentation.view();
            if (!ClientResearchCatalogHandlers.readyForCurrentConnection() || view == null || view.storedStage("FIRSTSTEPS") != 4
                || !view.knowsStrict("FIRSTSTEPS") || !view.knows("!gotcrystals") || !view.knows("!gotdream")
                || !view.knows("!gotthaumonomicon") || view.getKnowledgeRaw(
                    thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, "BASICS") != baseline.get("basicsRaw").getAsInt()) {
                stableTicks = 0; return;
            }
            JsonObject control = readPeer(directory, marker, "server", false);
            if (control == null || !"loaded".equals(string(control, "phase"))) return;
            TcpBootstrapReloadAudit.assertPlayer(owner, baseline, true);
            if (++stableTicks < 40) return;
            require(client.level.getBlockState(TcpBootstrapReloadAudit.WORKBENCH).is(thaumcraft.api.blocks.BlocksTC.arcaneWorkbench)
                && client.level.getBlockState(TcpBootstrapReloadAudit.BOOKSHELF).isAir(), "Native client did not receive persisted arena blocks");
            JsonObject report = state("completed"); report.addProperty("passed", true);
            report.addProperty("actualTCP", true); report.addProperty("loadedWithoutGrants", true);
            report.addProperty("acceptedCurrentConnectionOwner", true); report.addProperty("clientInventoryStackWrites", false);
            report.addProperty("clientKnowledgeGrants", false); report.addProperty("researchRequests", 0);
            report.addProperty("firststepsStage", 4); report.addProperty("stableNativeTicks", stableTicks);
            report.addProperty("catalogDigest", view.digest()); report.add("loadedPlayer", TcpBootstrapReloadAudit.playerSnapshot(owner));
            writeTerminal(directory, "client", report); sealed = true;
            writeState(directory, "client", state("completed"));
            Thaumcraft.log.info("Private TCP restart client accepted actual new-JVM full FIRSTSTEPS owner");
        }

        JsonObject state(String phase) {
            JsonObject result = TcpBootstrapReloadAudit.reloadIdentity(marker);
            result.addProperty("status", phase.equals("completed") ? "completed" : "running"); result.addProperty("phase", phase);
            result.addProperty("observedUtc", Instant.now().toString()); result.addProperty("ownerUuid", OWNER_UUID.toString());
            if (connection != null) {
                JsonObject proof = new JsonObject(); var remote = (InetSocketAddress) connection.getRemoteAddress();
                proof.addProperty("memory", connection.isMemoryConnection()); proof.addProperty("integratedServer", client.hasSingleplayerServer());
                proof.addProperty("remoteHost", remote.getAddress().getHostAddress()); proof.addProperty("remotePort", remote.getPort());
                proof.addProperty("ownerUuid", owner.getUUID().toString()); proof.addProperty("connected", connection.isConnected());
                result.add("connection", proof);
                result.addProperty("firststepsStage", ThaumcraftCapabilities.getKnowledge(owner).getResearchStage("FIRSTSTEPS"));
            }
            return result;
        }

        void fail(String failure) {
            if (stopped) return;
            try {
                if (!sealed) { JsonObject result = state("failed"); result.addProperty("passed", false);
                    result.addProperty("status", "failed"); result.addProperty("failure", failure); writeTerminal(directory, "client", result); }
            } catch (Exception writing) { Thaumcraft.log.error("Unable to record restart client failure", writing); }
            stopped = true; client.stop();
        }
    }
}
