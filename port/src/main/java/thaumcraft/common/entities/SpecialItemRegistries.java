package thaumcraft.common.entities;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;

/** Only source SpecialItem; modern lowercase registry identity, original 64-block/20-tick tracking. */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class SpecialItemRegistries {
    public static EntityType<EntitySpecialItem> TYPE;
    private SpecialItemRegistries() {}
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "special_item");
            TYPE = EntityType.Builder.<EntitySpecialItem>of(EntitySpecialItem::new, MobCategory.MISC)
                .sized(.25F, .25F).clientTrackingRange(4).updateInterval(20).setShouldReceiveVelocityUpdates(true).build(id.toString());
            helper.register(id, TYPE);
        });
    }
}
