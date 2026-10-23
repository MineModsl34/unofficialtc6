package thaumcraft.client.lib.events;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
/** Native tooltip images replace the source blank-line/PostBackground injection. */
@EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class HudClientRegistration {
    private HudClientRegistration(){}
    @SubscribeEvent public static void tooltips(RegisterClientTooltipComponentFactoriesEvent event){event.register(RenderEventHandler.AspectTooltip.class,RenderEventHandler.AspectTooltipRenderer::new);}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){event.registerReloadListener((barrier,resources,prepareProfiler,applyProfiler,background,game)->barrier.wait(null).thenRunAsync(()->{thaumcraft.client.lib.UtilsFX.clear();RenderEventHandler.wandHandler.clear();thaumcraft.client.SealClientEvents.clearRenderCache();},game));}
}
