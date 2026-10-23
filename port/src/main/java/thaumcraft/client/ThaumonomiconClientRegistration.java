package thaumcraft.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import thaumcraft.Thaumcraft;
import thaumcraft.client.gui.GuiResearchBrowser;
import thaumcraft.client.gui.ResearchIconRenderer;
import thaumcraft.common.items.curios.ItemThaumonomicon;

/** No server menu: TC6 opened GUI12 from actual Item.use, not a cancellable PRE event. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ThaumonomiconClientRegistration {
    private ThaumonomiconClientRegistration() {}

    @SubscribeEvent
    public static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> ResearchIconRenderer.clearTextureMetadata());
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemThaumonomicon.registerClientOpener(player -> {
            var minecraft = Minecraft.getInstance();
            if (!minecraft.isSameThread() || player != minecraft.player || minecraft.level == null
                || player.level() != minecraft.level || minecraft.getConnection() == null) return;
            minecraft.setScreen(new GuiResearchBrowser());
        }));
    }
}
