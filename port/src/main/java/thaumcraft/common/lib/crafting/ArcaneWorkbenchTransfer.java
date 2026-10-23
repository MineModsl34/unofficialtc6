package thaumcraft.common.lib.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.common.container.ContainerArcaneWorkbench;

/** Shared client preflight/server plan. Only the server menu commits this read-only result. */
public final class ArcaneWorkbenchTransfer {
    private ArcaneWorkbenchTransfer() {}
    public record Snapshot(List<ItemStack> table, List<ItemStack> inventory) {
        public Snapshot { table = table.stream().map(ItemStack::copy).toList(); inventory = inventory.stream().map(ItemStack::copy).toList(); }
        public boolean matches(ContainerArcaneWorkbench menu) {
            for (int slot = 0; slot < 15; slot++) if (!ItemStack.matches(table.get(slot), menu.table().getItem(slot))) return false;
            for (int slot = 0; slot < 36; slot++) if (!ItemStack.matches(inventory.get(slot), menu.slots.get(16 + slot).getItem())) return false;
            return true;
        }
    }
    public record Plan(ArcaneTransferPlanner.Failure failure, int batches, List<ItemStack> table, List<ItemStack> inventory) {
        public Plan { table = table.stream().map(ItemStack::copy).toList(); inventory = inventory.stream().map(ItemStack::copy).toList(); }
        public boolean successful() { return failure == ArcaneTransferPlanner.Failure.NONE; }
    }
    public static Snapshot snapshot(ContainerArcaneWorkbench menu) {
        List<ItemStack> table = new ArrayList<>(15), inventory = new ArrayList<>(36);
        for (int slot = 0; slot < 15; slot++) table.add(menu.table().getItem(slot));
        for (int slot = 16; slot < 52; slot++) inventory.add(menu.slots.get(slot).getItem());
        return new Snapshot(table, inventory);
    }
    public static boolean supported(IArcaneRecipe recipe) { return recipe instanceof RecipeArcaneWorkbench || recipe instanceof ShapelessArcaneRecipe; }
    public static Plan plan(IArcaneRecipe recipe, Snapshot snapshot, boolean maximum) {
        if (!supported(recipe)) throw new IllegalArgumentException("Unsupported arcane recipe geometry");
        List<ArcaneTransferPlanner.Requirement<ItemStack>> requirements = new ArrayList<>();
        List<Ingredient> ingredients = recipe.getIngredients();
        List<Integer> cells = recipe instanceof RecipeArcaneWorkbench shaped
            ? ArcaneTransferPlanner.shapedSlots(shaped.pattern().width(), shaped.pattern().height())
            : java.util.stream.IntStream.range(0, ingredients.size()).boxed().toList();
        for (int index = 0; index < ingredients.size(); index++) {
            Ingredient ingredient = ingredients.get(index);
            // Custom ingredient predicates must never receive the planner's component-identity key.
            if (!ingredient.isEmpty()) requirements.add(new ArcaneTransferPlanner.Requirement<>(cells.get(index), 1, stack -> ingredient.test(stack.copy())));
        }
        for (int index = 0; index < 6; index++) {
            int aspect = index;
            requirements.add(new ArcaneTransferPlanner.Requirement<>(9 + index, recipe.crystals().get(index), stack -> ArcaneWorkbenchCosts.crystal(stack, aspect)));
        }
        var result = ArcaneTransferPlanner.plan(snapshot.table().stream().map(ArcaneWorkbenchTransfer::stack).toList(),
            snapshot.inventory().stream().map(ArcaneWorkbenchTransfer::stack).toList(), requirements,
            ItemStack::isSameItemSameComponents, maximum);
        return new Plan(result.failure(), result.batches(), result.table().stream().map(ArcaneWorkbenchTransfer::item).toList(),
            result.inventory().stream().map(ArcaneWorkbenchTransfer::item).toList());
    }
    private static ArcaneTransferPlanner.Stack<ItemStack> stack(ItemStack item) {
        return item.isEmpty() ? ArcaneTransferPlanner.Stack.empty()
            : new ArcaneTransferPlanner.Stack<>(item.copyWithCount(1), item.getCount(), Math.min(64, item.getMaxStackSize()));
    }
    private static ItemStack item(ArcaneTransferPlanner.Stack<ItemStack> stack) {
        return stack.count() == 0 ? ItemStack.EMPTY : stack.key().copyWithCount(stack.count());
    }
}
