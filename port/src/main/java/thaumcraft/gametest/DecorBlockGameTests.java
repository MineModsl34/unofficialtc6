package thaumcraft.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/** Stage 2c: decorative blocks: candle support, special drop, recipes and the aspects derived from them. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class DecorBlockGameTests {
    private static List<ItemStack> drops(GameTestHelper h, BlockPos rel, ItemStack tool) {
        BlockPos abs = h.absolutePos(rel);
        return Block.getDrops(h.getLevel().getBlockState(abs), h.getLevel(), abs, null, null, tool);
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    @GameTest(template = "empty")
    public static void candleNeedsSupport(GameTestHelper h) {
        BlockPos ground = new BlockPos(1, 1, 1);
        BlockPos candle = ground.above();
        Block red = BlocksTC.candles.get(DyeColor.RED);
        h.assertFalse(red.defaultBlockState().canSurvive(h.getLevel(), h.absolutePos(candle)), "candle cannot float");
        h.setBlock(ground, Blocks.STONE);
        h.assertTrue(red.defaultBlockState().canSurvive(h.getLevel(), h.absolutePos(candle)), "candle on stone");
        h.setBlock(candle, red);
        h.setBlock(ground, Blocks.AIR);
        h.assertBlockPresent(Blocks.AIR, candle);
        h.assertItemEntityPresent(red.asItem());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void specialDrops(GameTestHelper h) {
        BlockPos p = new BlockPos(1, 1, 1);
        h.setBlock(p, BlocksTC.pillarArcane);
        List<ItemStack> pillar = drops(h, p, new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(count(pillar, BlocksTC.stoneArcane.asItem()) == 2 && count(pillar, BlocksTC.pillarArcane.asItem()) == 0,
            "pillar drops 2 arcane stone: " + pillar);

        h.setBlock(p, BlocksTC.slabAncient.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
        h.assertTrue(count(drops(h, p, new ItemStack(Items.IRON_PICKAXE)), BlocksTC.slabAncient.asItem()) == 2, "double slab drops 2");

        h.setBlock(p, BlocksTC.stonePorous);
        int gravel = 0;
        int other = 0;
        for (int i = 0; i < 200; i++) {
            for (ItemStack s : drops(h, p, new ItemStack(Items.IRON_PICKAXE))) {
                if (s.is(Items.GRAVEL)) {
                    gravel++;
                } else if (!s.is(BlocksTC.stonePorous.asItem())) {
                    other++;
                }
            }
        }
        // 1/15 finds: about 13 expected in 200 attempts
        h.assertTrue(gravel > 150 && other > 0, "porous stone: gravel=" + gravel + " treasure=" + other);
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
        silk.enchant(h.getLevel().registryAccess().holderOrThrow(Enchantments.SILK_TOUCH), 1);
        h.assertTrue(count(drops(h, p, silk), BlocksTC.stonePorous.asItem()) == 1, "silk touch keeps porous stone");

        h.setBlock(p, BlocksTC.grassAmbient);
        h.assertTrue(count(drops(h, p, ItemStack.EMPTY), Items.DIRT) == 1, "ambient grass drops dirt");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recipesAndAspects(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        for (String id : new String[]{"stonearcane", "thaumiumingotstoblock", "tallowcandlered", "slabeldritch", "plankgreatwood"}) {
            h.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, id)).isPresent(), "recipe " + id);
        }
        AspectList grass = ThaumcraftCraftingManager.getObjectTags(new ItemStack(BlocksTC.grassAmbient));
        h.assertTrue(grass.getAmount(Aspect.LIGHT) == 5 && grass.getAmount(Aspect.EARTH) > 0, "ambient grass = grass + lux 5: " + grass);
        AspectList glyph = ThaumcraftCraftingManager.getObjectTags(new ItemStack(BlocksTC.stoneAncientGlyphed));
        h.assertTrue(glyph.getAmount(Aspect.MIND) == 5, "glyphed stone: " + glyph);
        AspectList candle = ThaumcraftCraftingManager.getObjectTags(new ItemStack(BlocksTC.candles.get(DyeColor.BLUE)));
        h.assertTrue(candle.getAmount(Aspect.LIGHT) > 0, "candle has lux: " + candle);
        // Derived from recipes. Thaumium/brass blocks come after the crucible recipes (stage 5); small slabs
        // round down to zero by the TC6 formula (3 bricks * 2 * 0.75 / 6)
        for (Block b : new Block[]{BlocksTC.stoneArcane, BlocksTC.fleshBlock, BlocksTC.amberBlock, BlocksTC.stairsGreatwood, BlocksTC.tableWood}) {
            AspectList al = ThaumcraftCraftingManager.getObjectTags(new ItemStack(b));
            h.assertTrue(al != null && al.size() > 0, "derived aspects for " + b + ": " + al);
        }
        h.succeed();
    }
}
