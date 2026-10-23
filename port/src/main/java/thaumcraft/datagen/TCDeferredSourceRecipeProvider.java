package thaumcraft.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

/** Source-derived conditional native data, not ItemStack(null), fake registry objects or display recipes.
 * Conditions are evaluated before the native recipe codec touches a future item id.
 * Future item/device gameplay belongs to its own phase; these payloads alone do not implement it.
 */
public final class TCDeferredSourceRecipeProvider implements DataProvider {
    public static final String RESOURCE = "assets/thaumcraft/porting/deferred_recipes.json";
    private final PackOutput output;
    public TCDeferredSourceRecipeProvider(PackOutput output) { this.output = output; }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        try (var input = TCDeferredSourceRecipeProvider.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Missing reviewed source deferred recipe definitions");
            byte[] bytes = input.readNBytes(1048577);
            if (bytes.length > 1048576) throw new IOException("Deferred source recipe manifest too large");
            JsonObject manifest = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            validate(manifest);
            JsonArray recipes = manifest.getAsJsonArray("recipes");
            CompletableFuture<?>[] writes = new CompletableFuture<?>[recipes.size()];
            for (int index = 0; index < recipes.size(); index++) {
                JsonObject definition = recipes.get(index).getAsJsonObject();
                ResourceLocation id = ResourceLocation.parse(definition.get("id").getAsString());
                writes[index] = DataProvider.saveStable(cache, definition.getAsJsonObject("payload"),
                    output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(id.getNamespace()).resolve("recipe").resolve(id.getPath() + ".json"));
            }
            return CompletableFuture.allOf(writes);
        } catch (IOException | RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }

    public static void validate(JsonObject manifest) {
        if (manifest.get("format").getAsInt() != 1 || !manifest.get("sourceSha256").getAsString().matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid source recipe manifest provenance");
        JsonArray recipes = manifest.getAsJsonArray("recipes");
        if (recipes.size() > 512) throw new IllegalArgumentException("Too many deferred source recipes");
        Set<String> ids = new HashSet<>();
        Set<String> allowedTypes = Set.of("minecraft:crafting_shaped", "minecraft:crafting_shapeless",
            "thaumcraft:arcane_shaped", "thaumcraft:arcane_shapeless", "thaumcraft:crucible", "thaumcraft:infusion");
        for (var row : recipes) {
            JsonObject definition = row.getAsJsonObject();
            String name = definition.get("id").getAsString();
            ResourceLocation id = ResourceLocation.parse(name);
            if (!id.getNamespace().equals("thaumcraft") || !ids.add(name) || definition.get("sourceLine").getAsInt() <= 0)
                throw new IllegalArgumentException("Invalid/duplicate deferred recipe identity");
            JsonObject payload = definition.getAsJsonObject("payload");
            if (!allowedTypes.contains(payload.get("type").getAsString())) throw new IllegalArgumentException("Unsupported static source serializer");
            JsonArray conditions = payload.getAsJsonArray("neoforge:conditions");
            if (conditions == null || conditions.isEmpty() || conditions.size() > 64)
                throw new IllegalArgumentException("A deferred source recipe must have bounded real dependency conditions");
            Set<String> requiredItems = new HashSet<>(), guardedItems = new HashSet<>();
            Set<String> requiredTags = new HashSet<>(), guardedTags = new HashSet<>();
            dependencies(payload, requiredItems, requiredTags, 0);
            for (JsonElement entry : conditions) {
                JsonObject condition = entry.getAsJsonObject();
                String type = condition.get("type").getAsString();
                if (type.equals("neoforge:item_exists")) {
                    String item = condition.get("item").getAsString();
                    ResourceLocation.parse(item);
                    guardedItems.add(item);
                } else if (type.equals("neoforge:not")) {
                    JsonObject value = condition.getAsJsonObject("value");
                    if (!value.get("type").getAsString().equals("neoforge:tag_empty"))
                        throw new IllegalArgumentException("Deferred tag guard must be !tag_empty");
                    String tag = value.get("tag").getAsString();
                    ResourceLocation.parse(tag);
                    guardedTags.add(tag);
                } else throw new IllegalArgumentException("Unsupported deferred source condition");
            }
            if (!guardedItems.containsAll(requiredItems) || !guardedTags.containsAll(requiredTags))
                throw new IllegalArgumentException("Future item/tag reference is not guarded before native decoding");
        }
    }

    private static void dependencies(JsonElement element, Set<String> items, Set<String> tags, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Deferred native payload nesting too deep");
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) dependencies(child, items, tags, depth + 1);
        } else if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("neoforge:conditions")) continue;
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    String text = value.getAsString();
                    if ((entry.getKey().equals("id") || entry.getKey().equals("item")) && !text.startsWith("minecraft:")) items.add(text);
                    if (entry.getKey().equals("tag") && text.contains(":")) tags.add(text);
                }
                dependencies(value, items, tags, depth + 1);
            }
        }
    }

    @Override public String getName() { return "Source native recipes gated on genuine future content (not gameplay placeholders)"; }
}
