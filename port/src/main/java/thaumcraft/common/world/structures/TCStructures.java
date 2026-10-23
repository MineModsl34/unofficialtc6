package thaumcraft.common.world.structures;

import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.neoforged.neoforge.common.Tags;

/** Every-chunk source surface attempts: exclusive 1/100 mound, else-if 1/500 portal. */
public final class TCStructures {
    private static final ResourceLocation ID = ResourceLocation.parse("thaumcraft:mound");
    public static final ResourceKey<Structure> MOUND = ResourceKey.create(Registries.STRUCTURE, ID);
    public static final ResourceKey<StructureSet> MOUND_SET = ResourceKey.create(Registries.STRUCTURE_SET, ID);
    private static final ResourceLocation PORTAL_ID = ResourceLocation.parse("thaumcraft:cultist_portal");
    public static final ResourceKey<Structure> CULTIST_PORTAL = ResourceKey.create(Registries.STRUCTURE, PORTAL_ID);
    public static final ResourceKey<StructureSet> CULTIST_PORTAL_SET = ResourceKey.create(Registries.STRUCTURE_SET, PORTAL_ID);

    private TCStructures() {}

    public static void bootstrapStructures(BootstrapContext<Structure> context) {
        // Structure starts precede terrain and have no Level/dimension. The common overworld tag
        // excludes phantom starts/locates in Nether and End; the piece still enforces actual dimension.
        var biomes = context.lookup(Registries.BIOME).getOrThrow(Tags.Biomes.IS_OVERWORLD);
        context.register(MOUND, new TCMoundStructure(new Structure.StructureSettings(biomes, Map.of(),
            // TC6 generateSurface followed generateAll (ores + vegetation). Keep that overwrite
            // order, rather than allowing modern later trees/crystals to write into the mound.
            GenerationStep.Decoration.TOP_LAYER_MODIFICATION, TerrainAdjustment.NONE)));
        context.register(CULTIST_PORTAL, new TCCultistPortalStructure(new Structure.StructureSettings(biomes, Map.of(),
            GenerationStep.Decoration.TOP_LAYER_MODIFICATION, TerrainAdjustment.NONE)));
    }

    public static void bootstrapSets(BootstrapContext<StructureSet> context) {
        context.register(MOUND_SET, new StructureSet(context.lookup(Registries.STRUCTURE).getOrThrow(MOUND),
            new RandomSpreadStructurePlacement(1, 0, RandomSpreadType.LINEAR, 0x544336)));
        // Both GenerationContexts use the same large-feature seed, independent of structure ID.
        // Each retains only its own outcome from the shared roll; these are not independent chances.
        context.register(CULTIST_PORTAL_SET, new StructureSet(context.lookup(Registries.STRUCTURE).getOrThrow(CULTIST_PORTAL),
            new RandomSpreadStructurePlacement(1, 0, RandomSpreadType.LINEAR, 0x544336)));
    }
}
