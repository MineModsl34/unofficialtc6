package thaumcraft.client.lib;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.client.gui.ResearchToast;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;

/** Loaded only when a client-bound handler executes, including integrated singleplayer. */
public final class ClientPlayerDataHandlers {
    private ClientPlayerDataHandlers() {}

    public static void knowledge(PacketSyncKnowledge packet, IPayloadContext context) {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("Player knowledge application requires client MAIN");
        var listener = minecraft.getConnection();
        if (listener == null || listener.getConnection() != context.connection()) return;
        if (minecraft.player == null) return;
        if (!ClientResearchCatalogHandlers.readyForCurrentConnection()) {
            if (minecraft.getConnection() != null) minecraft.getConnection().getConnection().disconnect(
                Component.literal("Thaumcraft research catalog is missing before player knowledge."));
            return;
        }
        var notifications = PlayerDataSync.applyKnowledgeSnapshot(
            ThaumcraftCapabilities.getKnowledge(minecraft.player), packet.data());
        ClientResearchPresentation.knowledgeApplied(context.connection(), packet.data());
        for (String key : notifications) {
            var entry = ResearchCategories.getResearch(key);
            if (entry != null) minecraft.getToasts().addToast(new ResearchToast(entry));
        }
        if (Boolean.getBoolean("thaumcraft.playerDataNetworkAudit")) PlayerDataNetworkClientAudit.observeKnowledge(packet);
        if (Boolean.getBoolean("thaumcraft.researchCatalogNetworkAudit")) ResearchCatalogNetworkClientAudit.observeKnowledge(packet);
        if (Boolean.getBoolean("thaumcraft.researchProgressNetworkAudit")) ResearchProgressNetworkClientAudit.knowledge(packet);
        if (Boolean.getBoolean("thaumcraft.researchStageFourNetworkAudit")) ResearchStageFourNetworkClientAudit.knowledge(packet);
    }

    public static void warp(PacketSyncWarp packet) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            ThaumcraftCapabilities.getWarp(player).deserializeNBT(packet.data());
            if (Boolean.getBoolean("thaumcraft.playerDataNetworkAudit")) PlayerDataNetworkClientAudit.observeWarp(packet);
            if (Boolean.getBoolean("thaumcraft.researchCatalogNetworkAudit")) ResearchCatalogNetworkClientAudit.observeWarp(packet);
            if (Boolean.getBoolean("thaumcraft.researchProgressNetworkAudit")) ResearchProgressNetworkClientAudit.warp(packet);
            if (Boolean.getBoolean("thaumcraft.researchStageFourNetworkAudit")) ResearchStageFourNetworkClientAudit.warp(packet);
        }
    }
}
