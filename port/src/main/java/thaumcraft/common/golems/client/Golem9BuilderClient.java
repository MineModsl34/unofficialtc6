package thaumcraft.common.golems.client;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import thaumcraft.client.gui.GuiGolemBuilder;
import thaumcraft.client.renderers.tile.TileGolemBuilderRenderer;
import thaumcraft.common.golems.*;
import thaumcraft.common.tiles.crafting.Golem9BuilderRegistries;

@EventBusSubscriber(modid="thaumcraft",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class Golem9BuilderClient {
    private Golem9BuilderClient() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Golem9EntityRegistries.GOLEM,RenderThaumcraftGolem::new);
        event.registerBlockEntityRenderer(Golem9BuilderRegistries.TYPE,TileGolemBuilderRenderer::new);
        EntityThaumcraftGolem.CLIENT_STATUS = GolemEmotes::status;
    }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(Golem9BuilderRegistries.MENU,GuiGolemBuilder::new); }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((barrier,resources,prepareProfiler,applyProfiler,background,game) -> barrier.wait(null).thenRunAsync(() -> { GolemObjMeshes.clear(); GolemPartAnimations.clear(); },game));
    }
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack,tint) -> { try { return GolemItemData.tag(stack).contains("props") ? 0xff000000 | GolemItemData.properties(stack).getMaterial().itemColor : -1; } catch (IllegalArgumentException malformed) { return -1; } },thaumcraft.api.items.ItemsTC.golemPlacer);
    }
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private GolemBuilderItemRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { if (renderer == null) renderer = new GolemBuilderItemRenderer(); return renderer; }
        },thaumcraft.api.blocks.BlocksTC.golemBuilder.asItem());
    }
}
