package thaumcraft.common.entities;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.monster.cult.EntityCultistCleric;
import thaumcraft.common.entities.monster.cult.EntityCultistKnight;
import thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser;
import thaumcraft.common.entities.projectile.EntityGolemOrb;

/** Actual source portal/minions/projectile used by the stage4 warp event and theory aid. */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class CultEntities {
    public static EntityType<EntityCultistPortalLesser> PORTAL;
    public static EntityType<EntityCultistKnight> KNIGHT;
    public static EntityType<EntityCultistCleric> CLERIC;
    public static EntityType<EntityGolemOrb> GOLEM_ORB;
    private CultEntities() {}
    public static void register(RegisterEvent.RegisterHelper<EntityType<?>> registry) {
        PORTAL = EntityType.Builder.<EntityCultistPortalLesser>of(EntityCultistPortalLesser::new, MobCategory.MONSTER)
            .sized(1.5F, 3).fireImmune().clientTrackingRange(4).updateInterval(20).setShouldReceiveVelocityUpdates(false).build("thaumcraft:cultist_portal_lesser");
        KNIGHT = EntityType.Builder.<EntityCultistKnight>of(EntityCultistKnight::new, MobCategory.MONSTER)
            .sized(.6F, 1.8F).clientTrackingRange(4).updateInterval(3).build("thaumcraft:cultist_knight");
        CLERIC = EntityType.Builder.<EntityCultistCleric>of(EntityCultistCleric::new, MobCategory.MONSTER)
            .sized(.6F, 1.8F).clientTrackingRange(4).updateInterval(3).build("thaumcraft:cultist_cleric");
        GOLEM_ORB = EntityType.Builder.<EntityGolemOrb>of(EntityGolemOrb::new, MobCategory.MISC)
            .sized(.25F, .25F).clientTrackingRange(4).updateInterval(3).build("thaumcraft:golem_orb");
        registry.register(ResourceLocation.parse("thaumcraft:cultist_portal_lesser"), PORTAL);
        registry.register(ResourceLocation.parse("thaumcraft:cultist_knight"), KNIGHT);
        registry.register(ResourceLocation.parse("thaumcraft:cultist_cleric"), CLERIC);
        registry.register(ResourceLocation.parse("thaumcraft:golem_orb"), GOLEM_ORB);
    }
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event) {
        event.put(PORTAL, EntityCultistPortalLesser.createAttributes().build());
        event.put(KNIGHT, EntityCultistKnight.createAttributes().build());
        event.put(CLERIC, EntityCultistCleric.createAttributes().build());
    }
}
