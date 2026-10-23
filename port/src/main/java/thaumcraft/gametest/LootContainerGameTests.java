package thaumcraft.gametest;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.world.BlockLootTC;
import thaumcraft.common.lib.utils.LegacyLootRules;
import thaumcraft.common.lib.utils.LegacyLootRules.Rarity;
import thaumcraft.common.lib.utils.LegacyLootRules.Reward;
import thaumcraft.common.lib.utils.TCLegacyLoot;

/** The full TC6 reward set must be registered; no partial loot tables are accepted by these fixtures. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class LootContainerGameTests {
    @GameTest(template = "empty")
    public static void allLegacyRewardsResolveWithoutSubstitution(GameTestHelper helper) {
        TCLegacyLoot loot = TCLegacyLoot.create(helper.getLevel().registryAccess());
        for (Rarity rarity : Rarity.values()) {
            for (LegacyLootRules.Entry entry : LegacyLootRules.pool(rarity)) {
                Reward reward = entry.reward();
                ItemStack stack = loot.materialize(reward);
                helper.assertTrue(!stack.isEmpty(), "Missing source reward " + reward);
                helper.assertTrue(stack.getCount() == reward.count(), "Reward count " + reward);
                helper.assertTrue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(TCLegacyLoot.modernItemId(reward)),
                    "Legacy item mapping " + reward);
                if (reward.potionType() != null) {
                    PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
                    helper.assertTrue(contents != null, "Potion components " + reward);
                    if (reward.potionType().equals("minecraft:empty")) {
                        helper.assertTrue(contents.potion().isEmpty() && contents.customEffects().isEmpty(), "Uncraftable empty potion retained");
                    } else {
                        helper.assertTrue(contents.potion().orElseThrow().unwrapKey().orElseThrow().location().toString().equals(reward.potionType()),
                            "Old potion type " + reward);
                    }
                }
            }
        }
        ItemStack pearl = loot.materialize(new Reward("thaumcraft:primordial_pearl", 1, 7, null));
        helper.assertTrue(pearl.is(ItemsTC.primordialPearl) && pearl.getDamageValue() == 7, "Pearl mote charge seven");
        helper.assertTrue(loot.materialize(new Reward("minecraft:golden_apple", 1, 1, null)).is(Items.ENCHANTED_GOLDEN_APPLE),
            "Enchanted golden apple is an item variant, not damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cratesAndUrnsRetainShapeStrengthAndHandHarvest(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (BlockLootTC block : containers()) {
            AABB expected = block.isCrate() ? new AABB(1 / 16D, 0, 1 / 16D, 15 / 16D, 14 / 16D, 15 / 16D)
                : new AABB(2 / 16D, 1 / 16D, 2 / 16D, 14 / 16D, 13 / 16D, 14 / 16D);
            helper.assertTrue(block.defaultBlockState().getShape(helper.getLevel(), pos).bounds().equals(expected), "Source container shape");
            helper.assertTrue(block.defaultBlockState().getDestroySpeed(helper.getLevel(), pos) == 0.15F, "Source hardness .15");
            helper.assertFalse(block.defaultBlockState().requiresCorrectToolForDrops(), "Original canHarvestBlock is always true");
            helper.assertFalse(block.defaultBlockState().canOcclude(), "Original isOpaqueCube/isFullCube false");
            helper.assertTrue(block.defaultBlockState().getOcclusionShape(helper.getLevel(), pos).isEmpty(), "No full opaque cube");
            helper.assertFalse(block.defaultBlockState().hasBlockEntity(), "Loot blocks are not containers with block entities");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void silkUsesStandardLootTablesAndNeverInvokesContentsRandom(GameTestHelper helper) {
        ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
        silk.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (BlockLootTC block : containers()) {
            List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), helper.getLevel(), pos, null, null, silk);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount() == 1, "Silk returns own loot block");
        }
        // The test-only block is registered before holder freeze, and absent
        // from normal/datagen/production runs. It reuses the actual crate table.
        helper.assertTrue(LootTestProbe.block != null && BuiltInRegistries.BLOCK.containsKey(LootTestProbe.ID),
            "Dedicated probe requires thaumcraft.gearTestProbe=true");
        LootTestProbe.ProbeLootBlock sentinel = LootTestProbe.block;
        sentinel.contentsCalls = 0;
        List<ItemStack> sentinelDrops = Block.getDrops(sentinel.defaultBlockState(), helper.getLevel(), pos, null, null, silk);
        helper.assertTrue(sentinelDrops.size() == 1 && sentinelDrops.getFirst().is(BlocksTC.lootCrateCommon.asItem()), "Sentinel silk path uses only table dispatch");
        helper.assertTrue(sentinel.contentsCalls == 0, "Silk does not invoke contents or consume block-owned RNG");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eachRarityRetainsExactContainerCountAndIndependentRewardCopies(GameTestHelper helper) {
        TCLegacyLoot loot = TCLegacyLoot.create(helper.getLevel().registryAccess());
        for (BlockLootTC block : containers()) {
            Rarity rarity = block.rarity();
            int count = 3 + rarity.index();
            TraceRandom random = new TraceRandom().integer(3, 2);
            for (int draw = 0; draw < count; draw++) {
                if (rarity != Rarity.COMMON) random.floating(0.99F);
                random.integer(LegacyLootRules.totalWeight(rarity), 0);
            }
            List<ItemStack> rewards = block.generateContents(loot, random);
            helper.assertTrue(rewards.size() == count, "Source draw count " + rarity);
            helper.assertTrue(rewards.stream().allMatch(stack -> stack.is(Items.GOLD_NUGGET) && stack.getCount() == rarity.index() + 1),
                "Rarity nugget count " + rarity);
            rewards.getFirst().setCount(99);
            helper.assertTrue(rewards.get(1).getCount() == rarity.index() + 1, "Rewards do not share mutable ItemStacks");
            random.assertFinished();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bookReturnValueQuirkAndGearEnchantmentsUseOldRandomCatalog(GameTestHelper helper) {
        TCLegacyLoot loot = TCLegacyLoot.create(helper.getLevel().registryAccess());
        TraceRandom bookRandom = new TraceRandom().integer(2941, 2783).integer(18, 17)
            .integer(1, 0).integer(1, 0).floating(0.5F).floating(0.5F).integer(76, 0).integer(50, 49);
        ItemStack book = loot.generate(Rarity.COMMON, bookRandom);
        helper.assertTrue(book.is(Items.BOOK) && !EnchantmentHelper.hasAnyEnchantments(book), "Discarded enchanted-book result remains ordinary book");
        bookRandom.assertFinished();

        TraceRandom gearRandom = new TraceRandom().floating(0).integer(2, 1)
            .floating(0).floating(0).floating(0).floating(0.99F).floating(0.99F)
            .integer(5, 4).integer(46, 45).integer(4, 1).integer(18, 17)
            .integer(7, 6).integer(7, 6).floating(0.999F).floating(0.999F)
            .integer(7, 0).integer(50, 49).integer(5, 4).integer(50, 25);
        ItemStack helmet = loot.generate(Rarity.RARE, gearRandom);
        helper.assertTrue(helmet.is(ItemsTC.thaumiumHelm) && helmet.getDamageValue() == 45, "Thaumium helmet quality and initial damage");
        var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Enchantments.RESPIRATION), helmet) == 3, "Old weighted respirationIII");
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Enchantments.UNBREAKING), helmet) == 3, "Old continuation unbreakingIII");
        gearRandom.assertFinished();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tntQuarterChanceFiltersWholeStacksNotIndividualNuggets(GameTestHelper helper) {
        ItemStack nuggets = new ItemStack(Items.GOLD_NUGGET, 3);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        ItemStack experience = new ItemStack(Items.EXPERIENCE_BOTTLE);
        ArrayDeque<Float> draws = new ArrayDeque<>(List.of(0.0F, 0.25F, Math.nextUp(0.25F)));
        List<ItemStack> surviving = BlockLootTC.applyExplosionChance(List.of(nuggets, diamond, experience), 4, new LegacyRandomSource(0) {
            @Override
            public float nextFloat() {
                if (draws.isEmpty()) throw new AssertionError("Explosion consumed more than one float per stack");
                return draws.removeFirst();
            }
        });
        helper.assertTrue(draws.isEmpty(), "One chance draw for each original reward stack");
        helper.assertTrue(surviving.size() == 2 && surviving.getFirst() == nuggets && surviving.get(1) == diamond, "Whole-stack inclusive quarter chance");
        helper.assertTrue(nuggets.getCount() == 3, "Explosion does not reduce nugget stack to one or two");
        helper.succeed();
    }

    private static List<BlockLootTC> containers() {
        return List.of((BlockLootTC) BlocksTC.lootCrateCommon, (BlockLootTC) BlocksTC.lootCrateUncommon, (BlockLootTC) BlocksTC.lootCrateRare,
            (BlockLootTC) BlocksTC.lootUrnCommon, (BlockLootTC) BlocksTC.lootUrnUncommon, (BlockLootTC) BlocksTC.lootUrnRare);
    }

    private record Draw(char kind, int bound, float value) {}

    private static class TraceRandom extends Random {
        private final ArrayDeque<Draw> draws = new ArrayDeque<>();

        TraceRandom integer(int bound, int value) {
            draws.add(new Draw('i', bound, value));
            return this;
        }

        TraceRandom floating(float value) {
            draws.add(new Draw('f', 0, value));
            return this;
        }

        @Override
        public int nextInt(int bound) {
            Draw draw = take('i');
            if (bound != draw.bound()) throw new AssertionError("Expected int bound " + draw.bound() + ", actual " + bound);
            return (int) draw.value();
        }

        @Override
        public float nextFloat() {
            return take('f').value();
        }

        private Draw take(char kind) {
            if (draws.isEmpty()) throw new AssertionError("Unexpected random draw " + kind);
            Draw draw = draws.removeFirst();
            if (kind != draw.kind()) throw new AssertionError("Expected draw " + draw.kind() + ", actual " + kind);
            return draw;
        }

        void assertFinished() {
            if (!draws.isEmpty()) throw new AssertionError("Unconsumed random draws: " + draws);
        }
    }
}
