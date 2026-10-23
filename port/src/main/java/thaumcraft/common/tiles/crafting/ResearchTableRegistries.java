package thaumcraft.common.tiles.crafting;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.container.ContainerResearchTable;

/** First physical TC block-entity/menu registration; no theory actions or custom C2S packet. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class ResearchTableRegistries {
    public static BlockEntityType<TileResearchTable> RESEARCH_TABLE;
    public static MenuType<ContainerResearchTable> RESEARCH_TABLE_MENU;

    private ResearchTableRegistries() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            RESEARCH_TABLE = BlockEntityType.Builder.of(TileResearchTable::new, BlocksTC.researchTable).build(null);
            helper.register(id(), RESEARCH_TABLE);
        });
        event.register(Registries.MENU, helper -> {
            RESEARCH_TABLE_MENU = new MenuType<>((window, inventory) -> new ContainerResearchTable(window, inventory), FeatureFlags.DEFAULT_FLAGS);
            helper.register(id(), RESEARCH_TABLE_MENU);
        });
    }

    @SubscribeEvent
    public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, RESEARCH_TABLE, TileResearchTable::itemHandler);
    }

    private static ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "research_table"); }
}
