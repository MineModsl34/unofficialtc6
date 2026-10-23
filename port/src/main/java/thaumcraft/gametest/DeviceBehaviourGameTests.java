package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.tiles.devices.*;
import thaumcraft.common.tiles.essentia.*;
import thaumcraft.common.world.aura.AuraHandler;

/** Native producers, consumers, capabilities and redstone, sharing one GameTest launch. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class DeviceBehaviourGameTests {
    private DeviceBehaviourGameTests() {}
    @GameTestGenerator public static Collection<TestFunction> networksAndSmelters() {
        var tests = new ArrayList<TestFunction>();
        for (String mode : List.of("normal", "restrict", "filter", "filter_reject", "oneway", "oneway_reverse", "valve", "buffer"))
            tests.add(new TestFunction("bulk_devices", "thaumcraft.bulk_transport_" + mode, "thaumcraft:scan_test", 250, 0, true, h -> network(h, mode)));
        for (int tier = 0; tier < 3; tier++) {
            int selected = tier;
            tests.add(new TestFunction("bulk_devices", "thaumcraft.bulk_smelter_" + tier, "thaumcraft:scan_test", 320, 0, true, h -> smelter(h, selected)));
        }
        return tests;
    }
    private static void network(GameTestHelper h, String mode) {
        BlockPos sourcePos = new BlockPos(3, 3, 4), pipePos = sourcePos.east(), jarPos = pipePos.below();
        var source = BulkGameplayFixtures.tile(h, sourcePos, BlocksTC.alembic, TileAlembic.class); source.aspect = Aspect.AIR; source.amount = 8;
        Block block = switch (mode) { case "restrict" -> BlocksTC.tubeRestrict; case "filter", "filter_reject" -> BlocksTC.tubeFilter; case "oneway", "oneway_reverse" -> BlocksTC.tubeOneway; case "valve" -> BlocksTC.tubeValve; case "buffer" -> BlocksTC.tubeBuffer; default -> BlocksTC.tube; };
        var pipe = BulkGameplayFixtures.tile(h, pipePos, block, TileTube.class);
        var jar = BulkGameplayFixtures.tile(h, jarPos, BlocksTC.jarNormal, TileJarFillable.class); jar.aspectFilter = Aspect.AIR;
        if (pipe instanceof TileTubeFilter filter) filter.aspectFilter = mode.equals("filter_reject") ? Aspect.FIRE : Aspect.AIR;
        pipe.facing = mode.equals("oneway_reverse") ? Direction.WEST : mode.equals("oneway") ? Direction.UP : Direction.NORTH;
        if (mode.equals("valve")) h.setBlock(pipePos.north(), Blocks.REDSTONE_BLOCK);
        h.runAtTickTime(60, () -> {
            if (List.of("filter_reject", "oneway_reverse", "valve").contains(mode)) {
                h.assertTrue(jar.amount == 0 && source.amount == 8, "closed/ineligible native route retains all eight source units: " + mode);
                if (mode.equals("filter_reject")) { h.succeed(); return; }
                if (mode.equals("oneway_reverse")) pipe.facing = Direction.UP;
                else h.setBlock(pipePos.north(), Blocks.AIR);
            }
        });
        if (!mode.equals("filter_reject")) h.succeedWhen(() -> {
            h.assertTrue(jar.amount == 8 && jar.aspect == Aspect.AIR, "native route delivers eight units: " + mode + ", got " + jar.amount);
            h.assertTrue(source.amount == 0 && pipe.getEssentiaAmount(Direction.DOWN) == 0, "network conserves essentia, including pipe escrow");
            var clone = new TileJarFillable(jar.getBlockPos(), jar.getBlockState()); clone.loadWithComponents(jar.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            h.assertTrue(clone.amount == 8 && clone.aspectFilter == Aspect.AIR && clone.aspect == Aspect.AIR, "native jar state survives NBT reload");
        });
    }
    private static void smelter(GameTestHelper h, int tier) {
        BlockPos pos = new BlockPos(4, 2, 4);
        var smelter = BulkGameplayFixtures.tile(h, pos, tier == 0 ? BlocksTC.smelterBasic : tier == 1 ? BlocksTC.smelterThaumium : BlocksTC.smelterVoid, TileSmelter.class);
        var alembic = BulkGameplayFixtures.tile(h, pos.above(), BlocksTC.alembic, TileAlembic.class);
        smelter.setItem(0, new ItemStack(ItemsTC.amber, 8)); smelter.setItem(1, new ItemStack(Items.COAL, 2));
        h.succeedWhen(() -> {
            h.assertTrue(smelter.getItem(0).getCount() < 8 && smelter.getItem(1).getCount() < 2, "tier " + tier + " consumed real input and fuel");
            h.assertTrue(alembic.amount > 0 && alembic.aspect != null, "tier " + tier + " exported distilled aspects to actual alembic");
        });
    }
    @GameTest(template = "scan_test", batch = "bulk_devices", timeoutTicks = 130)
    public static void centrifugeConsumesOneCompoundAndProducesOnePrimal(GameTestHelper h) {
        var source = BulkGameplayFixtures.tile(h, new BlockPos(4, 2, 4), BlocksTC.tubeBuffer, TileTubeBuffer.class);
        for (int i = 0; i < 4; i++) h.assertTrue(source.addToContainer(Aspect.LIGHT, 1) == 0, "source mixed buffer filled");
        var centrifuge = BulkGameplayFixtures.tile(h, new BlockPos(4, 3, 4), BlocksTC.centrifuge, TileCentrifuge.class);
        h.succeedWhen(() -> {
            h.assertTrue(centrifuge.aspectOut == Aspect.AIR || centrifuge.aspectOut == Aspect.FIRE, "native compound decomposition selects a source component");
            h.assertTrue(source.aspects.getAmount(Aspect.LIGHT) == 3 && centrifuge.getEssentiaAmount(Direction.UP) == 1, "one compound becomes exactly one output, not both components");
        });
    }
    @GameTest(template = "scan_test", batch = "bulk_devices", timeoutTicks = 130)
    public static void urnAndSpaUseNativeFluidTransactions(GameTestHelper h) {
        BlockPos urnPos = new BlockPos(3, 3, 3);
        var urn = BulkGameplayFixtures.tile(h, urnPos, BlocksTC.everfullUrn, TileFluidDevice.class);
        var handler = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, urn.getBlockPos(), Direction.UP);
        h.assertTrue(handler != null && handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE) == 1000 && urn.tank.isEmpty(), "urn fluid simulation preserves tank");
        handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        BlockPos cauldron = TileFluidDevice.urnPosition(urnPos, 1); h.setBlock(cauldron, Blocks.CAULDRON);
        var spa = BulkGameplayFixtures.tile(h, new BlockPos(7, 2, 7), BlocksTC.spa, TileFluidDevice.class);
        spa.tank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE); spa.setItem(0, new ItemStack(ItemsTC.bathSalts));
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.WATER_CAULDRON, cauldron);
            h.assertBlockPresent(BlocksTC.purifyingFluid, new BlockPos(7, 3, 7));
            h.assertTrue(spa.tank.isEmpty() && spa.getItem(0).isEmpty(), "spa paid a water bucket and one salt");
        });
    }
    @GameTest(template = "scan_test", batch = "bulk_devices", timeoutTicks = 140)
    public static void nativePatternCrafterConsumesGridAndDepositsOneResult(GameTestHelper h) {
        var input = BulkGameplayFixtures.tile(h, new BlockPos(4, 4, 4), Blocks.CHEST, ChestBlockEntity.class);
        var output = BulkGameplayFixtures.tile(h, new BlockPos(4, 2, 4), Blocks.CHEST, ChestBlockEntity.class);
        var crafter = BulkGameplayFixtures.tile(h, new BlockPos(4, 3, 4), BlocksTC.patternCrafter, TilePatternCrafter.class);
        input.setItem(0, new ItemStack(Items.GOLD_NUGGET, 9)); crafter.power = 2; crafter.type = 0;
        h.succeedWhen(() -> { h.assertTrue(input.isEmpty(), "nine real nuggets consumed"); h.assertTrue(output.getItem(0).is(Items.GOLD_INGOT) && output.getItem(0).getCount() == 1, "one actual native 3x3 recipe result inserted"); h.assertTrue(crafter.power == 1, "one cached vis paid"); });
    }
    @GameTest(template = "scan_test", batch = "bulk_devices", timeoutTicks = 60)
    public static void storageCollisionAndMergedXpCountConserveInputs(GameTestHelper h) {
        var chest = BulkGameplayFixtures.tile(h, new BlockPos(2, 2, 2), BlocksTC.hungryChest, TileStorageDevice.class);
        var drop = Vec3Helper.drop(h, chest.getBlockPos(), new ItemStack(ItemsTC.amber, 8));
        var brain = BulkGameplayFixtures.tile(h, new BlockPos(7, 2, 7), BlocksTC.jarBrain, TileStorageDevice.class);
        var orb = new ExperienceOrb(h.getLevel(), brain.getBlockPos().getX() + .5, brain.getBlockPos().getY() + .5, brain.getBlockPos().getZ() + .5, 5);
        var tag = new CompoundTag(); orb.saveWithoutId(tag); tag.putInt("Count", 3); orb.load(tag); h.getLevel().addFreshEntity(orb);
        h.succeedWhen(() -> { h.assertTrue(chest.getItem(0).getCount() == 8, "hungry chest consumed the actual colliding item; drop=" + drop.position() + ", alive=" + drop.isAlive() + ", chest=" + chest.getBlockPos() + ", stored=" + chest.getItem(0) + ", brain=" + brain.xp); h.assertTrue(brain.xp == 15 && !orb.isAlive(), "brain stores Value*Count from merged orb without loss"); });
    }
    @GameTest(template = "scan_test", batch = "bulk_devices")
    public static void visGeneratorNativeCapabilityAndNbtPreserveEnergy(GameTestHelper h) {
        var generator = BulkGameplayFixtures.tile(h, new BlockPos(5, 2, 5), BlocksTC.visGenerator, TileEnergyDevice.class);
        var chunk = h.getLevel().getChunkAt(generator.getBlockPos()); var aura = AuraHandler.getAuraChunk(h.getLevel(), chunk.getPos().x, chunk.getPos().z);
        h.assertTrue(aura != null, "generator uses real loaded aura"); float previous = aura.getVis();
        try {
            aura.setVis(100); generator.update();
            h.assertTrue(generator.energy == 1000 && aura.getVis() == 99, "one vis becomes exactly 1000 native energy");
            var cap = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, generator.getBlockPos(), generator.facing());
            h.assertTrue(cap != null && cap.getEnergyStored() == 1000 && cap.extractEnergy(50, false) == 0, "generator exposes source push-only energy contract");
            var clone = new TileEnergyDevice(generator.getBlockPos(), generator.getBlockState()); clone.loadWithComponents(generator.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            h.assertTrue(clone.energy == 1000, "energy survives native save/load");
        } finally { aura.setVis(previous); }
        h.succeed();
    }
    private static final class Vec3Helper {
        static ItemEntity drop(GameTestHelper h, BlockPos pos, ItemStack stack) {
            var item = new ItemEntity(h.getLevel(), pos.getX() + .5, pos.getY() + 1.1, pos.getZ() + .5, stack);
            item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); item.setNoPickUpDelay(); h.getLevel().addFreshEntity(item); return item;
        }
    }
}
