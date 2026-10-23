package thaumcraft.common.tiles.crafting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.crafting.BlockVoidSiphon;
import thaumcraft.common.container.ContainerVoidSiphon;
import thaumcraft.common.entities.EntityFluxRift;

/** Root registers returned block/entity factories; runtime menu and capability are owned here. */
@EventBusSubscriber(modid="thaumcraft",bus=EventBusSubscriber.Bus.MOD)
public final class Phase10RiftRegistries {
    public static EntityType<EntityFluxRift> FLUX_RIFT;
    public static BlockEntityType<TileVoidSiphon> VOID_SIPHON;
    public static MenuType<ContainerVoidSiphon> VOID_SIPHON_MENU;
    private static Map<String,Block> blocks;
    private static Map<String,EntityType<?>> entities;
    private Phase10RiftRegistries() {}
    public static synchronized Map<String,Block> createBlocks() {
        if(blocks==null){var map=new LinkedHashMap<String,Block>();map.put("void_siphon",BlocksTC.voidSiphon=new BlockVoidSiphon(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2,12).sound(SoundType.METAL).noOcclusion()));blocks=Collections.unmodifiableMap(map);}return blocks;
    }
    public static synchronized Map<String,EntityType<?>> createEntityTypes() {
        if(entities==null){var map=new LinkedHashMap<String,EntityType<?>>();map.put("flux_rift",FLUX_RIFT=EntityType.Builder.<EntityFluxRift>of(EntityFluxRift::new,MobCategory.MISC).sized(2,2).fireImmune().clientTrackingRange(4).updateInterval(20).setShouldReceiveVelocityUpdates(false).build("thaumcraft:flux_rift"));entities=Collections.unmodifiableMap(map);}return entities;
    }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE,helper->{VOID_SIPHON=BlockEntityType.Builder.of(TileVoidSiphon::new,BlocksTC.voidSiphon).build(null);helper.register(ResourceLocation.parse("thaumcraft:void_siphon"),VOID_SIPHON);});
        event.register(Registries.MENU,helper->{VOID_SIPHON_MENU=new MenuType<>(ContainerVoidSiphon::new,FeatureFlags.DEFAULT_FLAGS);helper.register(ResourceLocation.parse("thaumcraft:void_siphon"),VOID_SIPHON_MENU);});
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        // Source disallows insertion on ALL sides, including an unsided capability lookup.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,VOID_SIPHON,(tile,side)->new SidedInvWrapper(tile,side==null?net.minecraft.core.Direction.DOWN:side));
    }
}
