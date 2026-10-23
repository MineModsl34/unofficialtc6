package thaumcraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.client.gui.GuiThaumatorium;
import thaumcraft.client.renderers.tile.TileBellowsRenderer;
import thaumcraft.client.renderers.tile.TileThaumatoriumRenderer;
import thaumcraft.common.tiles.devices.ProcessingRegistries;

@EventBusSubscriber(modid = Thaumcraft.MODID,bus = EventBusSubscriber.Bus.MOD,value = Dist.CLIENT)
public final class ProcessingClientRegistration {
    private ProcessingClientRegistration() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ProcessingRegistries.BELLOWS,TileBellowsRenderer::new);
        event.registerBlockEntityRenderer(ProcessingRegistries.THAUMATORIUM,TileThaumatoriumRenderer::new);
    }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(ProcessingRegistries.THAUMATORIUM_MENU,GuiThaumatorium::new); }
}
