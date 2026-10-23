package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.casters.*;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.monster.EntitySpellBat;
import thaumcraft.common.entities.projectile.*;
import thaumcraft.common.items.casters.*;
import thaumcraft.common.items.casters.foci.*;
import thaumcraft.common.world.aura.AuraHandler;

/** All effect families and delivery entities execute against the real world in one server. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class FocusBehaviourGameTests {
    private FocusBehaviourGameTests() {}
    private static FocusNode node(String key) { return (FocusNode) FocusEngine.getElement("thaumcraft." + key); }
    private static void setting(FocusNode node, String key, int value) { if (node.getSetting(key) != null) node.getSetting(key).setValue(value); }
    private static FocusPackage pack(LivingEntity caster, Trajectory trajectory, HitResult hit, IFocusElement... nodes) {
        var pack = new FocusPackage(caster); pack.setUniqueID(UUID.randomUUID());
        pack.addNode(new FocusMediumRoot(new Trajectory[]{trajectory}, new HitResult[]{hit}));
        for (var node : nodes) pack.addNode(node);
        return pack;
    }
    @GameTestGenerator
    public static Collection<TestFunction> effectsAndDeliveries() {
        var tests = new ArrayList<TestFunction>();
        for (String effect : List.of("AIR", "FIRE", "FROST", "EARTH", "FLUX", "HEAL", "CURSE")) for (int rank : new int[]{1, 5}) {
            tests.add(test("effect_" + effect.toLowerCase() + "_" + rank, 30, h -> entityEffect(h, effect, rank)));
        }
        for (String medium : List.of("TOUCH", "BOLT", "PROJECTILE", "CLOUD", "MINE", "SPELLBAT")) {
            int options = medium.equals("PROJECTILE") ? 4 : medium.equals("SPELLBAT") ? 2 : 1;
            for (int option = 0; option < options; option++) {
                int selected = option;
                tests.add(test("delivery_" + medium.toLowerCase() + "_" + option, 240, h -> delivery(h, medium, selected)));
            }
        }
        return tests;
    }
    private static TestFunction test(String name, int ticks, java.util.function.Consumer<GameTestHelper> body) {
        return new TestFunction("bulk_focus", "thaumcraft.bulk_focus_" + name, "thaumcraft:scan_test", ticks, 0L, true, body);
    }
    private static void entityEffect(GameTestHelper h, String key, int rank) {
        var caster = BulkGameplayFixtures.player(h);
        var target = EntityType.COW.create(h.getLevel()); target.setPos(h.absolutePos(new BlockPos(3, 2, 3)).getCenter());
        target.setHealth(key.equals("HEAL") ? 1 : target.getMaxHealth());
        float before = target.getHealth(), power = rank == 1 ? 1 : .5F;
        var effect = (FocusEffect) node(key); setting(effect, "power", rank); setting(effect, "duration", 3);
        Vec3 center = target.position(); var trajectory = new Trajectory(caster.getEyePosition(), center.subtract(caster.getEyePosition()).normalize());
        var p = pack(caster, trajectory, new EntityHitResult(target), effect); p.multiplyPower(power);
        FocusEngine.runFocusPackage(p, null, null);
        float expected = switch (key) { case "AIR", "CURSE" -> (1 + rank) * power; case "EARTH" -> 2 * rank * power; case "HEAL" -> -rank * power; default -> (3 + rank) * power; };
        h.assertTrue(Math.abs(target.getHealth() - (before - expected)) < .001, "source effect damage/healing " + key + ": " + target.getHealth());
        if (key.equals("FIRE")) h.assertTrue(target.getRemainingFireTicks() > 0, "fire duration applied");
        if (key.equals("FROST")) h.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "frost slows target");
        if (key.equals("CURSE")) h.assertTrue(target.hasEffect(MobEffects.POISON), "curse applies poison independently of random secondary effects");
        if (key.equals("AIR")) h.assertTrue(target.getDeltaMovement().lengthSqr() > 0, "air knocks back");
        h.succeed();
    }
    private static void delivery(GameTestHelper h, String key, int option) {
        BulkGameplayFixtures.floor(h);
        LivingEntity caster = key.equals("TOUCH") || key.equals("BOLT") ? BulkGameplayFixtures.player(h) : h.spawn(EntityType.COW, 2, 2, 1);
        if (caster instanceof Cow cow) cow.setNoAi(true);
        var target = h.spawnWithNoFreeWill(EntityType.COW, 2, 2, 4); target.setNoGravity(true);
        boolean friendly = key.equals("PROJECTILE") && option == 3 || key.equals("SPELLBAT") && option == 1;
        LivingEntity actualTarget = target;
        if (key.equals("SPELLBAT") && friendly) {
            target.discard(); caster.setHealth(1); actualTarget = caster;
        } else if (friendly) {
            target.discard(); var pet = h.spawnWithNoFreeWill(EntityType.WOLF, 2, 2, 4);
            pet.setOwnerUUID(caster.getUUID()); pet.setTame(true, false); pet.setNoGravity(true); pet.setHealth(1); actualTarget = pet;
        }
        float before = actualTarget.getHealth(); var observedTarget = actualTarget;
        var medium = node(key); setting(medium, "option", option); setting(medium, "target", friendly ? 1 : 0); setting(medium, "speed", 3);
        var effect = (FocusEffect) node(friendly ? "HEAL" : "FLUX"); setting(effect, "power", 1);
        Vec3 source = caster.getEyePosition();
        if (key.equals("CLOUD") || key.equals("MINE")) source = actualTarget.position().add(0, .25, 0);
        Vec3 direction = actualTarget.position().add(0, .6, 0).subtract(source).normalize();
        if (key.equals("PROJECTILE") && option == 1) {
            source = h.absolutePos(new BlockPos(2, 2, 6)).getCenter(); direction = new Vec3(0, 0, 1);
            for (int y = 2; y < 5; y++) for (int x = 1; x < 4; x++) h.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
        }
        var p = pack(caster, new Trajectory(source, direction), new EntityHitResult(caster), medium, effect);
        FocusEngine.castFocusPackage(caster, p, true);
        if (key.equals("PROJECTILE")) h.assertTrue(!h.getLevel().getEntitiesOfClass(EntityFocusProjectile.class, h.getBounds()).isEmpty(), "native projectile was spawned");
        if (key.equals("CLOUD")) h.assertTrue(!h.getLevel().getEntitiesOfClass(EntityFocusCloud.class, h.getBounds()).isEmpty(), "native cloud was spawned");
        if (key.equals("MINE")) h.assertTrue(!h.getLevel().getEntitiesOfClass(EntityFocusMine.class, h.getBounds()).isEmpty(), "native mine was spawned");
        if (key.equals("SPELLBAT")) h.assertTrue(!h.getLevel().getEntitiesOfClass(EntitySpellBat.class, h.getBounds()).isEmpty(), "native spell bat was spawned");
        h.succeedWhen(() -> h.assertTrue(friendly ? observedTarget.getHealth() > before : observedTarget.getHealth() < before,
            "native " + key + " option " + option + " reached its real target; hp=" + observedTarget.getHealth()));
    }

    @GameTest(template = "scan_test", batch = "bulk_focus_blocks", timeoutTicks = 130)
    public static void blockEffectsAndRiftRestoreRememberedStates(GameTestHelper h) {
        BulkGameplayFixtures.floor(h); var caster = BulkGameplayFixtures.player(h);
        BlockPos origin = new BlockPos(5, 3, 5); h.setBlock(origin, Blocks.STONE);
        var rift = (FocusEffect) node("RIFT"); setting(rift, "duration", 2); setting(rift, "depth", 8);
        for (int z = 5; z <= 7; z++) h.setBlock(new BlockPos(5, 3, z), z == 6 ? Blocks.GRANITE : Blocks.STONE);
        var at = h.absolutePos(origin); var hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false);
        var p = pack(caster, new Trajectory(Vec3.atCenterOf(at), new Vec3(0, 0, 1)), hit, rift);
        FocusEngine.castFocusPackage(caster, p, true);
        h.assertBlockPresent(BlocksTC.hole, origin);
        h.runAtTickTime(8, () -> { h.assertBlockPresent(BlocksTC.hole, origin.south()); h.assertBlockPresent(BlocksTC.hole, origin.south(2)); });
        h.runAtTickTime(90, () -> { h.assertBlockPresent(Blocks.STONE, origin); h.assertBlockPresent(Blocks.GRANITE, origin.south()); h.assertBlockPresent(Blocks.STONE, origin.south(2)); h.succeed(); });
    }

    @GameTest(template = "scan_test", batch = "bulk_focus_blocks")
    public static void fireFrostCurseAndPlanChangeOnlyEligibleBlocks(GameTestHelper h) {
        BulkGameplayFixtures.floor(h); var caster = BulkGameplayFixtures.player(h);
        BlockPos firePos = new BlockPos(2, 1, 3), water = new BlockPos(6, 2, 6), cursePos = new BlockPos(3, 1, 7);
        h.setBlock(water, Blocks.WATER);
        for (String key : List.of("FIRE", "FROST", "CURSE")) {
            var effect = (FocusEffect) node(key); setting(effect, "power", 1); setting(effect, "duration", 2);
            BlockPos relative = key.equals("FIRE") ? firePos : key.equals("FROST") ? water : cursePos;
            BlockPos absolute = h.absolutePos(relative); var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
            FocusEngine.castFocusPackage(caster, pack(caster, new Trajectory(Vec3.atCenterOf(absolute), Vec3.ZERO), hit, effect), true);
        }
        h.assertBlockPresent(Blocks.FIRE, firePos.above()); h.assertBlockPresent(Blocks.FROSTED_ICE, water); h.assertBlockPresent(BlocksTC.effectSap, cursePos.above());
        ItemStack wand = new ItemStack(ItemsTC.casterBasic); CasterManager.setAreaX(wand, 1); CasterManager.setAreaY(wand, 0); CasterManager.setAreaZ(wand, 1);
        var plan = new FocusMediumPlan(); setting(plan, "method", 1);
        var selected = plan.getArchitectBlocks(wand, h.getLevel(), h.absolutePos(new BlockPos(6, 1, 2)), Direction.UP, caster);
        h.assertTrue(selected.size() == 9, "source surface plan covers its actual 3x3 stone face: " + selected.size());
        setting(plan, "method", 0);
        selected = plan.getArchitectBlocks(wand, h.getLevel(), h.absolutePos(new BlockPos(6, 1, 2)), Direction.UP, caster);
        h.assertTrue(selected.size() == 9, "full plan includes the same bounded one-layer volume");
        h.succeed();
    }

    @GameTest(template = "scan_test", batch = "bulk_focus_blocks", timeoutTicks = 100)
    public static void breakAndExchangeUseRealInventoryAuraAndDeferredJobs(GameTestHelper h) {
        BulkGameplayFixtures.floor(h); var caster = BulkGameplayFixtures.player(h);
        var chunk = h.getLevel().getChunkAt(caster.blockPosition()); var aura = AuraHandler.getAuraChunk(h.getLevel(), chunk.getPos().x, chunk.getPos().z);
        h.assertTrue(aura != null, "loaded world aura exists"); aura.setVis(200);
        ItemStack wand = new ItemStack(ItemsTC.casterBasic), focus = new ItemStack(ItemsTC.focus1);
        var exchange = (FocusEffect) node("EXCHANGE"); var focusPack = new FocusPackage(caster); focusPack.addNode(exchange); ItemFocus.setPackage(focus, focusPack);
        ((ItemCaster) wand.getItem()).setFocus(wand, focus); ((ItemCaster) wand.getItem()).storePickedBlock(wand, new ItemStack(Items.DIRT));
        caster.setItemInHand(InteractionHand.MAIN_HAND, wand); caster.getInventory().setItem(1, new ItemStack(Items.DIRT, 2));
        BlockPos replace = new BlockPos(3, 2, 3), destroy = new BlockPos(5, 2, 3); h.setBlock(replace, Blocks.STONE); h.setBlock(destroy, Blocks.STONE);
        for (var entry : List.of(java.util.Map.entry(replace, exchange), java.util.Map.entry(destroy, (FocusEffect) node("BREAK")))) {
            var effect = entry.getValue(); setting(effect, "power", 5); var absolute = h.absolutePos(entry.getKey());
            var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
            FocusEngine.castFocusPackage(caster, pack(caster, new Trajectory(caster.getEyePosition(), new Vec3(0, 0, 1)), hit, effect), true);
        }
        h.succeedWhen(() -> { h.assertBlockPresent(Blocks.DIRT, replace); h.assertBlockPresent(Blocks.AIR, destroy); h.assertTrue(caster.getInventory().getItem(1).getCount() == 1, "exchange paid exactly one real replacement item"); h.assertTrue(aura.getVis() < 200, "deferred jobs paid aura"); });
    }

    @GameTest(template = "scan_test", batch = "bulk_focus")
    public static void splitBranchesScatterAndPackageReloadPreserveExecution(GameTestHelper h) {
        var caster = h.spawn(EntityType.COW, 2, 2, 1); caster.setNoAi(true); var target = EntityType.COW.create(h.getLevel());
        var split = new FocusModSplitTarget();
        for (String key : List.of("FROST", "FLUX")) { var branch = new FocusPackage(caster); branch.addNode(node(key)); split.getSplitPackages().add(branch); }
        var p = pack(caster, new Trajectory(caster.getEyePosition(), new Vec3(0, 0, 1)), new EntityHitResult(target), split);
        var restored = p.copy(caster);
        // Source NBT intentionally omits ephemeral ray/target seeds. Delivery resumption
        // supplies a fresh ROOT, exactly as the projectile/cloud engine entry point does.
        restored.nodes.removeFirst();
        FocusEngine.runFocusPackage(restored, new Trajectory[]{new Trajectory(caster.getEyePosition(), new Vec3(0, 0, 1))}, new HitResult[]{new EntityHitResult(target)});
        h.assertTrue(Math.abs(target.getHealth() - 4) < .001 && target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "both split effects execute at source 0.75 power despite hit immunity: " + target.getHealth());
        var scatter = new FocusModScatter(); setting(scatter, "forks", 10); scatter.setPackage(p); scatter.setParent((FocusNode) p.nodes.getFirst());
        var trajectories = scatter.supplyTrajectories(); h.assertTrue(trajectories.length == 10, "scatter supplies ten real rays");
        for (var t : trajectories) h.assertTrue(Math.abs(t.direction.length() - 1) < .0001, "scatter ray is normalized");
        var physicalTarget = h.spawnWithNoFreeWill(EntityType.COW, 2, 2, 3); physicalTarget.setNoGravity(true);
        var player = BulkGameplayFixtures.connectedPlayer(h);
        try {
            caster.discard();
            var trajectorySplit = new FocusModSplitTrajectory();
            for (String key : List.of("FROST", "FLUX")) { var branch = new FocusPackage(player); branch.addNode(new FocusMediumBolt()); branch.addNode(node(key)); trajectorySplit.getSplitPackages().add(branch); }
            var ray = new Trajectory(player.getEyePosition(), physicalTarget.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize());
            FocusEngine.castFocusPackage(player, pack(player, ray, null, trajectorySplit), true);
            h.assertTrue(Math.abs(physicalTarget.getHealth() - 4) < .001 && physicalTarget.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "trajectory split delivers both real bolt branches at 0.75 power: " + physicalTarget.getHealth());
        } finally { BulkGameplayFixtures.removeConnected(player); }
        h.succeed();
    }
}
