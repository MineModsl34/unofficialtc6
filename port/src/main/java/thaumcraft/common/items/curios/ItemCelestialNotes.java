package thaumcraft.common.items.curios;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** TC6's thirteen celestial-note metadata values split into thirteen registered modern items. */
public class ItemCelestialNotes extends Item {
    public static final String DESCRIPTION_ID = "item.celestial_notes";
    public static final List<String> VARIANTS = List.of("sun", "stars_1", "stars_2", "stars_3", "stars_4",
        "moon_1", "moon_2", "moon_3", "moon_4", "moon_5", "moon_6", "moon_7", "moon_8");
    public static final int VARIANT_COUNT = 13;

    private final int metadata;

    public ItemCelestialNotes(int metadata) {
        super(properties(metadata));
        this.metadata = metadata;
    }

    private static Properties properties(int metadata) {
        variantName(metadata); // Fail before creating an item with an invented fallback variant.
        // ItemTCBase's source defaults: stack64, no damage, common rarity, ordinary repair disabled.
        return new Properties().setNoRepair();
    }

    public int metadata() { return metadata; }
    public String variantName() { return variantName(metadata); }

    /** Source overlay filenames differ from variant names: stars_1 -> celestial/stars1.png. */
    public String textureSuffix() { return textureSuffix(metadata); }

    public static String variantName(int metadata) {
        if (metadata < 0 || metadata >= VARIANT_COUNT) {
            throw new IllegalArgumentException("Unknown TC6 celestial notes metadata: " + metadata);
        }
        return VARIANTS.get(metadata);
    }

    public static String textureSuffix(int metadata) { return variantName(metadata).replace("_", ""); }

    public static Component variantTooltip(int metadata) {
        return Component.translatable(DESCRIPTION_ID + "." + variantName(metadata) + ".text").withStyle(ChatFormatting.AQUA);
    }

    @Override
    public String getDescriptionId() { return DESCRIPTION_ID; }

    @Override
    public String getDescriptionId(ItemStack stack) { return DESCRIPTION_ID; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(variantTooltip(metadata));
    }
}
