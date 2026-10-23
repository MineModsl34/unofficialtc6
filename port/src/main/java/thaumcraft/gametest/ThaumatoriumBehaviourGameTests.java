package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.crafting.BlockThaumatorium;
import thaumcraft.common.blocks.devices.BlockBrainBox;
import thaumcraft.common.tiles.crafting.TileThaumatorium;
import thaumcraft.common.tiles.crafting.TileThaumatoriumTop;
import thaumcraft.common.tiles.essentia.TileJarFillable;
import thaumcraft.common.tiles.essentia.TileTube;

/** Paid source dust formation followed by native remote-input, piping and automatic output. */
@GameTestHolder(Thaumcraft.MODID)
public final class ThaumatoriumBehaviourGameTests {
    @GameTestGenerator public static Collection<TestFunction> chains() {
        var tests = new ArrayList<TestFunction>();
        for (String mode : List.of("nitor", "alumentum", "powered_resume", "cold_resume", "busy_reload", "upper_input", "research_gate", "brain_capacity", "full_smelter_chain"))
            tests.add(new TestFunction("bulk_thaumatorium", "thaumcraft.bulk_thaumatorium_" + mode, "thaumcraft:scan_test", mode.equals("full_smelter_chain") ? 1800 : 900, 0, true, h -> craft(h, mode)));
        return tests;
    }
    private static void craft(GameTestHelper h, String mode) {
        BulkGameplayFixtures.floor(h); var player = BulkGameplayFixtures.player(h); BulkGameplayFixtures.research(player);
        BlockPos base = new BlockPos(5, 3, 5); var absolute = h.absolutePos(base);
        h.setBlock(base.below(2), mode.equals("cold_resume") ? Blocks.STONE : Blocks.MAGMA_BLOCK); h.setBlock(base.below(), BlocksTC.crucible);
        h.setBlock(base, BlocksTC.metalAlchemical); h.setBlock(base.above(), BlocksTC.metalAlchemical);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemsTC.salisMundus, 2));
        var result = player.getItemInHand(InteractionHand.OFF_HAND).onItemUseFirst(new UseOnContext(player, InteractionHand.OFF_HAND, new BlockHitResult(absolute.getCenter(), Direction.SOUTH, absolute, false)));
        h.assertTrue(result == InteractionResult.SUCCESS && player.getItemInHand(InteractionHand.OFF_HAND).getCount() == 1, "actual source thaumatorium blueprint accepted and paid exactly one dust");
        var destination = BulkGameplayFixtures.tile(h, base.south(), Blocks.CHEST, ChestBlockEntity.class);
        var sources = new ArrayList<TileJarFillable>(); var pipes = new ArrayList<TileTube>();
        var furnaces = new ArrayList<thaumcraft.common.tiles.essentia.TileSmelter>();
        boolean fullChain = mode.equals("full_smelter_chain"); boolean[] released = {false};
        Aspect[] aspects = {Aspect.ENERGY, Aspect.FIRE, mode.equals("alumentum") ? Aspect.ENTROPY : Aspect.LIGHT};
        for (int i = 0; i < 3; i++) {
            var side = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH}[i];
            var jar = BulkGameplayFixtures.tile(h, base.relative(side).below(), BlocksTC.jarNormal, TileJarFillable.class); jar.aspectFilter = aspects[i]; if (!fullChain) { jar.aspect = aspects[i]; jar.amount = mode.equals("alumentum") && i == 2 ? 8 : 13; } sources.add(jar);
            pipes.add(BulkGameplayFixtures.tile(h, base.relative(side), BlocksTC.tube, TileTube.class));
            if (fullChain) {
                var furnacePos = base.relative(side, 3).below(); var furnace = BulkGameplayFixtures.tile(h, furnacePos, BlocksTC.smelterVoid, thaumcraft.common.tiles.essentia.TileSmelter.class);
                var crystals = thaumcraft.api.ThaumcraftApiHelper.makeCrystal(aspects[i]); crystals.setCount(64); furnace.setItem(0, crystals); furnace.setItem(1, new ItemStack(Items.COAL)); furnaces.add(furnace);
                var alembic = BulkGameplayFixtures.tile(h, furnacePos.above(), BlocksTC.alembic, thaumcraft.common.tiles.essentia.TileAlembic.class); alembic.facing = Direction.SOUTH.get3DDataValue();
                if (side == Direction.NORTH) alembic.facing = Direction.NORTH.get3DDataValue();
                BulkGameplayFixtures.tile(h, base.relative(side, 2), BlocksTC.tube, TileTube.class);
            }
        }
        h.runAtTickTime(60, () -> {
            h.assertTrue(h.getLevel().getBlockEntity(absolute) instanceof TileThaumatorium && h.getLevel().getBlockEntity(absolute.above()) instanceof TileThaumatoriumTop, "delayed native dust conversion produced both real halves above the original crucible");
            h.getLevel().setBlock(absolute, h.getLevel().getBlockState(absolute).setValue(BlockThaumatorium.FACING, Direction.SOUTH), 3);
            var tile = (TileThaumatorium) h.getLevel().getBlockEntity(absolute); var catalyst = new ItemStack(mode.equals("alumentum") ? Items.COAL : Items.GLOWSTONE_DUST, 1);
            if (mode.equals("upper_input")) {
                var cap = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, absolute.above(), Direction.UP);
                h.assertTrue(cap != null && cap.insertItem(0, catalyst, false).isEmpty() && tile.getItem(0).getCount() == 1, "upper native item capability forwards into the one actual bottom inventory");
            } else tile.setItem(0, catalyst);
            if (mode.equals("research_gate")) ThaumcraftCapabilities.getKnowledge(player).clear();
            var recipeId = ResourceLocation.parse("thaumcraft:" + (mode.equals("alumentum") ? "alumentum" : "nitor"));
            boolean accepted = tile.toggleRecipe(player, recipeId);
            if (mode.equals("research_gate")) { h.assertTrue(!accepted && tile.selections().isEmpty(), "recipe selection rejects unknown source research"); return; }
            h.assertTrue(accepted, "actual native known recipe selected");
            if (mode.equals("powered_resume") || fullChain) h.setBlock(base.above(2), Blocks.REDSTONE_BLOCK);
            if (mode.equals("cold_resume")) h.setBlock(base.below(2), Blocks.STONE);
            if (mode.equals("brain_capacity")) {
                tile.setItem(0, new ItemStack(Items.COAL, 1));
                h.assertTrue(!tile.toggleRecipe(player, ResourceLocation.parse("thaumcraft:alumentum")), "one default recipe slot rejects a second known matching selection");
                h.setBlock(base.above().east(), BlocksTC.brainBox.defaultBlockState().setValue(BlockBrainBox.FACING, Direction.WEST)); tile.getUpgrades();
                h.assertTrue(tile.maxRecipes == 3 && tile.toggleRecipe(player, ResourceLocation.parse("thaumcraft:alumentum")) && tile.selections().size() == 2, "correctly oriented native brain box grants two source slots and accepts the second actual recipe");
                h.setBlock(base.above().east(), Blocks.AIR); tile.getUpgrades(); h.assertTrue(tile.maxRecipes == 1 && tile.selections().size() == 1, "removing actual brain box restores quota and prunes trailing selection"); tile.setItem(0, new ItemStack(Items.GLOWSTONE_DUST, 1));
            }
        });
        h.runAtTickTime(120, () -> {
            var tile = (TileThaumatorium) h.getLevel().getBlockEntity(absolute);
            if (mode.equals("powered_resume") || mode.equals("cold_resume") || mode.equals("research_gate")) {
                h.assertTrue(destination.isEmpty() && tile.getItem(0).getCount() == 1 && sources.stream().mapToInt(j -> j.amount).sum() + pipes.stream().mapToInt(t -> t.getEssentiaAmount(Direction.DOWN)).sum() == 39, "stopped/forbidden source job consumes no catalyst or essentia");
                if (mode.equals("research_gate")) { h.succeed(); return; }
                if (mode.equals("powered_resume")) h.setBlock(base.above(2), Blocks.AIR); else h.setBlock(base.below(2), Blocks.MAGMA_BLOCK);
            }
            if (mode.equals("busy_reload")) { h.assertTrue(destination.isEmpty() && tile.currentCraft >= 0 && tile.essentia.visSize() > 0, "actual partially paid live job before persisted reload"); tile.loadWithComponents(tile.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess()); }
        });
        if (mode.equals("research_gate")) return;
        if (fullChain) h.onEachTick(() -> { if (!released[0] && h.getTick() > 60 && sources.stream().allMatch(j -> j.amount >= 10)) { h.assertTrue(destination.isEmpty() && furnaces.stream().allMatch(f -> f.getItem(0).getCount() < 64 && f.getItem(1).isEmpty()), "three real fueled smelter→alembic→tube lines filled originally empty labelled jars before automatic alchemy"); released[0] = true; h.setBlock(base.above(2), Blocks.AIR); } });
        h.succeedWhen(() -> {
            int output = 0; for (int slot = 0; slot < destination.getContainerSize(); slot++) if (destination.getItem(slot).is(mode.equals("alumentum") ? ItemsTC.alumentum : BlocksTC.nitor.get(net.minecraft.world.item.DyeColor.YELLOW).asItem())) output += destination.getItem(slot).getCount();
            h.assertTrue(output == 1, "one actual automatic alchemy result in native adjacent chest; got " + output);
            var tile = (TileThaumatorium) h.getLevel().getBlockEntity(absolute);
            h.assertTrue(tile.getItem(0).isEmpty() && tile.essentia.visSize() == 0, "exactly one catalyst paid and source stored essentia cleared; catalyst=" + tile.getItem(0) + ", stored=" + tile.essentia.visSize());
            if (fullChain) h.assertTrue(released[0], "the complete two-stage piping route ran before the paid native output");
            else h.assertTrue(sources.stream().mapToInt(j -> j.amount).sum() + pipes.stream().mapToInt(t -> t.getEssentiaAmount(Direction.DOWN)).sum() == 9, "source 25/30 essentia price paid, residual pipe cargo conserved");
        });
    }
}
