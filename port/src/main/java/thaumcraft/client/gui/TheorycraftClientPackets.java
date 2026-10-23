package thaumcraft.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.common.container.ContainerResearchTable;
import thaumcraft.common.lib.network.research.PacketTheorycraftState;

/** Loaded by the deferred client route only. An old connection/menu cannot install a new view. */
public final class TheorycraftClientPackets {
    private TheorycraftClientPackets() {}
    public static void state(PacketTheorycraftState packet, IPayloadContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) throw new IllegalStateException("Theory state requires client MAIN");
        if (context.protocol() != ConnectionProtocol.PLAY || context.flow() != PacketFlow.CLIENTBOUND || client.getConnection() == null
            || client.getConnection().getConnection() != context.connection() || client.player == null
            || !(client.player.containerMenu instanceof ContainerResearchTable menu) || menu.containerId != packet.menuId()) return;
        long generation = menu.receivedStateGeneration();
        menu.acceptTheoryState(packet, client.player.registryAccess());
        if (menu.receivedStateGeneration() != generation && Boolean.getBoolean("thaumcraft.researchStageFourNetworkAudit"))
            thaumcraft.client.lib.ResearchStageFourNetworkClientAudit.theoryState(packet, menu);
    }
}
