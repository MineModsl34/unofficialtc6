package thaumcraft.common.lib.network.playerdata;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.research.ResearchBrowserAvailability;
import thaumcraft.common.lib.research.ResearchReadFingerprint;
import thaumcraft.common.lib.research.catalog.ResearchCatalogRuntime;

/** Clear only source PAGE/RESEARCH, on the actual owning server connection and exact snapshot. */
public final class ServerResearchReadHandler {
    private ServerResearchReadHandler() {}

    public static void receive(PacketResearchReadToServer packet, IPayloadContext context) {
        if (context.protocol() != ConnectionProtocol.PLAY || context.flow() != PacketFlow.SERVERBOUND
            || !(context.player() instanceof ServerPlayer owner)) return;
        context.enqueueWork(() -> {
            if (context.player() != owner || owner.getServer() == null
                || owner.connection.getConnection() != context.connection() || !context.connection().isConnected()) return;
            boolean changed = tryHandle(owner, packet);
            if (Boolean.getBoolean(thaumcraft.common.lib.research.catalog.validation.ResearchStageFourNetworkAudit.ENABLE_PROPERTY))
                thaumcraft.common.lib.research.catalog.validation.ResearchStageFourNetworkAudit.observeRead(owner, packet, changed);
        });
    }

    public static boolean tryHandle(ServerPlayer owner, PacketResearchReadToServer packet) {
        if (owner == null || owner.getServer() == null || !owner.getServer().isSameThread())
            throw new IllegalStateException("Research read requires owner server thread");
        if (packet == null) return false;
        if (!ServerResearchRequestHandler.admitRead(owner)) return false;
        var reference = packet.reference();
        var payload = ResearchCatalogRuntime.currentPayload(owner.getServer());
        var catalog = ResearchCatalogRuntime.currentSnapshot(owner.getServer());
        if (payload == null || catalog == null || !payload.digest().equals(reference.catalogDigest())) return false;
        var entry = catalog.entries().get(reference.researchKey());
        if (entry == null || entry != ResearchCategories.getResearch(reference.researchKey())
            || !catalog.ownedEntries().contains(entry)) return false;
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        var availability = ResearchBrowserAvailability.evaluate(knowledge, reference.researchKey());
        if (!knowledge.isResearchKnown(reference.researchKey()) || !availability.visible()
            || !availability.categoryAccessible()) return false;
        if (knowledge.getResearchStage(reference.researchKey()) != reference.expectedStage()
            || !ResearchReadFingerprint.of(knowledge.serializeNBT()).equals(packet.readStateDigest())) {
            PlayerDataSync.syncKnowledgeNow(owner); // bounded recovery of a stale received read view
            return false;
        }
        boolean changed = knowledge.clearResearchFlag(reference.researchKey(), EnumResearchFlag.PAGE);
        changed |= knowledge.clearResearchFlag(reference.researchKey(), EnumResearchFlag.RESEARCH);
        if (changed) PlayerDataSync.syncKnowledgeNow(owner);
        return changed;
    }
}
