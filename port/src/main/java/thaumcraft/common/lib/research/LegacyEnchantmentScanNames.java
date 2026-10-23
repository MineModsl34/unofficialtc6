package thaumcraft.common.lib.research;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The thirty vanilla 1.12.2 getName() values, not translated text or modern description IDs.
 * References: Enchantment.registerEnchantments; subclass setName()/getName() overrides;
 * TC6 ConfigResearch.initScannables (Generic, enchantment handlers, then effect handlers).
 *
 * This list follows vanilla registration/insertion order (including the old numeric-ID gaps).
 * Patched Enchantment.REGISTRY.getKeys delegates through NamespacedWrapper to ForgeRegistry's
 * names HashBiMap; neither that path nor TC6 performs ResourceLocation.compareTo sorting.
 * It is not an enumeration of arbitrary addon registries and does not install any handlers.
 */
public final class LegacyEnchantmentScanNames {
    private LegacyEnchantmentScanNames() {}

    public record Descriptor(int originalId, ResourceLocation originalLocation,
                             ResourceKey<Enchantment> target, String legacyName) {
        public Descriptor {
            Objects.requireNonNull(originalLocation, "originalLocation");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(legacyName, "legacyName");
        }

        public String researchKey() { return "!" + legacyName; }

        public ScanEnchantment scanner() { return new ScanEnchantment(target, legacyName); }
    }

    public static final List<Descriptor> VANILLA = List.of(
        vanilla(0, "protection", "protect.all"),
        vanilla(1, "fire_protection", "protect.fire"),
        vanilla(2, "feather_falling", "protect.fall"),
        vanilla(3, "blast_protection", "protect.explosion"),
        vanilla(4, "projectile_protection", "protect.projectile"),
        vanilla(5, "respiration", "oxygen"),
        vanilla(6, "aqua_affinity", "waterWorker"),
        vanilla(7, "thorns", "thorns"),
        vanilla(8, "depth_strider", "waterWalker"),
        vanilla(9, "frost_walker", "frostWalker"),
        vanilla(10, "binding_curse", "binding_curse"),
        vanilla(16, "sharpness", "damage.all"),
        vanilla(17, "smite", "damage.undead"),
        vanilla(18, "bane_of_arthropods", "damage.arthropods"),
        vanilla(19, "knockback", "knockback"),
        vanilla(20, "fire_aspect", "fire"),
        vanilla(21, "looting", "lootBonus"),
        renamed(22, "sweeping", "sweeping_edge", "sweeping"),
        vanilla(32, "efficiency", "digging"),
        vanilla(33, "silk_touch", "untouching"),
        vanilla(34, "unbreaking", "durability"),
        vanilla(35, "fortune", "lootBonusDigger"),
        vanilla(48, "power", "arrowDamage"),
        vanilla(49, "punch", "arrowKnockback"),
        vanilla(50, "flame", "arrowFire"),
        vanilla(51, "infinity", "arrowInfinite"),
        vanilla(61, "luck_of_the_sea", "lootBonusFishing"),
        vanilla(62, "lure", "fishingSpeed"),
        vanilla(70, "mending", "mending"),
        vanilla(71, "vanishing_curse", "vanishing_curse")
    );

    private static Descriptor vanilla(int id, String path, String name) {
        return renamed(id, path, path, name);
    }

    private static Descriptor renamed(int id, String originalPath, String modernPath, String name) {
        return new Descriptor(id, ResourceLocation.withDefaultNamespace(originalPath),
            ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.withDefaultNamespace(modernPath)),
            "enchantment." + name);
    }
}
