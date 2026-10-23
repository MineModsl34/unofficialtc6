package thaumcraft.common.world.biomes;

import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;
import thaumcraft.Thaumcraft;
import thaumcraft.common.config.ModConfig;

/** Weighted temperate forest region; eerie and eldritch must not be natural Overworld biomes. */
public final class TCBiomeRegion extends Region {
    public static final ResourceLocation REGION_ID = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "overworld");

    public TCBiomeRegion(int weight) {
        super(REGION_ID, RegionType.OVERWORLD, weight);
    }

    /** Call from FMLCommonSetupEvent.enqueueWork after common config has loaded. */
    public static void register() {
        if (shouldGenerateForest(ModConfig.CONFIG_WORLD.generateMagicForest, ModConfig.CONFIG_WORLD.biomeMagicalForestWeight)) {
            Regions.register(new TCBiomeRegion(ModConfig.CONFIG_WORLD.biomeMagicalForestWeight));
        }
        // Registers the old dirt top/filler for forced/test Outer Lands biomes; this does not insert them into a biome source.
        SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, Thaumcraft.MODID, surfaceRules());
    }

    public static boolean shouldGenerateForest(boolean enabled, int weight) {
        return enabled && weight > 0;
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        // TC6 added forest to WARM/COOL legacy lists. In the new climate system, use oak/birch forest cells,
        // retaining all other vanilla climate cells. Region weight is not a legacy biome-list percentage.
        addModifiedVanillaOverworldBiomes(mapper, builder -> {
            builder.replaceBiome(Biomes.FOREST, TCBiomes.MAGICAL_FOREST);
            builder.replaceBiome(Biomes.BIRCH_FOREST, TCBiomes.MAGICAL_FOREST);
        });
    }

    public static SurfaceRules.RuleSource surfaceRules() {
        return SurfaceRules.ifTrue(SurfaceRules.isBiome(TCBiomes.ELDRITCH),
            SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(),
                SurfaceRules.sequence(
                    SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.state(Blocks.DIRT.defaultBlockState())),
                    SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SurfaceRules.state(Blocks.DIRT.defaultBlockState())))));
    }
}
