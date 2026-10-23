package thaumcraft.common.lib.network.misc;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.common.container.ContainerArcaneWorkbench;

public final class ServerArcaneTransferHandler {
    private ServerArcaneTransferHandler() {}
    public static void receive(PacketArcaneRecipeTransfer packet, IPayloadContext context) {
        if (context.protocol() != ConnectionProtocol.PLAY || context.flow() != PacketFlow.SERVERBOUND
            || !(context.player() instanceof ServerPlayer owner)) return;
        context.enqueueWork(() -> {
            if (context.player() != owner || owner.getServer() == null || !owner.getServer().isSameThread()
                || owner.connection.getConnection() != context.connection() || !context.connection().isConnected()) return;
            boolean accepted = owner.containerMenu instanceof ContainerArcaneWorkbench menu && menu.transferRecipe(owner, packet);
            PacketDistributor.sendToPlayer(owner, new PacketArcaneRecipeTransferResult(packet.requestId(), packet.menuId(), packet.stateId(), accepted));
        });
    }
}
