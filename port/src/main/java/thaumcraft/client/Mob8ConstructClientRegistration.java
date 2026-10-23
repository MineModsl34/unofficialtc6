package thaumcraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import thaumcraft.client.gui.GuiConstruct;
import thaumcraft.client.renderers.entity.construct.*;
import thaumcraft.client.renderers.entity.projectile.RenderDart;
import thaumcraft.client.renderers.models.entity.ConstructLayers;
import thaumcraft.common.entities.construct.Mob8ConstructRegistries;

@EventBusSubscriber(modid="thaumcraft",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class Mob8ConstructClientRegistration {
    private Mob8ConstructClientRegistration(){}
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){event.registerLayerDefinition(RenderTurretCrossbow.LAYER,()->ConstructLayers.source("crossbow"));event.registerLayerDefinition(RenderArcaneBore.LAYER,()->ConstructLayers.source("arcane_bore"));event.registerLayerDefinition(RenderTurretCrossbowAdvanced.LAYER,ConstructLayers::advanced);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(Mob8ConstructRegistries.BASIC,RenderTurretCrossbow::new);event.registerEntityRenderer(Mob8ConstructRegistries.ADVANCED,RenderTurretCrossbowAdvanced::new);event.registerEntityRenderer(Mob8ConstructRegistries.BORE,RenderArcaneBore::new);event.registerEntityRenderer(Mob8ConstructRegistries.DART,RenderDart::new);}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(Mob8ConstructRegistries.BASIC_MENU,GuiConstruct::new);event.register(Mob8ConstructRegistries.ADVANCED_MENU,GuiConstruct::new);event.register(Mob8ConstructRegistries.BORE_MENU,GuiConstruct::new);}
}
