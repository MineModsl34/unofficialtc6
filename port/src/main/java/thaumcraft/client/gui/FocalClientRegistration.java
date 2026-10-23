package thaumcraft.client.gui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumcraft.client.renderers.tile.TileFocalManipulatorRenderer;
import thaumcraft.Thaumcraft;
import thaumcraft.common.tiles.crafting.FocalRegistries;

@EventBusSubscriber(modid = Thaumcraft.MODID,value = Dist.CLIENT,bus = EventBusSubscriber.Bus.MOD)
public final class FocalClientRegistration {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(FocalRegistries.MENU,GuiFocalManipulator::new); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(FocalRegistries.TYPE,TileFocalManipulatorRenderer::new);}
}
