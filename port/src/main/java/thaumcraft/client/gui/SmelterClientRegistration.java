package thaumcraft.client.gui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.tiles.essentia.EssentiaRegistries;

@EventBusSubscriber(modid = Thaumcraft.MODID,bus = EventBusSubscriber.Bus.MOD,value = Dist.CLIENT)
public final class SmelterClientRegistration {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(EssentiaRegistries.SMELTER_MENU,GuiSmelter::new); }
}
