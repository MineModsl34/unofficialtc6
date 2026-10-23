package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.golems.parts.GolemMaterial;
import thaumcraft.common.golems.*;
import thaumcraft.common.tiles.crafting.TileGolemBuilder;
import thaumcraft.common.tiles.essentia.TileJarFillable;

/** Real ingredient capability payment, Machina, persisted press and paid item placement. */
@GameTestHolder(Thaumcraft.MODID)
public final class GolemBuildChainGameTests {
    @GameTestGenerator public static Collection<TestFunction> chains() {
        var tests = new ArrayList<TestFunction>();
        for (String material : List.of("WOOD", "IRON", "CLAY", "BRASS", "THAUMIUM", "VOID")) tests.add(test(material.toLowerCase(), h -> craft(h, material, "normal")));
        tests.add(test("resume", h -> craft(h, "IRON", "resume"))); tests.add(test("research_denied", h -> craft(h, "IRON", "research_denied")));
        return tests;
    }
    private static TestFunction test(String name, java.util.function.Consumer<GameTestHelper> body) { return new TestFunction("bulk_golem_builds", "thaumcraft.bulk_build_" + name, "thaumcraft:scan_test", 850, 0, true, body); }
    private static void craft(GameTestHelper h, String material, String mode) {
        BulkGameplayFixtures.floor(h); var player = BulkGameplayFixtures.player(h); BulkGameplayFixtures.research(player);
        var props = GolemProperties.fromLong(0); props.setMaterial(java.util.Arrays.stream(GolemMaterial.getMaterials()).filter(m -> m.key.equals(material)).findFirst().orElseThrow());
        var builder = BulkGameplayFixtures.tile(h, new BlockPos(4, 2, 4), BlocksTC.golemBuilder, TileGolemBuilder.class);
        var chest = BulkGameplayFixtures.tile(h, new BlockPos(5, 2, 4), Blocks.CHEST, ChestBlockEntity.class);
        var components = props.generateComponents(); int items = 0; for (int i = 0; i < components.length; i++) { chest.setItem(i, components[i].copy()); items += components[i].getCount(); }
        int expectedWork = items + props.getTraits().size() * 2;
        var source = BulkGameplayFixtures.tile(h, new BlockPos(4, 1, 4), BlocksTC.jarNormal, TileJarFillable.class); source.aspect = source.aspectFilter = Aspect.MECHANISM; source.amount = expectedWork + 3;
        if (mode.equals("research_denied")) ThaumcraftCapabilities.getKnowledge(player).clear();
        boolean accepted = builder.startCraft(props.toLong(), player);
        if (mode.equals("research_denied")) { h.assertTrue(!accepted && !builder.busy() && !chest.isEmpty() && source.amount == expectedWork + 3, "unknown material/part research pays no real component or Machina"); h.succeed(); return; }
        h.assertTrue(accepted && builder.busy() && chest.isEmpty() && builder.maxCost == expectedWork && !builder.startCraft(props.toLong(), player), "all actual adjacent components paid once, source work price, duplicate busy start rejected");
        if (mode.equals("resume")) h.runAtTickTime(30, () -> { h.assertTrue(builder.busy() && builder.cost < builder.maxCost && builder.cost > 0, "partially paid native press before save"); builder.loadWithComponents(builder.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess()); });
        h.succeedWhen(() -> {
            h.assertTrue(!builder.busy() && !builder.getItem(0).isEmpty(), "native " + material + " press completed");
            h.assertTrue(GolemItemData.properties(builder.getItem(0)).toLong() == props.toLong() && source.amount == 3 && chest.isEmpty(), "real built item retains its packed parts and exact Machina payment");
            var actual = builder.removeItem(0, 1); player.setItemInHand(InteractionHand.MAIN_HAND, actual);
            var floor = h.absolutePos(new BlockPos(7, 1, 7)); var blocked = floor.above(); h.getLevel().setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(floor.getCenter(), Direction.UP, floor, false));
            h.assertTrue(actual.onItemUseFirst(context) == InteractionResult.FAIL && actual.getCount() == 1, "obstructed native placement retains paid golem item");
            h.getLevel().setBlock(blocked, Blocks.AIR.defaultBlockState(), 3);
            h.assertTrue(actual.onItemUseFirst(context) == InteractionResult.SUCCESS && actual.isEmpty(), "native survival placement consumed exactly the completed item");
            var entities = h.getLevel().getEntitiesOfClass(EntityThaumcraftGolem.class, new net.minecraft.world.phys.AABB(blocked).inflate(1));
            h.assertTrue(entities.size() == 1 && entities.getFirst().getOwnerId().equals(player.getUUID()) && entities.getFirst().getProperties().toLong() == props.toLong() && entities.getFirst().getMaxHealth() > 0, "real registered source golem spawned with owner, parts and native attributes"); entities.forEach(BulkGameplayFixtures::remove);
        });
    }
}
