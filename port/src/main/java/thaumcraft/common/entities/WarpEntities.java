package thaumcraft.common.entities;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.monster.EntityMindSpider;
import thaumcraft.common.entities.monster.EntityEldritchGuardian;
import thaumcraft.common.entities.projectile.EntityEldritchOrb;
import thaumcraft.common.entities.monster.EntityThaumicSlime;
import net.minecraft.world.entity.monster.Monster;

/** Source entities required by warp, registered early because stage4 genuinely uses their gameplay. */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class WarpEntities {
    public static EntityType<EntityMindSpider> MIND_SPIDER;
    public static EntityType<EntityEldritchGuardian> ELDRITCH_GUARDIAN;
    public static EntityType<EntityEldritchOrb> ELDRITCH_ORB;
    public static EntityType<EntityThaumicSlime> THAUMIC_SLIME;
    private WarpEntities() {}
    public static void register(RegisterEvent.RegisterHelper<EntityType<?>> registry) {
        MIND_SPIDER = EntityType.Builder.<EntityMindSpider>of(EntityMindSpider::new, MobCategory.MONSTER)
            .sized(0.7F, 0.5F).eyeHeight(0.45F).clientTrackingRange(4).updateInterval(3)
            .build("thaumcraft:mind_spider");
        registry.register(ResourceLocation.parse("thaumcraft:mind_spider"), MIND_SPIDER);
        ELDRITCH_GUARDIAN = EntityType.Builder.<EntityEldritchGuardian>of(EntityEldritchGuardian::new, MobCategory.MONSTER)
            .sized(0.8F, 2.25F).eyeHeight(2.1F).clientTrackingRange(4).updateInterval(3)
            .build("thaumcraft:eldritch_guardian");
        registry.register(ResourceLocation.parse("thaumcraft:eldritch_guardian"), ELDRITCH_GUARDIAN);
        ELDRITCH_ORB = EntityType.Builder.<EntityEldritchOrb>of(EntityEldritchOrb::new, MobCategory.MISC)
            .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(20)
            .build("thaumcraft:eldritch_orb");
        registry.register(ResourceLocation.parse("thaumcraft:eldritch_orb"), ELDRITCH_ORB);
        THAUMIC_SLIME = EntityType.Builder.<EntityThaumicSlime>of(EntityThaumicSlime::new, MobCategory.MONSTER)
            .sized(0.51000005F,0.51000005F).clientTrackingRange(4).updateInterval(3).build("thaumcraft:thaumic_slime");
        registry.register(ResourceLocation.parse("thaumcraft:thaumic_slime"),THAUMIC_SLIME);
    }
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event) {
        event.put(MIND_SPIDER, EntityMindSpider.createAttributes().build());
        event.put(ELDRITCH_GUARDIAN, EntityEldritchGuardian.createAttributes().build());
        event.put(THAUMIC_SLIME,Monster.createMonsterAttributes().build());
    }
    @SubscribeEvent public static void spawnPlacements(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) {
        // Source guardian deliberately ignores light, but retains solid-ground and peaceful checks.
        event.register(ELDRITCH_GUARDIAN, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                && net.minecraft.world.entity.Mob.checkMobSpawnRules(type, level, reason, pos, random),
            net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
