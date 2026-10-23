package thaumcraft.common.tiles.crafting;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.devices.TileStabilizer;

@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class InfusionRegistries {
    public static BlockEntityType<TileInfusionMatrix> INFUSION_MATRIX;
    public static BlockEntityType<TilePedestal> PEDESTAL;
    public static BlockEntityType<TileStabilizer> STABILIZER;
    private InfusionRegistries() {}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            INFUSION_MATRIX = BlockEntityType.Builder.of(TileInfusionMatrix::new, BlocksTC.infusionMatrix).build(null);
            PEDESTAL = BlockEntityType.Builder.of(TilePedestal::new, BlocksTC.pedestalArcane, BlocksTC.pedestalAncient, BlocksTC.pedestalEldritch).build(null);
            STABILIZER = BlockEntityType.Builder.of(TileStabilizer::new, BlocksTC.stabilizer).build(null);
            helper.register(ResourceLocation.parse("thaumcraft:infusion_matrix"), INFUSION_MATRIX);
            helper.register(ResourceLocation.parse("thaumcraft:pedestal"), PEDESTAL);
            helper.register(ResourceLocation.parse("thaumcraft:stabilizer"), STABILIZER);
        });
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PEDESTAL, (tile, side) -> new InvWrapper(tile));
    }
}
