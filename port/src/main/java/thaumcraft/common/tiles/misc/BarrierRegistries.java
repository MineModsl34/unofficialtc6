package thaumcraft.common.tiles.misc;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;

@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class BarrierRegistries {
    public static BlockEntityType<TileBarrierStone> TYPE;
    private BarrierRegistries() {}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            TYPE = BlockEntityType.Builder.of(TileBarrierStone::new, BlocksTC.pavingStoneBarrier).build(null);
            helper.register(ResourceLocation.parse("thaumcraft:barrier_stone"), TYPE);
        });
    }
}
