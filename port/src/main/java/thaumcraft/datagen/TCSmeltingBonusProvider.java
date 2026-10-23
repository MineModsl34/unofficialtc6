package thaumcraft.datagen;

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

/** Bounded reviewed raw data. Does not instantiate absent chunk Items or implement a furnace. */
public final class TCSmeltingBonusProvider implements DataProvider {
    public static final String RESOURCE = "assets/thaumcraft/porting/smelting_bonus.json";
    private final PackOutput output;
    public TCSmeltingBonusProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        try (var input = TCSmeltingBonusProvider.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Missing reviewed source smelting bonus definitions");
            byte[] bytes = input.readNBytes(131073);
            if (bytes.length > 131072) throw new IOException("Smelting bonus source manifest too large");
            JsonObject manifest = JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            validate(manifest);
            var rows = manifest.getAsJsonArray("rules");
            CompletableFuture<?>[] writes = new CompletableFuture<?>[rows.size()];
            for (int index=0;index<rows.size();index++) {
                JsonObject row = rows.get(index).getAsJsonObject();
                ResourceLocation id = ResourceLocation.parse(row.get("id").getAsString());
                writes[index] = DataProvider.saveStable(cache,row.getAsJsonObject("payload"),output.getOutputFolder(PackOutput.Target.DATA_PACK)
                    .resolve(id.getNamespace()).resolve("smelting_bonus").resolve(id.getPath()+".json"));
            }
            return CompletableFuture.allOf(writes);
        } catch (IOException | RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }
    public static void validate(JsonObject manifest) {
        if (manifest.get("format").getAsInt() != 1 || !manifest.get("sourceSha256").getAsString().matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid source smelting bonus provenance");
        var rows = manifest.getAsJsonArray("rules");
        if (rows.isEmpty() || rows.size() > 256) throw new IllegalArgumentException("Invalid source bonus list size");
        Set<String> ids = new HashSet<>(); Set<Integer> orders = new HashSet<>();
        for (var entry : rows) {
            JsonObject row = entry.getAsJsonObject(), payload = row.getAsJsonObject("payload"), input = payload.getAsJsonObject("input");
            ResourceLocation id = ResourceLocation.parse(row.get("id").getAsString());
            int order = payload.get("source_order").getAsInt();
            if (!id.getNamespace().equals("thaumcraft") || !ids.add(id.toString()) || order < 0 || order > 4095 || !orders.add(order) || row.get("sourceLine").getAsInt() <= 0)
                throw new IllegalArgumentException("Invalid/duplicate smelting bonus source identity/order");
            float chance = payload.get("chance").getAsFloat();
            if (!Float.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Unbounded native bonus chance");
            Set<String> required = new HashSet<>(), guards = new HashSet<>();
            String outputId = payload.getAsJsonObject("output").get("id").getAsString(); ResourceLocation.parse(outputId);
            if (!outputId.startsWith("minecraft:")) required.add(outputId);
            String oreTag = null;
            if (input.has("items")) {
                var items = input.getAsJsonArray("items");
                if (items.isEmpty() || items.size() > 32 || input.has("legacy_ore") || input.has("tag")) throw new IllegalArgumentException("Invalid flattened source input family");
                Set<String> unique = new HashSet<>();
                for (var item : items) { String name = item.getAsString(); ResourceLocation.parse(name); if (!unique.add(name)) throw new IllegalArgumentException("Duplicate source input"); if (!name.startsWith("minecraft:")) required.add(name); }
            } else {
                if (!input.has("legacy_ore") || !input.has("tag") || input.get("legacy_ore").getAsString().length() > 256) throw new IllegalArgumentException("Missing bounded source ore binding");
                oreTag = input.get("tag").getAsString(); ResourceLocation.parse(oreTag);
            }
            boolean guardedOre = oreTag == null;
            var conditions = payload.getAsJsonArray("neoforge:conditions");
            if (conditions == null || conditions.isEmpty() || conditions.size() > 64) throw new IllegalArgumentException("Missing native bonus dependency gates");
            for (var condition : conditions) {
                JsonObject value = condition.getAsJsonObject();
                if (value.get("type").getAsString().equals("neoforge:item_exists")) guards.add(value.get("item").getAsString());
                else if (value.get("type").getAsString().equals("neoforge:not") && value.getAsJsonObject("value").get("type").getAsString().equals("neoforge:tag_empty"))
                    guardedOre |= value.getAsJsonObject("value").get("tag").getAsString().equals(oreTag);
                else throw new IllegalArgumentException("Unsupported smelting bonus dependency gate");
            }
            if (!guards.containsAll(required) || !guardedOre) throw new IllegalArgumentException("Future smelting input/output not guarded before codec decoding");
        }
    }
    @Override public String getName() { return "Source ordered infernal-smelting bonus definitions (not furnace gameplay)"; }
}
