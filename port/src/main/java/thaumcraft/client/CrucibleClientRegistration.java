package thaumcraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.client.renderers.tile.TileCrucibleRenderer;
import thaumcraft.common.tiles.crafting.CrucibleRegistries;

/** No common/server class references a client renderer. */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrucibleClientRegistration {
    private CrucibleClientRegistration() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CrucibleRegistries.CRUCIBLE, TileCrucibleRenderer::new);
    }
}
