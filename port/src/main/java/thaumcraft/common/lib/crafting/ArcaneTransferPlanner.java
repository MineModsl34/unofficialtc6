package thaumcraft.common.lib.crafting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Pure, bounded inventory transaction planning. No item is created, consumed or dropped.
 * Keys identify full item components, not just item IDs. Each destination receives a single
 * key; overlapping ingredients are assigned by constrained backtracking rather than greed.
 */
public final class ArcaneTransferPlanner {
    public static final int TABLE_SIZE = 15, INVENTORY_SIZE = 36, SEARCH_BUDGET = 20_000;
    private ArcaneTransferPlanner() {}

    public record Stack<T>(T key, int count, int capacity) {
        public Stack {
            if (capacity < 1 || capacity > 64 || count < 0 || count > capacity || (count == 0) != (key == null))
                throw new IllegalArgumentException("Invalid transfer stack");
        }
        public static <T> Stack<T> empty() { return new Stack<>(null, 0, 64); }
    }
    /** Zero amount means an unused crystal slot may retain its existing valid crystal. */
    public record Requirement<T>(int slot, int amount, Predicate<T> accepts) {
        public Requirement {
            if (slot < 0 || slot >= TABLE_SIZE || amount < 0 || amount > 64 || accepts == null)
                throw new IllegalArgumentException("Invalid transfer requirement");
        }
    }
    public enum Failure { NONE, MISSING_ITEMS, INVENTORY_FULL, COMPLEXITY }
    public record Result<T>(Failure failure, int batches, List<Stack<T>> table, List<Stack<T>> inventory) {
        public Result { table = List.copyOf(table); inventory = List.copyOf(inventory); }
        public boolean successful() { return failure == Failure.NONE; }
    }
    public static List<Integer> shapedSlots(int width, int height) {
        if (width < 1 || width > 3 || height < 1 || height > 3) throw new IllegalArgumentException("Pattern must fit 3x3");
        List<Integer> result = new ArrayList<>(width * height);
        int left = (3 - width) / 2, top = (3 - height) / 2;
        for (int row = 0; row < height; row++) for (int column = 0; column < width; column++)
            result.add((top + row) * 3 + left + column);
        return List.copyOf(result);
    }

    public static <T> Result<T> plan(List<Stack<T>> table, List<Stack<T>> inventory,
                                    List<Requirement<T>> requirements, BiPredicate<T, T> same, boolean maximum) {
        Objects.requireNonNull(same);
        if (table.size() != TABLE_SIZE || inventory.size() != INVENTORY_SIZE || requirements.size() > TABLE_SIZE)
            throw new IllegalArgumentException("Transfer inventory geometry");
        boolean[] occupied = new boolean[TABLE_SIZE];
        for (var requirement : requirements) {
            if (occupied[requirement.slot()]) throw new IllegalArgumentException("Duplicate transfer destination");
            occupied[requirement.slot()] = true;
        }
        if (requirements.stream().noneMatch(requirement -> requirement.amount() > 0))
            throw new IllegalArgumentException("Empty transfer recipe");
        return new Search<>(List.copyOf(table), List.copyOf(inventory), List.copyOf(requirements), same).run(maximum);
    }

    private static final class Group<T> {
        final T key;
        final int capacity;
        int count;
        Group(Stack<T> stack) { key = stack.key(); count = stack.count(); capacity = stack.capacity(); }
    }
    private static final class Search<T> {
        final List<Stack<T>> oldTable, oldInventory;
        final List<Requirement<T>> requirements;
        final BiPredicate<T, T> same;
        final List<Group<T>> groups = new ArrayList<>();
        final List<int[]> choices = new ArrayList<>();
        final List<Integer> order = new ArrayList<>();
        final int[] selected = new int[TABLE_SIZE];
        int budget = SEARCH_BUDGET;
        boolean completeAssignment;

        Search(List<Stack<T>> table, List<Stack<T>> inventory, List<Requirement<T>> requirements, BiPredicate<T, T> same) {
            oldTable = table; oldInventory = inventory; this.requirements = requirements; this.same = same;
            for (Stack<T> stack : table) add(stack);
            for (Stack<T> stack : inventory) add(stack);
            Arrays.fill(selected, -1);
            for (int index = 0; index < requirements.size(); index++) {
                var requirement = requirements.get(index);
                List<Integer> candidates = new ArrayList<>();
                for (int group = 0; group < groups.size(); group++)
                    if (requirement.accepts().test(groups.get(group).key)) candidates.add(group);
                // Keep a compatible stack already in the destination before selecting alternatives.
                int original = groupOf(oldTable.get(requirement.slot()));
                candidates.sort(Comparator.comparingInt(group -> group == original ? -1 : group));
                choices.add(candidates.stream().mapToInt(Integer::intValue).toArray());
                if (requirement.amount() > 0) order.add(index);
            }
            order.sort(Comparator.comparingInt(index -> choices.get(index).length));
        }
        void add(Stack<T> stack) {
            if (stack.count() == 0) return;
            int group = groupOf(stack);
            if (group < 0) groups.add(new Group<>(stack));
            else {
                if (groups.get(group).capacity != stack.capacity()) throw new IllegalArgumentException("Inconsistent stack capacity");
                groups.get(group).count += stack.count();
            }
        }
        int groupOf(Stack<T> stack) {
            if (stack.count() == 0) return -1;
            for (int index = 0; index < groups.size(); index++) if (same.test(groups.get(index).key, stack.key())) return index;
            return -1;
        }
        Result<T> run(boolean maximum) {
            int limit = maximum ? 64 : 1;
            for (int index : order) {
                int total = 0, capacity = 0;
                for (int group : choices.get(index)) { total += groups.get(group).count; capacity = Math.max(capacity, groups.get(group).capacity); }
                limit = Math.min(limit, Math.min(total, capacity) / requirements.get(index).amount());
            }
            for (int batches = limit; batches >= 1 && budget > 0; batches--) {
                int[] remaining = groups.stream().mapToInt(group -> group.count).toArray();
                Result<T> result = assign(0, batches, remaining);
                if (result != null) return result;
            }
            return new Result<>(budget <= 0 ? Failure.COMPLEXITY : completeAssignment ? Failure.INVENTORY_FULL : Failure.MISSING_ITEMS,
                0, List.of(), List.of());
        }
        Result<T> assign(int depth, int batches, int[] remaining) {
            if (--budget < 0) return null;
            if (depth == order.size()) {
                completeAssignment = true;
                return pack(batches, remaining.clone());
            }
            int requirementIndex = order.get(depth);
            var requirement = requirements.get(requirementIndex);
            int needed = requirement.amount() * batches;
            for (int group : choices.get(requirementIndex)) {
                if (needed > groups.get(group).capacity || remaining[group] < needed) continue;
                remaining[group] -= needed; selected[requirement.slot()] = group;
                Result<T> result = assign(depth + 1, batches, remaining);
                remaining[group] += needed; selected[requirement.slot()] = -1;
                if (result != null) return result;
                if (budget <= 0) break;
            }
            return null;
        }
        Result<T> pack(int batches, int[] remaining) {
            // Packing scans component groups against table/inventory slots. Charge this work
            // too: a malicious request for a broad-tag recipe with a full inventory must not
            // spend the entire assignment-node budget repeatedly packing impossible variants.
            budget -= 1 + groups.size() * (TABLE_SIZE + INVENTORY_SIZE * 2);
            if (budget < 0) return null;
            List<Stack<T>> table = empty(TABLE_SIZE), inventory = empty(INVENTORY_SIZE);
            int[] tableReserve = new int[groups.size()];
            for (var stack : oldTable) { int group = groupOf(stack); if (group >= 0) tableReserve[group] += stack.count(); }
            for (var requirement : requirements) if (requirement.amount() > 0)
                tableReserve[selected[requirement.slot()]] -= requirement.amount() * batches;
            for (int group = 0; group < tableReserve.length; group++) tableReserve[group] = Math.max(0, tableReserve[group]);
            for (int index = 0; index < requirements.size(); index++) {
                var requirement = requirements.get(index);
                int slot = requirement.slot(), group = selected[slot], needed = requirement.amount() * batches;
                Stack<T> original = oldTable.get(slot);
                if (needed == 0) {
                    group = groupOf(original);
                    if (group < 0 || !contains(choices.get(index), group)) continue;
                }
                var key = groups.get(group);
                int preferred = groupOf(original) == group ? Math.max(needed, original.count()) : needed;
                int retained = Math.min(Math.min(remaining[group], tableReserve[group]), Math.min(key.capacity, preferred) - needed);
                remaining[group] -= retained; tableReserve[group] -= retained;
                if (needed + retained > 0) table.set(slot, new Stack<>(key.key, needed + retained, key.capacity));
            }
            // Reposition a matching surplus already on the table instead of requiring empty
            // inventory slots just to rotate/rearrange a shape. Never pull surplus from inventory.
            for (var requirement : requirements) {
                if (requirement.amount() == 0) continue;
                int slot = requirement.slot(), group = selected[slot]; var key = groups.get(group);
                int retained = Math.min(Math.min(remaining[group], tableReserve[group]), key.capacity - table.get(slot).count());
                if (retained > 0) {
                    table.set(slot, new Stack<>(key.key, table.get(slot).count() + retained, key.capacity));
                    remaining[group] -= retained; tableReserve[group] -= retained;
                }
            }
            // Preserve inventory positions and component variants before merging leftovers.
            for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
                Stack<T> original = oldInventory.get(slot);
                int group = groupOf(original);
                if (group < 0 || remaining[group] == 0) continue;
                int kept = Math.min(remaining[group], original.count());
                var key = groups.get(group);
                inventory.set(slot, new Stack<>(key.key, kept, key.capacity)); remaining[group] -= kept;
            }
            for (int group = 0; group < groups.size(); group++) {
                var key = groups.get(group);
                for (int slot = 0; slot < INVENTORY_SIZE && remaining[group] > 0; slot++) {
                    var existing = inventory.get(slot);
                    if (existing.count() == 0 || !same.test(existing.key(), key.key)) continue;
                    int added = Math.min(remaining[group], key.capacity - existing.count());
                    inventory.set(slot, new Stack<>(key.key, existing.count() + added, key.capacity)); remaining[group] -= added;
                }
                for (int slot = 0; slot < INVENTORY_SIZE && remaining[group] > 0; slot++) {
                    if (inventory.get(slot).count() != 0) continue;
                    int added = Math.min(remaining[group], key.capacity);
                    inventory.set(slot, new Stack<>(key.key, added, key.capacity)); remaining[group] -= added;
                }
                if (remaining[group] > 0) return null;
            }
            return new Result<>(Failure.NONE, batches, table, inventory);
        }
    }
    private static boolean contains(int[] values, int value) { for (int candidate : values) if (candidate == value) return true; return false; }
    private static <T> List<Stack<T>> empty(int size) {
        List<Stack<T>> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) result.add(Stack.empty());
        return result;
    }
}
