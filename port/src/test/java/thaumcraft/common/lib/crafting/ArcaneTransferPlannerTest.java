package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.crafting.ArcaneTransferPlanner.Failure;
import thaumcraft.common.lib.crafting.ArcaneTransferPlanner.Requirement;
import thaumcraft.common.lib.crafting.ArcaneTransferPlanner.Stack;

/** Transaction invariants without a Minecraft world/registry bootstrap. */
class ArcaneTransferPlannerTest {
    private static List<Stack<String>> empty(int size) { return new ArrayList<>(java.util.Collections.nCopies(size, Stack.empty())); }
    private static Stack<String> stack(String key, int count) { return new Stack<>(key, count, 64); }
    private static Requirement<String> need(int slot, String key, int count) { return new Requirement<>(slot, count, key::equals); }
    private static ArcaneTransferPlanner.Result<String> plan(List<Stack<String>> table, List<Stack<String>> inventory,
            List<Requirement<String>> needs, boolean maximum) { return ArcaneTransferPlanner.plan(table, inventory, needs, String::equals, maximum); }
    private static Map<String, Integer> totals(List<Stack<String>> a, List<Stack<String>> b) {
        Map<String, Integer> counts = new HashMap<>();
        java.util.stream.Stream.concat(a.stream(), b.stream()).filter(stack -> stack.count() > 0)
            .forEach(stack -> counts.merge(stack.key(), stack.count(), Integer::sum));
        return counts;
    }
    private static void conserved(List<Stack<String>> table, List<Stack<String>> inventory, ArcaneTransferPlanner.Result<String> result) {
        assertTrue(result.successful()); assertEquals(totals(table, inventory), totals(result.table(), result.inventory()));
        java.util.stream.Stream.concat(result.table().stream(), result.inventory().stream()).forEach(stack -> assertTrue(stack.count() <= stack.capacity()));
    }
    @Test void shapedGeometryUsesNativeStrideAndKeepsEveryPatternCell() {
        assertEquals(List.of(0, 1, 3, 4), ArcaneTransferPlanner.shapedSlots(2, 2));
        assertEquals(List.of(1, 4, 7), ArcaneTransferPlanner.shapedSlots(1, 3));
        assertEquals(List.of(3, 4, 5), ArcaneTransferPlanner.shapedSlots(3, 1));
        assertEquals(List.of(4), ArcaneTransferPlanner.shapedSlots(1, 1));
        assertThrows(IllegalArgumentException.class, () -> ArcaneTransferPlanner.shapedSlots(4, 1));
    }
    @Test void transfersEachPrimalCrystalQuantityAlongWithGridInputs() {
        var table = empty(15); var inventory = empty(36); inventory.set(0, stack("iron", 10));
        List<Requirement<String>> requirements = new ArrayList<>(List.of(need(4, "iron", 1)));
        for (int aspect = 0; aspect < 6; aspect++) {
            inventory.set(1 + aspect, stack("crystal" + aspect, 10));
            requirements.add(need(9 + aspect, "crystal" + aspect, aspect + 1));
        }
        var result = plan(table, inventory, requirements, false); conserved(table, inventory, result);
        assertEquals(1, result.batches()); assertEquals(1, result.table().get(4).count());
        for (int aspect = 0; aspect < 6; aspect++) assertEquals(aspect + 1, result.table().get(9 + aspect).count());
    }
    @Test void overlappingIngredientsDoNotGreedilyConsumeAnExclusiveAlternative() {
        var table = empty(15); var inventory = empty(36); inventory.set(0, stack("iron", 1)); inventory.set(1, stack("gold", 1));
        var result = plan(table, inventory, List.of(new Requirement<>(0, 1, key -> key.equals("iron") || key.equals("gold")), need(1, "iron", 1)), false);
        conserved(table, inventory, result);
        assertEquals("gold", result.table().get(0).key()); assertEquals("iron", result.table().get(1).key());
    }
    @Test void repeatedSlotsConsumeDistinctUnitsOfTheSameExistingStack() {
        var table = empty(15); var inventory = empty(36); inventory.set(0, stack("quartz", 3));
        var result = plan(table, inventory, List.of(need(0, "quartz", 1), need(1, "quartz", 1), need(2, "quartz", 1)), false);
        conserved(table, inventory, result); assertTrue(result.inventory().stream().allMatch(stack -> stack.count() == 0));
        inventory.set(0, stack("quartz", 2));
        assertEquals(Failure.MISSING_ITEMS, plan(table, inventory, List.of(need(0, "quartz", 1), need(1, "quartz", 1), need(2, "quartz", 1)), false).failure());
    }
    @Test void componentVariantsAreNeverMergedOrInvented() {
        var table = empty(15); var inventory = empty(36);
        inventory.set(0, stack("crystal:air", 4)); inventory.set(1, stack("crystal:air+fire", 64));
        var result = plan(table, inventory, List.of(need(9, "crystal:air", 3)), false); conserved(table, inventory, result);
        assertEquals(stack("crystal:air", 3), result.table().get(9));
        assertEquals(stack("crystal:air+fire", 64), result.inventory().get(1));
    }
    @Test void maxTransferIsBoundedByCrystalQuantityAndDestinationCapacity() {
        var table = empty(15); var inventory = empty(36);
        inventory.set(0, stack("iron", 64)); inventory.set(1, stack("air", 64)); inventory.set(2, stack("air", 64));
        var result = plan(table, inventory, List.of(need(0, "iron", 1), need(9, "air", 3)), true); conserved(table, inventory, result);
        assertEquals(21, result.batches()); assertEquals(21, result.table().get(0).count()); assertEquals(63, result.table().get(9).count());
    }
    @Test void nonStackableAndSmallerStackLimitsBoundMaximum() {
        var table = empty(15); var inventory = empty(36);
        inventory.set(0, new Stack<>("sword:first", 1, 1)); inventory.set(1, new Stack<>("sword:first", 1, 1));
        inventory.set(2, new Stack<>("pearl", 16, 16));
        var result = plan(table, inventory, List.of(need(0, "sword:first", 1), need(1, "pearl", 1)), true); conserved(table, inventory, result);
        assertEquals(1, result.batches()); assertEquals(1, result.table().get(0).count());
    }
    @Test void existingMatchingGridStackAndUnusedCrystalCanStayWithFullInventory() {
        var table = empty(15); var inventory = empty(36);
        for (int slot = 0; slot < 36; slot++) inventory.set(slot, stack("unrelated" + slot, 64));
        table.set(4, stack("iron", 20)); table.set(10, stack("fire", 30));
        var result = plan(table, inventory, List.of(need(4, "iron", 1), need(10, "fire", 0)), false); conserved(table, inventory, result);
        assertEquals(table, result.table()); assertEquals(inventory, result.inventory());
    }
    @Test void displacedItemsNeedRealInventoryRoomAndFailureMutatesNothing() {
        var table = empty(15); var inventory = empty(36); table.set(0, stack("old:custom_components", 1));
        for (int slot = 0; slot < 36; slot++) inventory.set(slot, stack("full" + slot, 64));
        inventory.set(0, stack("iron", 64)); var originalTable = List.copyOf(table); var originalInventory = List.copyOf(inventory);
        var result = plan(table, inventory, List.of(need(4, "iron", 1)), false);
        assertEquals(Failure.INVENTORY_FULL, result.failure()); assertEquals(originalTable, table); assertEquals(originalInventory, inventory);
        assertTrue(result.table().isEmpty()); assertTrue(result.inventory().isEmpty());
        inventory.set(35, Stack.empty()); result = plan(table, inventory, List.of(need(4, "iron", 1)), false);
        conserved(table, inventory, result); assertEquals("old:custom_components", result.inventory().get(35).key());
    }
    @Test void rearrangingExistingMatchingSurplusDoesNotNeedInventoryRoomOrPullExtraInventoryItems() {
        var table = empty(15); var inventory = empty(36);
        for (int slot = 0; slot < 36; slot++) inventory.set(slot, stack("unrelated" + slot, 64));
        table.set(0, stack("iron", 64));
        var result = plan(table, inventory, List.of(need(4, "iron", 1)), false); conserved(table, inventory, result);
        assertEquals(stack("iron", 64), result.table().get(4)); assertEquals(inventory, result.inventory());
        table.set(0, Stack.empty()); inventory.set(0, stack("iron", 64));
        result = plan(table, inventory, List.of(need(4, "iron", 1)), false); conserved(table, inventory, result);
        assertEquals(stack("iron", 1), result.table().get(4)); assertEquals(stack("iron", 63), result.inventory().get(0));
    }
    @Test void tableAndInventoryAreOneFinitePoolWithoutConsumingAnything() {
        var table = empty(15); var inventory = empty(36);
        table.set(0, stack("iron", 1)); table.set(1, stack("gold", 1)); table.set(9, stack("air", 1));
        inventory.set(0, stack("air", 2));
        var result = plan(table, inventory, List.of(need(3, "gold", 1), need(4, "iron", 1), need(9, "air", 3)), false);
        conserved(table, inventory, result); assertEquals("gold", result.table().get(3).key()); assertEquals("iron", result.table().get(4).key());
        assertEquals(3, result.table().get(9).count());
    }
    @Test void invalidDestinationCountsAndGeometryAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Stack<>("iron", 65, 64));
        assertThrows(IllegalArgumentException.class, () -> new Stack<>(null, 1, 64));
        assertThrows(IllegalArgumentException.class, () -> new Requirement<String>(15, 1, key -> true));
        assertThrows(IllegalArgumentException.class, () -> new Requirement<String>(0, 65, key -> true));
        assertThrows(IllegalArgumentException.class, () -> plan(empty(15), empty(36), List.of(need(0, "iron", 1), need(0, "gold", 1)), false));
        assertThrows(IllegalArgumentException.class, () -> plan(empty(14), empty(36), List.of(need(0, "iron", 1)), false));
    }
    @Test void impossibleBroadIngredientsStopAtTheSharedAssignmentAndPackingBudget() {
        var table = empty(15); var inventory = empty(36); List<Requirement<String>> needs = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) { table.set(slot, stack("displaced" + slot, 1)); needs.add(new Requirement<>(slot, 1, key -> key.startsWith("alternative"))); }
        for (int slot = 0; slot < 36; slot++) inventory.set(slot, stack("alternative" + slot, 64));
        var originalTable = List.copyOf(table); var originalInventory = List.copyOf(inventory);
        var result = plan(table, inventory, needs, false);
        assertEquals(Failure.COMPLEXITY, result.failure()); assertEquals(originalTable, table); assertEquals(originalInventory, inventory);
    }
    @Test void deterministicRandomPlansPreserveAllItemComponentsAndCounts() {
        Random random = new Random(892403L);
        for (int sample = 0; sample < 300; sample++) {
            var table = empty(15); var inventory = empty(36);
            for (int slot = 0; slot < 15; slot++) if (random.nextBoolean()) table.set(slot, stack("type" + random.nextInt(8), 1 + random.nextInt(64)));
            for (int slot = 0; slot < 36; slot++) if (random.nextBoolean()) inventory.set(slot, stack("type" + random.nextInt(8), 1 + random.nextInt(64)));
            List<Requirement<String>> needs = new ArrayList<>();
            for (int slot = 0; slot < 9; slot++) if (slot == 0 || random.nextBoolean()) needs.add(need(slot, "type" + random.nextInt(8), 1));
            var result = plan(table, inventory, needs, random.nextBoolean());
            if (result.successful()) {
                conserved(table, inventory, result);
                for (var requirement : needs) {
                    assertTrue(requirement.accepts().test(result.table().get(requirement.slot()).key()));
                    assertTrue(result.table().get(requirement.slot()).count() >= requirement.amount() * result.batches());
                }
                assertThrows(UnsupportedOperationException.class, () -> result.table().add(Stack.empty()));
            }
        }
    }
}
