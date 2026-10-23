package thaumcraft.common.items.armor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
/** These genuine source infusion labels remain persistent synced native custom data. */
public final class WearableTags {
    private WearableTags() {}
    public static CompoundTag data(ItemStack stack) { var value = stack.get(DataComponents.CUSTOM_DATA); return value == null ? new CompoundTag() : value.copyTag(); }
    public static boolean has(ItemStack stack, String key) { return data(stack).contains(key); }
    public static int integer(ItemStack stack, String key) { return data(stack).getInt(key); }
    public static void integer(ItemStack stack, String key, int value) { var data = data(stack); data.putInt(key,value); stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data)); }
    public static int verdantType(ItemStack stack) { return data(stack).getByte("type"); }
}
