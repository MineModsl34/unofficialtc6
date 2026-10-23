package thaumcraft.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.tags.BiomeTags;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import thaumcraft.Thaumcraft;
import thaumcraft.common.world.biomes.TCBiomes;

/** The three legacy BiomeDictionary type sets, without extra aura-affecting climate tags. */
public class TCBiomeTagsProvider extends BiomeTagsProvider {
    public TCBiomeTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
        super(output, lookup, Thaumcraft.MODID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(Tags.Biomes.IS_MAGICAL).add(TCBiomes.MAGICAL_FOREST, TCBiomes.EERIE, TCBiomes.ELDRITCH);
        tag(BiomeTags.IS_FOREST).add(TCBiomes.MAGICAL_FOREST);
        tag(Tags.Biomes.IS_SPOOKY).add(TCBiomes.EERIE, TCBiomes.ELDRITCH);
        // c:is_end describes the legacy END type, without opting Outer Lands into End-city generation.
        tag(Tags.Biomes.IS_END).add(TCBiomes.ELDRITCH);
        tag(Tags.Biomes.IS_OVERWORLD).add(TCBiomes.MAGICAL_FOREST, TCBiomes.EERIE);
        // Exact unmodded Forge1.12 WARM/COOL/ICY pool, with TC's WARM/COOL forest insertion.
        // Datapacks can extend this tag for additional legacy-style biome-manager entries.
        tag(thaumcraft.common.world.biomes.TCMobSpawnModifier.LEGACY_POOL).add(
            net.minecraft.world.level.biome.Biomes.FOREST,net.minecraft.world.level.biome.Biomes.DARK_FOREST,
            net.minecraft.world.level.biome.Biomes.WINDSWEPT_HILLS,net.minecraft.world.level.biome.Biomes.PLAINS,
            net.minecraft.world.level.biome.Biomes.BIRCH_FOREST,net.minecraft.world.level.biome.Biomes.SWAMP,
            net.minecraft.world.level.biome.Biomes.TAIGA,net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS,
            net.minecraft.world.level.biome.Biomes.SNOWY_TAIGA,TCBiomes.MAGICAL_FOREST);
    }
}
