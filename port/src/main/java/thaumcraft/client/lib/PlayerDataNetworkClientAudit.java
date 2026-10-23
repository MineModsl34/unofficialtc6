package thaumcraft.client.lib;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;

/** Real LocalPlayer recorder; no packet, connection, codec or attachment is replaced. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class PlayerDataNetworkClientAudit {
    private static Run active;
    private static boolean attempted;

    private PlayerDataNetworkClientAudit() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(PlayerDataNetworkAudit.ENABLE_PROPERTY)) return;
        Run run = initialize();
        if (run == null || run.finished) return;
        try {
            run.step();
        } catch (Exception failure) {
            run.finish("failed", failure.toString());
        }
    }

    /** Observe only after the production handler has actually applied the snapshot. */
    public static void observeKnowledge(PacketSyncKnowledge packet) {
        observe("knowledge", packet.data());
    }

    public static void observeWarp(PacketSyncWarp packet) {
        observe("warp", packet.data());
    }

    private static void observe(String kind, CompoundTag packetData) {
        Run run = initialize();
        if (run == null || run.finished) return;
        try {
            run.observe(kind, packetData);
        } catch (Exception failure) {
            run.finish("failed", failure.toString());
        }
    }

    private static Run initialize() {
        if (active != null) return active;
        if (attempted || !Boolean.getBoolean(PlayerDataNetworkAudit.ENABLE_PROPERTY)) return null;
        attempted = true;
        try {
            active = new Run(Minecraft.getInstance());
        } catch (Exception failure) {
            Thaumcraft.log.error("Player-data client audit refused initialization", failure);
            Minecraft.getInstance().stop();
        }
        return active;
    }

    private static final class Run {
        private final Minecraft minecraft;
        private final Path directory;
        private final JsonObject marker;
        private final JsonObject state;
        private final JsonArray packets = new JsonArray();
        private final JsonArray entities = new JsonArray();
        private final JsonArray actions = new JsonArray();
        private final String expectedName;
        private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(900);
        private LocalPlayer lastPlayer;
        private Connection lastConnection;
        private int entityEpoch;
        private int connectionEpoch;
        private int knowledgeHandlers;
        private int warpHandlers;
        private int respawnRequests;
        private int creditsCallbacks;
        private int ticks;
        private String auditPhase = "startup";
        private boolean finished;

        private Run(Minecraft minecraft) throws Exception {
            this.minecraft = minecraft;
            expectedName = minecraft.getUser().getName();
            String role = PlayerDataNetworkAudit.A.equals(expectedName) ? "client-a"
                : PlayerDataNetworkAudit.B.equals(expectedName) ? "client-b" : null;
            if (role == null) throw new IllegalStateException("Only TC_Audit_A/B client profiles are allowed");
            if (minecraft.hasSingleplayerServer()) throw new IllegalStateException("Audit requires a real multiplayer TCP connection");
            directory = minecraft.gameDirectory.toPath().toRealPath();
            marker = PlayerDataNetworkAudit.validateDirectory(directory, role);
            state = PlayerDataNetworkAudit.identity(marker);
            state.addProperty("startedUtc", Instant.now().toString());
            state.addProperty("username", expectedName);
            state.add("packets", packets);
            state.add("entityInstances", entities);
            state.add("clientActions", actions);
            updateState();
            persist(false);
            Thaumcraft.log.info("Real player-data client recorder initialized for {}", expectedName);
        }

        private void checkConnection() {
            LocalPlayer player = minecraft.player;
            if (player == null) return;
            if (!expectedName.equals(player.getGameProfile().getName())) {
                throw new IllegalStateException("Unexpected actual LocalPlayer profile");
            }
            Connection connection = player.connection.getConnection();
            if (!(connection.getRemoteAddress() instanceof InetSocketAddress address)
                || !address.getAddress().isLoopbackAddress() || address.getPort() != marker.get("port").getAsInt()) {
                throw new IllegalStateException("Only exact private localhost TCP target is allowed");
            }
            if (connection != lastConnection) {
                lastConnection = connection;
                connectionEpoch++;
            }
            if (player != lastPlayer) {
                lastPlayer = player;
                entityEpoch++;
                JsonObject row = new JsonObject();
                row.addProperty("epoch", entityEpoch);
                row.addProperty("uuid", player.getUUID().toString());
                row.addProperty("entityId", player.getId());
                row.addProperty("identityHash", System.identityHashCode(player));
                row.addProperty("dimension", player.level().dimension().location().toString());
                row.addProperty("auditPhase", auditPhase);
                entities.add(row);
                if (entities.size() > 16) throw new IllegalStateException("Unbounded LocalPlayer replacement count");
            }
        }

        private void observe(String kind, CompoundTag packetData) throws Exception {
            checkConnection();
            if (minecraft.player == null) throw new IllegalStateException("Observe hook requires actual applied LocalPlayer");
            if (kind.equals("knowledge")) knowledgeHandlers++;
            else warpHandlers++;
            JsonObject row = new JsonObject();
            row.addProperty("sequence", packets.size() + 1);
            row.addProperty("kind", kind);
            row.addProperty("payloadNbt", packetData.toString());
            row.addProperty("uuid", minecraft.player.getUUID().toString());
            row.addProperty("entityEpoch", entityEpoch);
            row.addProperty("connectionEpoch", connectionEpoch);
            row.addProperty("dimension", minecraft.player.level().dimension().location().toString());
            row.addProperty("observedUtc", Instant.now().toString());
            packets.add(row);
            if (packets.size() > 128) throw new IllegalStateException("Unbounded private packet count");
            updateState();
            persist(false);
        }

        private void step() throws Exception {
            ticks++;
            if (System.nanoTime() > deadline) throw new IllegalStateException("Bounded real-client audit timeout");
            checkConnection();
            if (ticks % 5 != 0) return;
            Path controlPath = directory.getParent().resolve("server/server-state.json");
            JsonObject control = PlayerDataNetworkAudit.readProgressJson(controlPath, 4096);
            if (control != null) {
                if (!marker.get("token").equals(control.get("token")) || !marker.get("runId").equals(control.get("runId"))
                    || !PlayerDataNetworkAudit.MARKER.equals(control.get("marker").getAsString())
                    || !"server".equals(control.get("role").getAsString())) {
                    throw new IllegalStateException("Server audit isolation mismatch");
                }
                auditPhase = control.get("auditPhase").getAsString();
                if ("failed".equals(control.get("status").getAsString())
                    || "interrupted".equals(control.get("status").getAsString())) {
                    throw new IllegalStateException("Server audit failed in " + auditPhase);
                }
                LocalPlayer player = minecraft.player;
                if (PlayerDataNetworkAudit.A.equals(expectedName) && "death".equals(auditPhase)
                    && player != null && player.isDeadOrDying() && respawnRequests == 0) {
                    action("real-death-PERFORM_RESPAWN");
                    respawnRequests++;
                    player.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                }
                if (PlayerDataNetworkAudit.A.equals(expectedName) && "end-return".equals(auditPhase)
                    && minecraft.screen instanceof WinScreen credits && creditsCallbacks == 0) {
                    // Exact identified credits screen only. No generic screen, warning or blind input.
                    action("actual-WinScreen-received-and-onClose");
                    creditsCallbacks++;
                    credits.onClose(); // Vanilla callback sends PERFORM_RESPAWN to the actual server.
                }
                if ("finish".equals(auditPhase)) {
                    if (minecraft.player == null || knowledgeHandlers == 0 || warpHandlers == 0) {
                        throw new IllegalStateException("Cannot complete without actual applied owner snapshots");
                    }
                    finish("completed", null);
                    return;
                }
            }
            updateState();
            persist(false);
        }

        private void action(String name) {
            JsonObject row = new JsonObject();
            row.addProperty("name", name);
            row.addProperty("observedUtc", Instant.now().toString());
            row.addProperty("entityEpoch", entityEpoch);
            row.addProperty("auditPhase", auditPhase);
            actions.add(row);
            Thaumcraft.log.info("Player-data client {} action {}", expectedName, name);
        }

        private void updateState() {
            state.addProperty("auditPhase", auditPhase);
            state.addProperty("entityEpoch", entityEpoch);
            state.addProperty("connectionEpoch", connectionEpoch);
            state.addProperty("knowledgeHandlers", knowledgeHandlers);
            state.addProperty("warpHandlers", warpHandlers);
            state.addProperty("respawnRequests", respawnRequests);
            state.addProperty("creditsCallbacks", creditsCallbacks);
            state.addProperty("remoteLoopback", lastConnection != null);
            if (minecraft.player != null) {
                var player = minecraft.player;
                state.addProperty("uuid", player.getUUID().toString());
                state.addProperty("dimension", player.level().dimension().location().toString());
                state.addProperty("dead", player.isDeadOrDying());
                JsonArray position = new JsonArray();
                position.add(player.getX());
                position.add(player.getY());
                position.add(player.getZ());
                state.add("position", position);
                JsonArray velocity = new JsonArray();
                velocity.add(player.getDeltaMovement().x);
                velocity.add(player.getDeltaMovement().y);
                velocity.add(player.getDeltaMovement().z);
                state.add("velocity", velocity);
                state.addProperty("onGround", player.onGround());
                state.addProperty("knowledgeNbt", ThaumcraftCapabilities.getKnowledge(player).serializeNBT().toString());
                state.addProperty("warpNbt", ThaumcraftCapabilities.getWarp(player).serializeNBT().toString());
            }
        }

        private void persist(boolean finalReport) throws Exception {
            PlayerDataNetworkAudit.writeJson(directory.resolve(finalReport ? "client-report.json" : "client-state.json"), state, finalReport);
        }

        private void finish(String status, String detail) {
            if (finished) return;
            finished = true;
            try {
                try {
                    updateState();
                } catch (Exception snapshotFailure) {
                    status = "failed";
                    detail = (detail == null ? "" : detail + "; ") + "Terminal client snapshot failed: " + snapshotFailure;
                }
                state.addProperty("status", status);
                state.addProperty("finishedUtc", Instant.now().toString());
                if (detail != null) state.addProperty("failure", detail);
                try {
                    persist(false);
                } catch (Exception progressFailure) {
                    // Keep the original diagnostic, and attempt the independent CREATE_NEW final
                    // report even if the append-only progress journal cannot be written.
                    if ("completed".equals(status)) status = "failed";
                    detail = (detail == null ? "" : detail + "; ") + "Terminal progress publish failed: " + progressFailure;
                    state.addProperty("status", status);
                    state.addProperty("failure", detail);
                    Thaumcraft.log.error("Unable to publish terminal real-client audit progress", progressFailure);
                }
                persist(true);
                Thaumcraft.log.info("Real player-data client audit {} for {}: {}", status, expectedName, detail);
            } catch (Exception failure) {
                Thaumcraft.log.error("Unable to write real-client audit report", failure);
            } finally {
                minecraft.stop();
            }
        }
    }
}
