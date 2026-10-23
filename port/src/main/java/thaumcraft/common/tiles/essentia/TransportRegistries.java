package thaumcraft.common.tiles.essentia;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.EssentiaCapabilities;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.essentia.*;

/** Nine genuine server transports. Root registers returned blocks/items before BE registry event. */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class TransportRegistries {
    public static BlockEntityType<TileTube> TUBE;
    public static BlockEntityType<TileTubeValve> VALVE;
    public static BlockEntityType<TileTubeRestrict> RESTRICT;
    public static BlockEntityType<TileTubeOneway> ONEWAY;
    public static BlockEntityType<TileTubeFilter> FILTER;
    public static BlockEntityType<TileTubeBuffer> BUFFER;
    public static BlockEntityType<TileCentrifuge> CENTRIFUGE;
    public static BlockEntityType<TileEssentiaInput> INPUT;
    public static BlockEntityType<TileEssentiaOutput> OUTPUT;
    private TransportRegistries() {}
    public static Map<String, Block> createBlocks() {
        Map<String, Block> blocks = new LinkedHashMap<>();
        blocks.put("tube", BlocksTC.tube = tube(BlockTube.Kind.TUBE)); blocks.put("tube_valve", BlocksTC.tubeValve = tube(BlockTube.Kind.VALVE));
        blocks.put("tube_restrict", BlocksTC.tubeRestrict = tube(BlockTube.Kind.RESTRICT)); blocks.put("tube_oneway", BlocksTC.tubeOneway = tube(BlockTube.Kind.ONEWAY));
        blocks.put("tube_filter", BlocksTC.tubeFilter = tube(BlockTube.Kind.FILTER)); blocks.put("tube_buffer", BlocksTC.tubeBuffer = tube(BlockTube.Kind.BUFFER));
        blocks.put("centrifuge", BlocksTC.centrifuge = new BlockCentrifuge(BlockBehaviour.Properties.of().strength(1.5F, 2).sound(SoundType.WOOD).noOcclusion()));
        blocks.put("essentia_input", BlocksTC.essentiaTransportInput = new BlockEssentiaTransport(BlockBehaviour.Properties.of().strength(1, 10).sound(SoundType.METAL).noOcclusion(), false));
        blocks.put("essentia_output", BlocksTC.essentiaTransportOutput = new BlockEssentiaTransport(BlockBehaviour.Properties.of().strength(1, 10).sound(SoundType.METAL).noOcclusion(), true));
        return blocks;
    }
    private static Block tube(BlockTube.Kind kind) { return new BlockTube(BlockBehaviour.Properties.of().strength(.5F, 5).sound(SoundType.METAL).noOcclusion(), kind); }
    @SubscribeEvent public static void register(RegisterEvent event) { event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
        TUBE = BlockEntityType.Builder.of(TileTube::new, BlocksTC.tube).build(null); VALVE = BlockEntityType.Builder.of(TileTubeValve::new, BlocksTC.tubeValve).build(null);
        RESTRICT = BlockEntityType.Builder.of(TileTubeRestrict::new, BlocksTC.tubeRestrict).build(null); ONEWAY = BlockEntityType.Builder.of(TileTubeOneway::new, BlocksTC.tubeOneway).build(null);
        FILTER = BlockEntityType.Builder.of(TileTubeFilter::new, BlocksTC.tubeFilter).build(null); BUFFER = BlockEntityType.Builder.of(TileTubeBuffer::new, BlocksTC.tubeBuffer).build(null);
        CENTRIFUGE = BlockEntityType.Builder.of(TileCentrifuge::new, BlocksTC.centrifuge).build(null); INPUT = BlockEntityType.Builder.of(TileEssentiaInput::new, BlocksTC.essentiaTransportInput).build(null);
        OUTPUT = BlockEntityType.Builder.of(TileEssentiaOutput::new, BlocksTC.essentiaTransportOutput).build(null);
        helper.register(id("tube"), TUBE); helper.register(id("tube_valve"), VALVE); helper.register(id("tube_restrict"), RESTRICT); helper.register(id("tube_oneway"), ONEWAY);
        helper.register(id("tube_filter"), FILTER); helper.register(id("tube_buffer"), BUFFER); helper.register(id("centrifuge"), CENTRIFUGE); helper.register(id("essentia_input"), INPUT); helper.register(id("essentia_output"), OUTPUT);
    }); }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, name); }
    @SubscribeEvent public static void commonSetup(FMLCommonSetupEvent event) { event.enqueueWork(() -> {
        // ConfigRecipes/ModConfig postInitMisc source aspect event palette, without future crop rules.
        for (var aspect : thaumcraft.api.aspects.Aspect.aspects.values()) if (!thaumcraft.common.config.ModConfig.aspectOrder.contains(aspect)) thaumcraft.common.config.ModConfig.aspectOrder.add(aspect);
    }); }
    private static <T extends net.minecraft.world.level.block.entity.BlockEntity & IEssentiaTransport> void capability(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        if (type != null) event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, type, (tile, side) -> side == null || tile.isConnectable(side) ? tile : null);
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        capability(event, TUBE); capability(event, VALVE); capability(event, RESTRICT); capability(event, ONEWAY); capability(event, FILTER); capability(event, BUFFER);
        capability(event, CENTRIFUGE); capability(event, INPUT); capability(event, OUTPUT);
        capability(event, EssentiaRegistries.JAR); capability(event, EssentiaRegistries.JAR_VOID); capability(event, EssentiaRegistries.ALEMBIC);
    }
}
