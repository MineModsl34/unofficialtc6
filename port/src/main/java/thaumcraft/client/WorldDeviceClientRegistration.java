package thaumcraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.Thaumcraft;
import thaumcraft.common.tiles.devices.DeviceRegistries;
import thaumcraft.client.gui.GuiWorldDevice;
import thaumcraft.client.renderers.tile.TileWorldDeviceRenderer;

@EventBusSubscriber(modid = Thaumcraft.MODID,bus = EventBusSubscriber.Bus.MOD,value = Dist.CLIENT)
public final class WorldDeviceClientRegistration {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(DeviceRegistries.SPA_MENU,GuiWorldDevice::new); event.register(DeviceRegistries.SPRAYER_MENU,GuiWorldDevice::new); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(DeviceRegistries.DEVICE,TileWorldDeviceRenderer::new); }
}
