package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;
import thaumcraft.common.tiles.crafting.TilePedestal;
import thaumcraft.common.tiles.essentia.TileJarFillable;

/** Ordinary matrix ticks pay real source recipes, real jars and pedestal items. */
@GameTestHolder(Thaumcraft.MODID)
public final class InfusionBehaviourGameTests {
    @GameTestGenerator public static Collection<TestFunction> chains() {
        var tests = new ArrayList<TestFunction>();
        for (String aspect : List.of("aer", "ignis", "aqua", "terra", "ordo", "perditio", "vitium")) tests.add(test("cluster_" + aspect, h -> cluster(h, Aspect.getAspect(aspect), "normal")));
        for (String mode : List.of("resume", "unknown_research", "missing_pillar", "input_removed")) tests.add(test(mode, h -> cluster(h, Aspect.AIR, mode)));
        for (int charge : new int[]{0, 1}) { int c = charge; tests.add(test("runic_" + c, h -> runic(h, c))); }
        return tests;
    }
    private static TestFunction test(String name, java.util.function.Consumer<GameTestHelper> body) { return new TestFunction("bulk_infusion_chains", "thaumcraft.bulk_infusion_" + name, "thaumcraft:scan_test", 2200, 0, true, body); }
    private record Altar(TileInfusionMatrix matrix, TilePedestal center, TilePedestal west, TilePedestal east) {}
    private static Altar altar(GameTestHelper h) {
        BulkGameplayFixtures.floor(h);
        var center = BulkGameplayFixtures.tile(h, new BlockPos(5, 2, 5), BlocksTC.pedestalArcane, TilePedestal.class);
        for (int x : new int[]{4, 6}) for (int z : new int[]{4, 6}) h.setBlock(new BlockPos(x, 2, z), BlocksTC.pillarArcane);
        var matrix = BulkGameplayFixtures.tile(h, new BlockPos(5, 4, 5), BlocksTC.infusionMatrix, TileInfusionMatrix.class);
        var west = BulkGameplayFixtures.tile(h, new BlockPos(2, 2, 5), BlocksTC.pedestalArcane, TilePedestal.class);
        var east = BulkGameplayFixtures.tile(h, new BlockPos(8, 2, 5), BlocksTC.pedestalArcane, TilePedestal.class);
        return new Altar(matrix, center, west, east);
    }
    private static List<TileJarFillable> jars(GameTestHelper h, Aspect[] aspects, int[] costs) {
        var result = new ArrayList<TileJarFillable>(); BlockPos[] places = {new BlockPos(2, 2, 2), new BlockPos(8, 2, 2), new BlockPos(8, 2, 8)};
        for (int i = 0; i < aspects.length; i++) { var jar = BulkGameplayFixtures.tile(h, places[i], BlocksTC.jarNormal, TileJarFillable.class); jar.aspect = jar.aspectFilter = aspects[i]; jar.amount = costs[i] + 3; result.add(jar); }
        return result;
    }
    private static void start(GameTestHelper h, Altar a, net.minecraft.server.level.ServerPlayer player) {
        var wand = new ItemStack(ItemsTC.casterBasic);
        a.matrix.onCasterRightClick(h.getLevel(), wand, player, a.matrix.getBlockPos(), Direction.UP, InteractionHand.MAIN_HAND);
        a.matrix.onCasterRightClick(h.getLevel(), wand, player, a.matrix.getBlockPos(), Direction.UP, InteractionHand.MAIN_HAND);
    }
    private static void cluster(GameTestHelper h, Aspect aspect, String mode) {
        var a = altar(h); var player = BulkGameplayFixtures.player(h); BulkGameplayFixtures.research(player);
        a.center.setItem(0, ThaumcraftApiHelper.makeCrystal(aspect)); a.west.setItem(0, new ItemStack(Items.WHEAT_SEEDS)); a.east.setItem(0, new ItemStack(ItemsTC.salisMundus));
        var sources = jars(h, new Aspect[]{aspect, Aspect.CRYSTAL, Aspect.TRAP}, new int[]{10, 10, 5});
        if (mode.equals("unknown_research")) ThaumcraftCapabilities.getKnowledge(player).clear();
        if (mode.equals("missing_pillar")) h.setBlock(new BlockPos(4, 2, 4), Blocks.AIR);
        if (mode.equals("unknown_research") || mode.equals("missing_pillar")) {
            start(h, a, player);
            h.runAtTickTime(40, () -> { h.assertTrue(!a.matrix.crafting && a.center.getItem(0).is(ItemsTC.crystalEssence) && !a.west.isEmpty() && !a.east.isEmpty() && sources.stream().mapToInt(j -> j.amount).sum() == 34, "research/structure rejection spends no actual inputs"); h.succeed(); }); return;
        }
        // The taint cluster has genuine source instability. Warm a real symmetric altar,
        // as for runic augmentation, instead of relying on a lucky world RNG seed.
        for (int x = 1; x <= 4; x++) for (int z = 1; z <= 3 && (x - 1) * 3 + z <= 10; z++) { h.setBlock(new BlockPos(5 - x, 0, 5 - z), BlocksTC.stabilizer); h.setBlock(new BlockPos(5 + x, 0, 5 + z), BlocksTC.stabilizer); }
        a.matrix.onCasterRightClick(h.getLevel(), new ItemStack(ItemsTC.casterBasic), player, a.matrix.getBlockPos(), Direction.UP, InteractionHand.MAIN_HAND);
        h.runAtTickTime(300, () -> { h.assertTrue(a.matrix.stability >= 12, "actual balanced altar filled stability before cluster infusion"); a.matrix.onCasterRightClick(h.getLevel(), new ItemStack(ItemsTC.casterBasic), player, a.matrix.getBlockPos(), Direction.UP, InteractionHand.MAIN_HAND); h.assertTrue(a.matrix.active && a.matrix.crafting && a.matrix.costMult == 1, "actual ordinary altar accepted source recipe at its native price"); });
        if (mode.equals("resume")) h.runAtTickTime(340, () -> { h.assertTrue(a.matrix.crafting && sources.stream().mapToInt(j -> j.amount).sum() < 34, "paid busy job before native save"); a.matrix.loadWithComponents(a.matrix.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess()); });
        if (mode.equals("input_removed")) { h.runAtTickTime(340, () -> a.center.removeItem(0, 1)); h.runAtTickTime(380, () -> { h.assertTrue(!a.matrix.crafting && a.center.isEmpty(), "missing central input aborts without manufactured output (source instability penalties remain allowed)"); h.succeed(); }); return; }
        h.succeedWhen(() -> {
            h.assertTrue(!a.matrix.crafting && !a.center.getItem(0).is(ItemsTC.crystalEssence) && !a.center.isEmpty(), "real cluster result produced: " + a.center.getItem(0));
            var expected = switch (aspect.getTag()) { case "aer" -> BlocksTC.crystalAir; case "ignis" -> BlocksTC.crystalFire; case "aqua" -> BlocksTC.crystalWater; case "terra" -> BlocksTC.crystalEarth; case "ordo" -> BlocksTC.crystalOrder; case "perditio" -> BlocksTC.crystalEntropy; default -> BlocksTC.crystalTaint; };
            h.assertTrue(a.center.getItem(0).is(expected.asItem()) && a.center.getItem(0).getCount() == 1 && a.west.isEmpty() && a.east.isEmpty(), "correct typed cluster and two paid source components");
            h.assertTrue(sources.stream().allMatch(j -> j.amount == 3), "exactly 25 source essentia paid once including native resume");
        });
    }
    private static void runic(GameTestHelper h, int charge) {
        var a = altar(h); var player = BulkGameplayFixtures.player(h); BulkGameplayFixtures.research(player);
        var input = new ItemStack(ItemsTC.ringMundane);
        if (charge > 0) { var tag = new net.minecraft.nbt.CompoundTag(); tag.putByte("TC.RUNIC", (byte) charge); input.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag)); }
        a.center.setItem(0, input); a.west.setItem(0, new ItemStack(ItemsTC.salisMundus)); a.east.setItem(0, new ItemStack(ItemsTC.amber));
        TilePedestal extra = charge > 0 ? BulkGameplayFixtures.tile(h, new BlockPos(5, 2, 8), BlocksTC.pedestalArcane, TilePedestal.class) : null;
        if (extra != null) extra.setItem(0, new ItemStack(ItemsTC.amber));
        // Ten genuine symmetric stabilizer pairs bound worst-case source instability loss.
        for (int x = 1; x <= 4; x++) for (int z = 1; z <= 3 && (x - 1) * 3 + z <= 10; z++) { h.setBlock(new BlockPos(5 - x, 0, 5 - z), BlocksTC.stabilizer); h.setBlock(new BlockPos(5 + x, 0, 5 + z), BlocksTC.stabilizer); }
        int vis = charge == 0 ? 40 : 60; var sources = jars(h, new Aspect[]{Aspect.PROTECT, Aspect.CRYSTAL, Aspect.ENERGY}, new int[]{vis, vis / 2, vis / 2});
        // Sources are not symmetric stabilizers; placing them must not replace a pillar/pedestal.
        a.matrix.onCasterRightClick(h.getLevel(), new ItemStack(ItemsTC.casterBasic), player, a.matrix.getBlockPos(), Direction.UP, InteractionHand.MAIN_HAND);
        h.runAtTickTime(300, () -> { h.assertTrue(a.matrix.stability >= 12 && a.matrix.stabilityReplenish > .8, "native idle ticker filled stability from real symmetric stabilizers"); a.matrix.onCasterRightClick(h.getLevel(), new ItemStack(ItemsTC.casterBasic), player, a.matrix.getBlockPos(), Direction.UP, InteractionHand.MAIN_HAND); h.assertTrue(a.matrix.crafting, "dynamic runic recipe accepted"); });
        h.succeedWhen(() -> { h.assertTrue(InfusionRunicAugmentRecipe.getRunicCharge(a.center.getItem(0)) == charge + 1 && !a.matrix.crafting, "real runic component was incremented"); h.assertTrue(a.center.getItem(0).is(ItemsTC.ringMundane) && a.west.isEmpty() && a.east.isEmpty() && (extra == null || extra.isEmpty()), "same ring and exactly the source amber count"); h.assertTrue(sources.stream().allMatch(j -> j.amount == 3), "dynamic runic essentia paid exactly"); });
    }
}
