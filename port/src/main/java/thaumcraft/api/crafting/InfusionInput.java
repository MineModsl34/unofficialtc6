package thaumcraft.api.crafting;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** Physical pedestal snapshot, including its real initiating owner; never a vanilla grid. */
public record InfusionInput(ItemStack central, List<ItemStack> components, Player owner) implements RecipeInput {
    public InfusionInput { central = central.copy(); components = components.stream().map(ItemStack::copy).toList(); }
    @Override public ItemStack getItem(int index) { return index == 0 ? central : components.get(index - 1); }
    @Override public int size() { return components.size() + 1; }
}
