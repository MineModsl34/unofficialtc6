package thaumcraft.datagen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import thaumcraft.Thaumcraft;

/**
 * JSON data generation instead of porting by hand: blockstates and models, loot, tags, recipes.
 * Run: gradlew runData -> src/generated/resources.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput output = gen.getPackOutput();
        ExistingFileHelper files = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        TCWorldgenProvider worldgen = gen.addProvider(event.includeServer(), new TCWorldgenProvider(output, lookup));
        lookup = worldgen.getRegistryProvider();

        gen.addProvider(event.includeClient(), new TCBlockStateProvider(output, files));
        gen.addProvider(event.includeClient(), new TCItemModelProvider(output, files));
        gen.addProvider(event.includeClient(), new TCResearchGuiTextures(output));
        gen.addProvider(event.includeClient(), new TCResearchRecipeDisplayProvider(output));
        gen.addProvider(event.includeClient(), new TCTransportResourcesProvider(output));
        gen.addProvider(event.includeClient(), new TCProcessingAssetsProvider(output));
        gen.addProvider(event.includeClient(), new TCDeviceSupportResourcesProvider(output));
        gen.addProvider(event.includeClient(), new TCWorldDeviceAssetsProvider(output));
        gen.addProvider(event.includeClient() || event.includeServer(), new TCFocalAssetsProvider(output));
        gen.addProvider(event.includeClient(), new TCEquipmentResourcesProvider(output));
        gen.addProvider(event.includeClient() || event.includeServer(), new TCWearableAssetsProvider(output));
        gen.addProvider(event.includeClient(), new TCCasterAssetsProvider(output));
        gen.addProvider(event.includeClient() || event.includeServer(), new TCMob8AssetsProvider(output));
        gen.addProvider(event.includeClient() || event.includeServer(), new TCNaturalMobAssetsProvider(output));
        gen.addProvider(event.includeClient() || event.includeServer(), new TCConstructResourcesProvider(output));
        gen.addProvider(event.includeClient(), new TCMob8BossAssetsProvider(output));
        gen.addProvider(event.includeClient() || event.includeServer(), new TCGolemBuilderAssetsProvider(output));
        gen.addProvider(event.includeClient(), new TCSealResourcesProvider(output));
        gen.addProvider(event.includeClient(), new TCFluxRiftAssetsProvider(output));
        gen.addProvider(event.includeClient(), new TCDeathFluidResourcesProvider(output));
        gen.addProvider(event.includeClient() || event.includeServer(), new TCBannerResourcesProvider(output));
        gen.addProvider(event.includeClient(), new TCShaderResourcesProvider(output));
        gen.addProvider(event.includeClient(), new TCClientResourcesProvider(output));

        TCBlockTagsProvider blockTags = gen.addProvider(event.includeServer(), new TCBlockTagsProvider(output, lookup, files));
        gen.addProvider(event.includeServer(), new TCBiomeTagsProvider(output, lookup, files));
        gen.addProvider(event.includeServer(), new TCMobEntityTagsProvider(output, lookup, files));
        gen.addProvider(event.includeServer(), new TCItemTagsProvider(output, lookup, blockTags.contentsGetter(), files));
        gen.addProvider(event.includeServer(), new TCDataMapProvider(output, lookup));
        gen.addProvider(event.includeServer(), new TCCuriosProvider(output, files, lookup));
        gen.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
            List.of(new LootTableProvider.SubProviderEntry(TCBlockLoot::new, LootContextParamSets.BLOCK)), lookup));
        gen.addProvider(event.includeServer(), new TCRecipeProvider(output, lookup));
        gen.addProvider(event.includeServer(), new TCDeferredSourceRecipeProvider(output));
        gen.addProvider(event.includeServer(), new TCSmeltingBonusProvider(output));
        gen.addProvider(event.includeServer(), new TCScanTestStructureProvider(output));
        String moundSource = System.getProperty("thaumcraft.moundTemplateSource");
        gen.addProvider(event.includeServer(), new TCStructureTemplateProvider(output,
            moundSource != null && !moundSource.isBlank() ? java.nio.file.Path.of(moundSource) : null));
        String researchSource = System.getProperty("thaumcraft.researchSourceDirectory");
        gen.addProvider(event.includeServer(), new TCResearchDataProvider(output,
            researchSource != null && !researchSource.isBlank() ? java.nio.file.Path.of(researchSource) : null));
    }
}
