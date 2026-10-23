package thaumcraft.common.lib.crafting;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.items.resources.ItemCrystalEssence;

/**
 * TC6 RecipeMagicDust: bowl + flint + redstone + three DIFFERENT FIRST crystal aspect tags.
 * Counts and contained aspect amounts do not restrict matching; ordinary ResultSlot consumes
 * one per occupied cell and this recipe returns bowl/flint copies with their full components.
 * CraftingInput's trimmed grid replaces the old 3x3 inventory accessor; no ingredient resolver,
 * primal-only restriction, research prerequisite or acquisition grant is added here.
 * Normal compact/sparse grids are covered; larger addon grids use the modern whole-input
 * bridge, not a claim of parity with the original hard-coded 3x3 accessor.
 * Explicit modern safety boundary: absent/empty/null first aspect data rejects instead of the
 * original getAspects()[0] null/index exception. Valid multi-aspect crystals retain first-only
 * matching, including zero/negative amounts; later aspects are intentionally not inspected.
 */
public class RecipeMagicDust extends CustomRecipe {
    public RecipeMagicDust(CraftingBookCategory category) { super(category); }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean bowl = false;
        boolean flint = false;
        boolean redstone = false;
        Set<String> crystals = new HashSet<>();
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.BOWL)) {
                if (bowl) return false;
                bowl = true;
            } else if (stack.is(Items.FLINT)) {
                if (flint) return false;
                flint = true;
            } else if (stack.is(Items.REDSTONE)) {
                if (redstone) return false;
                redstone = true;
            } else {
                if (stack.getItem() != ItemsTC.crystalEssence || !(stack.getItem() instanceof ItemCrystalEssence)
                    || crystals.size() >= 3) return false;
                String tag = firstAspectTag(stack);
                if (tag == null || !crystals.add(tag)) return false;
            }
        }
        return bowl && flint && redstone && crystals.size() == 3;
    }

    private static String firstAspectTag(ItemStack stack) {
        if (TCDataComponents.ASPECTS == null) return null;
        AspectList raw = stack.get(TCDataComponents.ASPECTS);
        if (raw == null || raw.aspects == null || raw.aspects.isEmpty()) return null;
        // The real item copies its component on read, preserving source first-key iteration.
        AspectList aspects = ((ItemCrystalEssence) stack.getItem()).getAspects(stack);
        if (aspects == null) return null;
        Aspect[] ordered = aspects.getAspects();
        if (ordered.length == 0 || ordered[0] == null) return null;
        String tag = ordered[0].getTag();
        return tag == null || tag.isEmpty() ? null : tag;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return new ItemStack(ItemsTC.salisMundus);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(ItemsTC.salisMundus);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return width * height >= 6; }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < result.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            ItemStack remainder = stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem() : ItemStack.EMPTY;
            if (!stack.isEmpty() && (stack.is(Items.BOWL) || stack.is(Items.FLINT))) {
                remainder = stack.copyWithCount(1);
            }
            result.set(slot, remainder);
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() { return ResearchAcquisitionRecipes.MAGIC_DUST; }
}
