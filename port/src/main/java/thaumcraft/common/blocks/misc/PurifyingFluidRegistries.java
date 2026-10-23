package thaumcraft.common.blocks.misc;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;

@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class PurifyingFluidRegistries{
    public static FluidType TYPE;
    public static BaseFlowingFluid.Source SOURCE;
    public static BaseFlowingFluid.Flowing FLOWING;
    public static Item BUCKET;
    private PurifyingFluidRegistries(){}
    private static BaseFlowingFluid.Properties properties(){return new BaseFlowingFluid.Properties(()->TYPE,()->SOURCE,()->FLOWING).bucket(()->BUCKET).block(()->(LiquidBlock)BlocksTC.purifyingFluid).tickRate(5).slopeFindDistance(4).levelDecreasePerBlock(1);}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(NeoForgeRegistries.Keys.FLUID_TYPES,helper->{TYPE=new FluidType(FluidType.Properties.create().density(1000).viscosity(1000).lightLevel(5).canConvertToSource(false).canHydrate(false));helper.register(ResourceLocation.parse("thaumcraft:purifying_fluid"),TYPE);});
        event.register(Registries.FLUID,helper->{SOURCE=new BaseFlowingFluid.Source(properties());FLOWING=new BaseFlowingFluid.Flowing(properties());helper.register(ResourceLocation.parse("thaumcraft:purifying_fluid"),SOURCE);helper.register(ResourceLocation.parse("thaumcraft:flowing_purifying_fluid"),FLOWING);});
        // Retain the native liquid flag, empty sound and piston destruction of a true LiquidBlock.
        event.register(Registries.BLOCK,helper->{BlocksTC.purifyingFluid=new BlockFluidPure(SOURCE,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WATER).mapColor(MapColor.COLOR_LIGHT_GRAY).lightLevel(state->5));helper.register(ResourceLocation.parse("thaumcraft:purifying_fluid"),BlocksTC.purifyingFluid);});
    }
    public static void registerItems(RegisterEvent.RegisterHelper<Item> helper){BUCKET=new BucketItem(SOURCE,new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET).rarity(net.minecraft.world.item.Rarity.RARE));helper.register(ResourceLocation.parse("thaumcraft:purifying_fluid_bucket"),BUCKET);}
}
