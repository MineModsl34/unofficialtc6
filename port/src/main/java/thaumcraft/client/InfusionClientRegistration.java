package thaumcraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.client.renderers.tile.TileAlembicRenderer;
import thaumcraft.client.renderers.tile.TileInfusionMatrixRenderer;
import thaumcraft.client.renderers.tile.TileJarRenderer;
import thaumcraft.client.renderers.tile.TilePedestalRenderer;
import thaumcraft.common.tiles.crafting.InfusionRegistries;
import thaumcraft.common.tiles.essentia.EssentiaRegistries;

@EventBusSubscriber(modid = Thaumcraft.MODID,bus = EventBusSubscriber.Bus.MOD,value = Dist.CLIENT)
public final class InfusionClientRegistration {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(InfusionRegistries.INFUSION_MATRIX,TileInfusionMatrixRenderer::new);
        event.registerBlockEntityRenderer(InfusionRegistries.PEDESTAL,TilePedestalRenderer::new);
        event.registerBlockEntityRenderer(EssentiaRegistries.JAR,TileJarRenderer::new);
        event.registerBlockEntityRenderer(EssentiaRegistries.JAR_VOID,TileJarRenderer::new);
        event.registerBlockEntityRenderer(EssentiaRegistries.ALEMBIC,TileAlembicRenderer::new);
    }
}
