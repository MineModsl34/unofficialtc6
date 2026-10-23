package thaumcraft.client.jei;

import java.util.ArrayList;
import java.util.List;
import thaumcraft.common.lib.crafting.InfusionCosts;

/** Display exactly the levels accepted by the executable recipe's overflow gate. */
public final class InfusionDisplayStages {
    private InfusionDisplayStages() {}

    public static List<Integer> enchantmentLevels(int maximum) {
        if (maximum < 1 || maximum > Short.MAX_VALUE) throw new IllegalArgumentException("Invalid enchantment maximum");
        List<Integer> result = new ArrayList<>(maximum);
        for (int level = 1; level <= maximum; level++) result.add(level);
        return List.copyOf(result);
    }

    public static List<Integer> runicCharges() {
        List<Integer> result = new ArrayList<>();
        for (int charge = 0; charge < Byte.MAX_VALUE; charge++) {
            if (InfusionCosts.runicVis(charge) <= 0) break;
            result.add(charge);
        }
        return List.copyOf(result);
    }
}
