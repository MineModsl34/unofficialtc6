package thaumcraft.datagen;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;
/** Reviewed source item layers and native Curios slots/tags; canonical source textures are retained in main. */
public final class TCWearableAssetsProvider implements DataProvider {
    public static final String RESOURCE="assets/thaumcraft/porting/wearable_assets.json";
    private final PackOutput output;
    public TCWearableAssetsProvider(PackOutput output){this.output=output;}
    public static JsonObject read(){try(var stream=TCWearableAssetsProvider.class.getClassLoader().getResourceAsStream(RESOURCE)){if(stream==null)throw new IllegalArgumentException("Missing wearable assets");byte[] bytes=stream.readNBytes(131073);if(bytes.length>131072)throw new IllegalArgumentException("Wearable asset byte bound");var json=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();validate(json);return json;}catch(IOException error){throw new IllegalArgumentException("Reading wearable assets",error);}}
    public static void validate(JsonObject manifest){if(manifest.get("format").getAsInt()!=1||!manifest.get("source").getAsString().equals("reference/tc6/src"))throw new IllegalArgumentException("Wearable provenance");var entries=manifest.getAsJsonArray("entries");if(entries.isEmpty()||entries.size()>64)throw new IllegalArgumentException("Wearable asset count");Set<String> paths=new HashSet<>();for(var entry:entries){var row=entry.getAsJsonObject();String path=row.get("path").getAsString();if(!path.matches("(?:assets/thaumcraft/models/item|data/thaumcraft/curios/(?:slots|entities)|data/curios/tags/item)/[a-z0-9_]+\\.json")||!paths.add(path)||!row.get("payload").isJsonObject()||row.get("payload").toString().length()>16384||row.get("payload").toString().contains("thaumcraft:items/"))throw new IllegalArgumentException("Invalid native wearable asset");}}
    @Override public CompletableFuture<?> run(CachedOutput cache){List<CompletableFuture<?>> writes=new ArrayList<>();for(var entry:read().getAsJsonArray("entries")){var row=entry.getAsJsonObject();writes.add(DataProvider.saveStable(cache,row.get("payload"),output.getOutputFolder().resolve(row.get("path").getAsString())));}return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));}
    @Override public String getName(){return "Thaumcraft source armor and Curios assets";}
}
