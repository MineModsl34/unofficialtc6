package thaumcraft.datagen;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;
/** Source item geometry/texture manifest, separate from shared simple-item generator. */
public final class TCCasterAssetsProvider implements DataProvider{
    public static final String RESOURCE="assets/thaumcraft/porting/caster_assets.json";private final PackOutput output;
    public TCCasterAssetsProvider(PackOutput output){this.output=output;}
    public static JsonObject read(){try(var input=TCCasterAssetsProvider.class.getClassLoader().getResourceAsStream(RESOURCE)){if(input==null)throw new IllegalArgumentException("Missing caster asset manifest");byte[] data=input.readNBytes(65537);if(data.length>65536)throw new IllegalArgumentException("Caster assets bounds");var manifest=JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();validate(manifest);return manifest;}catch(java.io.IOException e){throw new IllegalArgumentException(e);}}
    public static void validate(JsonObject manifest){if(manifest.get("format").getAsInt()!=1||!manifest.get("source").getAsString().equals("reference/tc6/src/assets/thaumcraft"))throw new IllegalArgumentException("Caster asset provenance");var paths=new HashSet<String>();for(var entry:manifest.getAsJsonArray("entries")){var row=entry.getAsJsonObject();String path=row.get("path").getAsString();if(!path.matches("models/item/[a-z0-9_]+\\.json")||!paths.add(path)||!row.get("payload").isJsonObject()||row.getAsJsonObject("payload").has("forge_marker"))throw new IllegalArgumentException("Caster native asset path");}}
    @Override public CompletableFuture<?> run(CachedOutput cache){var futures=new ArrayList<CompletableFuture<?>>();for(var entry:read().getAsJsonArray("entries")){var row=entry.getAsJsonObject();futures.add(DataProvider.saveStable(cache,row.get("payload"),output.getOutputFolder().resolve("assets/thaumcraft/"+row.get("path").getAsString())));}return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));}
    @Override public String getName(){return "Thaumcraft source caster item assets";}
}
