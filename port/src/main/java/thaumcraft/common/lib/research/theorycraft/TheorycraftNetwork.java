package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.common.container.ContainerResearchTable;
import thaumcraft.common.lib.network.research.PacketTheorycraftAction;

/** The sender's live menu and exact server revision provide authority, not supplied tile coordinates. */
public final class TheorycraftNetwork {
    private TheorycraftNetwork() {}
    public static void receive(PacketTheorycraftAction packet, IPayloadContext context) {
        if (context.protocol() != ConnectionProtocol.PLAY || context.flow() != PacketFlow.SERVERBOUND || !(context.player() instanceof ServerPlayer owner)) return;
        context.enqueueWork(() -> {
            if (context.player() != owner || context.protocol() != ConnectionProtocol.PLAY || context.flow() != PacketFlow.SERVERBOUND
                || owner.connection.getConnection() != context.connection() || !context.connection().isConnected()) return;
            boolean applied = tryHandle(owner, packet);
            if (Boolean.getBoolean(thaumcraft.common.lib.research.catalog.validation.ResearchStageFourNetworkAudit.ENABLE_PROPERTY))
                thaumcraft.common.lib.research.catalog.validation.ResearchStageFourNetworkAudit.observeTheoryAction(owner, packet, applied);
        });
    }
    public static boolean tryHandle(ServerPlayer owner, PacketTheorycraftAction packet) {
        if (owner.getServer() == null || !owner.getServer().isSameThread()) throw new IllegalStateException("Theory actions require the owner server thread");
        if (!(owner.containerMenu instanceof ContainerResearchTable menu) || menu.containerId != packet.menuId()) return false;
        return menu.handleTheoryAction(owner, packet);
    }
}
