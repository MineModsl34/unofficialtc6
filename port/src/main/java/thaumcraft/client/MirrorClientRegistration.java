package thaumcraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.client.gui.GuiHandMirror;
import thaumcraft.client.renderers.tile.TileMirrorRenderer;
import thaumcraft.common.tiles.devices.MirrorRegistries;

@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class MirrorClientRegistration{
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(MirrorRegistries.MIRROR,TileMirrorRenderer::new);event.registerBlockEntityRenderer(MirrorRegistries.ESSENTIA,TileMirrorRenderer::new);}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(MirrorRegistries.HAND_MENU,GuiHandMirror::new);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{
        for(var block:new net.minecraft.world.level.block.Block[]{BlocksTC.mirror,BlocksTC.mirrorEssentia})net.minecraft.client.renderer.item.ItemProperties.register(block.asItem(),net.minecraft.resources.ResourceLocation.parse("thaumcraft:linked"),(stack,world,entity,seed)->stack.has(TCDataComponents.MIRROR_LINK)?1:0);
    });}
}
