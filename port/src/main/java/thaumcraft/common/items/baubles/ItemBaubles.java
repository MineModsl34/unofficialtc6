package thaumcraft.common.items.baubles;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import thaumcraft.api.items.IVisDiscountGear;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** TC6 metadata variants 0..6 are separate registered items in 1.21. */
public class ItemBaubles extends Item implements ICurioItem, IVisDiscountGear {
    private final int legacyMeta;

    public ItemBaubles(int legacyMeta) {
        super(properties(legacyMeta));
        this.legacyMeta = legacyMeta;
    }

    private static Properties properties(int legacyMeta) {
        if (legacyMeta < 0 || legacyMeta > 6) {
            throw new IllegalArgumentException("Unknown TC6 basic bauble metadata: " + legacyMeta);
        }
        return new Properties().stacksTo(1).rarity(legacyMeta >= 3 ? Rarity.UNCOMMON : Rarity.COMMON);
    }

    public int legacyMeta() {
        return legacyMeta;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        String variant = switch (legacyMeta) {
            case 0 -> "amulet_mundane";
            case 1 -> "ring_mundane";
            case 2 -> "girdle_mundane";
            case 3 -> "ring_apprentice";
            case 4 -> "amulet_fancy";
            case 5 -> "ring_fancy";
            default -> "girdle_fancy";
        };
        return "item.thaumcraft.baubles_" + variant;
    }

    public String slotIdentifier() {
        return switch (legacyMeta) {
            case 1, 3, 5 -> "ring";
            case 2, 6 -> "belt";
            default -> "necklace";
        };
    }

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        return slotIdentifier().equals(context.identifier());
    }

    @Override
    public int getVisDiscount(ItemStack stack, Player player) {
        return legacyMeta == 3 ? 5 : 0;
    }
}
