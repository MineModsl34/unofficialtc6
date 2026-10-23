package thaumcraft.common.entities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.container.ContainerPech;
import thaumcraft.common.entities.monster.*;

@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class Mob8NaturalRegistries {
    public static EntityType<EntityBrainyZombie> BRAINY_ZOMBIE;public static EntityType<EntityGiantBrainyZombie> GIANT_BRAINY_ZOMBIE;public static EntityType<EntityInhabitedZombie> INHABITED_ZOMBIE;public static EntityType<EntityEldritchCrab> ELDRITCH_CRAB;public static EntityType<EntityWisp> WISP;public static EntityType<EntityFireBat> FIREBAT;public static EntityType<EntityPech> PECH;
    public static final MenuType<ContainerPech> PECH_MENU=IMenuTypeExtension.create(ContainerPech::new);
    private static Map<String,EntityType<?>> types;private static Map<String,Item> eggs;
    private Mob8NaturalRegistries(){}
    public static synchronized Map<String,EntityType<?>> createEntityTypes(){if(types!=null)return types;Map<String,EntityType<?>> result=new LinkedHashMap<>();
        BRAINY_ZOMBIE=EntityType.Builder.of(EntityBrainyZombie::new,MobCategory.MONSTER).sized(.6F,1.95F).eyeHeight(1.74F).clientTrackingRange(4).updateInterval(3).build("thaumcraft:brainy_zombie");result.put("brainy_zombie",BRAINY_ZOMBIE);
        GIANT_BRAINY_ZOMBIE=EntityType.Builder.of(EntityGiantBrainyZombie::new,MobCategory.MONSTER).sized(.6F,1.95F).eyeHeight(1.74F).clientTrackingRange(4).updateInterval(3).build("thaumcraft:giant_brainy_zombie");result.put("giant_brainy_zombie",GIANT_BRAINY_ZOMBIE);
        INHABITED_ZOMBIE=EntityType.Builder.of(EntityInhabitedZombie::new,MobCategory.MONSTER).sized(.6F,1.95F).eyeHeight(1.74F).clientTrackingRange(4).updateInterval(3).build("thaumcraft:inhabited_zombie");result.put("inhabited_zombie",INHABITED_ZOMBIE);
        ELDRITCH_CRAB=EntityType.Builder.of(EntityEldritchCrab::new,MobCategory.MONSTER).sized(.8F,.6F).eyeHeight(.39F).clientTrackingRange(4).updateInterval(3).build("thaumcraft:eldritch_crab");result.put("eldritch_crab",ELDRITCH_CRAB);
        WISP=EntityType.Builder.of(EntityWisp::new,MobCategory.MONSTER).sized(.9F,.9F).clientTrackingRange(4).updateInterval(3).setShouldReceiveVelocityUpdates(false).build("thaumcraft:wisp");result.put("wisp",WISP);
        FIREBAT=EntityType.Builder.of(EntityFireBat::new,MobCategory.MONSTER).sized(.5F,.9F).fireImmune().clientTrackingRange(4).updateInterval(3).setShouldReceiveVelocityUpdates(false).build("thaumcraft:firebat");result.put("firebat",FIREBAT);
        PECH=EntityType.Builder.of(EntityPech::new,MobCategory.MONSTER).sized(.6F,1.8F).eyeHeight(1.8F*.66F).clientTrackingRange(4).updateInterval(3).build("thaumcraft:pech");result.put("pech",PECH);
        EntityPech.valuedItems.putIfAbsent(Item.getId(Items.ENDER_PEARL),15);types=Collections.unmodifiableMap(result);return types;}
    public static synchronized Map<String,Item> createItems(){if(eggs!=null)return eggs;createEntityTypes();Map<String,Item> result=new LinkedHashMap<>();result.put("brainy_zombie_spawn_egg",new DeferredSpawnEggItem(()->BRAINY_ZOMBIE,-16129,-16744448,new Item.Properties()));result.put("giant_brainy_zombie_spawn_egg",new DeferredSpawnEggItem(()->GIANT_BRAINY_ZOMBIE,-16129,-16760832,new Item.Properties()));result.put("inhabited_zombie_spawn_egg",new DeferredSpawnEggItem(()->INHABITED_ZOMBIE,8421504,5570560,new Item.Properties()));result.put("eldritch_crab_spawn_egg",new DeferredSpawnEggItem(()->ELDRITCH_CRAB,8421504,5570560,new Item.Properties()));result.put("wisp_spawn_egg",new DeferredSpawnEggItem(()->WISP,-16129,-1,new Item.Properties()));result.put("firebat_spawn_egg",new DeferredSpawnEggItem(()->FIREBAT,-16129,-806354944,new Item.Properties()));result.put("pech_spawn_egg",new DeferredSpawnEggItem(()->PECH,-16129,-12582848,new Item.Properties()));eggs=Collections.unmodifiableMap(result);return eggs;}
    @SubscribeEvent public static void menus(RegisterEvent event){event.register(Registries.MENU,ResourceLocation.parse("thaumcraft:pech"),()->PECH_MENU);}
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event){event.put(BRAINY_ZOMBIE,EntityBrainyZombie.attributes().build());event.put(GIANT_BRAINY_ZOMBIE,EntityGiantBrainyZombie.attributes().build());event.put(INHABITED_ZOMBIE,EntityInhabitedZombie.attributes().build());event.put(ELDRITCH_CRAB,EntityEldritchCrab.attributes().build());event.put(WISP,EntityWisp.attributes().build());event.put(FIREBAT,EntityFireBat.attributes().build());event.put(PECH,EntityPech.attributes().build());}
    @SubscribeEvent public static void spawns(RegisterSpawnPlacementsEvent event){var height=Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;var op=RegisterSpawnPlacementsEvent.Operation.REPLACE;event.register(BRAINY_ZOMBIE,SpawnPlacementTypes.ON_GROUND,height,Mob8NaturalRegistries::zombieSpawn,op);event.register(GIANT_BRAINY_ZOMBIE,SpawnPlacementTypes.ON_GROUND,height,Mob8NaturalRegistries::zombieSpawn,op);event.register(INHABITED_ZOMBIE,SpawnPlacementTypes.ON_GROUND,height,Mob8NaturalRegistries::zombieSpawn,op);event.register(ELDRITCH_CRAB,SpawnPlacementTypes.ON_GROUND,height,Mob8NaturalRegistries::zombieSpawn,op);event.register(WISP,SpawnPlacementTypes.NO_RESTRICTIONS,height,(type,world,reason,pos,random)->world.getDifficulty()!=Difficulty.PEACEFUL&&legacyDark(world,pos,random),op);event.register(FIREBAT,SpawnPlacementTypes.ON_GROUND,height,(type,world,reason,pos,random)->world.getDifficulty()!=Difficulty.PEACEFUL&&world.getMaxLocalRawBrightness(pos,0)<=random.nextInt(7)&&Mob.checkMobSpawnRules(type,world,reason,pos,random),op);event.register(PECH,SpawnPlacementTypes.ON_GROUND,height,(type,world,reason,pos,random)->pechBiome(world,pos)&&zombieSpawn(type,world,reason,pos,random),op);}
    private static boolean zombieSpawn(EntityType<? extends Monster> type,ServerLevelAccessor world,MobSpawnType reason,BlockPos pos,RandomSource random){return world.getDifficulty()!=Difficulty.PEACEFUL&&legacyDark(world,pos,random)&&Mob.checkMobSpawnRules(type,world,reason,pos,random);}
    private static boolean legacyDark(ServerLevelAccessor world,BlockPos pos,RandomSource random){if(world.getBrightness(LightLayer.SKY,pos)>random.nextInt(32))return false;int light=world.getLevel().isThundering()?world.getMaxLocalRawBrightness(pos,10):world.getMaxLocalRawBrightness(pos);return light<=random.nextInt(8);}
    private static boolean pechBiome(ServerLevelAccessor world,BlockPos pos){var biome=world.getBiome(pos);return biome.is(Tags.Biomes.IS_MAGICAL)&&(world.getLevel().dimension()==Level.OVERWORLD||biome.is(ResourceLocation.parse("thaumcraft:magical_forest"))||biome.is(ResourceLocation.parse("thaumcraft:eerie")));}
}
