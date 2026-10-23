package thaumcraft.api.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import thaumcraft.api.aspects.AspectList;

/** Snapshot of the physical crucible contents; never a vanilla crafting-grid input. */
public record CrucibleInput(ItemStack catalyst, AspectList aspects) implements RecipeInput {
    public CrucibleInput {
        catalyst = catalyst == null ? ItemStack.EMPTY : catalyst.copy();
        aspects = aspects == null ? null : aspects.copy();
    }

    @Override public ItemStack getItem(int index) {
        if (index != 0) throw new IndexOutOfBoundsException(index);
        return catalyst;
    }
    @Override public int size() { return 1; }
}
