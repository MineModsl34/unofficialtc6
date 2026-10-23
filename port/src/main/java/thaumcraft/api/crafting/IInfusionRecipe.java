package thaumcraft.api.crafting;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.AspectList;

/** Modern explicit interface for the original overridable InfusionRecipe owner-dependent API. */
public interface IInfusionRecipe extends IThaumcraftRecipe {
    boolean matches(List<ItemStack> components, ItemStack central, Level level, Player owner);
    Ingredient getRecipeInput();
    NonNullList<Ingredient> getComponents();
    Object getRecipeOutput();
    Object getRecipeOutput(Player owner, ItemStack input, List<ItemStack> components);
    AspectList getAspects();
    AspectList getAspects(Player owner, ItemStack input, List<ItemStack> components);
    int getInstability(Player owner, ItemStack input, List<ItemStack> components);
}
