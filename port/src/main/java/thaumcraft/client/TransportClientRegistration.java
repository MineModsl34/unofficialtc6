package thaumcraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.client.renderers.tile.TileCentrifugeRenderer;
import thaumcraft.client.renderers.tile.TubeAttachmentRenderer;
import thaumcraft.common.tiles.essentia.TileTubeFilter;
import thaumcraft.common.tiles.essentia.TransportRegistries;

@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TransportClientRegistration {
    private TransportClientRegistration() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TransportRegistries.VALVE, TubeAttachmentRenderer::new);
        event.registerBlockEntityRenderer(TransportRegistries.ONEWAY, TubeAttachmentRenderer::new);
        event.registerBlockEntityRenderer(TransportRegistries.BUFFER, TubeAttachmentRenderer::new);
        event.registerBlockEntityRenderer(TransportRegistries.CENTRIFUGE, TileCentrifugeRenderer::new);
    }
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Block event) { event.register((state, level, pos, tint) ->
        tint == 1 && level != null && pos != null && level.getBlockEntity(pos) instanceof TileTubeFilter filter && filter.aspectFilter != null ? 0xff000000 | filter.aspectFilter.getColor() : 0xffffffff,
        BlocksTC.tubeFilter); }
}
