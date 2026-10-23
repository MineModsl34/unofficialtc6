package thaumcraft.common.lib.research;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Declarative TC6 recipe DISPLAY data. This is explicitly not a craftable recipe registry.
 * It preserves source recipe kinds, geometry, groups, research gates and component amounts
 * without inventing later-stage crafting engines or granting research. Resource loading is
 * supplied by the client. The common parser is bounded and has no client class dependency.
 */
public final class ResearchRecipeDisplayCatalog {
    public static final ResourceLocation RESOURCE = ResourceLocation.parse("thaumcraft:research_recipe_display/catalog.json");
    public static final int MAX_BYTES = 4 * 1024 * 1024, MAX_RECIPES = 4096;
    public enum Kind { CRAFTING, ARCANE, CRUCIBLE, INFUSION, ENCHANTMENT, BLUEPRINT, MISC, GROUP }
    public record BlueprintCell(int x, int y, int z, String item) {}
    public record Display(ResourceLocation id, Kind kind, String research, String output,
                          int width, int height, List<List<String>> ingredients,
                          Map<String, Integer> aspects, int vis, int instability,
                          List<ResourceLocation> groupMembers, List<BlueprintCell> blueprint,
                          String sourceId, List<String> sourceGroupMembers) {
        public Display {
            Objects.requireNonNull(id); Objects.requireNonNull(kind);
            research = research == null ? "" : research;
            output = output == null ? "" : output;
            ingredients = ingredients.stream().map(List::copyOf).toList();
            aspects = Collections.unmodifiableMap(new LinkedHashMap<>(aspects));
            groupMembers = List.copyOf(groupMembers); blueprint = List.copyOf(blueprint);
            sourceId = Objects.requireNonNull(sourceId); sourceGroupMembers = List.copyOf(sourceGroupMembers);
        }
        public Display(ResourceLocation id, Kind kind, String research, String output, int width, int height,
                       List<List<String>> ingredients, Map<String, Integer> aspects, int vis, int instability,
                       List<ResourceLocation> groupMembers, List<BlueprintCell> blueprint) {
            this(id, kind, research, output, width, height, ingredients, aspects, vis, instability,
                groupMembers, blueprint, id.toString(), groupMembers.stream().map(ResourceLocation::toString).toList());
        }
    }
    private final Map<ResourceLocation, Display> recipes;
    private final Map<String, ResourceLocation> sourceIds;
    public ResearchRecipeDisplayCatalog(Map<ResourceLocation, Display> recipes) {
        this.recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
        Map<String, ResourceLocation> sources = new LinkedHashMap<>();
        for (var display : recipes.values()) if (sources.putIfAbsent(display.sourceId(), display.id()) != null)
            throw new IllegalArgumentException("Duplicate raw recipe display: " + display.sourceId());
        sourceIds = Collections.unmodifiableMap(sources);
    }
    public static ResearchRecipeDisplayCatalog empty() { return new ResearchRecipeDisplayCatalog(Map.of()); }
    public Display find(ResourceLocation key) { return recipes.get(key); }
    /** Exact raw group identity is distinct from a qualified native recipe. No group may
     * overwrite minecraft:wooden_door merely because ResourceLocation supplies that default. */
    public Display findSource(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 4096) return null;
        ResourceLocation exact = sourceIds.get(raw);
        return exact == null ? find(ResourceLocation.tryParse(raw)) : find(exact);
    }
    /** Source GUI priority: catalog/fake/vanilla leaf before group. The reserved group ID is
     * DISPLAY identity only, never a native RecipeManager key or recipe registration. */
    public ResourceLocation bookKey(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 4096) return null;
        ResourceLocation nativeKey = ResourceLocation.tryParse(raw);
        Display nativeDisplay = find(nativeKey);
        if (nativeDisplay != null && nativeDisplay.kind() != Kind.GROUP) return nativeKey;
        Display source = findSource(raw);
        if (source == null) {
            // Two dangling BETA26 book handles and two early native-port handles refer
            // to recipes/groups that already exist. Repair presentation without rewriting
            // source research documents or registering additional executable recipes.
            String alias = switch (raw) {
                case "thaumcraft:nitorcolor" -> "thaumcraft:nitorgroup";
                case "thaumcraft:jarlabelessence" -> "thaumcraft:jarlabels";
                case "thaumcraft:arcane_stone" -> "thaumcraft:stonearcane";
                case "thaumcraft:arcane_brick" -> "thaumcraft:brickarcane";
                default -> null;
            };
            if (alias != null) source = findSource(alias);
        }
        return source == null ? nativeKey : source.id();
    }
    public Map<ResourceLocation, Display> all() { return recipes; }

    public static ResearchRecipeDisplayCatalog parse(String json) {
        Objects.requireNonNull(json);
        if (json.length() > MAX_BYTES) throw new IllegalArgumentException("Recipe display resource exceeds budget");
        JsonArray array = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("recipes");
        if (array == null || array.size() > MAX_RECIPES) throw new IllegalArgumentException("Invalid recipe display list");
        // Resolve group references in a complete first pass, including forward/nested raw
        // unqualified group references. Keep source member strings separately, untouched.
        Map<String, ResourceLocation> sourceIds = new LinkedHashMap<>();
        for (JsonElement value : array) {
            var object = value.getAsJsonObject(); String raw = string(object, "id", "");
            Kind kind = Kind.valueOf(string(object, "kind", "MISC"));
            ResourceLocation identity = kind == Kind.GROUP && raw.indexOf(':') < 0
                ? ResourceLocation.fromNamespaceAndPath("thaumcraft", "source_group/" + id(raw).getPath()) : id(raw);
            if (sourceIds.putIfAbsent(raw, identity) != null) throw new IllegalArgumentException("Duplicate raw recipe display: " + raw);
        }
        Map<ResourceLocation, Display> recipes = new LinkedHashMap<>();
        for (JsonElement value : array) {
            JsonObject object = value.getAsJsonObject();
            String sourceId = string(object, "id", "");
            ResourceLocation id = sourceIds.get(sourceId);
            Kind kind = Kind.valueOf(string(object, "kind", "MISC"));
            int width = number(object, "width", 3), height = number(object, "height", 3);
            if (width < 0 || width > 16 || height < 0 || height > 16) throw new IllegalArgumentException("Unsafe recipe dimensions");
            List<List<String>> ingredients = new ArrayList<>();
            if (object.has("ingredients")) {
                JsonArray slots = object.getAsJsonArray("ingredients");
                if (slots.size() > 256) throw new IllegalArgumentException("Too many recipe slots");
                for (JsonElement slot : slots) {
                    List<String> alternatives = new ArrayList<>();
                    JsonArray options = slot.getAsJsonArray();
                    if (options.size() > 256) throw new IllegalArgumentException("Too many ingredient alternatives");
                    for (JsonElement option : options) alternatives.add(bounded(option.getAsString()));
                    ingredients.add(alternatives);
                }
            }
            Map<String, Integer> aspects = new LinkedHashMap<>();
            if (object.has("aspects")) {
                JsonObject tags = object.getAsJsonObject("aspects");
                if (tags.size() > 256) throw new IllegalArgumentException("Too many aspect costs");
                for (var tag : tags.entrySet()) {
                    int amount = tag.getValue().getAsInt();
                    if (amount < 0) throw new IllegalArgumentException("Negative aspect amount");
                    aspects.put(bounded(tag.getKey()), amount);
                }
            }
            List<ResourceLocation> members = new ArrayList<>();
            List<String> sourceMembers = new ArrayList<>();
            if (object.has("groupMembers")) {
                var group = object.getAsJsonArray("groupMembers");
                if (group.size() > MAX_RECIPES) throw new IllegalArgumentException("Too many group members");
                for (JsonElement member : group) {
                    String raw = bounded(member.getAsString()); sourceMembers.add(raw);
                    ResourceLocation exact = sourceIds.get(raw); members.add(exact == null ? id(raw) : exact);
                }
            }
            List<BlueprintCell> blueprint = new ArrayList<>();
            if (object.has("blueprint")) {
                var cells = object.getAsJsonArray("blueprint");
                if (cells.size() > 4096) throw new IllegalArgumentException("Too many blueprint cells");
                for (JsonElement cellValue : cells) {
                    var cell = cellValue.getAsJsonObject();
                    int x = number(cell, "x", 0), y = number(cell, "y", 0), z = number(cell, "z", 0);
                    if (Math.abs((long) x) > 32 || Math.abs((long) y) > 32 || Math.abs((long) z) > 32)
                        throw new IllegalArgumentException("Unsafe blueprint coordinates");
                    blueprint.add(new BlueprintCell(x, y, z, string(cell, "item", "")));
                }
            }
            Display display = new Display(id, kind, string(object, "research", ""), string(object, "output", ""),
                width, height, ingredients, aspects, number(object, "vis", 0), number(object, "instability", 0), members, blueprint, sourceId, sourceMembers);
            if (recipes.putIfAbsent(id, display) != null) throw new IllegalArgumentException("Duplicate recipe display: " + id);
        }
        return new ResearchRecipeDisplayCatalog(recipes);
    }

    private static ResourceLocation id(String raw) {
        if (raw.isBlank()) throw new IllegalArgumentException("Empty recipe display ID");
        ResourceLocation id = ResourceLocation.tryParse(bounded(raw));
        if (id == null) throw new IllegalArgumentException("Malformed recipe display ID");
        return id;
    }
    private static String bounded(String text) {
        if (text.length() > 4096) throw new IllegalArgumentException("Recipe display field exceeds budget");
        return text;
    }
    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) ? bounded(object.get(key).getAsString()) : fallback;
    }
    private static int number(JsonObject object, String key, int fallback) { return object.has(key) ? object.get(key).getAsInt() : fallback; }
}
