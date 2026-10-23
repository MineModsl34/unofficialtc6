package thaumcraft.common.tiles.devices;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.devices.*;
import thaumcraft.common.container.ContainerWorldDevice;
import static thaumcraft.common.blocks.devices.BlockWorldDevice.Kind.*;

@EventBusSubscriber(modid = Thaumcraft.MODID,bus = EventBusSubscriber.Bus.MOD)
public final class DeviceRegistries {
    public static BlockEntityType<DeviceTile> DEVICE;
    public static MenuType<ContainerWorldDevice> SPA_MENU, SPRAYER_MENU;
    private static final List<Block> ENTITY_BLOCKS = new ArrayList<>();
    private DeviceRegistries() {}
    private static Block device(BlockWorldDevice.Kind kind,SoundType sound,float hardness,float resistance) { var properties = BlockBehaviour.Properties.of().strength(hardness,resistance).sound(sound).lightLevel(state -> (kind == LAMP_ARCANE || kind == LAMP_GROWTH || kind == LAMP_FERTILITY) && state.getValue(BlockWorldDevice.ENABLED) ? 15 : 0); if (kind != SPA && kind != SPRAYER) properties.noOcclusion(); Block block = new BlockWorldDevice(kind,properties); ENTITY_BLOCKS.add(block); return block; }
    /** Called by ConfigBlocks before native BLOCK_ENTITY_TYPE/MENU registration. Actual aliases, not placeholders. */
    public static Map<String,Block> createBlocks() {
        Map<String,Block> blocks = new LinkedHashMap<>(); ENTITY_BLOCKS.clear();
        blocks.put("vis_generator",BlocksTC.visGenerator = device(GENERATOR,SoundType.WOOD,2,12));
        blocks.put("vis_battery",BlocksTC.visBattery = new BlockVisBattery(BlockBehaviour.Properties.of().strength(.5F).sound(SoundType.STONE).randomTicks().lightLevel(state -> state.getValue(BlockVisBattery.CHARGE))));
        blocks.put("recharge_pedestal",BlocksTC.rechargePedestal = device(RECHARGE,SoundType.STONE,2,12));
        blocks.put("levitator",BlocksTC.levitator = device(LEVITATOR,SoundType.WOOD,2,12));
        blocks.put("lamp_arcane",BlocksTC.lampArcane = device(LAMP_ARCANE,SoundType.METAL,1,12));
        blocks.put("lamp_growth",BlocksTC.lampGrowth = device(LAMP_GROWTH,SoundType.METAL,1,12));
        blocks.put("lamp_fertility",BlocksTC.lampFertility = device(LAMP_FERTILITY,SoundType.METAL,1,12));
        blocks.put("everfull_urn",BlocksTC.everfullUrn = device(URN,SoundType.STONE,2,12));
        blocks.put("hungry_chest",BlocksTC.hungryChest = device(CHEST,SoundType.WOOD,2.5F,2.5F));
        blocks.put("jar_brain",BlocksTC.jarBrain = device(BRAIN,thaumcraft.common.lib.SoundsTC.JAR,.3F,12));
        blocks.put("arcane_ear",BlocksTC.arcaneEar = device(EAR,SoundType.WOOD,1,12));
        blocks.put("arcane_ear_toggle",BlocksTC.arcaneEarToggle = device(EAR_TOGGLE,SoundType.WOOD,1,12));
        blocks.put("redstone_relay",BlocksTC.redstoneRelay = device(RELAY,SoundType.WOOD,0,0));
        blocks.put("dioptra",BlocksTC.dioptra = device(DIOPTRA,SoundType.STONE,2,12));
        blocks.put("spa",BlocksTC.spa = device(SPA,SoundType.STONE,2,12));
        blocks.put("potion_sprayer",BlocksTC.potionSprayer = device(SPRAYER,SoundType.METAL,2,12));
        blocks.put("pattern_crafter",BlocksTC.patternCrafter = device(CRAFTER,SoundType.METAL,2,12));
        blocks.put("condenser",BlocksTC.condenser = device(CONDENSER,SoundType.METAL,2,12));
        blocks.put("condenser_lattice",BlocksTC.condenserlattice = new BlockCondenserLattice(BlockBehaviour.Properties.of().strength(.5F,3).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 4)));
        blocks.put("condenser_lattice_dirty",BlocksTC.condenserlatticeDirty = new BlockCondenserLattice(BlockBehaviour.Properties.of().strength(.5F,3).sound(SoundType.METAL).noOcclusion()));
        return blocks;
    }
    public static Item createItem(Block block) { return block == BlocksTC.jarBrain ? new BlockJarBrainItem(block,new Item.Properties()) : null; }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE,helper -> { DEVICE = BlockEntityType.Builder.of(DeviceTile::create,ENTITY_BLOCKS.toArray(Block[]::new)).build(null); helper.register(ResourceLocation.parse("thaumcraft:world_device"),DEVICE); });
        event.register(Registries.MENU,helper -> { SPA_MENU = IMenuTypeExtension.create((id,inventory,buffer) -> ContainerWorldDevice.fromNetwork(id,inventory,buffer,false)); SPRAYER_MENU = IMenuTypeExtension.create((id,inventory,buffer) -> ContainerWorldDevice.fromNetwork(id,inventory,buffer,true)); helper.register(ResourceLocation.parse("thaumcraft:spa"),SPA_MENU); helper.register(ResourceLocation.parse("thaumcraft:potion_sprayer"),SPRAYER_MENU); });
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,DEVICE,(tile,side) -> tile.kind() == GENERATOR && side == tile.facing() ? (TileEnergyDevice)tile : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,DEVICE,(tile,side) -> tile instanceof TileFluidDevice fluid && (tile.kind() == SPA || tile.kind() == URN && side == net.minecraft.core.Direction.UP) ? fluid.tank : null);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,DEVICE,(tile,side) -> { if (tile.getContainerSize() == 0 || tile.kind() != CHEST && side == null || tile.kind() == SPA && side == net.minecraft.core.Direction.UP) return null; return new InvWrapper(tile); });
        event.registerBlockEntity(thaumcraft.api.aspects.EssentiaCapabilities.TRANSPORT,DEVICE,(tile,side) -> tile instanceof thaumcraft.api.aspects.IEssentiaTransport transport && (tile.kind() == CONDENSER || tile.kind() == SPRAYER || tile.kind() == LAMP_GROWTH || tile.kind() == LAMP_FERTILITY) && (side == null || transport.isConnectable(side)) ? transport : null);
    }
}
