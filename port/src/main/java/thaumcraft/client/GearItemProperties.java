package thaumcraft.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.curios.ItemPrimordialPearl;

@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class GearItemProperties {
    private GearItemProperties() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ItemsTC.primordialPearl,
            ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "type"),
            (stack, level, entity, seed) -> ItemPrimordialPearl.modelType(stack)));
    }
}
