package thaumcraft.common.entities.construct;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.container.ContainerConstruct;
import thaumcraft.common.container.ConstructEquipmentContainer;
import thaumcraft.common.entities.projectile.EntityGolemDart;

/** Only the common registrar owns IDs; callbacks here attach real runtime capabilities. */
@EventBusSubscriber(modid="thaumcraft",bus=EventBusSubscriber.Bus.MOD)
public final class Mob8ConstructRegistries {
    public static EntityType<EntityTurretCrossbow> BASIC;
    public static EntityType<EntityTurretCrossbowAdvanced> ADVANCED;
    public static EntityType<EntityArcaneBore> BORE;
    public static EntityType<EntityGolemDart> DART;
    public static Item BASIC_PLACER,ADVANCED_PLACER,BORE_PLACER;
    public static MenuType<ContainerConstruct> BASIC_MENU,ADVANCED_MENU,BORE_MENU;
    private Mob8ConstructRegistries(){}
    public static Map<String,EntityType<?>> createEntityTypes(){
        BASIC=EntityType.Builder.of(EntityTurretCrossbow::new,MobCategory.MISC).sized(.95F,1.25F).eyeHeight(.825F).clientTrackingRange(4).updateInterval(3).build("thaumcraft:turret_basic");
        ADVANCED=EntityType.Builder.of(EntityTurretCrossbowAdvanced::new,MobCategory.MISC).sized(.95F,1.5F).eyeHeight(1).clientTrackingRange(4).updateInterval(3).build("thaumcraft:turret_advanced");
        BORE=EntityType.Builder.of(EntityArcaneBore::new,MobCategory.MISC).sized(.9F,.9F).eyeHeight(.8125F).clientTrackingRange(4).updateInterval(3).build("thaumcraft:arcane_bore");
        DART=EntityType.Builder.<EntityGolemDart>of(EntityGolemDart::new,MobCategory.MISC).sized(.2F,.2F).clientTrackingRange(4).updateInterval(20).setShouldReceiveVelocityUpdates(false).build("thaumcraft:golem_dart");
        var result=new LinkedHashMap<String,EntityType<?>>();result.put("turret_basic",BASIC);result.put("turret_advanced",ADVANCED);result.put("arcane_bore",BORE);result.put("golem_dart",DART);return result;
    }
    public static Map<String,Item> createItems(){var result=new LinkedHashMap<String,Item>();result.put("turret_basic",ItemsTC.turretPlacer=BASIC_PLACER=new ItemTurretPlacer(0));result.put("turret_advanced",ADVANCED_PLACER=new ItemTurretPlacer(1));result.put("turret_bore",BORE_PLACER=new ItemTurretPlacer(2));return result;}
    public static Map<String,MenuType<?>> createMenus(){BASIC_MENU=menu(EntityTurretCrossbow.class);ADVANCED_MENU=menu(EntityTurretCrossbowAdvanced.class);BORE_MENU=menu(EntityArcaneBore.class);return Map.of("turret_basic",BASIC_MENU,"turret_advanced",ADVANCED_MENU,"arcane_bore",BORE_MENU);}
    private static MenuType<ContainerConstruct> menu(Class<? extends EntityOwnedConstruct> expected){return IMenuTypeExtension.create((id,inventory,buffer)->{var entity=inventory.player.level().getEntity(buffer.readVarInt());if(entity==null || entity.getClass()!=expected)throw new IllegalArgumentException("Untracked or mismatched construct menu");return new ContainerConstruct(id,inventory,(EntityOwnedConstruct)entity);});}
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event){event.put(BASIC,EntityTurretCrossbow.attributes().build());event.put(ADVANCED,EntityTurretCrossbowAdvanced.attributes().build());event.put(BORE,EntityArcaneBore.attributes().build());}
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event){equipment(event,BASIC);equipment(event,ADVANCED);equipment(event,BORE);}
    private static <E extends EntityOwnedConstruct> void equipment(RegisterCapabilitiesEvent event,EntityType<E> type){event.registerEntity(Capabilities.ItemHandler.ENTITY,type,(entity,context)->new InvWrapper(new ConstructEquipmentContainer(entity)));event.registerEntity(Capabilities.ItemHandler.ENTITY_AUTOMATION,type,(entity,side)->new InvWrapper(new ConstructEquipmentContainer(entity)));}
}
