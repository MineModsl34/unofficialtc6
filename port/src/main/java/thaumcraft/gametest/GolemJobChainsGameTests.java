package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.seals.ISealConfigToggles;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.seals.*;

/** Provider requests, stock quotas, actual block interactions, connected logs and combat. */
@GameTestHolder(Thaumcraft.MODID)
public final class GolemJobChainsGameTests {
    @GameTestGenerator public static Collection<TestFunction> chains() {
        var tests = new ArrayList<TestFunction>();
        for (String mode : List.of("stock", "single", "leave_one", "filter_resume", "powered_resume")) tests.add(test("provider_" + mode, 1100, h -> stock(h, mode)));
        for (String mode : List.of("empty_lever", "place", "provided_place", "left_break")) tests.add(test("use_" + mode, 750, h -> use(h, mode)));
        for (String wood : List.of("oak", "birch", "spruce")) tests.add(test("lumber_" + wood, 800, h -> lumber(h, wood)));
        for (String mode : List.of("surplus", "two_adults", "baby", "tamed")) tests.add(test("butcher_" + mode, 650, h -> butcher(h, mode)));
        return tests;
    }
    private static TestFunction test(String name, int ticks, java.util.function.Consumer<GameTestHelper> body) {
        // Stock requests are public positions: source providers intentionally serve any
        // such request within 64 blocks. Separate batches retain one JVM without cross-feeding.
        String batch = name.startsWith("provider_") ? "bulk_golem_" + name : "bulk_golem_chains";
        return new TestFunction(batch, "thaumcraft.bulk_job_" + name, "thaumcraft:scan_test", ticks, 0, true, body);
    }
    private static void toggle(ISealConfigToggles seal, String key, boolean value) { for (int i = 0; i < seal.getToggles().length; i++) if (seal.getToggles()[i].key.equals(key)) seal.setToggle(i, value); }
    private static int stored(ChestBlockEntity chest, net.minecraft.world.item.Item item) { int n = 0; for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(item)) n += chest.getItem(i).getCount(); return n; }
    private static int carried(EntityThaumcraftGolem golem, net.minecraft.world.item.Item item) { return golem.getCarrying().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private static void clearRequests(GameTestHelper h, BlockPos target) { var requests = GolemHelper.provisionRequests.get(h.getLevel().dimension()); if (requests != null) requests.removeIf(r -> h.absolutePos(target).equals(r.getPos()) || r.getSeal() != null && r.getSeal().getSealPos().pos.equals(h.absolutePos(target))); }
    private static void stock(GameTestHelper h, String mode) {
        var golem = GolemBehaviourGameTests.golem(h, "WALKER", "FINE"); BlockPos sourcePos = new BlockPos(2, 2, 4), targetPos = new BlockPos(7, 2, 4);
        var source = BulkGameplayFixtures.tile(h, sourcePos, Blocks.CHEST, ChestBlockEntity.class); var target = BulkGameplayFixtures.tile(h, targetPos, Blocks.CHEST, ChestBlockEntity.class);
        source.setItem(0, new ItemStack(Items.IRON_INGOT, mode.equals("leave_one") ? 5 : 8));
        var provider = new SealProvide(); if (mode.equals("single")) toggle(provider, "psing", true); if (mode.equals("leave_one")) toggle(provider, "pleave", true);
        if (mode.equals("filter_resume")) { provider.setBlacklist(false); provider.setFilterSlot(0, new ItemStack(Items.GOLD_INGOT)); }
        var provideSeal = GolemBehaviourGameTests.seal(h, sourcePos, Direction.EAST, provider, golem);
        var stock = new SealStock(); stock.setFilterSlot(0, new ItemStack(Items.IRON_INGOT)); stock.setFilterSlotSize(0, 4);
        var stockSeal = GolemBehaviourGameTests.seal(h, targetPos, Direction.WEST, stock, golem);
        if (mode.equals("powered_resume")) { stockSeal.setRedstoneSensitive(true); h.setBlock(targetPos.above(), Blocks.REDSTONE_BLOCK); }
        if (mode.equals("filter_resume") || mode.equals("powered_resume")) h.runAtTickTime(100, () -> { h.assertTrue(target.isEmpty() && source.getItem(0).getCount() == 8 && carried(golem, Items.IRON_INGOT) == 0, "blocked stock/provider request pays no source cargo"); if (mode.equals("filter_resume")) provider.setFilterSlot(0, new ItemStack(Items.IRON_INGOT)); else h.setBlock(targetPos.above(), Blocks.AIR); });
        h.succeedWhen(() -> { int n = stored(target, Items.IRON_INGOT); h.assertTrue(n == 4, "native request→provider→path→stock reached exact quota: " + n); h.assertTrue(stored(source, Items.IRON_INGOT) == (mode.equals("leave_one") ? 1 : 4) && carried(golem, Items.IRON_INGOT) == 0, "source leftovers and cargo conserved without quota overflow"); GolemBehaviourGameTests.cleanup(h, golem, provideSeal, stockSeal); clearRequests(h, targetPos); });
    }
    private static void use(GameTestHelper h, String mode) {
        var golem = GolemBehaviourGameTests.golem(h, "WALKER", "FINE"); var type = new SealUse(); BlockPos pos = new BlockPos(6, 2, 4);
        h.setBlock(pos, mode.equals("empty_lever") ? Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : mode.equals("left_break") ? Blocks.TORCH.defaultBlockState() : Blocks.STONE.defaultBlockState());
        if (mode.equals("empty_lever") || mode.equals("left_break")) { toggle(type, "pemptyhand", true); golem.holdItem(new ItemStack(Items.IRON_INGOT, 3)); }
        if (mode.equals("left_break")) toggle(type, "pleft", true);
        if (mode.equals("place") || mode.equals("provided_place")) { type.setBlacklist(false); type.setFilterSlot(0, new ItemStack(Items.DIRT)); if (mode.equals("place")) golem.holdItem(new ItemStack(Items.DIRT, 3)); else toggle(type, "ppro", true); }
        var seal = GolemBehaviourGameTests.seal(h, pos, Direction.UP, type, golem);
        SealEntity provide = null; ChestBlockEntity source = null;
        if (mode.equals("provided_place")) { var sourcePos = new BlockPos(2, 2, 4); source = BulkGameplayFixtures.tile(h, sourcePos, Blocks.CHEST, ChestBlockEntity.class); source.setItem(0, new ItemStack(Items.DIRT, 3)); provide = GolemBehaviourGameTests.seal(h, sourcePos, Direction.EAST, new SealProvide(), golem); }
        var provider = provide; var chest = source;
        h.succeedWhen(() -> {
            if (mode.equals("empty_lever")) h.assertTrue(h.getLevel().getBlockState(h.absolutePos(pos)).getValue(LeverBlock.POWERED) && carried(golem, Items.IRON_INGOT) == 3, "empty-hand native lever interaction preserved carried items");
            else if (mode.equals("left_break")) { h.assertBlockPresent(Blocks.AIR, pos); h.assertTrue(carried(golem, Items.IRON_INGOT) == 3, "native left-click break preserved unused carried items"); h.assertItemEntityPresent(Items.TORCH); }
            else { h.assertBlockPresent(Blocks.DIRT, pos.above()); h.assertTrue(mode.equals("place") ? carried(golem, Items.DIRT) == 2 : stored(chest, Items.DIRT) + carried(golem, Items.DIRT) == 2, "exactly one real carried/provided block paid for native placement"); }
            h.assertTrue(golem.rankXp > 0, "actual use job completed");
            if (provider == null) GolemBehaviourGameTests.cleanup(h, golem, seal); else { GolemBehaviourGameTests.cleanup(h, golem, seal, provider); clearRequests(h, pos); }
        });
    }
    private static void lumber(GameTestHelper h, String wood) {
        var golem = GolemBehaviourGameTests.golem(h, "WALKER", "BREAKERS"); var log = wood.equals("oak") ? Blocks.OAK_LOG : wood.equals("birch") ? Blocks.BIRCH_LOG : Blocks.SPRUCE_LOG; BlockPos base = new BlockPos(6, 2, 4);
        h.setBlock(base, log); h.setBlock(base.above(), log); h.setBlock(base.above(2), log); h.setBlock(base.above(2).east(), log);
        var seal = GolemBehaviourGameTests.seal(h, base.below(), Direction.UP, new SealLumber(), golem); seal.setArea(new BlockPos(1, 1, 1));
        h.succeedWhen(() -> { for (var p : List.of(base, base.above(), base.above(2), base.above(2).east())) h.assertBlockPresent(Blocks.AIR, p); int drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, h.getBounds()).stream().filter(e -> e.getItem().is(log.asItem())).mapToInt(e -> e.getItem().getCount()).sum(); h.assertTrue(drops == 4 && golem.rankXp >= 4, "actual connected native tree yielded four paid harvest jobs and four real log drops: " + drops); GolemBehaviourGameTests.cleanup(h, golem, seal); });
    }
    private static void butcher(GameTestHelper h, String mode) {
        var golem = GolemBehaviourGameTests.golem(h, "WALKER", "CLAWS"); var animals = new ArrayList<net.minecraft.world.entity.LivingEntity>();
        for (int i = 0; i < (mode.equals("two_adults") ? 2 : 3); i++) {
            var animal = mode.equals("tamed") ? h.spawnWithNoFreeWill(EntityType.WOLF, 5 + i, 2, 4) : h.spawnWithNoFreeWill(EntityType.COW, 5 + i, 2, 4);
            if (animal instanceof net.minecraft.world.entity.animal.Wolf wolf) { wolf.setOwnerUUID(golem.getOwnerId()); wolf.setTame(true, false); }
            if (mode.equals("baby") && i == 2) ((Cow) animal).setBaby(true);
            animals.add(animal);
        }
        var seal = GolemBehaviourGameTests.seal(h, new BlockPos(6, 1, 4), Direction.UP, new SealButcher(), golem); seal.setArea(new BlockPos(4, 1, 2));
        if (!mode.equals("surplus")) { h.runAtTickTime(430, () -> { h.assertTrue(animals.stream().allMatch(e -> e.isAlive() && e.getHealth() == e.getMaxHealth()), "source butcher protects breeding pair, babies and tamed pets: " + mode); GolemBehaviourGameTests.cleanup(h, golem, seal); animals.forEach(BulkGameplayFixtures::remove); h.succeed(); }); return; }
        h.succeedWhen(() -> { h.assertTrue(animals.stream().filter(net.minecraft.world.entity.Entity::isAlive).count() == 2, "butcher removed one true surplus adult"); h.assertTrue(golem.rankXp > 0, "actual native butcher task and combat ran"); h.assertItemEntityPresent(Items.BEEF); GolemBehaviourGameTests.cleanup(h, golem, seal); animals.forEach(BulkGameplayFixtures::remove); });
    }
}
