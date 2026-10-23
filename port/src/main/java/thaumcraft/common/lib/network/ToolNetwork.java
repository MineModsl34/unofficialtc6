package thaumcraft.common.lib.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.network.fx.PacketFXScanSource;
import thaumcraft.common.lib.network.fx.PacketFXSlash;

/** Presentation routes only. No client/serverbound enchanting/mining request is added. */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ToolNetwork {
    private ToolNetwork() {}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PacketHandler.PROTOCOL_VERSION);
        registrar.playToClient(PacketFXScanSource.TYPE, PacketFXScanSource.STREAM_CODEC,
            (packet, context) -> thaumcraft.client.lib.ToolClientPackets.scan(packet));
        registrar.playToClient(PacketFXSlash.TYPE, PacketFXSlash.STREAM_CODEC,
            (packet, context) -> thaumcraft.client.lib.ToolClientPackets.slash(packet));
    }
}
