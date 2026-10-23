package thaumcraft.datagen;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;
/** Reviewed source-backed snapshots; neither initializes entities nor runs an old-code oracle. */
public final class TCNaturalMobAssetsProvider implements DataProvider {
    public static final String RESOURCE="assets/thaumcraft/porting/natural_mob_assets.json";
    private final PackOutput output;
    public TCNaturalMobAssetsProvider(PackOutput output){this.output=output;}
    @Override public CompletableFuture<?> run(CachedOutput cache){try(var input=getClass().getClassLoader().getResourceAsStream(RESOURCE)){if(input==null)throw new IOException("Missing natural mob source manifest");byte[] data=input.readNBytes(65537);if(data.length>65536)throw new IOException("Natural mob manifest too large");var manifest=JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();validate(manifest);List<CompletableFuture<?>> writes=new ArrayList<>();for(var entry:manifest.getAsJsonArray("entries")){var row=entry.getAsJsonObject();writes.add(DataProvider.saveStable(cache,row.getAsJsonObject("payload"),output.getOutputFolder().resolve(row.get("path").getAsString())));}return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));}catch(IOException|RuntimeException error){return CompletableFuture.failedFuture(error);}}
    public static void validate(JsonObject manifest){if(manifest.get("format").getAsInt()!=1||manifest.getAsJsonArray("entities").size()!=7||manifest.getAsJsonArray("entries").size()!=8||manifest.getAsJsonArray("sourceFiles").size()!=13)throw new IllegalArgumentException("Natural source manifest count");Set<String> paths=new HashSet<>();for(var entry:manifest.getAsJsonArray("entries")){var row=entry.getAsJsonObject();String path=row.get("path").getAsString();if(!(path.matches("assets/thaumcraft/models/item/[a-z_]+_spawn_egg\\.json")||path.equals("data/thaumcraft/loot_table/entities/pech.json"))||!paths.add(path)||!row.get("payload").isJsonObject())throw new IllegalArgumentException("Invalid natural resource path");}for(var entry:manifest.getAsJsonArray("sourceFiles")){var row=entry.getAsJsonObject();if(!row.get("sha256").getAsString().matches("[a-f0-9]{64}")||row.get("path").getAsString().contains(".."))throw new IllegalArgumentException("Invalid source provenance");}}
    @Override public String getName(){return "BETA26 natural mob eggs and genuine Pech loot";}
}
