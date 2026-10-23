package thaumcraft.api.items;

import net.minecraft.core.component.DataComponentType;
import thaumcraft.api.aspects.AspectList;

/**
 * Not in TC6: Data Component types that replace 1.12.2 item NBT. Filled in Registrar.
 * AspectList values in components are not changed in place, only through a copy and stack.set(...).
 */
public class TCDataComponents {
    /** NBT "Aspects" (ItemGenericEssentiaContainer, jars, phials, crystals). */
    public static DataComponentType<AspectList> ASPECTS;
    /** TC6 recharge NBT "tc.charge"; absent means zero. */
    public static DataComponentType<Integer> CHARGE;
    /** Source ordered "infench" list, not vanilla enchantments. */
    public static DataComponentType<thaumcraft.common.lib.enchantment.InfusionEnchantments> INFUSION_ENCHANTMENTS;
    /** Source caster "focus" compound, stored as an immutable native stack value. */
    public static DataComponentType<CasterStoredStack> CASTER_FOCUS;
    /** Source caster block picker, synchronized native stack used by Exchange/Plan. */
    public static DataComponentType<CasterStoredStack> CASTER_PICKED;
    /** Source labelled jar/item "AspectFilter". */
    public static DataComponentType<String> ASPECT_FILTER;
    /** Source mirror linkX/Y/Z/linkDim, flattened to an actual native dimension key. */
    public static DataComponentType<net.minecraft.core.GlobalPos> MIRROR_LINK;

    public static int validCharge(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Negative vis charge");
        }
        return value;
    }
}
