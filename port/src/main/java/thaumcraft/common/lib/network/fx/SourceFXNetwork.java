package thaumcraft.common.lib.network.fx;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.fx.SourceFX;
import thaumcraft.common.lib.network.PacketHandler;

@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class SourceFXNetwork {
    private SourceFXNetwork() {}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PacketHandler.PROTOCOL_VERSION).playToClient(PacketSourceFX.TYPE, PacketSourceFX.STREAM_CODEC,
            (packet, context) -> SourceFX.receive(packet)); // MAIN by default; client installs its own handler.
    }
}
