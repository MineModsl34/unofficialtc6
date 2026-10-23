package thaumcraft.common.tiles.crafting;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.misc.TileNitor;

@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class CrucibleRegistries {
    public static BlockEntityType<TileCrucible> CRUCIBLE;
    public static BlockEntityType<TileNitor> NITOR;
    private CrucibleRegistries() {}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            CRUCIBLE = BlockEntityType.Builder.of(TileCrucible::new, BlocksTC.crucible).build(null);
            NITOR = BlockEntityType.Builder.of(TileNitor::new, BlocksTC.nitor.values().toArray(net.minecraft.world.level.block.Block[]::new)).build(null);
            helper.register(ResourceLocation.parse("thaumcraft:crucible"), CRUCIBLE);
            helper.register(ResourceLocation.parse("thaumcraft:nitor"), NITOR);
        });
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CRUCIBLE, (tile, side) -> tile.tank);
    }
}
