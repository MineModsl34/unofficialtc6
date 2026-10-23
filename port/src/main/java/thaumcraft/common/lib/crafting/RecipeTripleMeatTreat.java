package thaumcraft.common.lib.crafting;

import java.util.IdentityHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.research.LegacyScanIdentity;

/** TC6 one sugar + three DISTINCT chunks metadata, not any nuggetMeat or three same meats.
 * Counts/components do not constrain matching. Original hard-coded3x3 is bridged by modern
 * trimmed CraftingInput; larger addon grids inspect all cells, like RecipeMagicDust.
 * No food/placeholder items are registered here. Missing actual output rejects safely.
 */
public final class RecipeTripleMeatTreat extends CustomRecipe {
    private static final Map<Item,Integer> FLATTENED_CHUNKS = java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    public RecipeTripleMeatTreat(CraftingBookCategory category) { super(category); }

    /** Add-ons can explicitly override their actual old damage value. Canonical six source
     * flattened ids work without this extra hook. Neither path creates/registers food items.
     */
    public static void registerChunkVariant(Item item, int sourceMetadata) {
        FLATTENED_CHUNKS.put(java.util.Objects.requireNonNull(item), sourceMetadata);
    }
    @Nullable private static Integer chunkMetadata(ItemStack stack) {
        Integer explicit = FLATTENED_CHUNKS.get(stack.getItem());
        if (explicit != null) return explicit;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Integer canonical = SourceTripleMeatRules.canonicalChunkMetadata(id.getNamespace(), id.getPath());
        if (canonical != null) return canonical;
        // Only the genuine legacy shared item uses raw DAMAGE. Canonical before alias ensures
        // a later ItemsTC.chunks alias to one flattened item cannot collapse its metadata to0.
        if (ItemsTC.chunks != null && stack.getItem() == ItemsTC.chunks) return LegacyScanIdentity.rawDamage(stack);
        return null;
    }

    @Override public boolean matches(CraftingInput input, Level level) {
        if (ItemsTC.tripleMeatTreat == null) return false;
        SourceTripleMeatRules ingredients = new SourceTripleMeatRules();
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.SUGAR)) {
                if (!ingredients.addSugar()) return false;
            } else {
                Integer metadata = chunkMetadata(stack);
                if (metadata == null || !ingredients.addMeat(metadata)) return false;
            }
        }
        return ingredients.complete();
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return getResultItem(registries); }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemsTC.tripleMeatTreat == null ? ItemStack.EMPTY : new ItemStack(ItemsTC.tripleMeatTreat); }
    @Override public boolean isSpecial() { return false; } // Source IRecipe.isDynamic default=false, unlike dyes.
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 4; }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) result.set(slot, input.getItem(slot).getCraftingRemainingItem());
        return result;
    }
    @Override public RecipeSerializer<?> getSerializer() { return OrdinaryCustomRecipes.TRIPLE_MEAT; }
}
