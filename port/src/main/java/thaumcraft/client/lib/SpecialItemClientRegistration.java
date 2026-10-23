package thaumcraft.client.lib;

import thaumcraft.client.renderers.entity.RenderSpecialItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.SpecialItemRegistries;

/** Source seeded special-item fan renderer; physical floating/explosion immunity remains common. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SpecialItemClientRegistration {
    private SpecialItemClientRegistration() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SpecialItemRegistries.TYPE, RenderSpecialItem::new);
    }
}
