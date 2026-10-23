package thaumcraft.datagen;

import thaumcraft.api.aspects.Aspect;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.block.Blocks;
import java.util.Set;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.DynamicLoot;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.blocks.world.ore.BlockCrystal;
import thaumcraft.common.blocks.world.BlockLootTC;
import thaumcraft.common.config.ConfigBlocks;

public class TCBlockLoot extends BlockLootSubProvider {
    /**
     * TC6 sapling chance from leaves: 1/(75 - (2 << fortune)), at least 1/10 (BlockLeaves 1.12).
     * Quicksilver nugget chance from silverwood: 1/(0.75 * (200 - (10 << fortune))), at least 1/30.
     */
    private static final float[] SAPLING_CHANCES = {1 / 75F, 1 / 71F, 1 / 67F, 1 / 59F};
    private static final float[] QUICKSILVER_CHANCES = {1 / 150F, 1 / 135F, 1 / 120F, 1 / 90F};

    public TCBlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    private Holder<Enchantment> fortune() {
        return this.registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
    }

    @Override
    protected void generate() {
        // Gameplay getDrops overrides preserve fibre RNG/aura and finite goo has no ItemBlock.
        add(BlocksTC.fluxGoo, LootTable.lootTable());
        // LiquidDeath uses noLootTable(): do not add BuiltInLootTables.EMPTY to this provider.
        add(BlocksTC.taintFibre, LootTable.lootTable());
        // Stage8 blocks implement exact runtime silk/RNG/BE-aware getDrops; no duplicate JSON reward.
        for(Block block:new Block[]{BlocksTC.bannerCrimsonCult,BlocksTC.taintCrust,BlocksTC.taintSoil,
            BlocksTC.taintRock,BlocksTC.taintGeyser,BlocksTC.taintLog,BlocksTC.taintFeature})add(block,LootTable.lootTable());
        for(Block banner:BlocksTC.banners.values())add(banner,LootTable.lootTable()); // Runtime BE-aware drops, never duplicate.
        // Actual BlockOreAmber applies source 6.6% whole-stack curio replacement after this loot.
        add(BlocksTC.oreAmber, b -> createSilkTouchDispatchTable(b, applyExplosionDecay(b,
            LootItem.lootTableItem(ItemsTC.amber)
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
                .apply(ApplyBonusCount.addOreBonusCount(fortune())))));
        dropSelf(BlocksTC.oreCinnabar);
        add(BlocksTC.oreQuartz, b -> createOreDrop(b, Items.QUARTZ));

        for (BlockCrystal crystal : ConfigBlocks.CRYSTALS) {
            add(crystal, crystalDrops(crystal));
        }

        dropSelf(BlocksTC.logGreatwood);
        dropSelf(BlocksTC.logSilverwood);
        dropSelf(BlocksTC.plankGreatwood);
        dropSelf(BlocksTC.plankSilverwood);
        dropSelf(BlocksTC.saplingGreatwood);
        dropSelf(BlocksTC.saplingSilverwood);
        dropSelf(BlocksTC.shimmerleaf);
        dropSelf(BlocksTC.cinderpearl);
        dropSelf(BlocksTC.vishroom);
        add(BlocksTC.leafGreatwood, leaves(BlocksTC.leafGreatwood, BlocksTC.saplingGreatwood));
        add(BlocksTC.leafSilverwood, leaves(BlocksTC.leafSilverwood, BlocksTC.saplingSilverwood)
            .withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .when(noShearsOrSilk())
                .add(((LootPoolSingletonContainer.Builder<?>) applyExplosionCondition(BlocksTC.leafSilverwood,
                    LootItem.lootTableItem(ItemsTC.nuggetQuicksilver)))
                    .when(BonusLevelTableCondition.bonusLevelFlatChance(fortune(), QUICKSILVER_CHANCES)))));

        // Stage 2c: decoration
        for (Block b : new Block[]{BlocksTC.stoneArcane, BlocksTC.stoneArcaneBrick, BlocksTC.stoneAncient, BlocksTC.stoneAncientTile,
            BlocksTC.stoneAncientRock, BlocksTC.stoneAncientGlyphed, BlocksTC.stoneAncientDoorway, BlocksTC.stoneEldritchTile,
            BlocksTC.stairsArcane, BlocksTC.stairsArcaneBrick, BlocksTC.stairsAncient, BlocksTC.stairsGreatwood, BlocksTC.stairsSilverwood,
            BlocksTC.amberBlock, BlocksTC.amberBrick, BlocksTC.fleshBlock, BlocksTC.tableWood, BlocksTC.tableStone, BlocksTC.researchTable, BlocksTC.arcaneWorkbench,
            BlocksTC.metalBlockBrass, BlocksTC.metalBlockThaumium, BlocksTC.metalBlockVoid, BlocksTC.metalAlchemical,
            BlocksTC.metalAlchemicalAdvanced, BlocksTC.pavingStoneTravel, BlocksTC.pavingStoneBarrier, BlocksTC.matrixSpeed,
            BlocksTC.matrixCost, BlocksTC.empty}) {
            dropSelf(b);
        }
        for (Block b : BlocksTC.candles.values()) {
            dropSelf(b);
        }
        dropSelf(BlocksTC.crucible);
        dropSelf(BlocksTC.wandWorkbench);
        dropSelf(BlocksTC.voidSiphon);
        for (Block block : new Block[]{BlocksTC.pedestalArcane,BlocksTC.pedestalAncient,BlocksTC.pedestalEldritch,BlocksTC.infusionMatrix,BlocksTC.stabilizer,BlocksTC.inlay,BlocksTC.arcaneWorkbenchCharger,BlocksTC.alembic,BlocksTC.smelterBasic,BlocksTC.smelterThaumium,BlocksTC.smelterVoid}) dropSelf(block);
        add(BlocksTC.jarNormal,LootTable.lootTable()); // Actual BlockJar.getDrops preserves aspects/filter/brace.
        add(BlocksTC.jarVoid,LootTable.lootTable());
        add(BlocksTC.effectGlimmer,LootTable.lootTable());
        // Stage 6: source ordinary self-drops; mirrors/brain jar decorate these with persisted data.
        for (Block block : new Block[]{BlocksTC.tube,BlocksTC.tubeValve,BlocksTC.tubeRestrict,BlocksTC.tubeOneway,BlocksTC.tubeFilter,BlocksTC.tubeBuffer,BlocksTC.centrifuge,BlocksTC.essentiaTransportInput,BlocksTC.essentiaTransportOutput,
            BlocksTC.bellows,BlocksTC.smelterAux,BlocksTC.smelterVent,BlocksTC.brainBox,
            BlocksTC.mirror,BlocksTC.mirrorEssentia,BlocksTC.activatorRail,
            BlocksTC.visGenerator,BlocksTC.visBattery,BlocksTC.rechargePedestal,BlocksTC.levitator,
            BlocksTC.lampArcane,BlocksTC.lampGrowth,BlocksTC.lampFertility,BlocksTC.everfullUrn,BlocksTC.hungryChest,BlocksTC.jarBrain,
            BlocksTC.arcaneEar,BlocksTC.arcaneEarToggle,BlocksTC.redstoneRelay,BlocksTC.dioptra,BlocksTC.spa,BlocksTC.potionSprayer,
            BlocksTC.patternCrafter,BlocksTC.condenser,BlocksTC.condenserlattice,BlocksTC.condenserlatticeDirty}) dropSelf(block);
        add(BlocksTC.infernalFurnace,LootTable.lootTable());
        add(BlocksTC.placeholderNetherbrick,createSingleItemTable(Blocks.NETHER_BRICKS));
        add(BlocksTC.placeholderObsidian,createSingleItemTable(Blocks.OBSIDIAN));
        add(BlocksTC.thaumatorium,createSingleItemTable(BlocksTC.metalAlchemical));
        add(BlocksTC.thaumatoriumTop,createSingleItemTable(BlocksTC.metalAlchemical));
        add(BlocksTC.golemBuilder,createSingleItemTable(Blocks.PISTON));
        add(BlocksTC.placeholderBars,createSingleItemTable(Blocks.IRON_BARS));
        add(BlocksTC.placeholderCauldron,createSingleItemTable(Blocks.CAULDRON));
        add(BlocksTC.placeholderAnvil,createSingleItemTable(Blocks.ANVIL));
        add(BlocksTC.placeholderTable,createSingleItemTable(BlocksTC.tableStone));
        for (Block b : BlocksTC.nitor.values()) dropSelf(b);
        for (Block b : new Block[]{BlocksTC.slabArcaneStone, BlocksTC.slabArcaneBrick, BlocksTC.slabAncient, BlocksTC.slabEldritch,
            BlocksTC.slabGreatwood, BlocksTC.slabSilverwood}) {
            add(b, this::createSlabItemTable);
        }
        // BlockGrass 1.12: dirt without silk touch
        add(BlocksTC.grassAmbient, b -> createSingleItemTableWithSilkTouch(b, Blocks.DIRT));
        // BlockPillar.breakBlock: 2 blocks of the source stone, the pillar itself does not drop
        add(BlocksTC.pillarArcane, b -> createSingleItemTable(BlocksTC.stoneArcane, ConstantValue.exactly(2)));
        add(BlocksTC.pillarAncient, b -> createSingleItemTable(BlocksTC.stoneAncient, ConstantValue.exactly(2)));
        add(BlocksTC.pillarEldritch, b -> createSingleItemTable(BlocksTC.stoneEldritchTile, ConstantValue.exactly(2)));
        add(BlocksTC.stonePorous, porousStone());
        for (Block b : new Block[]{BlocksTC.lootCrateCommon, BlocksTC.lootCrateUncommon, BlocksTC.lootCrateRare,
            BlocksTC.lootUrnCommon, BlocksTC.lootUrnUncommon, BlocksTC.lootUrnRare}) {
            add(b, block -> createSilkTouchDispatchTable(block, DynamicLoot.dynamicEntry(BlockLootTC.CONTENTS_ID)));
        }
    }

    /**
     * BlockStonePorous.getDrops: rand(15) + fortune > 13 -> a random find (WeightedRandomLoot), otherwise gravel.
     * Silk touch gives the block itself (in 1.12 this worked by default for full blocks).
     */
    private LootTable.Builder porousStone() {
        LootPool.Builder treasure = LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F));
        for (Aspect aspect : Aspect.getCompoundAspects()) {
            int amount = aspect == Aspect.FLUX ? 100 : (aspect.isPrimal() ? 20 : 1);
            treasure.add(LootItem.lootTableItem(ItemsTC.crystalEssence).setWeight(1)
                .apply(SetComponentsFunction.setComponent(TCDataComponents.ASPECTS, new AspectList().add(aspect, amount))));
        }
        treasure.add(LootItem.lootTableItem(ItemsTC.amber).setWeight(20));
        treasure.add(LootItem.lootTableItem(ItemsTC.clusterIron).setWeight(20));
        treasure.add(LootItem.lootTableItem(ItemsTC.clusterGold).setWeight(10));
        treasure.add(LootItem.lootTableItem(ItemsTC.clusterCinnabar).setWeight(10));
        net.minecraft.world.item.Item[] optionalClusters = {ItemsTC.clusterCopper, ItemsTC.clusterTin, ItemsTC.clusterSilver, ItemsTC.clusterLead};
        String[] metals = {"copper", "tin", "silver", "lead"};
        for (int i = 0; i < metals.length; i++) {
            var tag = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "ingots/" + metals[i]);
            treasure.add(LootItem.lootTableItem(optionalClusters[i]).setWeight(i == 2 ? 8 : 10)
                .when(() -> new thaumcraft.common.lib.crafting.TaggedMetalRecipes.MaterialAvailable(tag)));
        }
        treasure.add(LootItem.lootTableItem(Items.DIAMOND).setWeight(2));
        treasure.add(LootItem.lootTableItem(Items.EMERALD).setWeight(4));
        treasure.add(LootItem.lootTableItem(Items.REDSTONE).setWeight(8));
        treasure.add(LootItem.lootTableItem(Items.PRISMARINE_CRYSTALS).setWeight(3));
        treasure.add(LootItem.lootTableItem(Items.PRISMARINE_SHARD).setWeight(3));
        treasure.add(LootItem.lootTableItem(Items.CLAY_BALL).setWeight(30));
        treasure.add(LootItem.lootTableItem(Items.QUARTZ).setWeight(15));

        return LootTable.lootTable().withPool(LootPool.lootPool()
            .setRolls(ConstantValue.exactly(1.0F))
            .add(AlternativesEntry.alternatives(
                LootItem.lootTableItem(BlocksTC.stonePorous).when(hasSilkTouch()),
                NestedLootTable.inlineLootTable(LootTable.lootTable().withPool(treasure).build())
                    .when(BonusLevelTableCondition.bonusLevelFlatChance(fortune(), 1 / 15F, 2 / 15F, 3 / 15F, 4 / 15F)),
                LootItem.lootTableItem(Blocks.GRAVEL))));
    }

    /** Like createLeavesDrops, but without sticks: in 1.12 TC leaves did not drop them. */
    private LootTable.Builder leaves(Block leaves, Block sapling) {
        return createSilkTouchOrShearsDispatchTable(leaves,
            ((LootPoolSingletonContainer.Builder<?>) applyExplosionCondition(leaves, LootItem.lootTableItem(sapling)))
                .when(BonusLevelTableCondition.bonusLevelFlatChance(fortune(), SAPLING_CHANCES)));
    }

    private LootItemCondition.Builder noShearsOrSilk() {
        return HAS_SHEARS.or(hasSilkTouch()).invert();
    }

    /** BlockCrystal.getDrops: size+1 essence crystals of its aspect, silk touch does not work. */
    private LootTable.Builder crystalDrops(BlockCrystal crystal) {
        LootPoolSingletonContainer.Builder<?> entry = LootItem.lootTableItem(ItemsTC.crystalEssence)
            .apply(SetComponentsFunction.setComponent(TCDataComponents.ASPECTS, new AspectList().add(crystal.aspect, 1)));
        for (int size = 0; size < 4; size++) {
            entry.apply(SetItemCountFunction.setCount(ConstantValue.exactly(size + 1))
                .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(crystal)
                    .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(BlockCrystal.SIZE, size))));
        }
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(entry));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return BuiltInRegistries.BLOCK.entrySet().stream()
            .filter(e -> e.getKey().location().getNamespace().equals(Thaumcraft.MODID))
            .map(e -> (Block) e.getValue())
            .toList();
    }
}
