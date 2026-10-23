package thaumcraft.common.world.biomes;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.world.WorldgenBlacklists;

/**
 * Port of BiomeHandler. 1.12 BiomeDictionary.Type is replaced by c:/minecraft: biome tags.
 * In the port the priority of known types is determined by registration order.
 * In TC6 getTypes returned a Set, so this priority does not reproduce its iteration order bit for bit.
 */
public class BiomeHandler {
    /** Legacy public API names; registry keys replace the removed numeric dimension/biome IDs. */
    public static void addDimBlacklist(ResourceLocation dimension, int mode) {
        WorldgenBlacklists.registerDimension(dimension, mode);
    }

    public static int getDimBlacklist(ResourceLocation dimension) {
        return ModConfig.CONFIG_WORLD.worldgenPolicy.dimensionMode(dimension);
    }

    public static void addBiomeBlacklist(ResourceLocation biome, int mode) {
        WorldgenBlacklists.registerBiome(biome, mode);
    }

    public static int getBiomeBlacklist(ResourceLocation biome) {
        return ModConfig.CONFIG_WORLD.worldgenPolicy.biomeMode(biome);
    }

    /** Direct holders used by tests/datapacks can lack a registry key; they have no blacklist entry. */
    public static ResourceLocation biomeKey(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.location()).orElse(null);
    }
    public record BiomeInfo(TagKey<Biome> tag, float auraLevel, Aspect aspect, boolean greatwood, float greatwoodChance) {
    }

    public static final List<BiomeInfo> BIOME_INFO = new ArrayList<>();

    private static void registerBiomeInfo(TagKey<Biome> tag, float auraLevel, Aspect aspect, boolean greatwood, float greatwoodChance) {
        // TC6 registered the DRY type twice and the last entry won; the same here (replacement)
        BIOME_INFO.removeIf(i -> i.tag().equals(tag));
        BIOME_INFO.add(new BiomeInfo(tag, auraLevel, aspect, greatwood, greatwoodChance));
    }

    private static List<BiomeInfo> infoFor(Holder<Biome> biome) {
        List<BiomeInfo> out = new ArrayList<>();
        for (BiomeInfo info : BIOME_INFO) {
            if (biome.is(info.tag())) {
                out.add(info);
            }
        }
        return out;
    }

    /**
     * Average auraLevel over the biome's types; 0.5 without known types.
     * (In TC6 a type without an entry gave an NPE and also 0.5 for the whole biome; here tags without an entry are simply ignored.)
     */
    public static float getBiomeAuraModifier(Holder<Biome> biome) {
        List<BiomeInfo> infos = infoFor(biome);
        if (infos.isEmpty()) {
            return 0.5F;
        }
        float sum = 0.0F;
        for (BiomeInfo info : infos) {
            sum += info.auraLevel();
        }
        return sum / infos.size();
    }

    public static Aspect getRandomBiomeTag(Holder<Biome> biome, RandomSource random) {
        List<BiomeInfo> infos = infoFor(biome);
        return infos.isEmpty() ? null : infos.get(random.nextInt(infos.size())).aspect();
    }

    public static float getBiomeSupportsGreatwood(Holder<Biome> biome) {
        for (BiomeInfo info : infoFor(biome)) {
            if (info.greatwood()) {
                return info.greatwoodChance();
            }
        }
        return 0.0F;
    }

    static {
        registerBiomeInfo(Tags.Biomes.IS_AQUATIC, 0.33F, Aspect.WATER, false, 0.0F);
        registerBiomeInfo(BiomeTags.IS_OCEAN, 0.33F, Aspect.WATER, false, 0.0F);
        registerBiomeInfo(BiomeTags.IS_RIVER, 0.4F, Aspect.WATER, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_WET, 0.4F, Aspect.WATER, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_LUSH, 0.5F, Aspect.WATER, true, 0.5F);
        registerBiomeInfo(Tags.Biomes.IS_HOT, 0.33F, Aspect.FIRE, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_DRY, 0.25F, Aspect.FIRE, false, 0.0F);
        registerBiomeInfo(BiomeTags.IS_NETHER, 0.125F, Aspect.FIRE, false, 0.0F);
        registerBiomeInfo(BiomeTags.IS_BADLANDS, 0.33F, Aspect.FIRE, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_SPOOKY, 0.5F, Aspect.FIRE, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_DENSE_VEGETATION, 0.4F, Aspect.ORDER, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_SNOWY, 0.25F, Aspect.ORDER, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_COLD, 0.25F, Aspect.ORDER, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_MUSHROOM, 0.75F, Aspect.ORDER, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_MAGICAL, 0.75F, Aspect.ORDER, true, 1.0F);
        registerBiomeInfo(Tags.Biomes.IS_CONIFEROUS_TREE, 0.33F, Aspect.EARTH, true, 0.2F);
        registerBiomeInfo(BiomeTags.IS_FOREST, 0.5F, Aspect.EARTH, true, 1.0F);
        registerBiomeInfo(Tags.Biomes.IS_SANDY, 0.25F, Aspect.EARTH, false, 0.0F);
        registerBiomeInfo(BiomeTags.IS_BEACH, 0.3F, Aspect.EARTH, false, 0.0F);
        registerBiomeInfo(BiomeTags.IS_JUNGLE, 0.6F, Aspect.EARTH, false, 0.0F);
        registerBiomeInfo(BiomeTags.IS_SAVANNA, 0.25F, Aspect.AIR, true, 0.2F);
        registerBiomeInfo(Tags.Biomes.IS_MOUNTAIN, 0.3F, Aspect.AIR, false, 0.0F);
        registerBiomeInfo(BiomeTags.IS_HILL, 0.33F, Aspect.AIR, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_PLAINS, 0.3F, Aspect.AIR, true, 0.2F);
        registerBiomeInfo(Tags.Biomes.IS_END, 0.125F, Aspect.AIR, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_DRY, 0.125F, Aspect.ENTROPY, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_SPARSE_VEGETATION, 0.2F, Aspect.ENTROPY, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_SWAMP, 0.5F, Aspect.ENTROPY, true, 0.2F);
        registerBiomeInfo(Tags.Biomes.IS_WASTELAND, 0.125F, Aspect.ENTROPY, false, 0.0F);
        registerBiomeInfo(Tags.Biomes.IS_DEAD, 0.1F, Aspect.ENTROPY, false, 0.0F);
    }
}
