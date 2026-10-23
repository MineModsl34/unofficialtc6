package thaumcraft.common.items.tools;

import net.minecraft.world.item.Item;
import thaumcraft.api.items.IScribeTools;

/** TC6 writing tool properties only: no new refill, use, ink-consumption or recipe behavior. */
public class ItemScribingTools extends Item implements IScribeTools {
    public static final int MAX_DAMAGE = 100;

    public ItemScribingTools() {
        // durability also sets stack size one. ItemTCBase in TC6 disabled ordinary repairs.
        super(new Properties().durability(MAX_DAMAGE).setNoRepair());
    }
}
