package thaumcraft.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.config.ConfigBlocks;
import thaumcraft.common.lib.TCTags;

public class TCBlockTagsProvider extends BlockTagsProvider {
    public TCBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
        super(output, lookup, Thaumcraft.MODID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // setHarvestLevel from ConfigBlocks.initMisc: amber needs stone (1), cinnabar needs iron (2)
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(BlocksTC.oreAmber, BlocksTC.oreCinnabar, BlocksTC.oreQuartz);
        tag(BlockTags.NEEDS_STONE_TOOL).add(BlocksTC.oreAmber);
        tag(BlockTags.NEEDS_IRON_TOOL).add(BlocksTC.oreCinnabar);
        tag(BlockTags.MINEABLE_WITH_AXE).add(BlocksTC.logGreatwood, BlocksTC.logSilverwood, BlocksTC.plankGreatwood, BlocksTC.plankSilverwood);
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(BlocksTC.infusionMatrix,BlocksTC.pedestalArcane,BlocksTC.pedestalAncient,BlocksTC.pedestalEldritch,BlocksTC.stabilizer,BlocksTC.inlay,BlocksTC.smelterBasic,BlocksTC.smelterThaumium,BlocksTC.smelterVoid);
        tag(BlockTags.MINEABLE_WITH_AXE).add(BlocksTC.arcaneWorkbench,BlocksTC.arcaneWorkbenchCharger,BlocksTC.alembic);
        tag(BlockTags.MINEABLE_WITH_HOE).add(BlocksTC.leafGreatwood, BlocksTC.leafSilverwood);
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(BlocksTC.tube,BlocksTC.tubeValve,BlocksTC.tubeRestrict,BlocksTC.tubeOneway,BlocksTC.tubeFilter,BlocksTC.tubeBuffer,BlocksTC.essentiaTransportInput,BlocksTC.essentiaTransportOutput,
            BlocksTC.smelterAux,BlocksTC.smelterVent,BlocksTC.infernalFurnace,BlocksTC.placeholderNetherbrick,BlocksTC.placeholderObsidian,BlocksTC.thaumatorium,BlocksTC.thaumatoriumTop,BlocksTC.brainBox,
            BlocksTC.mirror,BlocksTC.mirrorEssentia,BlocksTC.activatorRail,BlocksTC.visBattery,BlocksTC.rechargePedestal,
            BlocksTC.lampArcane,BlocksTC.lampGrowth,BlocksTC.lampFertility,BlocksTC.everfullUrn,BlocksTC.jarBrain,BlocksTC.dioptra,BlocksTC.spa,BlocksTC.potionSprayer,
            BlocksTC.patternCrafter,BlocksTC.condenser,BlocksTC.condenserlattice,BlocksTC.condenserlatticeDirty);
        tag(BlockTags.MINEABLE_WITH_AXE).add(BlocksTC.centrifuge,BlocksTC.bellows,BlocksTC.visGenerator,BlocksTC.levitator,BlocksTC.hungryChest,BlocksTC.arcaneEar,BlocksTC.arcaneEarToggle,BlocksTC.redstoneRelay);
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(BlocksTC.golemBuilder,BlocksTC.placeholderBars,
            BlocksTC.placeholderCauldron,BlocksTC.placeholderAnvil,BlocksTC.placeholderTable);
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(BlocksTC.voidSiphon);
        tag(BlockTags.RAILS).add(BlocksTC.activatorRail);
        tag(BlockTags.MINEABLE_WITH_AXE).add(BlocksTC.taintLog,BlocksTC.bannerCrimsonCult);
        for(Block banner:BlocksTC.banners.values())tag(BlockTags.MINEABLE_WITH_AXE).add(banner);
        tag(BlockTags.LOGS).add(BlocksTC.taintLog);
        // Exact reviewed legacy materials for the lesser cultist portal surface branch.
        // Do not use broad modern stone/dirt tags (grass, snow_block and new stones differ).
        var portalSupport = tag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.resources.ResourceLocation.parse(thaumcraft.common.world.structures.TCCultistPortalSupport.TAG_ID)));
        for (String id : thaumcraft.common.world.structures.TCCultistPortalSupport.sourceSupportIds()) {
            var location = net.minecraft.resources.ResourceLocation.parse(id);
            if (location.getNamespace().equals("minecraft")) portalSupport.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(location)
                .orElseThrow(() -> new IllegalStateException("Missing native source portal support " + id)));
            else portalSupport.addOptional(location); // Genuine future TC blocks, never fake registry entries.
        }

        tag(TCTags.Blocks.ORES_AMBER).add(BlocksTC.oreAmber);
        tag(TCTags.Blocks.TC_ORE_REPLACEABLES).add(Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
            Blocks.DEEPSLATE, Blocks.TUFF);
        tag(TCTags.Blocks.ORES_CINNABAR).add(BlocksTC.oreCinnabar);
        tag(Tags.Blocks.ORES_QUARTZ).add(BlocksTC.oreQuartz);
        tag(Tags.Blocks.ORES).addTag(TCTags.Blocks.ORES_AMBER).addTag(TCTags.Blocks.ORES_CINNABAR);
        tag(Tags.Blocks.ORES_IN_GROUND_STONE).add(BlocksTC.oreAmber, BlocksTC.oreCinnabar, BlocksTC.oreQuartz);

        // TC logs are not in logs_that_burn: they have their own burn time (data map) and their own charcoal recipe
        tag(TCTags.Blocks.GREATWOOD_LOGS).add(BlocksTC.logGreatwood);
        tag(TCTags.Blocks.SILVERWOOD_LOGS).add(BlocksTC.logSilverwood);
        tag(BlockTags.LOGS).addTag(TCTags.Blocks.GREATWOOD_LOGS).addTag(TCTags.Blocks.SILVERWOOD_LOGS);
        tag(BlockTags.PLANKS).add(BlocksTC.plankGreatwood, BlocksTC.plankSilverwood);
        tag(BlockTags.LEAVES).add(BlocksTC.leafGreatwood, BlocksTC.leafSilverwood);
        tag(BlockTags.SAPLINGS).add(BlocksTC.saplingGreatwood, BlocksTC.saplingSilverwood);
        tag(BlockTags.SMALL_FLOWERS).add(BlocksTC.shimmerleaf, BlocksTC.cinderpearl);
        tag(BlockTags.ENDERMAN_HOLDABLE).add(BlocksTC.vishroom);

        tag(TCTags.Blocks.CINDERPEARL_SOIL).addTag(BlockTags.SAND).addTag(BlockTags.DIRT).addTag(BlockTags.TERRACOTTA);

        // Material.ROCK from 1.12: natural and processed stone, ores
        tag(TCTags.Blocks.CRYSTAL_SUPPORT)
            .addTag(BlockTags.BASE_STONE_OVERWORLD)
            .addTag(BlockTags.BASE_STONE_NETHER)
            .addTag(Tags.Blocks.STONES)
            .addTag(Tags.Blocks.COBBLESTONES)
            .addTag(Tags.Blocks.SANDSTONE_BLOCKS)
            .addTag(Tags.Blocks.END_STONES)
            .addTag(Tags.Blocks.OBSIDIANS)
            .addTag(Tags.Blocks.ORES)
            .addTag(BlockTags.STONE_BRICKS)
            .addTag(BlockTags.TERRACOTTA)
            .add(Blocks.BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.SMOOTH_STONE, Blocks.NETHER_BRICKS, Blocks.PRISMARINE,
                Blocks.DRIPSTONE_BLOCK, Blocks.CALCITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_GRANITE,
                Blocks.POLISHED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES);
        Block[] stones = {BlocksTC.stoneArcane, BlocksTC.stoneArcaneBrick, BlocksTC.stoneAncient, BlocksTC.stoneAncientTile,
            BlocksTC.stoneAncientRock, BlocksTC.stoneAncientGlyphed, BlocksTC.stoneAncientDoorway, BlocksTC.stoneEldritchTile,
            BlocksTC.stonePorous, BlocksTC.matrixSpeed, BlocksTC.matrixCost};
        tag(TCTags.Blocks.CRYSTAL_SUPPORT).add(stones);

        // Stage 2c: decoration
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(stones).add(
            BlocksTC.stairsArcane, BlocksTC.stairsArcaneBrick, BlocksTC.stairsAncient,
            BlocksTC.slabArcaneStone, BlocksTC.slabArcaneBrick, BlocksTC.slabAncient, BlocksTC.slabEldritch,
            BlocksTC.metalBlockBrass, BlocksTC.metalBlockThaumium, BlocksTC.metalBlockVoid, BlocksTC.metalAlchemical,
            BlocksTC.metalAlchemicalAdvanced, BlocksTC.pillarArcane, BlocksTC.pillarAncient, BlocksTC.pillarEldritch,
            BlocksTC.tableStone, BlocksTC.pavingStoneTravel, BlocksTC.pavingStoneBarrier);
        tag(BlockTags.MINEABLE_WITH_AXE).add(BlocksTC.tableWood, BlocksTC.researchTable, BlocksTC.stairsGreatwood, BlocksTC.stairsSilverwood,
            BlocksTC.slabGreatwood, BlocksTC.slabSilverwood);
        tag(BlockTags.MINEABLE_WITH_HOE).add(BlocksTC.fleshBlock);
        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(BlocksTC.grassAmbient);
        tag(BlockTags.STAIRS).add(BlocksTC.stairsArcane, BlocksTC.stairsArcaneBrick, BlocksTC.stairsAncient);
        tag(BlockTags.WOODEN_STAIRS).add(BlocksTC.stairsGreatwood, BlocksTC.stairsSilverwood);
        tag(BlockTags.SLABS).add(BlocksTC.slabArcaneStone, BlocksTC.slabArcaneBrick, BlocksTC.slabAncient, BlocksTC.slabEldritch);
        tag(BlockTags.WOODEN_SLABS).add(BlocksTC.slabGreatwood, BlocksTC.slabSilverwood);
        // isBeaconBase of BlockStoneTC, BlockMetalTC, BlockTranslucent
        tag(BlockTags.BEACON_BASE_BLOCKS).add(stones).add(BlocksTC.metalBlockBrass, BlocksTC.metalBlockThaumium,
            BlocksTC.metalBlockVoid, BlocksTC.metalAlchemical, BlocksTC.metalAlchemicalAdvanced,
            BlocksTC.amberBlock, BlocksTC.amberBrick, BlocksTC.empty);
        // canEntityDestroy: false when hardness < 0
        tag(BlockTags.WITHER_IMMUNE).add(BlocksTC.stoneAncientRock, BlocksTC.stoneAncientDoorway);
        tag(BlockTags.DRAGON_IMMUNE).add(BlocksTC.stoneAncientRock, BlocksTC.stoneAncientDoorway);
        tag(BlockTags.DIRT).add(BlocksTC.grassAmbient);
        tag(BlockTags.ANIMALS_SPAWNABLE_ON).add(BlocksTC.grassAmbient);
        tag(TCTags.Blocks.CANDLES).add(BlocksTC.candles.values().toArray(new Block[0]));
        tag(TCTags.Blocks.STORAGE_THAUMIUM).add(BlocksTC.metalBlockThaumium);
        tag(TCTags.Blocks.STORAGE_VOID).add(BlocksTC.metalBlockVoid);
        tag(TCTags.Blocks.STORAGE_BRASS).add(BlocksTC.metalBlockBrass);
        tag(Tags.Blocks.STORAGE_BLOCKS).addTag(TCTags.Blocks.STORAGE_THAUMIUM).addTag(TCTags.Blocks.STORAGE_VOID).addTag(TCTags.Blocks.STORAGE_BRASS);
    }
}
