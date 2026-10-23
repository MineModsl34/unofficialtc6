package thaumcraft.common.tiles.essentia;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.container.ContainerSmelter;

@EventBusSubscriber(modid = Thaumcraft.MODID,bus = EventBusSubscriber.Bus.MOD)
public final class EssentiaRegistries {
    public static BlockEntityType<TileJarFillable> JAR;
    public static BlockEntityType<TileJarFillableVoid> JAR_VOID;
    public static BlockEntityType<TileAlembic> ALEMBIC;
    public static BlockEntityType<TileSmelter> SMELTER;
    public static MenuType<ContainerSmelter> SMELTER_MENU;
    private EssentiaRegistries() {}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE,helper -> {
            JAR = BlockEntityType.Builder.of(TileJarFillable::new,BlocksTC.jarNormal).build(null);
            JAR_VOID = BlockEntityType.Builder.of(TileJarFillableVoid::new,BlocksTC.jarVoid).build(null);
            ALEMBIC = BlockEntityType.Builder.of(TileAlembic::new,BlocksTC.alembic).build(null);
            SMELTER = BlockEntityType.Builder.of(TileSmelter::new,BlocksTC.smelterBasic,BlocksTC.smelterThaumium,BlocksTC.smelterVoid).build(null);
            helper.register(ResourceLocation.parse("thaumcraft:jar_normal"),JAR); helper.register(ResourceLocation.parse("thaumcraft:jar_void"),JAR_VOID);
            helper.register(ResourceLocation.parse("thaumcraft:alembic"),ALEMBIC); helper.register(ResourceLocation.parse("thaumcraft:smelter"),SMELTER);
        });
        event.register(Registries.MENU,helper -> { SMELTER_MENU = new MenuType<>(ContainerSmelter::new,FeatureFlags.DEFAULT_FLAGS); helper.register(ResourceLocation.parse("thaumcraft:smelter"),SMELTER_MENU); });
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,SMELTER,(tile,side) -> side == null ? new InvWrapper(tile) : new SidedInvWrapper(tile,side));
    }
}
