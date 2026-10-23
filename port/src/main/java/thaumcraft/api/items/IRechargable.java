package thaumcraft.api.items;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Original TC6 API spelling retained for addon compatibility. */
public interface IRechargable {
    int getMaxCharge(ItemStack stack, LivingEntity wearer);

    EnumChargeDisplay showInHud(ItemStack stack, LivingEntity wearer);

    enum EnumChargeDisplay {
        NEVER,
        NORMAL,
        PERIODIC
    }
}
