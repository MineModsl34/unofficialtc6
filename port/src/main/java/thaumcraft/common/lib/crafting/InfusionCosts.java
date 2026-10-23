package thaumcraft.common.lib.crafting;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/** Exact TC6 float/truncation/exponential arithmetic, separated for cheap bounded tests. */
public final class InfusionCosts {
    private InfusionCosts() {}
    public static AspectList enchanted(AspectList base, int nextLevel, int otherEnchantments) {
        AspectList result = new AspectList(); float modifier = nextLevel + otherEnchantments * 0.33F;
        for (Aspect aspect : base.getAspects()) result.add(aspect, (int)(base.getAmount(aspect) * modifier));
        return result;
    }
    public static int runicVis(int charge) { return 20 + (int)(20.0 * Math.pow(2.0, charge)); }
    public static int runicInstability(int charge) { return 5 + charge / 2; }
    public static boolean extraWarp(int existingEntries, int rollTen) { return rollTen < existingEntries; }
}
