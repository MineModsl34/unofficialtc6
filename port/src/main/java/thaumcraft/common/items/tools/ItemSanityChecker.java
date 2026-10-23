package thaumcraft.common.items.tools;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
/** Source item is passive; the actual meter is the client HUD, not an invented right-click action. */
public final class ItemSanityChecker extends Item {
    public ItemSanityChecker(){super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));}
}
