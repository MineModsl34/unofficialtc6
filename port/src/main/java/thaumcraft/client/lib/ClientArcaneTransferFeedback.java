package thaumcraft.client.lib;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.network.misc.PacketArcaneRecipeTransfer;
import thaumcraft.common.lib.network.misc.PacketArcaneRecipeTransferResult;
import thaumcraft.common.lib.crafting.ArcaneTransferReceiptSession;

/** Client receipt routing with no JEI dependency. One bounded pending callback, scoped to the
 * exact connection/menu/request state; an optional JEI handler installs the completion callback.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientArcaneTransferFeedback {
    private static final ArcaneTransferReceiptSession SESSION = new ArcaneTransferReceiptSession();
    private static long ticks;
    private ClientArcaneTransferFeedback() {}
    public static boolean busy() { return SESSION.busy(); }
    public static boolean begin(PacketArcaneRecipeTransfer request, AbstractContainerMenu menu, Consumer<Boolean> complete) {
        var client = Minecraft.getInstance(); var listener = client.getConnection();
        if (listener == null || client.player == null || client.player.containerMenu != menu) return false;
        return SESSION.begin(listener.getConnection(), menu, request.requestId(), request.menuId(), request.stateId(), ticks + 100, complete);
    }
    public static void cancel(int requestId) { SESSION.cancel(requestId); }
    public static void receive(PacketArcaneRecipeTransferResult packet, IPayloadContext context) {
        if (context.protocol() != ConnectionProtocol.PLAY || context.flow() != PacketFlow.CLIENTBOUND) return;
        context.enqueueWork(() -> {
            var client = Minecraft.getInstance(); var listener = client.getConnection();
            if (listener == null || listener.getConnection() != context.connection()) return;
            SESSION.accept(context.connection(), client.player == null ? null : client.player.containerMenu,
                packet.requestId(), packet.menuId(), packet.stateId(), packet.accepted());
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ticks++;
        if (!SESSION.busy()) return;
        var client = Minecraft.getInstance(); var listener = client.getConnection();
        SESSION.tick(listener == null ? null : listener.getConnection(), client.player == null ? null : client.player.containerMenu, ticks);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { SESSION.end(); }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { SESSION.end(); }
}
