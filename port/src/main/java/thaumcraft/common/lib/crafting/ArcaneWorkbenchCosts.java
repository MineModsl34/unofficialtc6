package thaumcraft.common.lib.crafting;

import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.items.GearHelper;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.lib.potions.WarpEffects;
import top.theillusivec4.curios.api.CuriosApi;

/** Source CasterManager discount and strict one-aspect crystal component equality. */
public final class ArcaneWorkbenchCosts {
    public static final Aspect[] PRIMALS = {Aspect.AIR, Aspect.FIRE, Aspect.WATER, Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY};
    public static final int[] X = {64, 17, 112, 17, 112, 64}, Y = {13, 35, 35, 93, 93, 115};
    private ArcaneWorkbenchCosts() {}
    /** Bounded JSON/wire recipe fields, shared by the shaped and shapeless serializers. */
    public static void validateRecipeCosts(String group, String research, int vis, List<Integer> crystals) {
        if (group == null || group.length() > 256 || research == null || research.length() > 256
            || vis < 0 || vis > 32767 || crystals == null || crystals.size() != 6
            || crystals.stream().anyMatch(count -> count == null || count < 0 || count > 64))
            throw new IllegalArgumentException("Invalid arcane recipe costs");
    }
    public static float discount(Player player) {
        if (player == null) return 0F;
        int gear = 0;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isPresent()) {
            var equipped = curios.get().getEquippedCurios();
            for (int slot = 0; slot < equipped.getSlots(); slot++) gear += GearHelper.getFinalDiscount(equipped.getStackInSlot(slot), player);
        }
        for (ItemStack armor : player.getInventory().armor) gear += GearHelper.getFinalDiscount(armor, player);
        int first = WarpEffects.VIS_EXHAUST != null && player.hasEffect(WarpEffects.VIS_EXHAUST)
            ? player.getEffect(WarpEffects.VIS_EXHAUST).getAmplifier() : -1;
        int second = WarpEffects.INFECTIOUS_VIS_EXHAUST != null && player.hasEffect(WarpEffects.INFECTIOUS_VIS_EXHAUST)
            ? player.getEffect(WarpEffects.INFECTIOUS_VIS_EXHAUST).getAmplifier() : -1;
        return discount(gear, first, second);
    }
    /** -1 denotes absent effect. Original combines integer percents before dividing once. */
    public static float discount(int gear, int first, int second) {
        if (first >= 0 || second >= 0) gear -= (Math.max(Math.max(first, 0), Math.max(second, 0)) + 1) * 10;
        return gear / 100F;
    }
    public static int visCost(int base, float discount) { return (int) (base * (1F - discount)); }
    public static boolean crystal(ItemStack stack, int index) {
        var aspects = TCDataComponents.ASPECTS == null ? null : stack.get(TCDataComponents.ASPECTS);
        return index >= 0 && index < 6 && !stack.isEmpty() && aspects != null && aspects.aspects != null
            && ItemStack.isSameItemSameComponents(stack, ThaumcraftApiHelper.makeCrystal(PRIMALS[index]));
    }
    public static boolean hasCrystals(Container table, IArcaneRecipe recipe) {
        if (table.getContainerSize() < 15) return false;
        for (int aspect = 0; aspect < 6; aspect++) {
            int amount = recipe.crystals().get(aspect);
            if (amount == 0) continue;
            boolean found = false;
            for (int slot = 9; slot < 15; slot++) if (crystal(table.getItem(slot), aspect) && table.getItem(slot).getCount() >= amount) { found = true; break; }
            if (!found) return false;
        }
        return true;
    }
}
