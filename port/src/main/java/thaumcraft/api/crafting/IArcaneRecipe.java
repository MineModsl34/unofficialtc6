package thaumcraft.api.crafting;

import java.util.List;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;

/** Modern typed counterpart of source IArcaneRecipe. Both shapes share one separate
 * recipe type, NOT vanilla CraftingRecipe. Crystals follow source ShardType order. */
public interface IArcaneRecipe extends Recipe<CraftingInput>, IThaumcraftRecipe {
    @Override String getResearch();
    int getVis();
    AspectList getCrystals();
    default String research() { return getResearch(); }
    default int vis() { return getVis(); }
    default List<Integer> crystals() { return ArcaneRecipeInputs.crystals(getCrystals()); }
    @Override default String getGroup() { return ""; }
    /** Fresh API copy; callers cannot change immutable recipe costs. */
    static AspectList aspects(List<Integer> crystals) {
        AspectList result = new AspectList();
        for (int index = 0; index < 6; index++) {
            int count = crystals.get(index);
            if (count > 0) result.add(ArcaneWorkbenchCosts.PRIMALS[index], count);
        }
        return result.size() == 0 ? null : result;
    }
}
