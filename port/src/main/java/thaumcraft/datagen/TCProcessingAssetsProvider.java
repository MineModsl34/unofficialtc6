package thaumcraft.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Reproducible native blockstates/items for the source machines without a client/registry boot. */
public final class TCProcessingAssetsProvider implements DataProvider {
    public static final String RESOURCE = "assets/thaumcraft/porting/processing_assets.json";
    private final PackOutput output;
    public TCProcessingAssetsProvider(PackOutput output) { this.output = output; }
    public static JsonObject read() {
        try (var input = TCProcessingAssetsProvider.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalArgumentException("Missing source processing assets");
            byte[] bytes = input.readNBytes(131073); if (bytes.length > 131072) throw new IllegalArgumentException("Processing assets byte bound");
            JsonObject manifest = JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject(); validate(manifest); return manifest;
        } catch (IOException invalid) { throw new IllegalArgumentException("Could not read processing source assets",invalid); }
    }
    public static void validate(JsonObject manifest) {
        if (manifest.get("format").getAsInt() != 1 || !manifest.get("source").getAsString().equals("reference/tc6/src/assets/thaumcraft")) throw new IllegalArgumentException("Processing provenance");
        var entries = manifest.getAsJsonArray("entries"); if (entries.isEmpty() || entries.size() > 64) throw new IllegalArgumentException("Processing assets count bound");
        HashSet<String> paths = new HashSet<>();
        for (var value : entries) {
            JsonObject row = value.getAsJsonObject(); String path = row.get("path").getAsString();
            if (!path.matches("(?:blockstates|models/(?:block|item))/[a-z0-9_]+\\.json") || !paths.add(path)
                || !row.get("payload").isJsonObject() || row.get("payload").toString().length() > 16384) throw new IllegalArgumentException("Invalid native processing asset path/payload");
        }
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> saves = new ArrayList<>();
        for (var value : read().getAsJsonArray("entries")) {
            JsonObject row = value.getAsJsonObject(); saves.add(DataProvider.saveStable(cache,row.get("payload"),output.getOutputFolder().resolve("assets/thaumcraft/" + row.get("path").getAsString())));
        }
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "Thaumcraft source processing machine assets"; }
}
