package thaumcraft.common.lib.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.container.ContainerThaumatorium;
import thaumcraft.common.lib.network.misc.PacketSelectThaumotoriumRecipeToServer;
import thaumcraft.common.lib.network.misc.PacketThaumatoriumState;

@EventBusSubscriber(modid = Thaumcraft.MODID,bus = EventBusSubscriber.Bus.MOD)
public final class ProcessingNetwork {
    private ProcessingNetwork() {}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PacketHandler.PROTOCOL_VERSION);
        registrar.playToServer(PacketSelectThaumotoriumRecipeToServer.TYPE,PacketSelectThaumotoriumRecipeToServer.STREAM_CODEC,(packet,context) -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof ContainerThaumatorium menu) menu.handleSelection(player,packet);
        });
        registrar.playToClient(PacketThaumatoriumState.TYPE,PacketThaumatoriumState.STREAM_CODEC,(packet,context) -> thaumcraft.client.gui.ProcessingClientPackets.state(packet));
    }
}
