package thaumcraft.common.tiles.crafting;

import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.crafting.BlockFocalManipulator;
import thaumcraft.common.container.ContainerFocalManipulator;

@EventBusSubscriber(modid = Thaumcraft.MODID,bus = EventBusSubscriber.Bus.MOD)
public final class FocalRegistries {
    public static BlockEntityType<TileFocalManipulator> TYPE;
    public static MenuType<ContainerFocalManipulator> MENU;
    private static Map<String,Block> blocks;
    private FocalRegistries() {}
    public static Map<String,Block> createBlocks() {
        if (blocks == null) blocks = Map.of("wand_workbench",BlocksTC.wandWorkbench = new BlockFocalManipulator(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1).sound(SoundType.STONE).noOcclusion()));
        return blocks;
    }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE,helper -> { TYPE = BlockEntityType.Builder.of(TileFocalManipulator::new,BlocksTC.wandWorkbench).build(null); helper.register(ResourceLocation.parse("thaumcraft:wand_workbench"),TYPE); });
        event.register(Registries.MENU,helper -> { MENU = new MenuType<>(ContainerFocalManipulator::new,FeatureFlags.DEFAULT_FLAGS); helper.register(ResourceLocation.parse("thaumcraft:focal_manipulator"),MENU); });
    }
}
