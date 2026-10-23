package thaumcraft.common.blocks.world.taint;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.api.blocks.BlocksTC;

/** Actual finite flux fluid/block and source fibre dependency; no vanilla water substitutions. */
public final class WarpTaintRegistries {
    public static FluidType FLUX_TYPE;
    public static Fluid FLUX;
    private WarpTaintRegistries() {}
    public static void registerFluidTypes(RegisterEvent.RegisterHelper<FluidType> registry) {
        FLUX_TYPE = new FluidType(FluidType.Properties.create().density(8).viscosity(6000)
            .canPushEntity(false).canSwim(false).canDrown(false).canHydrate(false));
        registry.register(ResourceLocation.parse("thaumcraft:flux_goo"), FLUX_TYPE);
    }
    public static void registerFluids(RegisterEvent.RegisterHelper<Fluid> registry) {
        FLUX = new FiniteFluxFluid(); registry.register(ResourceLocation.parse("thaumcraft:flux_goo"), FLUX);
    }
    public static void registerBlocks(RegisterEvent.RegisterHelper<Block> registry) {
        BlocksTC.fluxGoo = new BlockFluxGoo(); registry.register(ResourceLocation.parse("thaumcraft:flux_goo"), BlocksTC.fluxGoo);
        BlocksTC.taintFibre = new BlockTaintFibre(); registry.register(ResourceLocation.parse("thaumcraft:taint_fibre"), BlocksTC.taintFibre);
    }
}
