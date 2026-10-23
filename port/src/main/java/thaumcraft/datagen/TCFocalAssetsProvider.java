package thaumcraft.datagen;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;

/** Reproducible source model binding and native loot, runnable without a client. */
public final class TCFocalAssetsProvider implements DataProvider {
    public static final String RESOURCE = "assets/thaumcraft/porting/focal_assets.json";
    private final PackOutput output;
    public TCFocalAssetsProvider(PackOutput output) { this.output = output; }
    public static JsonObject read() {
        try (var input = TCFocalAssetsProvider.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalArgumentException("Missing focal assets"); byte[] bytes = input.readNBytes(65537); if (bytes.length > 65536) throw new IllegalArgumentException("Focal asset bytes");
            JsonObject manifest = JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject(); validate(manifest); return manifest;
        } catch (IOException failure) { throw new IllegalArgumentException("Focal assets",failure); }
    }
    public static void validate(JsonObject manifest) {
        if (manifest.get("format").getAsInt() != 1 || !manifest.get("source").getAsString().equals("reference/tc6/src/thaumcraft/common/blocks/crafting/BlockFocalManipulator.java")) throw new IllegalArgumentException("Focal provenance");
        Set<String> paths = new HashSet<>(); var entries = manifest.getAsJsonArray("entries"); if (entries.size() != 4) throw new IllegalArgumentException("Focal assets count");
        for (var value : entries) { var row = value.getAsJsonObject(); String path = row.get("path").getAsString();
            if (!path.matches("(?:assets/thaumcraft/(?:models/(?:block|item)|blockstates)|data/thaumcraft/loot_table/blocks)/wand_workbench\\.json") || !paths.add(path) || !row.get("payload").isJsonObject()) throw new IllegalArgumentException("Focal asset path");
        }
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) { List<CompletableFuture<?>> tasks = new ArrayList<>(); for (var value : read().getAsJsonArray("entries")) { var row = value.getAsJsonObject(); tasks.add(DataProvider.saveStable(cache,row.get("payload"),output.getOutputFolder().resolve(row.get("path").getAsString()))); } return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)); }
    @Override public String getName() { return "Thaumcraft source Focal Manipulator assets"; }
}
