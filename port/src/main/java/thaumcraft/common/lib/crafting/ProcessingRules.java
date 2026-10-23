package thaumcraft.common.lib.crafting;

import java.util.function.DoubleSupplier;

/** Arithmetic lifted from TileInfernalFurnace/TileSmelter, independent of engine bootstrap. */
public final class ProcessingRules {
    private ProcessingRules() {}
    public static int infernalCookTime(boolean speedy,int bellows) {
        int b = Math.max(0,Math.min(4,bellows));
        return Math.max(10,(speedy ? 80 : 140) - (b == 0 ? 0 : (20 - (b - 1)) * b));
    }
    public static boolean bonus(float roll,float chance) { return roll <= chance; }
    /** Source per-flux-point vents are sequential trials; stop at the first successful vent. */
    public static boolean vented(int vents,DoubleSupplier rolls) {
        for (int i = 0; i < vents; i++) if (rolls.getAsDouble() < .333) return true;
        return false;
    }
    public static int vanillaBellowsProgress(int progress) { return vanillaBellowsProgress(progress,200); }
    /** Native completion uses == totalTime. Never skip a modern datapack's shorter finish point. */
    public static int vanillaBellowsProgress(int progress,int totalTime) { return progress > 0 && progress < Math.min(199,totalTime - 1) ? progress + 1 : progress; }
    public static int experience(int count,float experience,DoubleSupplier fractionalRoll) {
        if (experience == 0) return 0;
        if (experience >= 1) return count; // TC6 furnace treats all experience>=1 this way.
        double exact = count * experience;
        int value = (int)Math.floor(exact);
        return value < Math.ceil(exact) && fractionalRoll.getAsDouble() < exact - value ? value + 1 : value;
    }
}
