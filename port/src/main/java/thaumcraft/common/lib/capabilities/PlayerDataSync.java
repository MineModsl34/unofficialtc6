package thaumcraft.common.lib.capabilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.lib.network.misc.PacketKnowledgeGain;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;

/** TC6 explicit/20-tick sync, scoped to server sessions; storage mutators do not mark dirty. */
public final class PlayerDataSync {
    // UUID replaces the old name-keyed global syncList, without leaking between server sessions.
    private static final Map<MinecraftServer, Set<UUID>> DIRTY = new WeakHashMap<>();
    private static final ThreadLocal<BiConsumer<ServerPlayer, CustomPacketPayload>> TEST_SENDER = new ThreadLocal<>();

    private PlayerDataSync() {}

    public static void syncKnowledgeNow(ServerPlayer owner) {
        send(owner, PacketSyncKnowledge.fromPlayer(owner));
        // Explicit sync must not consume a queued ResearchManager update.
    }

    public static void syncWarpNow(ServerPlayer owner) {
        send(owner, PacketSyncWarp.fromPlayer(owner));
    }

    /** Same owner-only production route and guarded GameTest capture as personal-data snapshots. */
    public static void sendKnowledgeGain(ServerPlayer owner, PacketKnowledgeGain packet) {
        send(owner, packet);
    }

    public static void sendWarpMessage(ServerPlayer owner, thaumcraft.common.lib.network.playerdata.PacketWarpMessage packet) {
        send(owner, packet);
    }

    public static void sendMiscEvent(ServerPlayer owner, thaumcraft.common.lib.network.misc.PacketMiscEvent packet) {
        send(owner, packet);
    }

    public static void sendTheoryState(ServerPlayer owner, thaumcraft.common.lib.network.research.PacketTheorycraftState packet) {
        send(owner, packet);
    }

    public static void syncAllNow(ServerPlayer owner) {
        syncKnowledgeNow(owner);
        syncWarpNow(owner);
    }

    private static void send(ServerPlayer owner, CustomPacketPayload payload) {
        var capture = TEST_SENDER.get();
        if (capture != null) {
            capture.accept(owner, payload);
        } else {
            PacketDistributor.sendToPlayer(owner, payload);
            if (Boolean.getBoolean(thaumcraft.common.lib.research.catalog.validation.ResearchStageFourNetworkAudit.ENABLE_PROPERTY))
                thaumcraft.common.lib.research.catalog.validation.ResearchStageFourNetworkAudit.observeSent(owner, payload);
            if (Boolean.getBoolean("thaumcraft.researchProgressNetworkAudit")) {
                thaumcraft.common.lib.research.catalog.validation.ResearchProgressNetworkAudit.observeSent(owner, payload);
            }
            if (Boolean.getBoolean("thaumcraft.researchCatalogNetworkAudit")) {
                thaumcraft.common.lib.research.catalog.validation.ResearchCatalogNetworkAudit.observeSent(owner, payload);
            }
            if (Boolean.getBoolean("thaumcraft.playerDataNetworkAudit")) {
                thaumcraft.common.lib.capabilities.validation.PlayerDataNetworkAudit.observeSent(owner, payload);
            }
        }
    }

    public static void markKnowledgeDirty(ServerPlayer owner) {
        synchronized (DIRTY) {
            DIRTY.computeIfAbsent(owner.getServer(), server -> new HashSet<>()).add(owner.getUUID());
        }
    }

    public static boolean isKnowledgeDirty(ServerPlayer owner) {
        synchronized (DIRTY) {
            Set<UUID> pending = DIRTY.get(owner.getServer());
            return pending != null && pending.contains(owner.getUUID());
        }
    }

    public static void flushKnowledgeIfDue(ServerPlayer owner) {
        if (owner.tickCount % 20 != 0) return;
        boolean pending;
        synchronized (DIRTY) {
            Set<UUID> players = DIRTY.get(owner.getServer());
            // Source PlayerEvents removes the entry before calling knowledge.sync.
            pending = players != null && players.remove(owner.getUUID());
        }
        if (pending) syncKnowledgeNow(owner);
    }

    public static void forget(ServerPlayer owner) {
        synchronized (DIRTY) {
            Set<UUID> pending = DIRTY.get(owner.getServer());
            if (pending != null) pending.remove(owner.getUUID());
        }
    }

    public static void clearServer(MinecraftServer server) {
        synchronized (DIRTY) {
            DIRTY.remove(server);
        }
    }

    /** Common-side application logic used by the client handler and deterministic tests. */
    public static List<String> applyKnowledgeSnapshot(IPlayerKnowledge local, CompoundTag data) {
        local.deserializeNBT(data.copy());
        List<String> notifications = new ArrayList<>();
        for (String key : local.getResearchList()) {
            if (local.hasResearchFlag(key, EnumResearchFlag.POPUP) && ResearchCategories.getResearch(key) != null) {
                notifications.add(key);
            }
            local.clearResearchFlag(key, EnumResearchFlag.POPUP);
        }
        return List.copyOf(notifications);
    }

    /** No networking interception is possible outside the explicitly enabled GameTest process. */
    public static AutoCloseable captureGameTestPackets(BiConsumer<ServerPlayer, CustomPacketPayload> sender) {
        if (!GameTestHooks.isGametestServer()) throw new IllegalStateException("GameTest capture is disabled");
        var previous = TEST_SENDER.get();
        TEST_SENDER.set(java.util.Objects.requireNonNull(sender));
        return () -> {
            if (previous == null) TEST_SENDER.remove();
            else TEST_SENDER.set(previous);
        };
    }
}
