package thaumcraft.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.world.ore.BlockCrystal;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/** Stage 2b: world blocks: crystal attachment, plant soil, loot, aspects. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public class WorldBlockGameTests {
    private static List<ItemStack> drops(GameTestHelper h, BlockPos rel, ItemStack tool) {
        BlockPos abs = h.absolutePos(rel);
        BlockState state = h.getLevel().getBlockState(abs);
        return Block.getDrops(state, h.getLevel(), abs, null, null, tool);
    }

    private static int count(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    @GameTest(template = "empty")
    public static void crystalAttachesAndDrops(GameTestHelper h) {
        BlockPos stone = new BlockPos(1, 1, 1);
        BlockPos crystalPos = stone.above();
        h.setBlock(stone, Blocks.STONE);
        BlockState state = BlockCrystal.withConnections(BlocksTC.crystalAir.defaultBlockState(), h.getLevel(), h.absolutePos(crystalPos))
            .setValue(BlockCrystal.SIZE, 2);
        h.assertTrue(state.getValue(BlockCrystal.DOWN), "crystal sees stone below");
        h.assertFalse(state.getValue(BlockCrystal.UP), "no support above");
        h.assertTrue(state.canSurvive(h.getLevel(), h.absolutePos(crystalPos)), "crystal survives on stone");
        h.setBlock(crystalPos, state);

        List<ItemStack> d = drops(h, crystalPos, ItemStack.EMPTY);
        h.assertTrue(count(d, ItemsTC.crystalEssence) == 3, "size 2 drops 3 essences: " + d);
        AspectList al = ((ItemGenericEssentiaContainer) ItemsTC.crystalEssence).getAspects(d.get(0));
        h.assertTrue(al != null && al.getAmount(Aspect.AIR) == 1, "essence of aer: " + al);

        // Without support the crystal crumbles (in TC6: neighborChanged + dropBlockAsItem)
        h.setBlock(stone, Blocks.AIR);
        h.assertBlockPresent(Blocks.AIR, crystalPos);
        h.assertItemEntityPresent(ItemsTC.crystalEssence);
        h.assertFalse(BlocksTC.crystalFire.defaultBlockState().canSurvive(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1))),
            "crystal cannot float in air");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void plantSoil(GameTestHelper h) {
        BlockPos ground = new BlockPos(1, 0, 1);
        BlockPos plant = ground.above();
        BlockPos abs = h.absolutePos(plant);

        h.setBlock(ground, Blocks.STONE);
        h.assertTrue(BlocksTC.vishroom.defaultBlockState().canSurvive(h.getLevel(), abs), "vishroom on stone");
        h.assertFalse(BlocksTC.shimmerleaf.defaultBlockState().canSurvive(h.getLevel(), abs), "shimmerleaf not on stone");
        h.assertFalse(BlocksTC.cinderpearl.defaultBlockState().canSurvive(h.getLevel(), abs), "cinderpearl not on stone");

        h.setBlock(ground, Blocks.GRASS_BLOCK);
        h.assertTrue(BlocksTC.shimmerleaf.defaultBlockState().canSurvive(h.getLevel(), abs), "shimmerleaf on grass");

        h.setBlock(ground, Blocks.RED_SAND);
        h.assertTrue(BlocksTC.cinderpearl.defaultBlockState().canSurvive(h.getLevel(), abs), "cinderpearl on sand");
        h.setBlock(ground, Blocks.ORANGE_TERRACOTTA);
        h.assertTrue(BlocksTC.cinderpearl.defaultBlockState().canSurvive(h.getLevel(), abs), "cinderpearl on terracotta");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oreAndLeafLoot(GameTestHelper h) {
        BlockPos p = new BlockPos(1, 1, 1);
        h.setBlock(p, BlocksTC.oreAmber);
        var amberDrops = drops(h, p, new ItemStack(Items.STONE_PICKAXE));
        int amber = count(amberDrops, ItemsTC.amber);
        var preservedItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("thaumcraft:curio_preserved"));
        int preserved = count(amberDrops, preservedItem);
        // BETA26 can replace the entire ordinary amber stack with one preserved
        // curio. The live world RNG must keep that source branch, including in QA.
        h.assertTrue(amber >= 1 && amber <= 2 && preserved == 0 || amber == 0 && preserved == 1,
            "amber ore yields 1-2 amber OR its one source preserved curio, got " + amberDrops);
        h.assertTrue(BlocksTC.oreAmber.defaultBlockState().requiresCorrectToolForDrops(), "amber ore needs a tool");
        h.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(BlocksTC.oreAmber.defaultBlockState()), "stone pick mines amber");
        h.assertFalse(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(BlocksTC.oreCinnabar.defaultBlockState()), "cinnabar needs iron");

        h.setBlock(p, BlocksTC.oreQuartz);
        h.assertTrue(count(drops(h, p, new ItemStack(Items.WOODEN_PICKAXE)), Items.QUARTZ) >= 1, "quartz ore drops quartz");

        h.setBlock(p, BlocksTC.leafSilverwood);
        h.assertTrue(count(drops(h, p, new ItemStack(Items.SHEARS)), BlocksTC.leafSilverwood.asItem()) == 1, "shears keep leaves");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockAspects(GameTestHelper h) {
        AspectList crystal = ThaumcraftCraftingManager.getObjectTags(new ItemStack(BlocksTC.crystalOrder));
        h.assertTrue(crystal.getAmount(Aspect.ORDER) == 15 && crystal.getAmount(Aspect.CRYSTAL) == 10, "ordo crystal: " + crystal);
        AspectList cinnabar = ThaumcraftCraftingManager.getObjectTags(new ItemStack(BlocksTC.oreCinnabar));
        h.assertTrue(cinnabar.getAmount(Aspect.ALCHEMY) == 5, "cinnabar ore via c:ores/cinnabar: " + cinnabar);
        AspectList log = ThaumcraftCraftingManager.getObjectTags(new ItemStack(BlocksTC.logSilverwood));
        h.assertTrue(log.getAmount(Aspect.AURA) == 5, "silverwood log: " + log);
        AspectList planks = ThaumcraftCraftingManager.getObjectTags(new ItemStack(BlocksTC.plankGreatwood));
        h.assertTrue(planks != null && planks.getAmount(Aspect.PLANT) > 0, "greatwood planks derived from log recipe: " + planks);
        h.succeed();
    }
}
