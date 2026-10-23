package thaumcraft.client.gui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.tiles.crafting.ArcaneWorkbenchRegistries;

@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ArcaneWorkbenchClientRegistration {
    private ArcaneWorkbenchClientRegistration() {}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(ArcaneWorkbenchRegistries.ARCANE_WORKBENCH_MENU, GuiArcaneWorkbench::new);
    }
}
