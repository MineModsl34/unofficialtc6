package thaumcraft.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.blocks.misc.PurifyingFluidRegistries;

@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class PurifyingFluidClientRegistration{
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(()->{
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(PurifyingFluidRegistries.SOURCE,net.minecraft.client.renderer.RenderType.translucent());
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(PurifyingFluidRegistries.FLOWING,net.minecraft.client.renderer.RenderType.translucent());
    });}
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){event.registerFluidType(new IClientFluidTypeExtensions(){
        @Override public ResourceLocation getStillTexture(){return ResourceLocation.parse("minecraft:block/water_still");}
        @Override public ResourceLocation getFlowingTexture(){return ResourceLocation.parse("minecraft:block/water_flow");}
        @Override public int getTintColor(){return 2013252778;}
    },PurifyingFluidRegistries.TYPE);}
}
