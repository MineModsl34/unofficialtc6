package thaumcraft.common.blocks.misc;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;

/** Own MOD registration for type/fluids/block; only the explicit item factory is consumed by ConfigItems. */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DeathFluidRegistries {
    public static FluidType TYPE;
    public static DeathFluid SOURCE, FLOWING;
    public static Item BUCKET;
    private DeathFluidRegistries() {}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.FLUID_TYPES, helper -> {
            TYPE = new FluidType(FluidType.Properties.create().density(1000).viscosity(1500)
                .canConvertToSource(false).canHydrate(false).canDrown(false));
            helper.register(ResourceLocation.parse("thaumcraft:liquid_death"), TYPE);
        });
        event.register(Registries.FLUID, helper -> {
            SOURCE = new DeathFluid(true); FLOWING = new DeathFluid(false);
            helper.register(ResourceLocation.parse("thaumcraft:liquid_death"), SOURCE);
            helper.register(ResourceLocation.parse("thaumcraft:flowing_liquid_death"), FLOWING);
        });
        event.register(Registries.BLOCK, helper -> {
            BlocksTC.liquidDeath = new BlockFluidDeath();
            helper.register(ResourceLocation.parse("thaumcraft:liquid_death"), BlocksTC.liquidDeath);
        });
    }
    @SubscribeEvent public static void capabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            (level, pos, state, blockEntity, side) -> new SourceFluidBlockHandler(level, pos, true), BlocksTC.fluxGoo);
        event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            (level, pos, state, blockEntity, side) -> new SourceFluidBlockHandler(level, pos, false), BlocksTC.liquidDeath);
    }
    /** ConfigItems owns item registration; there is no fictitious ItemBlock for this source fluid. */
    public static java.util.Map<String, Item> createItems() {
        BUCKET = new BucketItem(SOURCE, new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET).rarity(Rarity.RARE));
        var items = new java.util.LinkedHashMap<String, Item>();
        items.put("liquid_death_bucket", BUCKET);
        return items;
    }
}
