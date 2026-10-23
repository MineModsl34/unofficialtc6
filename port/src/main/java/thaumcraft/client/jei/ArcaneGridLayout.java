package thaumcraft.client.jei;

import java.util.List;
import thaumcraft.common.lib.crafting.ArcaneTransferPlanner;

/** Maps trimmed native patterns to the visible 3x3 table without dropping empty cells. */
public final class ArcaneGridLayout {
    private ArcaneGridLayout() {}

    public static List<Integer> shapedSlots(int width, int height) {
        return ArcaneTransferPlanner.shapedSlots(width, height);
    }
}
