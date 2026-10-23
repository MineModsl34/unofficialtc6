package thaumcraft.common.lib.research;

import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import thaumcraft.api.research.IScanThing;
import thaumcraft.api.research.ScanningManager;

/**
 * TC6's enchantment-presence scanner. A registry key replaces the old stable singleton
 * identity, so a handler never retains a Holder/value belonging to a previous world's registry.
 * The marker is the caller's opaque old getName(), NOT the modern registry/description ID.
 * Addons/new 1.21 enchantments may explicitly supply their own name; that is not a claim that
 * their enchantment or marker existed in TC6. Construction does not mutate scanner registries.
 */
public final class ScanEnchantment implements IScanThing {
    private final ResourceKey<Enchantment> target;
    private final String legacyName;

    public ScanEnchantment(ResourceKey<Enchantment> target, String legacyName) {
        this.target = Objects.requireNonNull(target, "target");
        this.legacyName = Objects.requireNonNull(legacyName, "legacyName");
    }

    public ResourceKey<Enchantment> target() { return target; }

    public String legacyName() { return legacyName; }

    @Override
    public boolean checkThing(Player player, Object object) {
        if (object == null) return false;
        ItemStack stack = ScanningManager.getItemFromParms(player, object);
        if (stack == null || stack.isEmpty()) return false;
        // This deliberately ignores the value/level, including a codec-retained level 0.
        // For enchanted books the helper selects STORED_ENCHANTMENTS, otherwise ENCHANTMENTS.
        // Do not use gameplay-level hooks, equipment applicability, or both components at once.
        for (Holder<Enchantment> enchantment : EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet()) {
            if (enchantment.is(target)) return true;
        }
        return false;
    }

    @Override
    public String getResearchKey(Player player, Object object) {
        return "!" + legacyName;
    }
}
