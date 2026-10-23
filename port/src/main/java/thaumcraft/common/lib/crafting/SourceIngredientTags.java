package thaumcraft.common.lib.crafting;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/** Pure TC6 ThaumcraftInvHelper.compareTagsRelaxed; native item codecs are not initialized. */
public final class SourceIngredientTags {
    private SourceIngredientTags() {}
    public static boolean contains(CompoundTag expected, CompoundTag actual) {
        for (String key : expected.getAllKeys())
            if (actual == null || !Objects.equals(expected.get(key), actual.get(key))) return false;
        return true;
    }
}
