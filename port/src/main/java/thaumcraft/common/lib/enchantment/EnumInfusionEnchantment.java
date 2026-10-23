package thaumcraft.common.lib.enchantment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import thaumcraft.api.items.TCDataComponents;

/** All thirteen source ordinal ids. Only the source eight ConfigRecipes recipes are registered. */
public enum EnumInfusionEnchantment {
    COLLECTOR(Set.of("axe", "pickaxe", "shovel", "weapon"), 1, "INFUSIONENCHANTMENT"),
    DESTRUCTIVE(Set.of("axe", "pickaxe", "shovel"), 1, "INFUSIONENCHANTMENT"),
    BURROWING(Set.of("axe", "pickaxe"), 1, "INFUSIONENCHANTMENT"),
    SOUNDING(Set.of("pickaxe"), 4, "INFUSIONENCHANTMENT"),
    REFINING(Set.of("pickaxe"), 4, "INFUSIONENCHANTMENT"),
    ARCING(Set.of("weapon"), 4, "INFUSIONENCHANTMENT"),
    ESSENCE(Set.of("weapon"), 5, "INFUSIONENCHANTMENT"),
    VISBATTERY(Set.of("chargable"), 3, "?"),
    VISCHARGE(Set.of("chargable"), 1, "?"),
    SWIFT(Set.of("boots"), 4, "IEARMOR"),
    AGILE(Set.of("legs"), 1, "IEARMOR"),
    INFESTED(Set.of("chest"), 1, "IETAINT"),
    LAMPLIGHT(Set.of("axe", "pickaxe", "shovel"), 1, "INFUSIONENCHANTMENT");

    public final Set<String> toolClasses;
    public final int maxLevel;
    public final String research;
    EnumInfusionEnchantment(Set<String> classes, int maximum, String research) { toolClasses = classes; maxLevel = maximum; this.research = research; }
    public static final Codec<EnumInfusionEnchantment> CODEC = Codec.STRING.comapFlatMap(name -> {
        try { return DataResult.success(valueOf(name)); }
        catch (IllegalArgumentException exception) { return DataResult.error(() -> "Unknown infusion enchantment " + name); }
    }, EnumInfusionEnchantment::name);

    public static InfusionEnchantments data(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return InfusionEnchantments.EMPTY;
        if (TCDataComponents.INFUSION_ENCHANTMENTS != null && stack.has(TCDataComponents.INFUSION_ENCHANTMENTS)) return stack.get(TCDataComponents.INFUSION_ENCHANTMENTS);
        CustomData legacy = stack.get(DataComponents.CUSTOM_DATA);
        return legacy == null ? InfusionEnchantments.EMPTY : fromLegacy(legacy.copyTag().getList("infench", Tag.TAG_COMPOUND));
    }
    public static InfusionEnchantments fromLegacy(ListTag list) {
        List<InfusionEnchantments.Entry> entries = new ArrayList<>();
        for (int index = 0; index < Math.min(list.size(), InfusionEnchantments.MAX_ENTRIES); index++) {
            CompoundTag entry = list.getCompound(index); int id = entry.getShort("id");
            if (id >= 0 && id < values().length) entries.add(new InfusionEnchantments.Entry(id, entry.getShort("lvl")));
        }
        return new InfusionEnchantments(entries);
    }
    public static ListTag getInfusionEnchantmentTagList(ItemStack stack) {
        ListTag list = new ListTag();
        for (var entry : data(stack).entries()) { CompoundTag row = new CompoundTag(); row.putShort("id", (short)entry.id()); row.putShort("lvl", (short)entry.level()); list.add(row); }
        // Source hasTagCompound returned an empty list (not null) when other NBT existed.
        boolean hasSourceTags = stack != null && !stack.isEmpty()
            && stack.getComponentsPatch().entrySet().stream().anyMatch(entry -> entry.getKey() != DataComponents.DAMAGE);
        return list.isEmpty() && !hasSourceTags ? null : list;
    }
    public static List<EnumInfusionEnchantment> getInfusionEnchantments(ItemStack stack) {
        return data(stack).entries().stream().map(entry -> values()[entry.id()]).toList();
    }
    public static int getInfusionEnchantmentLevel(ItemStack stack, EnumInfusionEnchantment enchantment) { return data(stack).level(enchantment); }
    public static void addInfusionEnchantment(ItemStack stack, EnumInfusionEnchantment enchantment, int level) {
        if (stack == null || stack.isEmpty() || level > enchantment.maxLevel) return;
        InfusionEnchantments previous = data(stack), updated = previous.add(enchantment, level);
        if (updated == previous) return;
        stack.set(TCDataComponents.INFUSION_ENCHANTMENTS, updated);
        CustomData legacy = stack.get(DataComponents.CUSTOM_DATA);
        if (legacy != null) { CompoundTag tag = legacy.copyTag(); tag.remove("infench"); if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA); else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); }
    }
}
