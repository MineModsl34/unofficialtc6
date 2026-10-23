package thaumcraft.common.golems.client.gui;
import net.minecraft.world.SimpleContainer;
/** Read-only 81 source catalogue cells; not actual inventory ownership. */
public final class InventoryLogistics extends SimpleContainer {public InventoryLogistics(){super(81);}@Override public int getMaxStackSize(){return Integer.MAX_VALUE;}}
