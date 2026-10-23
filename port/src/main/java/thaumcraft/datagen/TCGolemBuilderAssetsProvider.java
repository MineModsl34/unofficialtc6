package thaumcraft.datagen;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;

/** Source-backed native press/proxy/item snapshots; all18 actual OBJ meshes are checked-in main assets. */
public final class TCGolemBuilderAssetsProvider implements DataProvider {
    public static final String RESOURCE = "assets/thaumcraft/porting/golem_builder_assets.json";
    private final PackOutput output;
    public TCGolemBuilderAssetsProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        try (var input = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Missing golem builder source manifest"); byte[] raw = input.readNBytes(131073); if (raw.length > 131072) throw new IOException("Golem source manifest bound");
            var manifest = JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject(); validate(manifest);
            var writes = new ArrayList<CompletableFuture<?>>(); for (var entry : manifest.getAsJsonArray("entries")) { var row = entry.getAsJsonObject(); writes.add(DataProvider.saveStable(cache,row.getAsJsonObject("payload"),output.getOutputFolder().resolve(row.get("path").getAsString()))); }
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        } catch (IOException | RuntimeException invalid) { return CompletableFuture.failedFuture(invalid); }
    }
    public static void validate(JsonObject manifest) {
        if (manifest.get("format").getAsInt() != 1 || manifest.getAsJsonArray("entries").size() != 17 || manifest.getAsJsonArray("models").size() != 18 || manifest.getAsJsonArray("sourceFiles").size() != 27) throw new IllegalArgumentException("Golem source manifest counts");
        Set<String> paths = new HashSet<>();
        for (var entry : manifest.getAsJsonArray("entries")) { var row = entry.getAsJsonObject(); String path = row.get("path").getAsString();
            if (!path.matches("assets/thaumcraft/(blockstates|models/block|models/item)/[a-z_]+\\.json") || !paths.add(path) || !row.get("payload").isJsonObject()) throw new IllegalArgumentException("Golem native resource path");
        }
        for (String array : List.of("sourceFiles","models")) for (var entry : manifest.getAsJsonArray(array)) { var row = entry.getAsJsonObject(); String path = row.get("path").getAsString();
            if (!path.startsWith("reference/tc6/src/") || path.contains("..") || !row.get("sha256").getAsString().matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Golem source provenance");
        }
    }
    @Override public String getName() { return "BETA26 genuine golem catalog, press and source OBJ bindings"; }
}
