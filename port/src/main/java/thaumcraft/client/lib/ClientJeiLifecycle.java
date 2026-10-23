package thaumcraft.client.lib;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.Thaumcraft;

/** Optional callback installed only by JEI discovery. Loading this hook never requires JEI. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientJeiLifecycle {
    private static Runnable refresh;
    private ClientJeiLifecycle() {}
    public static void install(Runnable callback) { refresh = callback; }
    public static void clear() { refresh = null; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { if (refresh != null) refresh.run(); }
}
