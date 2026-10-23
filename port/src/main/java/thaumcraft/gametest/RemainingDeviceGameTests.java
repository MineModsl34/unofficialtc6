package thaumcraft.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.*;
import thaumcraft.common.blocks.devices.BlockWorldDevice;
import thaumcraft.common.tiles.devices.*;
import thaumcraft.common.tiles.essentia.TileAlembic;
import thaumcraft.common.world.aura.AuraHandler;

/** Remaining native controllers, paid actions and mirror queues; no surrogate endpoints. */
@GameTestHolder(Thaumcraft.MODID)
public final class RemainingDeviceGameTests {
    @GameTestGenerator public static Collection<TestFunction> controllers() {
        var tests = new ArrayList<TestFunction>();
        for (Direction face : List.of(Direction.UP, Direction.EAST, Direction.NORTH)) tests.add(test("levitator_" + face.getName(), h -> lift(h, face, false)));
        tests.add(test("levitator_disabled", h -> lift(h, Direction.UP, true)));
        tests.add(test("recharge", RemainingDeviceGameTests::recharge)); tests.add(test("relay", RemainingDeviceGameTests::relay)); tests.add(test("dioptra", RemainingDeviceGameTests::dioptra));
        tests.add(test("fertility", h -> fertility(h, false))); tests.add(test("fertility_powered", h -> fertility(h, true)));
        tests.add(test("growth", RemainingDeviceGameTests::growth)); tests.add(test("condenser", h -> condenser(h, false))); tests.add(test("condenser_clogged", h -> condenser(h, true)));
        tests.add(test("infernal", RemainingDeviceGameTests::infernal));
        for (String mode : List.of("pulse", "toggle", "wrong_note")) tests.add(test("ear_" + mode, h -> ear(h, mode)));
        tests.add(test("sprayer_paid", RemainingDeviceGameTests::sprayer)); tests.add(test("arcane_light_cleanup", RemainingDeviceGameTests::lights));
        for (String mode : List.of("items", "reload", "unlink", "nether", "essentia")) tests.add(test("mirror_" + mode, h -> mirror(h, mode)));
        return tests;
    }
    private static TestFunction test(String name, java.util.function.Consumer<GameTestHelper> f) { return new TestFunction("bulk_remaining_devices", "thaumcraft.bulk_device_" + name, "thaumcraft:scan_test", 850, 0, true, f); }
    private static void lift(GameTestHelper h, Direction face, boolean disabled) {
        BulkGameplayFixtures.floor(h); var pos = new BlockPos(5, 2, 5);
        var tile = BulkGameplayFixtures.tile(h, pos, BlocksTC.levitator, TileEnergyDevice.class);
        h.setBlock(pos, tile.getBlockState().setValue(BlockWorldDevice.FACING, face).setValue(BlockWorldDevice.ENABLED, !disabled)); tile.vis = 1200; tile.range = 0;
        var target = h.spawnWithNoFreeWill(EntityType.COW, pos.relative(face, 2)); target.setNoGravity(true); var start = target.position();
        h.runAtTickTime(8, () -> { h.assertTrue(disabled ? target.position().distanceToSqr(start) < .01 : target.position().subtract(start).dot(new net.minecraft.world.phys.Vec3(face.getStepX(), face.getStepY(), face.getStepZ())) > .1, "native levitator moves in its real direction only when enabled"); h.assertTrue(disabled ? tile.vis == 1200 : tile.vis < 1200, "actual lift pays source vis buffer"); target.discard(); h.succeed(); });
    }
    private static void recharge(GameTestHelper h) {
        var tile = BulkGameplayFixtures.tile(h, new BlockPos(5, 2, 5), BlocksTC.rechargePedestal, TileEnergyDevice.class); var stack = new ItemStack(ItemsTC.grappleGun); tile.setItem(0, stack);
        var chunk = h.getLevel().getChunkAt(tile.getBlockPos()); var aura = AuraHandler.getAuraChunk(h.getLevel(), chunk.getPos().x, chunk.getPos().z); h.assertTrue(aura != null, "native recharge aura exists"); float before = aura.getVis();
        try { aura.setVis(100); tile.update(); h.assertTrue(RechargeHelper.getCharge(tile.getItem(0)) == 5 && aura.getVis() == 95, "pedestal pays five actual aura for five real stored item charge: " + RechargeHelper.getCharge(tile.getItem(0)) + "/" + aura.getVis()); var data = tile.saveWithFullMetadata(h.getLevel().registryAccess()); tile.loadWithComponents(data, h.getLevel().registryAccess()); h.assertTrue(RechargeHelper.getCharge(tile.getItem(0)) == 5, "charged item survives native BE component save/load"); } finally { aura.setVis(before); } h.succeed();
    }
    private static void relay(GameTestHelper h) {
        BulkGameplayFixtures.floor(h); var tile = BulkGameplayFixtures.tile(h, new BlockPos(5, 2, 5), BlocksTC.redstoneRelay, TileSignalDevice.class); tile.in = 15; tile.out = 7;
        h.setBlock(new BlockPos(5, 2, 5).relative(tile.facing()), Blocks.REDSTONE_BLOCK);
        h.runAtTickTime(5, () -> { var state = h.getLevel().getBlockState(tile.getBlockPos()); h.assertTrue(state.getValue(BlockWorldDevice.ENABLED) && state.getSignal(h.getLevel(), tile.getBlockPos(), tile.facing()) == 7, "native relay uses configured input/output threshold"); h.setBlock(new BlockPos(5, 2, 5).relative(tile.facing()), Blocks.AIR); });
        h.runAtTickTime(12, () -> { h.assertTrue(!h.getLevel().getBlockState(tile.getBlockPos()).getValue(BlockWorldDevice.ENABLED), "relay scheduled tick switches off after signal removal"); h.succeed(); });
    }
    private static void dioptra(GameTestHelper h) {
        var tile = BulkGameplayFixtures.tile(h, new BlockPos(5, 2, 5), BlocksTC.dioptra, TileSignalDevice.class); var cp = h.getLevel().getChunkAt(tile.getBlockPos()).getPos(); var aura = AuraHandler.getAuraChunk(h.getLevel(), cp.x, cp.z); float v = aura.getVis(), f = aura.getFlux();
        try { aura.setVis(250); aura.setFlux(125); for (int i = 0; i < 20; i++) tile.update(); h.assertTrue(tile.grid_amt[84] == 32, "actual center chunk 250 vis maps to 32/64"); h.setBlock(new BlockPos(5, 2, 5), tile.getBlockState().setValue(BlockWorldDevice.ENABLED, false)); for (int i = 0; i < 20; i++) tile.update(); h.assertTrue(tile.grid_amt[84] == 16, "actual flux mode maps center 125 to 16/64"); } finally { aura.setVis(v); aura.setFlux(f); } h.succeed();
    }
    private static void fertility(GameTestHelper h, boolean powered) {
        BulkGameplayFixtures.floor(h); var tile = BulkGameplayFixtures.tile(h, new BlockPos(5, 2, 5), BlocksTC.lampFertility, TileLampDevice.class); tile.charges = 10;
        var one = h.spawnWithNoFreeWill(EntityType.COW, 3, 2, 5); var two = h.spawnWithNoFreeWill(EntityType.COW, 7, 2, 5);
        if (powered) h.setBlock(new BlockPos(5, 3, 5), Blocks.REDSTONE_BLOCK);
        tile.update(); h.assertTrue(powered ? !one.isInLove() && !two.isInLove() && tile.charges == 10 : one.isInLove() && two.isInLove() && tile.charges == 5, "fertility lamp pays five charges for two actual adults only without power"); one.discard(); two.discard(); h.succeed();
    }
    private static void growth(GameTestHelper h) {
        BulkGameplayFixtures.floor(h); var pos = new BlockPos(5, 2, 5); var tile = BulkGameplayFixtures.tile(h, pos, BlocksTC.lampGrowth, TileLampDevice.class); tile.charges = 20;
        var crop = pos.east(2); h.setBlock(crop.below(), Blocks.FARMLAND); h.setBlock(crop, Blocks.WHEAT); h.setBlock(crop.above(2), Blocks.GLOWSTONE);
        h.runAtTickTime(220, () -> { h.assertTrue(tile.charges < 20 && h.getLevel().getBlockState(h.absolutePos(crop)).is(Blocks.WHEAT), "native shuffled growth scan finds a live plant and pays its scheduled plant tick"); h.succeed(); });
    }
    private static void condenser(GameTestHelper h, boolean clogged) {
        var pos = new BlockPos(5, 2, 5); var tile = BulkGameplayFixtures.tile(h, pos, BlocksTC.condenser, TileCondenser.class); h.setBlock(pos.above(), clogged ? BlocksTC.condenserlatticeDirty : BlocksTC.condenserlattice); tile.triggerCheck();
        if (clogged) { h.assertTrue(tile.latticeCount == 0 && tile.interval == 0, "clogged root lattice cannot manufacture flux essentia"); h.succeed(); return; }
        var source = BulkGameplayFixtures.tile(h, pos.north(), BlocksTC.alembic, TileAlembic.class); source.aspect = Aspect.AIR; source.amount = 100;
        var cp = h.getLevel().getChunkAt(tile.getBlockPos()).getPos(); var aura = AuraHandler.getAuraChunk(h.getLevel(), cp.x, cp.z); float old = aura.getFlux();
        try { aura.setFlux(20); h.assertTrue(tile.interval > 0 && tile.cost > 0, "real lattice gives source processing period and price"); for (int i = 0; i < tile.interval; i++) tile.update(); h.assertTrue(tile.flux == 1 && aura.getFlux() == 19 && source.amount + tile.essentia == 100 - tile.cost, "real remote pull pays essentia and one aura flux for one flux unit"); h.assertTrue(tile.takeEssentia(Aspect.FLUX, 1, Direction.UP) == 0 && tile.takeEssentia(Aspect.FLUX, 1, Direction.DOWN) == 1, "only native downward output releases paid flux unit"); } finally { aura.setFlux(old); } h.succeed();
    }
    private static void infernal(GameTestHelper h) {
        BulkGameplayFixtures.floor(h); var pos = new BlockPos(5, 2, 5); var tile = BulkGameplayFixtures.tile(h, pos, BlocksTC.infernalFurnace, TileInfernalFurnace.class); var input = new ItemStack(Items.RAW_IRON, 2); h.assertTrue(tile.addItemsToInventory(input).isEmpty(), "native infernal input accepted real smeltables");
        h.succeedWhen(() -> { int ingots = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, h.getBounds()).stream().filter(e -> e.getItem().is(Items.IRON_INGOT)).mapToInt(e -> e.getItem().getCount()).sum(); h.assertTrue(ingots == 2 && tile.isEmpty(), "two native smelting results ejected without a fuel surrogate: " + ingots); });
    }
    private static void ear(GameTestHelper h, String mode) {
        BulkGameplayFixtures.floor(h); var pos=new BlockPos(5,2,5); var tile=BulkGameplayFixtures.tile(h,pos,mode.equals("toggle")?BlocksTC.arcaneEarToggle:BlocksTC.arcaneEar,TileSignalDevice.class);
        h.setBlock(pos.below(),Blocks.DIRT);h.setBlock(pos, tile.getBlockState().setValue(BlockWorldDevice.FACING,Direction.UP).setValue(BlockWorldDevice.ENABLED,false));tile.note=7;tile.updateTone();h.assertTrue(tile.tone==0,"actual native support selects harp tone");
        DeviceEvents.note(new net.neoforged.neoforge.event.level.NoteBlockEvent.Play(h.getLevel(),h.absolutePos(pos.east()),Blocks.NOTE_BLOCK.defaultBlockState(),mode.equals("wrong_note")?8:7,net.minecraft.world.level.block.state.properties.NoteBlockInstrument.HARP)); tile.update();
        DeviceEvents.end(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(()->true,h.getLevel()));
        h.assertTrue(tile.enabled()==!mode.equals("wrong_note"),"actual native note listener matches tone/note before powering output");
        if(mode.equals("toggle")){for(int i=0;i<12;i++)tile.update();h.assertTrue(tile.enabled(),"toggle remains latched after pulse duration");DeviceEvents.note(new net.neoforged.neoforge.event.level.NoteBlockEvent.Play(h.getLevel(),h.absolutePos(pos.east()),Blocks.NOTE_BLOCK.defaultBlockState(),7,net.minecraft.world.level.block.state.properties.NoteBlockInstrument.HARP));tile.update();DeviceEvents.end(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(()->true,h.getLevel()));h.assertTrue(!tile.enabled(),"second matching native note unlatches toggle");h.succeed();}
        else h.runAtTickTime(12,()->{h.assertTrue(!tile.enabled(),"native pulse expires and wrong-note output stays off");h.succeed();});
    }
    private static void sprayer(GameTestHelper h) {
        BulkGameplayFixtures.floor(h);var pos=new BlockPos(5,2,5);var tile=BulkGameplayFixtures.tile(h,pos,BlocksTC.potionSprayer,TileFluidDevice.class);var potion=new ItemStack(Items.POTION);potion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.HEALING));tile.setItem(0,potion);
        var source=BulkGameplayFixtures.tile(h,pos.north(),BlocksTC.alembic,TileAlembic.class); source.amount=100;var remaining=new java.util.LinkedHashMap<Aspect,Integer>();for(var aspect:tile.recipe.getAspectsSortedByName())remaining.put(aspect,tile.recipe.getAmount(aspect));
        for(var entry:remaining.entrySet()){source.aspect=entry.getKey();source.amount=entry.getValue();for(int i=0;i<entry.getValue()*5+5;i++)tile.update();h.assertTrue(source.amount==0,"actual sprayer ingredient paid from native alembic: "+entry.getKey());}
        for(int i=0;i<5&&tile.charges==0;i++)tile.update();h.assertTrue(tile.charges==1,"complete real potion recipe produces one paid charge");
        var target=h.spawnWithNoFreeWill(EntityType.COW,pos.relative(tile.facing(),2));target.setHealth(5);h.setBlock(pos,tile.getBlockState().setValue(BlockWorldDevice.ENABLED,false));tile.update();h.assertTrue(target.getHealth()>5&&tile.charges==0&&tile.activated,"powered sprayer consumes one real charge to apply native instant healing");float health=target.getHealth();tile.update();h.assertTrue(target.getHealth()==health&&tile.charges==0,"latched powered sprayer cannot heal again for free");target.discard();h.succeed();
    }
    private static void lights(GameTestHelper h) {
        BulkGameplayFixtures.floor(h);var pos=new BlockPos(5,2,5);var tile=BulkGameplayFixtures.tile(h,pos,BlocksTC.lampArcane,TileLampDevice.class);h.setBlock(pos.east(2),BlocksTC.effectGlimmer);h.setBlock(pos.west(2),BlocksTC.effectGlimmer);tile.removeLights();h.assertTrue(h.getLevel().getBlockState(h.absolutePos(pos.east(2))).isAir()&&h.getLevel().getBlockState(h.absolutePos(pos.west(2))).isAir(),"native emitter cleanup removes actual nearby invisible light blocks");h.runAtTickTime(25,()->{h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(pos))==tile,"actual arcane emitter survives five native ray-tracing ticks");h.succeed();});
    }
    private static void mirror(GameTestHelper h, String mode) {
        BulkGameplayFixtures.floor(h); var pos = new BlockPos(2, 2, 2); h.setBlock(pos, mode.equals("essentia") ? BlocksTC.mirrorEssentia : BlocksTC.mirror); var left = (TileMirrorBase) h.getLevel().getBlockEntity(h.absolutePos(pos));
        var world = mode.equals("nether") ? h.getLevel().getServer().getLevel(Level.NETHER) : h.getLevel(); h.assertTrue(world != null, "native target dimension exists"); var remote = mode.equals("nether") ? new BlockPos(h.absolutePos(pos).getX(), 80, h.absolutePos(pos).getZ()) : h.absolutePos(new BlockPos(8, 2, 8));
        world.getChunkAt(remote); if (mode.equals("nether")) { world.setChunkForced(remote.getX() >> 4, remote.getZ() >> 4, true); for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) world.setBlock(remote.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3); } world.setBlock(remote, (mode.equals("essentia") ? BlocksTC.mirrorEssentia : BlocksTC.mirror).defaultBlockState(), 3); var right = (TileMirrorBase) world.getBlockEntity(remote);
        left.destination = GlobalPos.of(world.dimension(), remote); left.restoreLink(); h.assertTrue(left.isLinkValidSimple() && right.isLinkValidSimple(), "actual reciprocal native mirror pair linked");
        if (mode.equals("essentia")) { var jarPos = remote.relative(right.facing(), 2); world.setBlock(jarPos, BlocksTC.jarNormal.defaultBlockState(), 3); var jar = (thaumcraft.common.tiles.essentia.TileJarFillable) world.getBlockEntity(jarPos); jar.aspect = jar.aspectFilter = Aspect.AIR; jar.amount = 5; var e = (TileMirrorEssentia) left; h.assertTrue(e.doesContainerContainAmount(Aspect.AIR, 1) && jar.amount == 5 && e.takeFromContainer(Aspect.AIR, 1) && jar.amount == 4, "remote mirror probe is read-only and actual transfer pays one real far-side jar unit"); world.setBlock(remote, Blocks.AIR.defaultBlockState(), 3); h.succeed(); return; }
        var item = new ItemStack(Items.IRON_INGOT, 8); var sender = (TileMirror) left; h.assertTrue(sender.insertion.insertItem(0, item, true).isEmpty() && left.instability == 0, "native item capability simulation neither enqueues nor pays instability"); h.assertTrue(sender.insertion.insertItem(0, item, false).isEmpty(), "real insertion queued actual copied cargo");
        if (mode.equals("reload")) right.loadWithComponents(right.saveWithFullMetadata(world.registryAccess()), world.registryAccess());
        if (mode.equals("unlink")) { world.setBlock(remote, Blocks.AIR.defaultBlockState(), 3); h.assertTrue(!left.isLinkValidSimple(), "removal invalidates actual reciprocal pair"); int n = world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(remote).inflate(2)).stream().filter(e -> e.getItem().is(Items.IRON_INGOT)).mapToInt(e -> e.getItem().getCount()).sum(); h.assertTrue(n == 8, "native removal drops conserved queued cargo once"); h.succeed(); return; }
        h.succeedWhen(() -> { int n = world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(remote).inflate(3)).stream().filter(e -> e.getItem().is(Items.IRON_INGOT)).mapToInt(e -> e.getItem().getCount()).sum(); h.assertTrue(n == 8, "native queued mirror cargo delivered exactly eight units: " + n); world.setBlock(remote, Blocks.AIR.defaultBlockState(), 3); if (mode.equals("nether")) world.setChunkForced(remote.getX() >> 4, remote.getZ() >> 4, false); });
    }
}
