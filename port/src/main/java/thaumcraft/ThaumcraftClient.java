package thaumcraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import thaumcraft.proxies.ClientProxy;

/** Client entry point, the analogue of TC6's ClientProxy.preInit/init. */
@Mod(value = Thaumcraft.MODID, dist = Dist.CLIENT)
public class ThaumcraftClient {
    public ThaumcraftClient(IEventBus modBus, ModContainer container) {
        Thaumcraft.proxy = new ClientProxy();
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // Client subscribers install colors, keys, renderers and shaders on their native lifecycle events.
    }
}
