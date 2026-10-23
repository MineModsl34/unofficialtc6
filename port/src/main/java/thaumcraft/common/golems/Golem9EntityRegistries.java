package thaumcraft.common.golems;

import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = "thaumcraft", bus = EventBusSubscriber.Bus.MOD)
public final class Golem9EntityRegistries {
    public static EntityType<EntityThaumcraftGolem> GOLEM;
    private Golem9EntityRegistries() {}
    public static Map<String, EntityType<?>> createEntityTypes() {
        GOLEM = EntityType.Builder.<EntityThaumcraftGolem>of(EntityThaumcraftGolem::new, MobCategory.MISC)
                .sized(0.4F, 0.9F).eyeHeight(0.7F).clientTrackingRange(4).updateInterval(3)
                .setShouldReceiveVelocityUpdates(true).build("thaumcraft:golem");
        return Map.of("golem", GOLEM);
    }
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event) {
        event.put(GOLEM, EntityThaumcraftGolem.createAttributes().build());
    }
}
