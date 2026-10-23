package thaumcraft.client;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.api.casters.ICaster;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.casters.*;
import thaumcraft.client.gui.GuiFocusPouch;
import thaumcraft.client.renderers.entity.projectile.RenderFocusMine;
import thaumcraft.client.renderers.entity.mob.RenderSpellBat;
import thaumcraft.client.renderers.tile.TileHoleRenderer;
@EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class CasterClientRegistration{
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(CasterClientEvents.FOCUS);event.register(CasterClientEvents.MISC);}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(CasterRegistries.POUCH_MENU,GuiFocusPouch::new);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(CasterRegistries.PROJECTILE,thaumcraft.client.renderers.entity.projectile.RenderNoProjectile::new);event.registerEntityRenderer(CasterRegistries.CLOUD,net.minecraft.client.renderer.entity.NoopRenderer::new);event.registerEntityRenderer(CasterRegistries.MINE,RenderFocusMine::new);event.registerEntityRenderer(CasterRegistries.SPELLBAT,RenderSpellBat::new);event.registerBlockEntityRenderer(CasterRegistries.HOLE,TileHoleRenderer::new);}
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item event){event.register((stack,tint)->stack.getItem() instanceof ItemFocus focus?0xff000000|focus.getFocusColor(stack.copy()):-1,ItemsTC.focus1,ItemsTC.focus2,ItemsTC.focus3);event.register((stack,tint)->{if(tint<=0||!(stack.getItem() instanceof ItemCaster caster))return -1;var focus=caster.getFocusStack(stack);return focus!=null&&focus.getItem() instanceof ItemFocus item?0xff000000|item.getFocusColor(focus):-1;},ItemsTC.casterBasic);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->net.minecraft.client.renderer.item.ItemProperties.register(ItemsTC.casterBasic,ResourceLocation.withDefaultNamespace("focus"),(stack,level,entity,seed)->((ItemCaster)stack.getItem()).getFocus(stack)==null?0:1));}
    @SubscribeEvent public static void hud(RegisterGuiLayersEvent event){event.registerAboveAll(ResourceLocation.parse("thaumcraft:source_hud"),thaumcraft.client.lib.events.RenderEventHandler.hudHandler::renderHuds);}
}
