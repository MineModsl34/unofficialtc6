package thaumcraft.datagen;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.registries.RegistryPatchGenerator;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import thaumcraft.Thaumcraft;
import thaumcraft.common.world.features.TCWorldgen;

public class TCWorldgenProvider extends DatapackBuiltinEntriesProvider {
    public TCWorldgenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup.thenCompose(base -> RegistryPatchGenerator.createLookup(
            CompletableFuture.completedFuture(base), TCWorldgen.builder(base))), Set.of(Thaumcraft.MODID));
    }
}
