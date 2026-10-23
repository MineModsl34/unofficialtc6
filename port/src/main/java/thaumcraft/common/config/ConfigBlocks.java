package thaumcraft.common.config;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.basic.BlockCandle;
import thaumcraft.common.blocks.basic.BlockPavingStone;
import thaumcraft.common.blocks.basic.BlockPillar;
import thaumcraft.common.blocks.basic.BlockTable;
import thaumcraft.common.blocks.basic.BlockResearchTable;
import thaumcraft.common.blocks.world.BlockGrassAmbient;
import thaumcraft.common.blocks.world.BlockLootTC;
import thaumcraft.common.lib.utils.LegacyLootRules;
import thaumcraft.common.blocks.world.ore.BlockCrystal;
import thaumcraft.common.blocks.world.ore.ShardType;
import thaumcraft.common.blocks.world.plants.BlockLeavesTC;
import thaumcraft.common.blocks.world.plants.BlockPlantCinderpearl;
import thaumcraft.common.blocks.world.plants.BlockPlantShimmerleaf;
import thaumcraft.common.blocks.world.plants.BlockPlantVishroom;
import thaumcraft.common.blocks.world.plants.BlockSaplingTC;
import thaumcraft.common.lib.SoundsTC;

/**
 * Port of ConfigBlocks. Hardness/blast resistance numbers are recomputed from 1.12:
 * resistance_1.21 = max(setResistance*0.6, hardness), which is how 1.12 computed the final value (see PORTING.md).
 * Harvest levels (setHarvestLevel) are the mineable/needs_*_tool tags in datagen.
 */
public class ConfigBlocks {
    /** Blocks that get a plain BlockItem (in registration order). */
    public static final List<Block> BLOCKS_WITH_ITEMS = new ArrayList<>();
    public static final List<BlockCrystal> CRYSTALS = new ArrayList<>();

    private static RegisterEvent.RegisterHelper<Block> registry;

    public static void initMisc() {
        // In TC6 flammability was set by overriding getFlammability/getFireSpreadSpeed
        FireBlock fire = (FireBlock) Blocks.FIRE;
        fire.setFlammable(BlocksTC.logGreatwood, 5, 5);
        fire.setFlammable(BlocksTC.logSilverwood, 5, 5);
        fire.setFlammable(BlocksTC.leafGreatwood, 30, 60);
        fire.setFlammable(BlocksTC.leafSilverwood, 30, 60);
        fire.setFlammable(BlocksTC.saplingGreatwood, 30, 60);
        fire.setFlammable(BlocksTC.saplingSilverwood, 30, 60);
        fire.setFlammable(BlocksTC.plankGreatwood, 5, 20);
        fire.setFlammable(BlocksTC.plankSilverwood, 5, 20);
        fire.setFlammable(BlocksTC.stairsGreatwood, 5, 20);
        fire.setFlammable(BlocksTC.stairsSilverwood, 5, 20);
        fire.setFlammable(BlocksTC.slabGreatwood, 5, 20);
        fire.setFlammable(BlocksTC.slabSilverwood, 5, 20);
        // Original thirteen IDs, expanded only where flattening split old metadata variants.
        var blacklist = thaumcraft.common.items.casters.FocusBlockActions.portableHoleBlackList;
        for (DyeColor color : DyeColor.values()) {
            String id = "minecraft:" + color.getName() + "_bed";
            if (!blacklist.contains(id)) blacklist.add(id);
        }
        for (String id : List.of("minecraft:piston", "minecraft:piston_head", "minecraft:sticky_piston",
                "minecraft:moving_piston", "minecraft:oak_door", "minecraft:spruce_door", "minecraft:birch_door",
                "minecraft:jungle_door", "minecraft:acacia_door", "minecraft:dark_oak_door", "thaumcraft:infernal_furnace")) {
            if (!blacklist.contains(id)) blacklist.add(id);
        }
    }

    public static void initBlocks(RegisterEvent.RegisterHelper<Block> iForgeRegistry) {
        registry = iForgeRegistry;
        BlocksTC.oreAmber = register("ore_amber", new thaumcraft.common.blocks.world.ore.BlockOreAmber(ore(1.5F)));
        BlocksTC.oreCinnabar = register("ore_cinnabar", new DropExperienceBlock(ConstantInt.of(0), ore(2.0F)));
        BlocksTC.oreQuartz = register("ore_quartz", new DropExperienceBlock(UniformInt.of(1, 4), ore(3.0F)));
        BlocksTC.crystalAir = crystal("crystal_aer", Aspect.AIR);
        BlocksTC.crystalFire = crystal("crystal_ignis", Aspect.FIRE);
        BlocksTC.crystalWater = crystal("crystal_aqua", Aspect.WATER);
        BlocksTC.crystalEarth = crystal("crystal_terra", Aspect.EARTH);
        BlocksTC.crystalOrder = crystal("crystal_ordo", Aspect.ORDER);
        BlocksTC.crystalEntropy = crystal("crystal_perditio", Aspect.ENTROPY);
        BlocksTC.crystalTaint = crystal("crystal_vitium", Aspect.FLUX);
        ShardType.AIR.setOre(BlocksTC.crystalAir);
        ShardType.FIRE.setOre(BlocksTC.crystalFire);
        ShardType.WATER.setOre(BlocksTC.crystalWater);
        ShardType.EARTH.setOre(BlocksTC.crystalEarth);
        ShardType.ORDER.setOre(BlocksTC.crystalOrder);
        ShardType.ENTROPY.setOre(BlocksTC.crystalEntropy);
        ShardType.FLUX.setOre(BlocksTC.crystalTaint);
        BlocksTC.stoneArcane = register("stone_arcane", new Block(stone(2.0F, 6.0F)));
        BlocksTC.stoneArcaneBrick = register("stone_arcane_brick", new Block(stone(2.0F, 6.0F)));
        BlocksTC.stoneAncient = register("stone_ancient", new Block(stone(2.0F, 6.0F)));
        BlocksTC.stoneAncientTile = register("stone_ancient_tile", new Block(stone(2.0F, 6.0F)));
        // setHardness(-1): unbreakable, but TC6's blast resistance stayed normal (6)
        BlocksTC.stoneAncientRock = register("stone_ancient_rock", new Block(stone(-1.0F, 6.0F)));
        BlocksTC.stoneAncientGlyphed = register("stone_ancient_glyphed", new Block(stone(2.0F, 6.0F)));
        BlocksTC.stoneAncientDoorway = register("stone_ancient_doorway", new Block(stone(-1.0F, 6.0F)));
        BlocksTC.stoneEldritchTile = register("stone_eldritch_tile", new Block(stone(15.0F, 600.0F)));
        BlocksTC.stonePorous = register("stone_porous", new Block(stone(1.0F, 3.0F)));
        BlocksTC.stairsArcane = register("stairs_arcane", stairs(BlocksTC.stoneArcane));
        BlocksTC.stairsArcaneBrick = register("stairs_arcane_brick", stairs(BlocksTC.stoneArcaneBrick));
        BlocksTC.stairsAncient = register("stairs_ancient", stairs(BlocksTC.stoneAncient));
        // 1.12 double slabs (slab_double_*) are the type=double state of the same SlabBlock in 1.21
        BlocksTC.slabArcaneStone = register("slab_arcane_stone", new SlabBlock(stone(2.0F, 6.0F)));
        BlocksTC.slabArcaneBrick = register("slab_arcane_brick", new SlabBlock(stone(2.0F, 6.0F)));
        BlocksTC.slabAncient = register("slab_ancient", new SlabBlock(stone(2.0F, 6.0F)));
        BlocksTC.slabEldritch = register("slab_eldritch", new SlabBlock(stone(2.0F, 6.0F)));
        BlocksTC.saplingGreatwood = register("sapling_greatwood",
            new BlockSaplingTC(BlockSaplingTC.GREATWOOD_GROWER, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING)));
        BlocksTC.saplingSilverwood = register("sapling_silverwood",
            new BlockSaplingTC(BlockSaplingTC.SILVERWOOD_GROWER, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING)));
        BlocksTC.logGreatwood = register("log_greatwood", new RotatedPillarBlock(log(MapColor.WOOD, 0)));
        BlocksTC.logSilverwood = register("log_silverwood", new RotatedPillarBlock(log(MapColor.QUARTZ, 5)));
        BlocksTC.leafGreatwood = register("leaves_greatwood", new BlockLeavesTC(false, leaves(MapColor.PLANT)));
        BlocksTC.leafSilverwood = register("leaves_silverwood", new BlockLeavesTC(true, leaves(MapColor.COLOR_LIGHT_BLUE)));
        BlocksTC.shimmerleaf = register("shimmerleaf", new BlockPlantShimmerleaf(plant(6).offsetType(BlockBehaviour.OffsetType.XZ)));
        BlocksTC.cinderpearl = register("cinderpearl", new BlockPlantCinderpearl(plant(7).offsetType(BlockBehaviour.OffsetType.XZ)));
        BlocksTC.vishroom = register("vishroom", new BlockPlantVishroom(plant(6)));
        BlocksTC.plankGreatwood = register("plank_greatwood", new Block(planks()));
        BlocksTC.plankSilverwood = register("plank_silverwood", new Block(planks()));
        BlocksTC.stairsGreatwood = register("stairs_greatwood", stairs(BlocksTC.plankGreatwood));
        BlocksTC.stairsSilverwood = register("stairs_silverwood", stairs(BlocksTC.plankSilverwood));
        BlocksTC.slabGreatwood = register("slab_greatwood", new SlabBlock(planks().strength(1.2F, 1.2F)));
        BlocksTC.slabSilverwood = register("slab_silverwood", new SlabBlock(planks().strength(1.0F, 1.2F)));
        // Source public double-slab fields refer to the native TYPE=DOUBLE block family.
        BlocksTC.doubleSlabArcaneStone = BlocksTC.slabArcaneStone;
        BlocksTC.doubleSlabArcaneBrick = BlocksTC.slabArcaneBrick;
        BlocksTC.doubleSlabAncient = BlocksTC.slabAncient;
        BlocksTC.doubleSlabEldritch = BlocksTC.slabEldritch;
        BlocksTC.doubleSlabGreatwood = BlocksTC.slabGreatwood;
        BlocksTC.doubleSlabSilverwood = BlocksTC.slabSilverwood;
        BlocksTC.amberBlock = register("amber_block", new HalfTransparentBlock(translucent()));
        BlocksTC.amberBrick = register("amber_brick", new HalfTransparentBlock(translucent()));
        BlocksTC.fleshBlock = register("flesh_block", new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PINK).strength(0.25F, 1.2F).sound(SoundsTC.GORE)));
        BlocksTC.lootCrateCommon = register("loot_crate_common", new BlockLootTC(BlockBehaviour.Properties.of(), true, LegacyLootRules.Rarity.COMMON));
        BlocksTC.lootCrateUncommon = register("loot_crate_uncommon", new BlockLootTC(BlockBehaviour.Properties.of(), true, LegacyLootRules.Rarity.UNCOMMON));
        BlocksTC.lootCrateRare = register("loot_crate_rare", new BlockLootTC(BlockBehaviour.Properties.of(), true, LegacyLootRules.Rarity.RARE));
        BlocksTC.lootUrnCommon = register("loot_urn_common", new BlockLootTC(BlockBehaviour.Properties.of(), false, LegacyLootRules.Rarity.COMMON));
        BlocksTC.lootUrnUncommon = register("loot_urn_uncommon", new BlockLootTC(BlockBehaviour.Properties.of(), false, LegacyLootRules.Rarity.UNCOMMON));
        BlocksTC.lootUrnRare = register("loot_urn_rare", new BlockLootTC(BlockBehaviour.Properties.of(), false, LegacyLootRules.Rarity.RARE));
        // Taint blocks are registered by the stage-10 block registry below.
        BlocksTC.grassAmbient = register("grass_ambient", new BlockGrassAmbient(BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK)));
        BlocksTC.tableWood = register("table_wood", new BlockTable(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava().noOcclusion()));
        BlocksTC.tableStone = register("table_stone", new BlockTable(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).strength(2.5F).sound(SoundType.STONE).noOcclusion()));
        // BlockTCTile: hardness 2, setResistance(20) -> actual old resistance 12.
        BlocksTC.researchTable = register("research_table", new BlockResearchTable(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F, 12.0F)
            .sound(SoundType.WOOD).ignitedByLava().noOcclusion()));
        BlocksTC.arcaneWorkbench = register("arcane_workbench", new thaumcraft.common.blocks.crafting.BlockArcaneWorkbench(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F, 12.0F)
            .sound(SoundType.WOOD).ignitedByLava().noOcclusion()));
        BlocksTC.crucible = register("crucible", new thaumcraft.common.blocks.crafting.BlockCrucible(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(2.0F, 12.0F).sound(SoundType.METAL).noOcclusion()));
        BlocksTC.infusionMatrix = register("infusion_matrix", new thaumcraft.common.blocks.crafting.BlockInfusionMatrix(stone(2F,12F).noOcclusion()));
        BlocksTC.pedestalArcane = register("pedestal_arcane", new thaumcraft.common.blocks.devices.BlockPedestal(stone(2F,12F).noOcclusion()));
        BlocksTC.pedestalAncient = register("pedestal_ancient", new thaumcraft.common.blocks.devices.BlockPedestal(stone(2F,12F).noOcclusion()));
        BlocksTC.pedestalEldritch = register("pedestal_eldritch", new thaumcraft.common.blocks.devices.BlockPedestal(stone(2F,12F).noOcclusion()));
        BlocksTC.stabilizer = register("stabilizer", new thaumcraft.common.blocks.devices.BlockStabilizer(stone(2F,12F).noOcclusion().lightLevel(state -> 4)));
        BlocksTC.effectGlimmer = new thaumcraft.common.blocks.misc.BlockEffect(BlockBehaviour.Properties.of().strength(1.5F,199.8F).noCollission().noOcclusion().replaceable().lightLevel(state -> 15));
        registry.register(ResourceLocation.parse("thaumcraft:effect_glimmer"),BlocksTC.effectGlimmer);
        BlocksTC.inlay = register("inlay", new thaumcraft.common.blocks.devices.BlockInlay(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(.5F,1.5F).sound(SoundType.METAL).noCollission().noOcclusion().lightLevel(state -> 1)));
        BlocksTC.arcaneWorkbenchCharger = register("arcane_workbench_charger", new thaumcraft.common.blocks.crafting.BlockArcaneWorkbenchCharger(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(1.25F,6F).sound(SoundType.WOOD).noOcclusion()));
        BlocksTC.jarNormal = register("jar_normal", new thaumcraft.common.blocks.essentia.BlockJar(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(.3F,12F).sound(SoundsTC.JAR).noOcclusion()));
        BlocksTC.jarVoid = register("jar_void", new thaumcraft.common.blocks.essentia.BlockJar(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(.3F,12F).sound(SoundsTC.JAR).noOcclusion()));
        BlocksTC.alembic = register("alembic", new thaumcraft.common.blocks.essentia.BlockAlembic(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2F,12F).sound(SoundType.WOOD).noOcclusion()));
        BlocksTC.smelterBasic = register("smelter_basic", smelter());
        BlocksTC.smelterThaumium = register("smelter_thaumium", smelter());
        BlocksTC.smelterVoid = register("smelter_void", smelter());
        BlocksTC.metalBlockBrass = register("metal_brass", new Block(metal(MapColor.GOLD)));
        BlocksTC.metalBlockThaumium = register("metal_thaumium", new Block(metal(MapColor.COLOR_PURPLE)));
        BlocksTC.metalBlockVoid = register("metal_void", new Block(metal(MapColor.COLOR_BLACK)));
        BlocksTC.metalAlchemical = register("metal_alchemical", new Block(metal(MapColor.METAL)));
        BlocksTC.metalAlchemicalAdvanced = register("metal_alchemical_advanced", new Block(metal(MapColor.METAL)));
        BlocksTC.pavingStoneTravel = register("paving_stone_travel", new BlockPavingStone(true, paving()));
        BlocksTC.pavingStoneBarrier = register("paving_stone_barrier", new thaumcraft.common.blocks.basic.BlockBarrierPavingStone(paving()));
        BlocksTC.barrier = new thaumcraft.common.blocks.misc.BlockBarrier(BlockBehaviour.Properties.of()
            .replaceable().noOcclusion().noLootTable().isViewBlocking((s,l,p) -> false)
            .isSuffocating((s,l,p) -> false).isRedstoneConductor((s,l,p) -> false));
        registry.register(ResourceLocation.parse("thaumcraft:barrier"), BlocksTC.barrier);
        BlocksTC.pillarArcane = register("pillar_arcane", new BlockPillar(pillar()));
        BlocksTC.pillarAncient = register("pillar_ancient", new BlockPillar(pillar()));
        BlocksTC.pillarEldritch = register("pillar_eldritch", new BlockPillar(pillar()));
        BlocksTC.matrixSpeed = register("matrix_speed", new Block(stone(2.0F, 6.0F)));
        BlocksTC.matrixCost = register("matrix_cost", new Block(stone(2.0F, 6.0F)));
        for (DyeColor dye : DyeColor.values()) {
            BlocksTC.nitor.put(dye, register("nitor_" + dye.getName(), new thaumcraft.common.blocks.misc.BlockNitor(dye, BlockBehaviour.Properties.of()
                .mapColor(dye.getMapColor()).strength(.1F, 1.5F).sound(SoundType.WOOL).lightLevel(state -> 15)
                .noCollission().noOcclusion().pushReaction(PushReaction.DESTROY))));
            BlocksTC.candles.put(dye, register("candle_" + dye.getName(), new BlockCandle(dye, BlockBehaviour.Properties.of()
                .mapColor(dye.getMapColor())
                .strength(0.1F, 1.5F)
                .sound(SoundType.WOOL)
                .lightLevel(st -> 14)
                .noCollission()
                .pushReaction(PushReaction.DESTROY))));
        }
        // Real banner and golem multiblock registrations are supplied by their owners below.
        BlocksTC.empty = register("empty", new HalfTransparentBlock(translucent()));
        thaumcraft.common.tiles.essentia.TransportRegistries.createBlocks().forEach(ConfigBlocks::register);
        thaumcraft.common.tiles.devices.ProcessingRegistries.createBlocks().forEach(ConfigBlocks::register);
        thaumcraft.common.tiles.crafting.FocalRegistries.createBlocks().forEach(ConfigBlocks::register);
        thaumcraft.common.items.casters.CasterRegistries.createBlocks().forEach(ConfigBlocks::register);
        thaumcraft.common.tiles.devices.DeviceRegistries.createBlocks().forEach(ConfigBlocks::register);
        thaumcraft.common.tiles.devices.MirrorRegistries.createBlocks().forEach(ConfigBlocks::register);
        thaumcraft.common.blocks.basic.BannerRegistries.createBlocks().forEach(ConfigBlocks::register);
        thaumcraft.common.blocks.world.taint.Taint8Registries.createBlocks().forEach(ConfigBlocks::register);
        thaumcraft.common.tiles.crafting.Golem9BuilderRegistries.createBlocks().forEach(ConfigBlocks::register);
        thaumcraft.common.tiles.crafting.Phase10RiftRegistries.createBlocks().forEach(ConfigBlocks::register);
        // TC6's misleadingly named activator_rail is constructed as BlockRailPowered.
        BlocksTC.activatorRail=register("activator_rail",new net.minecraft.world.level.block.PoweredRailBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POWERED_RAIL),true));
    }

    public static void initBlockItems(RegisterEvent.RegisterHelper<Item> iForgeRegistry) {
        for (Block block : BLOCKS_WITH_ITEMS) {
            if (!thaumcraft.common.items.casters.CasterRegistries.hasBlockItem(block)) continue;
            Item item = thaumcraft.common.tiles.devices.DeviceRegistries.createItem(block);
            if(item==null)item = block instanceof thaumcraft.common.blocks.basic.BlockBannerTC banner
                ? new thaumcraft.common.blocks.basic.BlockBannerTCItem(banner)
                : block instanceof thaumcraft.common.blocks.devices.BlockMirror
                ? new thaumcraft.common.blocks.devices.BlockMirrorItem(block,new Item.Properties())
                : block instanceof thaumcraft.common.blocks.essentia.BlockJar
                ? new thaumcraft.common.blocks.essentia.BlockJarItem(block, new Item.Properties()) : new BlockItem(block, new Item.Properties());
            iForgeRegistry.register(block.builtInRegistryHolder().key().location(), item);
        }
    }

    private static <T extends Block> T register(String id, T block) {
        registry.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, id), block);
        BLOCKS_WITH_ITEMS.add(block);
        return block;
    }
    private static Block smelter() {
        return new thaumcraft.common.blocks.essentia.BlockSmelter(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
            .strength(2F,12F).sound(SoundType.METAL).lightLevel(state -> state.getValue(thaumcraft.common.blocks.essentia.BlockSmelter.ENABLED) ? 13 : 0));
    }

    private static Block crystal(String id, Aspect aspect) {
        BlockCrystal block = register(id, new BlockCrystal(aspect, BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE)
            .strength(0.25F)
            .sound(SoundsTC.CRYSTAL)
            .lightLevel(s -> 1)
            .noOcclusion()
            .noCollission()
            .randomTicks()
            .pushReaction(PushReaction.DESTROY)
            .isViewBlocking((s, l, p) -> false)
            .isSuffocating((s, l, p) -> false)));
        CRYSTALS.add(block);
        return block;
    }

    private static BlockBehaviour.Properties stone(float hardness, float resistance) {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .strength(hardness, resistance)
            .sound(SoundType.STONE);
    }

    private static Block stairs(Block base) {
        return new StairBlock(base.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(base));
    }

    private static BlockBehaviour.Properties translucent() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .strength(0.5F, 1.5F)
            .sound(SoundType.STONE)
            .noOcclusion()
            .isValidSpawn((st, l, p, e) -> false)
            .isRedstoneConductor((st, l, p) -> false)
            .isSuffocating((st, l, p) -> false)
            .isViewBlocking((st, l, p) -> false);
    }

    private static BlockBehaviour.Properties metal(MapColor color) {
        return BlockBehaviour.Properties.of()
            .mapColor(color)
            .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
            .requiresCorrectToolForDrops()
            .strength(4.0F, 6.0F)
            .sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties paving() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .strength(2.5F)
            .sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties pillar() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .strength(2.5F)
            .sound(SoundType.STONE)
            .noOcclusion()
            .pushReaction(PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties ore(float hardness) {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .strength(hardness, 3.0F)
            .sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties log(MapColor color, int light) {
        return BlockBehaviour.Properties.of()
            .mapColor(color)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F, 3.0F)
            .sound(SoundType.WOOD)
            .lightLevel(s -> light)
            .ignitedByLava();
    }

    private static BlockBehaviour.Properties leaves(MapColor color) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES).mapColor(color);
    }

    private static BlockBehaviour.Properties plant(int light) {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollission()
            .instabreak()
            .sound(SoundType.GRASS)
            .lightLevel(s -> light)
            .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties planks() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F, 2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava();
    }
}
