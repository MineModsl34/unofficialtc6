package thaumcraft.api.golems;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** TC6 BETA26 golem contract, using native 1.21 entity/world/stack types. */
public interface IGolemAPI {
    LivingEntity getGolemEntity();
    IGolemProperties getProperties();
    void setProperties(IGolemProperties properties);
    Level getGolemWorld();
    ItemStack holdItem(ItemStack stack);
    ItemStack dropItem(ItemStack stack);
    boolean canCarry(ItemStack stack, boolean partial);
    int canCarryAmount(ItemStack stack);
    boolean isCarrying(ItemStack stack);
    NonNullList<ItemStack> getCarrying();
    void addRankXp(int amount);
    byte getGolemColor();
    void swingArm();
    boolean isInCombat();
}
