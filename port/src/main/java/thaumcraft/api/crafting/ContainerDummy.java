package thaumcraft.api.crafting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Source non-interactable recipe container, never installed as a player's live menu. */
public class ContainerDummy extends AbstractContainerMenu {
    public ContainerDummy() { super(null, -1); }
    @Override public boolean stillValid(Player player) { return false; }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
