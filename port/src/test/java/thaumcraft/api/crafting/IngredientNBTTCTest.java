package thaumcraft.api.crafting;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.crafting.SourceIngredientTags;

/** Pure legacy tag matching only; no native item registry/bootstrap or game process needed. */
class IngredientNBTTCTest {
    @Test void requiresExpectedTopLevelKeysButAllowsUnrelatedExtras() {
        CompoundTag expected = new CompoundTag(); expected.putString("source", "value");
        CompoundTag actual = expected.copy(); actual.putInt("addon", 42);
        assertTrue(SourceIngredientTags.contains(expected, actual));
        assertFalse(SourceIngredientTags.contains(expected, null));
        assertFalse(SourceIngredientTags.contains(expected, new CompoundTag()));
        actual.putString("source", "different");
        assertFalse(SourceIngredientTags.contains(expected, actual));
        assertTrue(SourceIngredientTags.contains(new CompoundTag(), null));
    }
    @Test void nestedCompoundAndAspectListAreExactNotRecursiveSubsetTests() {
        CompoundTag expected = new CompoundTag();
        CompoundTag nested = new CompoundTag(); nested.putInt("amount", 1); expected.put("nested", nested);
        CompoundTag actual = expected.copy(); actual.getCompound("nested").putString("extra", "not source-equal");
        assertFalse(SourceIngredientTags.contains(expected, actual));
        assertTrue(SourceIngredientTags.contains(expected, expected.copy()));
        ListTag list = new ListTag(); CompoundTag row = new CompoundTag(); row.putString("aspect", "aer"); list.add(row);
        expected.put("Aspects", list);
        actual = expected.copy(); actual.getList("Aspects", 10).add(row.copy());
        assertFalse(SourceIngredientTags.contains(expected, actual));
    }
}
