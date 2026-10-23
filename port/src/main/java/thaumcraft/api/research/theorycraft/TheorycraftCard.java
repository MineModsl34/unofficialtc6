package thaumcraft.api.research.theorycraft;

import java.util.Arrays;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Source card contract. Only the translation representation and stack-NBT lookup context changed. */
public abstract class TheorycraftCard {
    private long seed = -1L;
    protected HolderLookup.Provider registries = RegistryAccess.EMPTY;
    public final void bindRegistries(HolderLookup.Provider value) { registries = value; }
    public long getSeed() { if (seed < 0L) setSeed(System.nanoTime()); return seed; }
    public void setSeed(long value) { seed = Math.abs(value); }
    public boolean initialize(Player player, ResearchTableData data) { return true; }
    public boolean isAidOnly() { return false; }
    public abstract int getInspirationCost();
    public String getResearchCategory() { return null; }
    public abstract Component getLocalizedName();
    public abstract Component getLocalizedText();
    public ItemStack[] getRequiredItems() { return null; }
    public boolean[] getRequiredItemsConsumed() {
        ItemStack[] items = getRequiredItems();
        if (items == null) return null;
        boolean[] result = new boolean[items.length];
        Arrays.fill(result, false);
        return result;
    }
    /** Missing later-stage registered objects are requirements, never an empty-stack exemption. */
    public boolean requirementsResolved() {
        ItemStack[] requirements = getRequiredItems();
        if (requirements != null) for (ItemStack stack : requirements) if (stack == null || stack.isEmpty()) return false;
        return true;
    }
    public abstract boolean activate(Player player, ResearchTableData data);
    public CompoundTag serialize() { CompoundTag tag = new CompoundTag(); tag.putLong("seed", seed); return tag; }
    public void deserialize(CompoundTag tag) { if (tag != null) seed = tag.getLong("seed"); }
}
