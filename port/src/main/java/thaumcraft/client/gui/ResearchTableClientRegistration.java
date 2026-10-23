package thaumcraft.client.gui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.tiles.crafting.ResearchTableRegistries;

/** Client-only binding; no common registrar references Minecraft/client classes. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ResearchTableClientRegistration {
    private ResearchTableClientRegistration() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(ResearchTableRegistries.RESEARCH_TABLE,thaumcraft.client.renderers.tile.TileResearchTableRenderer::new);}

    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(ResearchTableRegistries.RESEARCH_TABLE_MENU, GuiResearchTable::new);
    }
}
