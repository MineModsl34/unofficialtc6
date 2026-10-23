package thaumcraft.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.blocks.essentia.BlockJarItem;

@EventBusSubscriber(modid = Thaumcraft.MODID,value = Dist.CLIENT,bus = EventBusSubscriber.Bus.MOD)
public final class AlchemyItemProperties {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(() -> {
        for (var block : new net.minecraft.world.level.block.Block[]{BlocksTC.jarNormal,BlocksTC.jarVoid}) ItemProperties.register(block.asItem(),ResourceLocation.withDefaultNamespace("fill"),(stack,level,entity,seed) -> {
            var aspects = ((BlockJarItem)stack.getItem()).getAspects(stack); if (aspects == null || aspects.visSize() == 0) return 0;
            double durability = 1 - aspects.visSize() / 250.0;
            return durability >= .75 ? 1 : durability >= .5 ? 2 : durability >= .25 ? 3 : 4;
        });
        ItemProperties.register(ItemsTC.label,ResourceLocation.parse("thaumcraft:filled"),(stack,level,entity,seed) -> {
            var tags = stack.get(TCDataComponents.ASPECTS); return tags != null && tags.size() > 0 ? 1 : 0;
        });
    }); }
}
