package thaumcraft.common.lib.crafting;

import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.ItemsTC;

/** Original two instanceof-robe recipes share their algorithm. Actual armor stays in its
 * later equipment stage; no surrogate Item or leather recipe is installed to enable this.
 * Native DYED_COLOR replaces display.color, with a read-only legacy fallback and all other
 * components preserved. Source hasColor() is always true, including undyed purple robes.
 */
public abstract class SourceRobeDyeRecipe extends CustomRecipe {
    public static final int DEFAULT_ROBE_COLOR = 6961280;
    public enum Family { CLOTH, VOID }

    /** Real later-stage robe subclasses/add-ons can retain their source color overrides.
     * Must also extend ArmorItem: source rejects other items even if dyeable/tagged.
     */
    public interface ColorAccess {
        Family robeDyeFamily();
        default boolean hasRobeColor(ItemStack stack) { return true; }
        default int getRobeColor(ItemStack stack) { return sourceColor(stack); }
        default void setRobeColor(ItemStack stack, int rgb) { setNativeColor(stack, rgb); }
    }

    private final Family family;
    protected SourceRobeDyeRecipe(CraftingBookCategory category, Family family) { super(category); this.family = family; }

    private boolean acceptsArmor(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem)) return false;
        if (stack.getItem() instanceof ColorAccess access) return access.robeDyeFamily() == family;
        return family == Family.CLOTH
            ? stack.getItem() == ItemsTC.clothChest || stack.getItem() == ItemsTC.clothLegs || stack.getItem() == ItemsTC.clothBoots
            : stack.getItem() == ItemsTC.voidRobeHelm || stack.getItem() == ItemsTC.voidRobeChest || stack.getItem() == ItemsTC.voidRobeLegs;
    }

    @Override public boolean matches(CraftingInput input, Level level) {
        boolean armor = false, dye = false;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ArmorItem) {
                if (armor || !acceptsArmor(stack)) return false;
                armor = true;
            } else {
                if (sourceDyeColor(stack) == null) return false;
                dye = true;
            }
        }
        return armor && dye;
    }

    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = ItemStack.EMPTY;
        ColorAccess colorAccess = null;
        SourceRobeColorMixer mixer = new SourceRobeColorMixer();
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ArmorItem) {
                if (!result.isEmpty() || !acceptsArmor(stack)) return ItemStack.EMPTY;
                result = stack.copyWithCount(1);
                colorAccess = stack.getItem() instanceof ColorAccess access ? access : null;
                if (colorAccess == null || colorAccess.hasRobeColor(stack)) mixer.addArmorColor(colorAccess == null ? sourceColor(result) : colorAccess.getRobeColor(result));
            } else {
                DyeColor dye = sourceDyeColor(stack);
                if (dye == null) return ItemStack.EMPTY;
                mixer.addDyeColor(dye.getTextureDiffuseColor());
            }
        }
        // Only malformed override hasColor=false/no-dyes is safer than source division by zero.
        if (result.isEmpty() || mixer.sampleCount() == 0) return ItemStack.EMPTY;
        if (colorAccess == null) setNativeColor(result, mixer.finish()); else colorAccess.setRobeColor(result, mixer.finish());
        return result;
    }

    public static int sourceColor(ItemStack stack) {
        DyedItemColor nativeColor = stack.get(DataComponents.DYED_COLOR);
        if (nativeColor != null) return nativeColor.rgb();
        var legacy = stack.get(DataComponents.CUSTOM_DATA);
        if (legacy != null) {
            var display = legacy.copyTag().getCompound("display");
            if (display.contains("color")) return display.getInt("color");
        }
        return DEFAULT_ROBE_COLOR;
    }

    public static void setNativeColor(ItemStack stack, int color) {
        DyedItemColor prior = stack.get(DataComponents.DYED_COLOR);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, prior == null || prior.showInTooltip()));
    }

    @Nullable public static DyeColor sourceDyeColor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof DyeItem dye) return dye.getDyeColor();
        // OreDictionary registration order is unavailable. Standard tags use source enum
        // white..black order; include custom black (native getColor currently skips id15).
        for (DyeColor dye : DyeColor.values()) if (stack.is(dye.getTag())) return dye;
        return null;
    }

    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) result.set(slot, input.getItem(slot).getCraftingRemainingItem());
        return result;
    }
}
