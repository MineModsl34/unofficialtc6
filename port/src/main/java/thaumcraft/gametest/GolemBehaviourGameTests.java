package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.golems.parts.*;
import thaumcraft.api.golems.seals.*;
import thaumcraft.common.golems.*;
import thaumcraft.common.golems.seals.*;
import thaumcraft.common.golems.tasks.TaskHandler;

/** Real seal scheduling, goal selection, pathfinding and transfer; results are never injected. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class GolemBehaviourGameTests {
    private GolemBehaviourGameTests() {}
    @GameTestGenerator public static Collection<TestFunction> logisticsAndRestrictions() {
        var tests = new ArrayList<TestFunction>();
        for (String legs : List.of("WALKER", "ROLLER", "CLIMBER", "FLYER"))
            tests.add(test("pickup_store_" + legs.toLowerCase(), h -> logistics(h, legs, "normal")));
        for (String mode : List.of("wrong_owner", "wrong_color", "redstone", "extract_store"))
            tests.add(test(mode, h -> logistics(h, "WALKER", mode)));
        return tests;
    }
    private static TestFunction test(String name, java.util.function.Consumer<GameTestHelper> body) {
        return new TestFunction("bulk_golems", "thaumcraft.bulk_golem_" + name, "thaumcraft:scan_test", 650, 0, true, body);
    }
    static EntityThaumcraftGolem golem(GameTestHelper h, String legs, String arms) {
        BulkGameplayFixtures.floor(h);
        var golem = h.spawn(Golem9EntityRegistries.GOLEM, 3, 2, 4);
        var properties = GolemProperties.fromLong(0);
        properties.setMaterial(java.util.Arrays.stream(GolemMaterial.getMaterials()).filter(p -> p.key.equals("IRON")).findFirst().orElseThrow());
        properties.setHead(java.util.Arrays.stream(GolemHead.getHeads()).filter(p -> p.key.equals("SMART")).findFirst().orElseThrow());
        properties.setArms(java.util.Arrays.stream(GolemArm.getArms()).filter(p -> p.key.equals(arms)).findFirst().orElseThrow());
        properties.setLegs(java.util.Arrays.stream(GolemLeg.getLegs()).filter(p -> p.key.equals(legs)).findFirst().orElseThrow());
        golem.setProperties(properties); golem.updateEntityAttributes(); golem.setHealth(golem.getMaxHealth());
        golem.setOwnerId(java.util.UUID.randomUUID()); golem.setOwned(true); golem.setValidSpawn(); golem.setPersistenceRequired();
        return golem;
    }
    static SealEntity seal(GameTestHelper h, BlockPos pos, Direction side, ISeal type, EntityThaumcraftGolem golem) {
        var entity = new SealEntity(h.getLevel(), new SealPos(h.absolutePos(pos), side), type);
        entity.setOwner(golem.getOwnerId().toString()); entity.setLocked(true); entity.setArea(new BlockPos(8, 3, 8));
        h.assertTrue(SealHandler.addSealEntity(h.getLevel(), entity), "native chunk seal registered: " + type.getKey());
        return entity;
    }
    static void cleanup(GameTestHelper h, EntityThaumcraftGolem golem, SealEntity... seals) {
        for (var seal : seals) SealHandler.removeSealEntity(h.getLevel(), seal.getSealPos(), true);
        BulkGameplayFixtures.remove(golem);
    }
    private static void logistics(GameTestHelper h, String legs, String mode) {
        var golem = golem(h, legs, "FINE"); var start = golem.position();
        BlockPos sourcePos = new BlockPos(2, 1, 4), targetPos = new BlockPos(7, 2, 4);
        var destination = BulkGameplayFixtures.tile(h, targetPos, Blocks.CHEST, ChestBlockEntity.class);
        SealEntity source;
        ItemEntity loose = null;
        if (mode.equals("extract_store")) {
            sourcePos = new BlockPos(2, 2, 4);
            var input = BulkGameplayFixtures.tile(h, sourcePos, Blocks.CHEST, ChestBlockEntity.class); input.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
            source = seal(h, sourcePos, Direction.EAST, new SealEmpty(), golem);
        } else {
            source = seal(h, sourcePos, Direction.UP, new SealPickup(), golem);
            var pos = h.absolutePos(sourcePos.above());
            loose = new ItemEntity(h.getLevel(), pos.getX() + .5, pos.getY() + .05, pos.getZ() + .5, new ItemStack(Items.IRON_INGOT, 8));
            loose.setNoPickUpDelay(); h.getLevel().addFreshEntity(loose);
        }
        var target = seal(h, targetPos, Direction.WEST, new SealFill(), golem); target.setPriority((byte) 2);
        if (mode.equals("wrong_owner")) { source.setOwner(java.util.UUID.randomUUID().toString()); target.setOwner(source.getOwner()); }
        if (mode.equals("wrong_color")) { source.setColor((byte) 3); target.setColor((byte) 3); golem.setGolemColor((byte) 2); }
        if (mode.equals("redstone")) { source.setRedstoneSensitive(true); h.setBlock(sourcePos.north(), Blocks.REDSTONE_BLOCK); }
        var observedLoose = loose; var powerPos = sourcePos.north();
        if (mode.equals("wrong_owner") || mode.equals("wrong_color")) {
            h.runAtTickTime(120, () -> { h.assertTrue(destination.isEmpty() && golem.getCarrying().stream().allMatch(ItemStack::isEmpty) && observedLoose.isAlive(), "owner/color restriction prevents theft"); cleanup(h, golem, source, target); h.succeed(); });
            return;
        }
        if (mode.equals("redstone")) h.runAtTickTime(60, () -> { h.assertTrue(destination.isEmpty() && observedLoose.isAlive(), "powered seal stops native pickup"); h.setBlock(powerPos, Blocks.AIR); });
        h.succeedWhen(() -> {
            int stored = java.util.stream.IntStream.range(0, destination.getContainerSize()).map(i -> destination.getItem(i).is(Items.IRON_INGOT) ? destination.getItem(i).getCount() : 0).sum();
            h.assertTrue(stored == 8, "native " + legs + " " + mode + " pickup/extract→path→store completed; stored=" + stored + ", task=" + (golem.getTask() == null ? "none" : golem.getTask().getId()));
            h.assertTrue(golem.getCarrying().stream().allMatch(ItemStack::isEmpty) && golem.position().distanceToSqr(start) > .25, "golem moved and retained no duplicate cargo");
            h.assertTrue(golem.rankXp > 0, "actual task completion awards rank XP");
            cleanup(h, golem, source, target);
        });
    }
    @GameTest(template = "scan_test", batch = "bulk_golems", timeoutTicks = 650)
    public static void harvestSealReplantsUsingCarriedSeed(GameTestHelper h) {
        var golem = golem(h, "WALKER", "FINE"); BlockPos crop = new BlockPos(6, 2, 4);
        h.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(crop.above(2), Blocks.GLOWSTONE);
        h.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        golem.holdItem(new ItemStack(Items.WHEAT_SEEDS, 1));
        var seal = seal(h, new BlockPos(5, 1, 4), Direction.UP, new SealHarvest(), golem);
        seal.setArea(new BlockPos(2, 1, 1));
        h.succeedWhen(() -> {
            var state = h.getLevel().getBlockState(h.absolutePos(crop));
            h.assertTrue(state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) < 7 && golem.rankXp >= 3, "native harvesting and replant task finished; state=" + state + ", below=" + h.getLevel().getBlockState(h.absolutePos(crop.below())) + ", XP=" + golem.rankXp + ", cargo=" + golem.getCarrying() + ", seed=" + SealBlockActions.getSeed(Blocks.WHEAT) + ", seal=" + seal.writeNBT(h.getLevel().registryAccess()));
            h.assertTrue(!golem.isCarrying(new ItemStack(Items.WHEAT_SEEDS)), "replant paid the carried seed"); cleanup(h, golem, seal);
        });
    }
    @GameTest(template = "scan_test", batch = "bulk_golems", timeoutTicks = 650)
    public static void breakerSealUsesRegisteredBreakerArmAndNativeDrops(GameTestHelper h) {
        var golem = golem(h, "WALKER", "BREAKERS"); BlockPos ore = new BlockPos(6, 2, 4); h.setBlock(ore, Blocks.STONE);
        var seal = seal(h, ore.east(), Direction.WEST, new SealBreaker(), golem); h.setBlock(ore.east(), Blocks.STONE); seal.setArea(new BlockPos(1, 1, 1));
        h.succeedWhen(() -> { h.assertBlockPresent(Blocks.AIR, ore); h.assertTrue(golem.rankXp > 0, "native break completion awarded XP"); h.assertItemEntityPresent(Items.COBBLESTONE); cleanup(h, golem, seal); });
    }
    @GameTest(template = "scan_test", batch = "bulk_golems", timeoutTicks = 450)
    public static void guardSealSchedulesTargetAndFighterAiDealsDamage(GameTestHelper h) {
        var golem = golem(h, "WALKER", "CLAWS"); var target = h.spawnWithNoFreeWill(EntityType.COW, 6, 2, 4);
        var guard = new SealGuardAdvanced();
        for (int i = 0; i < guard.getToggles().length; i++) if (guard.getToggles()[i].key.equals("panimal")) guard.setToggle(i, true);
        var seal = seal(h, new BlockPos(5, 1, 4), Direction.UP, guard, golem); float before = target.getHealth();
        seal.setArea(new BlockPos(4, 1, 2));
        h.assertTrue(!guard.canGolemPerformTask(golem, new thaumcraft.api.golems.tasks.Task(seal.getSealPos(), golem)), "animal guard rejects its own golem before scheduling combat");
        h.succeedWhen(() -> { h.assertTrue(target.getHealth() < before, "guard task and native fighter goals damaged their actual target; traits=" + golem.getProperties().getTraits() + ", XP=" + golem.rankXp + ", target=" + golem.getTarget() + ", tasks=" + TaskHandler.getEntityTasksSorted(h.getLevel().dimension(), golem.getUUID(), golem).size() + ", pos=" + golem.position()); cleanup(h, golem, seal); });
    }
    @GameTest(template = "scan_test", batch = "bulk_golems")
    public static void allRegisteredSealsRoundtripConfigurationAndRemovalSuspendsTasks(GameTestHelper h) {
        var golem = golem(h, "WALKER", "FINE"); int count = 0;
        for (String key : SealHandler.getRegisteredSeals()) {
            var source = new SealEntity(h.getLevel(), new SealPos(h.absolutePos(new BlockPos(3, 1, 3)), Direction.UP), SealHandler.newSeal(key));
            source.setOwner(golem.getOwnerId().toString()); source.setLocked(true); source.setPriority((byte) 4); source.setColor((byte) 6); source.setRedstoneSensitive(true);
            if (source.getSeal() instanceof ISealConfigFilter filter) { filter.setBlacklist(false); filter.setFilterSlot(0, new ItemStack(Items.IRON_INGOT)); }
            var tag = source.writeNBT(h.getLevel().registryAccess()); var restored = new SealEntity(); restored.readNBT(tag, h.getLevel().registryAccess());
            h.assertTrue(restored.writeNBT(h.getLevel().registryAccess()).equals(tag), "native seal configuration NBT " + key); count++;
        }
        h.assertTrue(count >= 16, "all registered source seal types covered, got " + count);
        var installed = seal(h, new BlockPos(3, 1, 3), Direction.UP, new SealPickup(), golem);
        var task = new thaumcraft.api.golems.tasks.Task(installed.getSealPos(), h.absolutePos(new BlockPos(3, 2, 3))); TaskHandler.addTask(h.getLevel().dimension(), task);
        SealHandler.removeSealEntity(h.getLevel(), installed.getSealPos(), true);
        h.assertTrue(task.isSuspended() && SealHandler.getSealEntity(h.getLevel(), installed.getSealPos()) == null, "removal releases seal and suspends its actual queued tasks");
        golem.discard(); h.succeed();
    }
}
