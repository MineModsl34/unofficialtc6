package thaumcraft.client.lib;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.network.misc.PacketAuraToClient;

/** One immutable owner-only aura snapshot. Original aura HUD presentation is a later UI slice. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientAuraHandlers {
    private static Connection connection;
    private static PacketAuraToClient current;

    private ClientAuraHandlers() {}

    public static void aura(PacketAuraToClient packet, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        requireMainThread(minecraft);
        var listener = minecraft.getConnection();
        if (minecraft.player == null || minecraft.level == null || listener == null
            || listener.getConnection() != context.connection()) return;
        connection = context.connection();
        current = packet;
    }

    @Nullable
    public static PacketAuraToClient currentAura() {
        Minecraft minecraft = Minecraft.getInstance();
        requireMainThread(minecraft);
        var listener = minecraft.getConnection();
        return minecraft.player != null && listener != null && listener.getConnection() == connection ? current : null;
    }

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) { clear(); }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    private static void clear() {
        requireMainThread(Minecraft.getInstance());
        connection = null;
        current = null;
    }

    private static void requireMainThread(Minecraft minecraft) {
        if (!minecraft.isSameThread()) throw new IllegalStateException("Aura snapshot requires client MAIN");
    }
}
