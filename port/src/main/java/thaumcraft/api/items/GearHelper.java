package thaumcraft.api.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/** Equipment queries shared by the caster cost and player warp systems. */
public final class GearHelper {
    private GearHelper() {
    }

    public static int getFinalWarp(ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int warp = stack.getItem() instanceof IWarpingGear gear ? gear.getWarp(stack, player) : 0;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            warp += data.copyTag().getByte("TC.WARP");
        }
        return warp;
    }

    public static int getFinalDiscount(ItemStack stack, Player player) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IVisDiscountGear gear
            ? gear.getVisDiscount(stack, player) : 0;
    }

    /** Original WarpEvents: main hand + four armor slots + functional Baubles/Curios, not offhand. */
    public static int getWarpFromGear(Player player) {
        if (player == null) {
            return 0;
        }
        int total = getFinalWarp(player.getMainHandItem(), player);
        for (ItemStack armor : player.getInventory().armor) {
            total += getFinalWarp(armor, player);
        }
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isPresent()) {
            var equipped = curios.get().getEquippedCurios();
            for (int slot = 0; slot < equipped.getSlots(); slot++) {
                total += getFinalWarp(equipped.getStackInSlot(slot), player);
            }
        }
        return total;
    }

    /** Gear contribution; CasterManager adds the separate vis exhaustion penalty. */
    public static float getGearVisDiscount(Player player) {
        if (player == null) {
            return 0.0F;
        }
        int total = 0;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isPresent()) {
            var equipped = curios.get().getEquippedCurios();
            for (int slot = 0; slot < equipped.getSlots(); slot++) {
                total += getFinalDiscount(equipped.getStackInSlot(slot), player);
            }
        }
        for (ItemStack armor : player.getInventory().armor) {
            total += getFinalDiscount(armor, player);
        }
        return total / 100.0F;
    }
}
