package thaumcraft.common.items.consumables;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Source dissolves on ItemExpire, after 200 ticks, not immediately on water contact. */
public final class ItemBathSalts extends Item{
    public ItemBathSalts(){super(new Properties());}
    @Override public int getEntityLifespan(ItemStack stack,Level level){return 200;}
}
