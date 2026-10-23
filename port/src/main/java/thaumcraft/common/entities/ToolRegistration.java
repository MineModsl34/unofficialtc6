package thaumcraft.common.entities;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;

@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ToolRegistration {
    public static EntityType<EntityFollowingItem> FOLLOWING_ITEM;
    private ToolRegistration() {}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            // Original ConfigEntities following item range64/update20/no velocity sync.
            FOLLOWING_ITEM = EntityType.Builder.<EntityFollowingItem>of(EntityFollowingItem::new, MobCategory.MISC)
                .sized(.25F, .25F).clientTrackingRange(4).updateInterval(20).setShouldReceiveVelocityUpdates(false).build("thaumcraft:following_item");
            helper.register(ResourceLocation.parse("thaumcraft:following_item"), FOLLOWING_ITEM);
        });
    }
}
