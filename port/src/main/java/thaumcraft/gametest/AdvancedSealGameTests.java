package thaumcraft.gametest;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.common.golems.client.gui.*;
import thaumcraft.common.golems.seals.*;

/** Native advanced filters, paid golem transfers, and server menu authority. */
@GameTestHolder(Thaumcraft.MODID)
public final class AdvancedSealGameTests {
    @GameTestGenerator public static Collection<TestFunction> remainingSeals() {
        var tests = new ArrayList<TestFunction>();
        for (String mode : List.of("strict", "nbt", "damage", "ore", "mod")) tests.add(test("filter_" + mode, h -> filter(h, mode)));
        for (String mode : List.of("pickup_strict", "pickup_relaxed", "empty_leave", "empty_cycle", "fill_limit", "fill_existing", "fill_absent")) tests.add(test(mode, h -> transfer(h, mode)));
        tests.add(test("ghost_menu", AdvancedSealGameTests::menu));
        tests.add(new TestFunction("bulk_logistics_authority", "thaumcraft.bulk_advanced_logistics", "thaumcraft:scan_test", 700, 0, true, AdvancedSealGameTests::logistics));
        return tests;
    }
    private static TestFunction test(String name, java.util.function.Consumer<GameTestHelper> body) { return new TestFunction("bulk_advanced_seals", "thaumcraft.bulk_advanced_" + name, "thaumcraft:scan_test", 700, 0, true, body); }
    private static void filter(GameTestHelper h, String mode) {
        var chest = BulkGameplayFixtures.tile(h, new BlockPos(5, 2, 5), Blocks.CHEST, ChestBlockEntity.class);
        var sample = new ItemStack(mode.equals("damage") ? Items.IRON_PICKAXE : mode.equals("ore") ? Items.OAK_PLANKS : Items.IRON_INGOT);
        var actual = new ItemStack(mode.equals("damage") ? Items.IRON_PICKAXE : mode.equals("ore") ? Items.BIRCH_PLANKS : mode.equals("mod") ? Items.GOLD_INGOT : Items.IRON_INGOT, mode.equals("damage") ? 1 : 8);
        if (mode.equals("damage")) actual.setDamageValue(12); else if (!mode.equals("ore") && !mode.equals("mod")) actual.set(DataComponents.CUSTOM_NAME, Component.literal("real cargo"));
        chest.setItem(0, actual.copy()); var inv = SealInventoryOps.getItemHandlerAt(h.getLevel(), chest.getBlockPos(), Direction.UP);
        var f = new SealInventoryOps.Filter(mode.equals("damage"), mode.equals("nbt"), mode.equals("ore"), mode.equals("mod"));
        var before = chest.getItem(0).copy(); var simulated = SealInventoryOps.removeStackFrom(inv, sample.copyWithCount(3), f, true);
        h.assertTrue(ItemStack.matches(chest.getItem(0), before), "simulation preserves real source stack and components");
        var out = SealInventoryOps.removeStackFrom(inv, sample.copyWithCount(3), f, false);
        if (mode.equals("strict")) h.assertTrue(out.isEmpty() && simulated.isEmpty() && ItemStack.matches(chest.getItem(0), before), "strict NBT rejects a different named stack");
        else h.assertTrue(out.getCount() == Math.min(3, before.getCount()) && ItemStack.isSameItemSameComponents(out, before) && chest.getItem(0).getCount() + out.getCount() == before.getCount(), "relaxed filter extracts actual cargo, preserves its components, and conserves units");
        h.succeed();
    }
    private static void transfer(GameTestHelper h, String mode) {
        var golem = GolemBehaviourGameTests.golem(h, "WALKER", "FINE"); var srcPos = new BlockPos(2, 2, 4); var dstPos = new BlockPos(7, 2, 4);
        var dst = BulkGameplayFixtures.tile(h, dstPos, Blocks.CHEST, ChestBlockEntity.class); ChestBlockEntity src = null; ItemEntity loose = null;
        var cargo = new ItemStack(Items.IRON_INGOT, 8); cargo.set(DataComponents.CUSTOM_NAME, Component.literal("kept components"));
        thaumcraft.api.golems.seals.ISeal sourceType;
        if (mode.startsWith("pickup")) { var p = new SealPickupAdvanced(); p.setBlacklist(false); p.setFilterSlot(0, new ItemStack(Items.IRON_INGOT)); p.setToggle(1, mode.equals("pickup_strict")); sourceType = p;
            loose = new ItemEntity(h.getLevel(), h.absolutePos(srcPos).getX() + .5, h.absolutePos(srcPos).getY(), h.absolutePos(srcPos).getZ() + .5, cargo.copy()); h.getLevel().addFreshEntity(loose);
        } else { var p = new SealEmptyAdvanced(); sourceType = p; src = BulkGameplayFixtures.tile(h, srcPos, Blocks.CHEST, ChestBlockEntity.class); src.setItem(0, cargo.copy());
            if (mode.equals("empty_leave")) p.setToggle(5, true);
            if (mode.equals("empty_cycle")) { p.setBlacklist(false); p.setFilterSlot(0, cargo.copyWithCount(1)); p.setFilterSlot(1, new ItemStack(Items.GOLD_INGOT)); p.setToggle(4, true); src.setItem(1, new ItemStack(Items.GOLD_INGOT, 4)); }
        }
        var source = GolemBehaviourGameTests.seal(h, mode.startsWith("pickup") ? srcPos.below() : srcPos, mode.startsWith("pickup") ? Direction.UP : Direction.EAST, sourceType, golem); source.setArea(new BlockPos(3, 3, 3));
        var fill = new SealFillAdvanced(); if (mode.equals("fill_limit")) { fill.setBlacklist(false); fill.setFilterSlot(0, cargo.copyWithCount(1)); fill.setFilterSlotSize(0, 3); }
        if (mode.startsWith("fill_e") || mode.equals("fill_absent")) { fill.setToggle(4, true); if (mode.equals("fill_existing")) dst.setItem(0, cargo.copyWithCount(1)); }
        var target = GolemBehaviourGameTests.seal(h, dstPos, Direction.WEST, fill, golem); target.setPriority((byte)2);
        final var input = src; final var ground = loose;
        int expected = mode.equals("empty_leave") ? 7 : mode.equals("fill_limit") ? 3 : mode.equals("fill_existing") ? 9 : 8;
        Runnable cleanup = () -> { GolemBehaviourGameTests.cleanup(h, golem, source, target); if (ground != null) ground.discard(); };
        if (mode.equals("pickup_strict") || mode.equals("fill_absent")) h.runAtTickTime(220, () -> { int retained = ground != null ? ground.getItem().getCount() : input.getItem(0).getCount() + golem.getCarrying().stream().mapToInt(ItemStack::getCount).sum(); h.assertTrue(dst.isEmpty() && retained == 8, "native advanced restriction blocks transfer while source/golem custody conserves eight items"); cleanup.run(); h.succeed(); });
        else h.succeedWhen(() -> { int count = 0, gold = 0; for (int i = 0; i < dst.getContainerSize(); i++) { var stack = dst.getItem(i); if (stack.is(Items.IRON_INGOT)) { count += stack.getCount(); h.assertTrue(ItemStack.isSameItemSameComponents(stack, cargo), "actual transferred named cargo survives golem custody"); } if (stack.is(Items.GOLD_INGOT)) gold += stack.getCount(); }
            h.assertTrue(count == expected && (!mode.equals("empty_cycle") || gold == 4), "native advanced chain completed expected quota/cycle: " + count + "/" + gold);
            if (mode.equals("empty_leave")) h.assertTrue(input.getItem(0).getCount() == 1, "leave-one retains one real source unit"); cleanup.run(); });
    }
    private static void menu(GameTestHelper h) {
        var p = BulkGameplayFixtures.connectedPlayer(h); var peer = BulkGameplayFixtures.connectedPlayer(h); var golem = GolemBehaviourGameTests.golem(h, "WALKER", "FINE"); golem.setOwnerId(p.getUUID());
        var filter = new SealFillAdvanced(); filter.setBlacklist(false); var seal = GolemBehaviourGameTests.seal(h, new BlockPos(3, 1, 3), Direction.UP, filter, golem);
        try { var menu = new SealBaseContainer(1, p.getInventory(), seal); p.containerMenu = menu; h.assertTrue(menu.stillValid(p) && !menu.stillValid(peer), "locked seal admits only actual nearby owner");
            var cursor = new ItemStack(Items.IRON_INGOT, 7); menu.setCarried(cursor); menu.clicked(0, 0, ClickType.PICKUP, p); menu.clicked(0, 0, ClickType.PICKUP, p);
            h.assertTrue(filter.getFilterSlot(0).getCount() == 1 && filter.getFilterSlotSize(0) == 7 && menu.getCarried().getCount() == 7, "ghost filter stores exemplar/quota without consuming cursor");
            menu.setCarried(ItemStack.EMPTY); menu.clicked(0, 0, ClickType.QUICK_MOVE, p); h.assertTrue(filter.getFilterSlotSize(0) == 17 && menu.quickMoveStack(p, 0).isEmpty(), "shift-click adjusts quota ten and cannot steal filter");
            menu.clickMenuButton(p, 1); h.assertTrue(menu.category == 3 && menu.clickMenuButton(p, 64) && !filter.getToggles()[4].value, "native toggle category routes real button ids");
            menu.clickMenuButton(p, 2); menu.clickMenuButton(p, 81); menu.clickMenuButton(p, 83); h.assertTrue(seal.getPriority() == 1 && seal.getColor() == 1 && !menu.clickMenuButton(peer, 26) && seal.isLocked(), "priority/color owner updates and foreign unlock rejected");
            p.setPos(p.getX() + 20, p.getY(), p.getZ()); h.assertTrue(!menu.stillValid(p) && !menu.clickMenuButton(p, 26), "distance invalidates actual menu authority");
        } finally { GolemBehaviourGameTests.cleanup(h, golem, seal); BulkGameplayFixtures.removeConnected(p); BulkGameplayFixtures.removeConnected(peer); } h.succeed();
    }
    private static void logistics(GameTestHelper h) {
        var p = BulkGameplayFixtures.connectedPlayer(h); var peer = BulkGameplayFixtures.connectedPlayer(h); BulkGameplayFixtures.research(p); BulkGameplayFixtures.research(peer);
        var golem = GolemBehaviourGameTests.golem(h, "WALKER", "FINE"); golem.setOwnerId(p.getUUID());
        var chest = BulkGameplayFixtures.tile(h, new BlockPos(3, 2, 3), Blocks.CHEST, ChestBlockEntity.class); chest.setItem(0, new ItemStack(Items.IRON_INGOT, 12)); chest.setItem(1, new ItemStack(Items.IRON_INGOT, 8)); chest.setItem(2, new ItemStack(Items.GOLD_INGOT, 5));
        var seal = GolemBehaviourGameTests.seal(h, new BlockPos(3, 2, 3), Direction.EAST, new SealProvide(), golem);
        var tickets = GolemHelper.provisionRequests.computeIfAbsent(h.getLevel().dimension(), ignored -> new ArrayList<>()); var before = new HashSet<>(tickets);
        try { var menu = new ContainerLogistics(1, p.getInventory(), p.blockPosition(), -1); var foreign = new ContainerLogistics(2, peer.getInventory(), peer.blockPosition(), -1);
            h.assertTrue(java.util.stream.IntStream.range(0,81).map(i -> menu.input.getItem(i).getCount()).sum() == 25 && foreign.input.isEmpty(), "catalogue merges only owner's actual stock");
            menu.action(p, 0, 0, 0, "iron"); h.assertTrue(menu.input.getItem(0).is(Items.IRON_INGOT) && menu.input.getItem(0).getCount() == 20 && menu.input.getItem(1).isEmpty(), "native search returns exact aggregate stock");
            menu.clicked(0,0,ClickType.PICKUP,p); h.assertTrue(menu.getCarried().isEmpty() && menu.quickMoveStack(p,0).isEmpty() && chest.getItem(0).getCount()==12, "catalogue cannot manufacture or remove real items");
            menu.action(peer,1,0,4,""); menu.action(p,1,0,21,""); h.assertTrue(tickets.size()==before.size(), "foreign and overstock requests rejected");
            menu.action(p,1,0,4,""); h.assertTrue(tickets.size()==before.size()+1, "owner request creates one real provisioning ticket"); menu.action(p,1,0,4,""); h.assertTrue(tickets.size()==before.size()+1, "same-tick duplicate is throttled");
        } finally { tickets.removeIf(r -> !before.contains(r)); GolemBehaviourGameTests.cleanup(h,golem,seal); BulkGameplayFixtures.removeConnected(p); BulkGameplayFixtures.removeConnected(peer); } h.succeed();
    }
}
