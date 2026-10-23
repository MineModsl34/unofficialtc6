package thaumcraft.common.golems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import thaumcraft.api.golems.IGolemProperties;

/** Native component storage of the exact source props(long)/xp(int) item NBT contract. */
public final class GolemItemData {
    private GolemItemData() {}
    public static CompoundTag tag(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag(); }
    public static GolemProperties properties(ItemStack stack) {
        var tag = tag(stack); if (tag.contains("props") && !tag.contains("props",Tag.TAG_LONG)) throw new IllegalArgumentException("Invalid golem props tag");
        return GolemProperties.fromLong(tag.getLong("props"));
    }
    public static int experience(ItemStack stack) { return Math.max(0,Math.min(121000,tag(stack).getInt("xp"))); }
    public static ItemStack write(ItemStack stack,IGolemProperties properties,int xp) {
        if (!GolemProperties.isValid(properties.toLong())) throw new IllegalArgumentException("Invalid golem properties");
        var tag = tag(stack); tag.putLong("props",properties.toLong());
        if (xp > 0) tag.putInt("xp",Math.min(xp,121000)); else tag.remove("xp");
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag)); return stack;
    }
}
