package thaumcraft.common.lib.research;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Exact 27 vanilla 1.12 Potion.getName() values, not modern registry/translation-key guesses.
 * Source: MC1.12.2 Potion.registerPotions:424–450. Additional modern/addon effects keep their
 * supplied current description name with explicit sourceProven=false; no historical parity claim. */
public final class LegacyPotionScanNames {
    private static final Map<ResourceLocation, String> VANILLA = vanillaNames();

    private LegacyPotionScanNames() {}

    public record Name(String value, boolean sourceProven) {
        public Name { Objects.requireNonNull(value, "effect name"); }
        public String researchKey() { return "!" + value; }
    }

    public static Name resolve(ResourceLocation effectId, String modernDescriptionId) {
        String legacy = VANILLA.get(Objects.requireNonNull(effectId, "effect id"));
        return legacy == null ? new Name(modernDescriptionId, false) : new Name(legacy, true);
    }

    public static Map<ResourceLocation, String> vanillaNamesById() { return VANILLA; }

    private static Map<ResourceLocation, String> vanillaNames() {
        Map<ResourceLocation, String> result = new LinkedHashMap<>();
        add(result, "speed", "moveSpeed");
        add(result, "slowness", "moveSlowdown");
        add(result, "haste", "digSpeed");
        add(result, "mining_fatigue", "digSlowDown");
        add(result, "strength", "damageBoost");
        add(result, "instant_health", "heal");
        add(result, "instant_damage", "harm");
        add(result, "jump_boost", "jump");
        add(result, "nausea", "confusion");
        add(result, "regeneration", "regeneration");
        add(result, "resistance", "resistance");
        add(result, "fire_resistance", "fireResistance");
        add(result, "water_breathing", "waterBreathing");
        add(result, "invisibility", "invisibility");
        add(result, "blindness", "blindness");
        add(result, "night_vision", "nightVision");
        add(result, "hunger", "hunger");
        add(result, "weakness", "weakness");
        add(result, "poison", "poison");
        add(result, "wither", "wither");
        add(result, "health_boost", "healthBoost");
        add(result, "absorption", "absorption");
        add(result, "saturation", "saturation");
        add(result, "glowing", "glowing");
        add(result, "levitation", "levitation");
        add(result, "luck", "luck");
        add(result, "unluck", "unluck");
        return Collections.unmodifiableMap(result);
    }

    private static void add(Map<ResourceLocation, String> names, String id, String oldSuffix) {
        names.put(ResourceLocation.withDefaultNamespace(id), "effect." + oldSuffix);
    }
}
