package thaumcraft.common.tiles.crafting;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.container.ContainerArcaneWorkbench;

@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ArcaneWorkbenchRegistries {
    public static BlockEntityType<TileArcaneWorkbench> ARCANE_WORKBENCH;
    public static MenuType<ContainerArcaneWorkbench> ARCANE_WORKBENCH_MENU;
    private ArcaneWorkbenchRegistries() {}
    @SubscribeEvent public static void register(RegisterEvent event) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "arcane_workbench");
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            ARCANE_WORKBENCH = BlockEntityType.Builder.of(TileArcaneWorkbench::new, BlocksTC.arcaneWorkbench).build(null);
            helper.register(id, ARCANE_WORKBENCH);
        });
        event.register(Registries.MENU, helper -> {
            ARCANE_WORKBENCH_MENU = new MenuType<>((window, inventory) -> new ContainerArcaneWorkbench(window, inventory), FeatureFlags.DEFAULT_FLAGS);
            helper.register(id, ARCANE_WORKBENCH_MENU);
        });
    }
}
