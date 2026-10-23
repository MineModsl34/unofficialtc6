package thaumcraft.common.lib.network.playerdata;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.container.ContainerFocalManipulator;

@EventBusSubscriber(modid = Thaumcraft.MODID,bus = EventBusSubscriber.Bus.MOD)
public final class FocalNetwork {
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(PacketFocusEdit.TYPE,PacketFocusEdit.STREAM_CODEC,(packet,context) -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof ContainerFocalManipulator menu) {
                if (Boolean.getBoolean(thaumcraft.common.lib.validation.TcpCasterAuditIO.ENABLE_PROPERTY))
                    thaumcraft.common.lib.validation.TcpCasterAudit.observeFocalBefore(player,menu,packet);
                boolean accepted = menu.handle(player,packet);
                if (Boolean.getBoolean(thaumcraft.common.lib.validation.TcpCasterAuditIO.ENABLE_PROPERTY))
                    thaumcraft.common.lib.validation.TcpCasterAudit.observeFocalAfter(player,menu,packet,accepted);
            }
        });
        registrar.playToClient(PacketFocalState.TYPE,PacketFocalState.STREAM_CODEC,(packet,context) -> thaumcraft.client.gui.FocalClientPackets.state(packet));
    }
}
