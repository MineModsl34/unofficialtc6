package thaumcraft.common.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable snapshot of TC6's world-generation blacklists, using registry keys instead of numeric IDs.
 * Entries have the form {@code namespace:path=mode}. The original IMC accepted any integer mode;
 * only the actual comparisons in ThaumcraftWorldGenerator and AuraHandler define its meaning.
 * A replacement snapshot can be published atomically while chunk-generation workers read this one.
 */
public final class WorldgenPolicy {
    public static final WorldgenPolicy EMPTY = new WorldgenPolicy(Map.of(), Map.of());

    private final Map<ResourceLocation, Integer> dimensions;
    private final Map<ResourceLocation, Integer> biomes;

    private WorldgenPolicy(Map<ResourceLocation, Integer> dimensions, Map<ResourceLocation, Integer> biomes) {
        this.dimensions = Map.copyOf(dimensions);
        this.biomes = Map.copyOf(biomes);
    }

    /** Last entry for a key wins, matching the original HashMap-based IMC registration. */
    public static WorldgenPolicy fromEntries(List<?> dimensionEntries, List<?> biomeEntries) {
        return new WorldgenPolicy(parseEntries(dimensionEntries), parseEntries(biomeEntries));
    }

    public WorldgenPolicy withDimension(ResourceLocation key, int mode) {
        Map<ResourceLocation, Integer> updated = new HashMap<>(dimensions);
        updated.put(Objects.requireNonNull(key), mode);
        return new WorldgenPolicy(updated, biomes);
    }

    public WorldgenPolicy withBiome(ResourceLocation key, int mode) {
        Map<ResourceLocation, Integer> updated = new HashMap<>(biomes);
        updated.put(Objects.requireNonNull(key), mode);
        return new WorldgenPolicy(dimensions, updated);
    }

    /** Explicit administrator entries override addon entries, including mode -1 to lift a restriction. */
    public WorldgenPolicy overriddenBy(WorldgenPolicy overrides) {
        Map<ResourceLocation, Integer> mergedDimensions = new HashMap<>(dimensions);
        Map<ResourceLocation, Integer> mergedBiomes = new HashMap<>(biomes);
        mergedDimensions.putAll(overrides.dimensions);
        mergedBiomes.putAll(overrides.biomes);
        return new WorldgenPolicy(mergedDimensions, mergedBiomes);
    }

    /** Element validator for the two ModConfigSpec lists; absent registries are intentionally allowed. */
    public static boolean isValidEntry(Object candidate) {
        return parseEntry(candidate) != null;
    }

    public int dimensionMode(ResourceLocation dimension) {
        return dimension == null ? -1 : dimensions.getOrDefault(dimension, -1);
    }

    public int biomeMode(ResourceLocation biome) {
        return biome == null ? -1 : biomes.getOrDefault(biome, -1);
    }

    public boolean allowsOres(ResourceLocation dimension, ResourceLocation biome) {
        return permitsOres(dimensionMode(dimension)) && permitsOres(biomeMode(biome));
    }

    public boolean allowsVegetation(ResourceLocation dimension, ResourceLocation biome) {
        return dimensionMode(dimension) == -1 && biomeMode(biome) == -1;
    }

    /**
     * TC6's actual generateAura path checked the biome alone. The unused generateNodes method had
     * different dimension checks, which must not be mistaken for the behavior of generated aura.
     */
    public boolean allowsAura(ResourceLocation biome) {
        return biomeMode(biome) == -1;
    }

    /** TC6 surface structures checked the dimension blacklist, not the biome blacklist. */
    public boolean allowsStructures(ResourceLocation dimension) {
        return dimensionMode(dimension) == -1;
    }

    private static boolean permitsOres(int mode) {
        return mode != 0 && mode != 2;
    }

    private static Map<ResourceLocation, Integer> parseEntries(List<?> entries) {
        Map<ResourceLocation, Integer> parsed = new HashMap<>();
        for (Object candidate : entries) {
            Entry entry = parseEntry(candidate);
            if (entry == null) {
                throw new IllegalArgumentException("Invalid Thaumcraft blacklist entry: " + candidate);
            }
            parsed.put(entry.key(), entry.mode());
        }
        return parsed;
    }

    private static Entry parseEntry(Object candidate) {
        if (!(candidate instanceof String text)) {
            return null;
        }
        int separator = text.indexOf('=');
        if (separator < 0 || separator != text.lastIndexOf('=')) {
            return null;
        }
        String keyText = text.substring(0, separator).trim();
        int namespaceSeparator = keyText.indexOf(':');
        if (namespaceSeparator <= 0 || namespaceSeparator == keyText.length() - 1
            || namespaceSeparator != keyText.lastIndexOf(':')) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(keyText);
        if (key == null) {
            return null;
        }
        try {
            return new Entry(key, Integer.parseInt(text.substring(separator + 1).trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record Entry(ResourceLocation key, int mode) {
    }
}
