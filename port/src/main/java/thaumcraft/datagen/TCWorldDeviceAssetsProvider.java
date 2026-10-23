package thaumcraft.datagen;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;

/** Source geometry remains in main; reviewed native state/item/texture overrides regenerate from this manifest. */
public final class TCWorldDeviceAssetsProvider implements DataProvider {
    public static final String RESOURCE = "assets/thaumcraft/porting/world_device_assets.json";
    private final PackOutput output;
    public TCWorldDeviceAssetsProvider(PackOutput output) { this.output = output; }
    public static JsonObject read() { try (var input = TCWorldDeviceAssetsProvider.class.getClassLoader().getResourceAsStream(RESOURCE)) { if (input == null) throw new IllegalArgumentException("Missing world device assets"); byte[] bytes = input.readNBytes(262145); if (bytes.length > 262144) throw new IllegalArgumentException("World device assets byte bound"); JsonObject manifest = JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject(); validate(manifest); return manifest; } catch (IOException error) { throw new IllegalArgumentException("Could not read world device assets",error); } }
    public static void validate(JsonObject manifest) { if (manifest.get("format").getAsInt() != 1 || !manifest.get("source").getAsString().equals("reference/tc6/src/assets/thaumcraft")) throw new IllegalArgumentException("World device asset provenance"); var entries = manifest.getAsJsonArray("entries"); if (entries.isEmpty() || entries.size() > 160) throw new IllegalArgumentException("World device asset count bound"); Set<String> paths = new HashSet<>(); for (var entry : entries) { JsonObject row = entry.getAsJsonObject(); String path = row.get("path").getAsString(); if (!path.matches("(?:blockstates|models/(?:block|item))/[a-z0-9_]+\\.json") || !paths.add(path) || !row.get("payload").isJsonObject() || row.get("payload").toString().length() > 16384 || row.getAsJsonObject("payload").has("forge_marker")) throw new IllegalArgumentException("Invalid native world device asset"); } }
    @Override public CompletableFuture<?> run(CachedOutput cache) { List<CompletableFuture<?>> writes = new ArrayList<>(); for (var entry : read().getAsJsonArray("entries")) { JsonObject row = entry.getAsJsonObject(); writes.add(DataProvider.saveStable(cache,row.get("payload"),output.getOutputFolder().resolve("assets/thaumcraft/" + row.get("path").getAsString()))); } return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)); }
    @Override public String getName() { return "Thaumcraft source world device assets"; }
}
