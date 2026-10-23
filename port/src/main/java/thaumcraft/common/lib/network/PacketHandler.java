package thaumcraft.common.lib.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import thaumcraft.Thaumcraft;
import thaumcraft.client.lib.ClientPacketHandlers;
import thaumcraft.client.lib.ClientPlayerDataHandlers;
import thaumcraft.client.lib.ClientResearchCatalogHandlers;
import thaumcraft.client.lib.ClientKnowledgeGainHandlers;
import thaumcraft.client.lib.ClientAuraHandlers;
import thaumcraft.common.lib.network.misc.PacketKnowledgeGain;
import thaumcraft.common.lib.network.misc.PacketAuraToClient;
import thaumcraft.common.lib.network.misc.PacketSyncObjectTags;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;
import thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer;
import thaumcraft.common.lib.network.playerdata.ServerResearchRequestHandler;
import thaumcraft.common.lib.network.research.PacketSyncResearchCatalog;

/**
 * Replacement for TC6's SimpleNetworkWrapper. Each of the 40 IMessages becomes a CustomPacketPayload record
 * with a StreamCodec and is registered here (playToClient / playToServer).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class PacketHandler {
    public static final String PROTOCOL_VERSION = "6";

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(thaumcraft.common.lib.network.misc.PacketSyncJeiRecipes.TYPE,
            thaumcraft.common.lib.network.misc.PacketSyncJeiRecipes.STREAM_CODEC,
            (packet, ctx) -> thaumcraft.client.lib.ClientJeiRecipeSnapshot.accept(packet, ctx));
        registrar.playToClient(PacketSyncResearchCatalog.TYPE, PacketSyncResearchCatalog.STREAM_CODEC,
            (packet, ctx) -> ClientResearchCatalogHandlers.catalog(packet, ctx));
        registrar.playToClient(PacketSyncObjectTags.TYPE, PacketSyncObjectTags.STREAM_CODEC,
            (packet, ctx) -> ClientPacketHandlers.handleObjectTags(packet, ctx.connection()));
        registrar.playToServer(thaumcraft.common.lib.network.misc.PacketArcaneRecipeTransfer.TYPE,
            thaumcraft.common.lib.network.misc.PacketArcaneRecipeTransfer.STREAM_CODEC,
            thaumcraft.common.lib.network.misc.ServerArcaneTransferHandler::receive);
        registrar.playToClient(thaumcraft.common.lib.network.misc.PacketArcaneRecipeTransferResult.TYPE,
            thaumcraft.common.lib.network.misc.PacketArcaneRecipeTransferResult.STREAM_CODEC,
            (packet, ctx) -> thaumcraft.client.lib.ClientArcaneTransferFeedback.receive(packet, ctx));
        registrar.playToClient(PacketSyncKnowledge.TYPE, PacketSyncKnowledge.STREAM_CODEC,
            (packet, ctx) -> ClientPlayerDataHandlers.knowledge(packet, ctx));
        registrar.playToClient(PacketSyncWarp.TYPE, PacketSyncWarp.STREAM_CODEC,
            (packet, ctx) -> ClientPlayerDataHandlers.warp(packet));
        registrar.playToClient(PacketKnowledgeGain.TYPE, PacketKnowledgeGain.STREAM_CODEC,
            (packet, ctx) -> ClientKnowledgeGainHandlers.gain(packet, ctx));
        registrar.playToClient(PacketAuraToClient.TYPE, PacketAuraToClient.STREAM_CODEC,
            (packet, ctx) -> ClientAuraHandlers.aura(packet, ctx));
        // Required PLAY/serverbound route; registrar defaults to MAIN. No client grant flags.
        registrar.playToServer(PacketSyncProgressToServer.TYPE, PacketSyncProgressToServer.STREAM_CODEC,
            ServerResearchRequestHandler::receive);
        registrar.playToServer(thaumcraft.common.lib.network.playerdata.PacketResearchReadToServer.TYPE,
            thaumcraft.common.lib.network.playerdata.PacketResearchReadToServer.STREAM_CODEC,
            thaumcraft.common.lib.network.playerdata.ServerResearchReadHandler::receive);
        registrar.playToClient(thaumcraft.common.lib.network.playerdata.PacketWarpMessage.TYPE,
            thaumcraft.common.lib.network.playerdata.PacketWarpMessage.STREAM_CODEC,
            (packet, ctx) -> thaumcraft.client.lib.ClientWarpHandlers.message(packet, ctx));
        registrar.playToClient(thaumcraft.common.lib.network.misc.PacketMiscEvent.TYPE,
            thaumcraft.common.lib.network.misc.PacketMiscEvent.STREAM_CODEC,
            (packet, ctx) -> thaumcraft.client.lib.ClientWarpHandlers.misc(packet, ctx));
        registrar.playToClient(thaumcraft.common.lib.network.fx.PacketFXSonic.TYPE,
            thaumcraft.common.lib.network.fx.PacketFXSonic.STREAM_CODEC,
            (packet, ctx) -> thaumcraft.client.lib.ClientWarpHandlers.sonic(packet, ctx));
        registrar.playToServer(thaumcraft.common.lib.network.research.PacketTheorycraftAction.TYPE,
            thaumcraft.common.lib.network.research.PacketTheorycraftAction.STREAM_CODEC,
            thaumcraft.common.lib.research.theorycraft.TheorycraftNetwork::receive);
        registrar.playToClient(thaumcraft.common.lib.network.research.PacketTheorycraftState.TYPE,
            thaumcraft.common.lib.network.research.PacketTheorycraftState.STREAM_CODEC,
            (packet, ctx) -> thaumcraft.client.gui.TheorycraftClientPackets.state(packet, ctx));
        // Remaining feature-owned payloads are registered by their subsystem subscribers.
    }
}
