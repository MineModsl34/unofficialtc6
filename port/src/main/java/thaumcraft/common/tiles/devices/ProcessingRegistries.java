package thaumcraft.common.tiles.devices;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.crafting.BlockThaumatorium;
import thaumcraft.common.blocks.devices.BlockBellows;
import thaumcraft.common.blocks.devices.BlockBrainBox;
import thaumcraft.common.blocks.devices.BlockInfernalFurnace;
import thaumcraft.common.blocks.essentia.BlockSmelterAux;
import thaumcraft.common.blocks.essentia.BlockSmelterVent;
import thaumcraft.common.blocks.misc.BlockInfernalPlaceholder;
import thaumcraft.common.container.ContainerThaumatorium;
import thaumcraft.common.tiles.crafting.TileThaumatorium;
import thaumcraft.common.tiles.crafting.TileThaumatoriumTop;

/** ConfigBlocks owns actual BLOCK/ITEM registrations; this class only creates the source devices. */
@EventBusSubscriber(modid = Thaumcraft.MODID,bus = EventBusSubscriber.Bus.MOD)
public final class ProcessingRegistries {
    public static BlockEntityType<TileBellows> BELLOWS;
    public static BlockEntityType<TileInfernalFurnace> INFERNAL_FURNACE;
    public static BlockEntityType<TileThaumatorium> THAUMATORIUM;
    public static BlockEntityType<TileThaumatoriumTop> THAUMATORIUM_TOP;
    public static MenuType<ContainerThaumatorium> THAUMATORIUM_MENU;
    private static Map<String,Block> blocks;
    private ProcessingRegistries() {}
    private static BlockBehaviour.Properties metal() { return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1,10).sound(SoundType.METAL).noOcclusion(); }
    public static Map<String,Block> createBlocks() {
        if (blocks != null) return blocks;
        LinkedHashMap<String,Block> created = new LinkedHashMap<>();
        created.put("bellows",BlocksTC.bellows = new BlockBellows(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1).noOcclusion()));
        created.put("smelter_aux",BlocksTC.smelterAux = new BlockSmelterAux(metal()));
        created.put("smelter_vent",BlocksTC.smelterVent = new BlockSmelterVent(metal()));
        created.put("infernal_furnace",BlocksTC.infernalFurnace = new BlockInfernalFurnace(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F).sound(SoundType.STONE).lightLevel(state -> 13).noOcclusion()));
        created.put("placeholder_brick",BlocksTC.placeholderNetherbrick = new BlockInfernalPlaceholder(BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).strength(2.5F).sound(SoundType.STONE).noOcclusion().pushReaction(PushReaction.BLOCK)));
        created.put("placeholder_obsidian",BlocksTC.placeholderObsidian = new BlockInfernalPlaceholder(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.5F).sound(SoundType.STONE).noOcclusion().pushReaction(PushReaction.BLOCK)));
        created.put("thaumatorium",BlocksTC.thaumatorium = new BlockThaumatorium(false,metal()));
        created.put("thaumatorium_top",BlocksTC.thaumatoriumTop = new BlockThaumatorium(true,metal()));
        created.put("brain_box",BlocksTC.brainBox = new BlockBrainBox(metal()));
        blocks = java.util.Collections.unmodifiableMap(created); return blocks;
    }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE,helper -> {
            BELLOWS = BlockEntityType.Builder.of(TileBellows::new,BlocksTC.bellows).build(null);
            INFERNAL_FURNACE = BlockEntityType.Builder.of(TileInfernalFurnace::new,BlocksTC.infernalFurnace).build(null);
            THAUMATORIUM = BlockEntityType.Builder.of(TileThaumatorium::new,BlocksTC.thaumatorium).build(null);
            THAUMATORIUM_TOP = BlockEntityType.Builder.of(TileThaumatoriumTop::new,BlocksTC.thaumatoriumTop).build(null);
            helper.register(ResourceLocation.parse("thaumcraft:bellows"),BELLOWS); helper.register(ResourceLocation.parse("thaumcraft:infernal_furnace"),INFERNAL_FURNACE);
            helper.register(ResourceLocation.parse("thaumcraft:thaumatorium"),THAUMATORIUM); helper.register(ResourceLocation.parse("thaumcraft:thaumatorium_top"),THAUMATORIUM_TOP);
        });
        event.register(Registries.MENU,helper -> { THAUMATORIUM_MENU = new MenuType<>(ContainerThaumatorium::new,FeatureFlags.DEFAULT_FLAGS); helper.register(ResourceLocation.parse("thaumcraft:thaumatorium"),THAUMATORIUM_MENU); });
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,INFERNAL_FURNACE,(tile,side) -> new SidedInvWrapper(tile,side == null ? net.minecraft.core.Direction.UP : side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,THAUMATORIUM,(tile,side) -> side == null ? new InvWrapper(tile) : new SidedInvWrapper(tile,side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,THAUMATORIUM_TOP,(tile,side) -> tile.base() == null ? null : side == null ? new InvWrapper(tile) : new SidedInvWrapper(tile,side));
        event.registerBlockEntity(thaumcraft.api.aspects.EssentiaCapabilities.TRANSPORT,THAUMATORIUM,(tile,side) -> side == null || tile.isConnectable(side) ? tile : null);
        event.registerBlockEntity(thaumcraft.api.aspects.EssentiaCapabilities.TRANSPORT,THAUMATORIUM_TOP,(tile,side) -> side == null || tile.isConnectable(side) ? tile : null);
    }
    public static void restoreLava(Level level,net.minecraft.core.BlockPos pos) { ProcessingEvents.restoreLava(level,pos); }
}
