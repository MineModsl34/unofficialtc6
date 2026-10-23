package thaumcraft.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;

/** TC6's IFuelHandler (CraftingEvents.getBurnTime) -> the neoforge:furnace_fuels data map. */
public class TCDataMapProvider extends DataMapProvider {
    public TCDataMapProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void gather(HolderLookup.Provider provider) {
        builder(NeoForgeDataMaps.FURNACE_FUELS)
            .add(BlocksTC.logGreatwood.asItem().builtInRegistryHolder(), new FurnaceFuel(500), false)
            .add(BlocksTC.logSilverwood.asItem().builtInRegistryHolder(), new FurnaceFuel(400), false)
            .add(ItemsTC.alumentum.builtInRegistryHolder(), new FurnaceFuel(4800), false);
    }
}
