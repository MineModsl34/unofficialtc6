package thaumcraft.client;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.client.renderers.entity.projectile.RenderGrapple;
import thaumcraft.common.items.tools.*;
/** Equipment registrations; hand-ordered HUD is registered once in CasterClientRegistration. */
@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class EquipmentClientRegistration {
    private EquipmentClientRegistration() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(EquipmentRegistries.GRAPPLE,RenderGrapple::new);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->net.minecraft.client.renderer.item.ItemProperties.register(ItemsTC.grappleGun,ResourceLocation.withDefaultNamespace("type"),(stack,level,entity,seed)->ItemGrappleGun.loaded(stack)?1:0));}
}
