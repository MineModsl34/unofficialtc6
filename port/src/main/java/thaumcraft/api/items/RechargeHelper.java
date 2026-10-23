package thaumcraft.api.items;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.aura.AuraHelper;

/** TC6 integer charge and drain ordering; tc.charge NBT is now a persistent, synced data component. */
public final class RechargeHelper {
    public static final String NBT_TAG = "tc.charge";

    private RechargeHelper() {
    }

    public static float rechargeItem(Level world, ItemStack stack, BlockPos pos, Player player, int amount) {
        if (!rechargeable(stack) || world.isClientSide || amount <= 0) {
            return 0.0F;
        }
        if (player != null && AuraHelper.shouldPreserveAura(world, player, pos)) {
            return 0.0F;
        }
        amount = Math.min(amount, remaining(stack, player));
        if (amount <= 0) {
            return 0.0F;
        }
        // TC6 deliberately truncates drained fractional aura, rather than granting a fractional charge.
        int drained = (int) AuraHelper.drainVis(world, pos, amount, false);
        if (drained <= 0) {
            return 0.0F;
        }
        addCharge(stack, player, drained);
        return drained;
    }

    public static float rechargeItemBlindly(ItemStack stack, Player player, int amount) {
        if (!rechargeable(stack) || amount <= 0) {
            return 0.0F;
        }
        amount = Math.min(amount, remaining(stack, player));
        if (amount > 0) {
            addCharge(stack, player, amount);
        }
        return Math.max(0, amount);
    }

    private static int remaining(ItemStack stack, LivingEntity player) {
        return Math.max(0, ((IRechargable) stack.getItem()).getMaxCharge(stack, player) - getCharge(stack));
    }

    private static void addCharge(ItemStack stack, LivingEntity player, int amount) {
        int max = ((IRechargable) stack.getItem()).getMaxCharge(stack, player);
        stack.set(TCDataComponents.CHARGE, (int) Math.min(max, (long) getCharge(stack) + amount));
    }

    public static int getCharge(ItemStack stack) {
        return rechargeable(stack) ? stack.getOrDefault(TCDataComponents.CHARGE, 0) : -1;
    }

    public static float getChargePercentage(ItemStack stack, Player player) {
        if (!rechargeable(stack)) {
            return -1.0F;
        }
        return (float) getCharge(stack) / ((IRechargable) stack.getItem()).getMaxCharge(stack, player);
    }

    public static boolean consumeCharge(ItemStack stack, LivingEntity player, int amount) {
        // Negative amounts used to permit charge creation; reject invalid addon requests explicitly.
        if (!rechargeable(stack) || amount < 0 || !stack.has(TCDataComponents.CHARGE)) {
            return false;
        }
        int charge = getCharge(stack);
        if (charge < amount) {
            return false;
        }
        stack.set(TCDataComponents.CHARGE, charge - amount);
        return true;
    }

    private static boolean rechargeable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IRechargable;
    }
}
