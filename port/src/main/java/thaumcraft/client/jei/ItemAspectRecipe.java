package thaumcraft.client.jei;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import thaumcraft.client.lib.ClientAspectSnapshot;

/** One received component-preserving source item and all its real aspect quantities. */
public final class ItemAspectRecipe {
    private final ItemStack stack;
    private final List<JeiAspectIngredient> aspects;

    public ItemAspectRecipe(ClientAspectSnapshot.Entry entry) {
        stack = entry.stack();
        aspects = JeiAspectIngredient.fromAmounts(entry.aspects());
    }
    public ItemStack stack() { return stack.copy(); }
    public List<JeiAspectIngredient> aspects() { return aspects; }
}
