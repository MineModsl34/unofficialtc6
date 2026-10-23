package thaumcraft.datagen;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;

/** Native source siphon model/state/item snapshots. Source textures were migrated without pixel edits. */
public final class TCFluxRiftAssetsProvider implements DataProvider {
    public static final String RESOURCE="assets/thaumcraft/porting/flux_rift_siphon_assets.json";
    private final PackOutput output;
    public TCFluxRiftAssetsProvider(PackOutput output){this.output=output;}
    @Override public CompletableFuture<?> run(CachedOutput cache){try(var input=getClass().getClassLoader().getResourceAsStream(RESOURCE)){
        if(input==null)throw new IOException("Missing flux rift/source siphon manifest");byte[] bytes=input.readNBytes(65537);if(bytes.length>65536)throw new IOException("Rift asset manifest bound");var manifest=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();validate(manifest);
        var writes=new ArrayList<CompletableFuture<?>>();for(var item:manifest.getAsJsonArray("entries")){var row=item.getAsJsonObject();writes.add(DataProvider.saveStable(cache,row.getAsJsonObject("payload"),output.getOutputFolder().resolve(row.get("path").getAsString())));}return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }catch(IOException|RuntimeException error){return CompletableFuture.failedFuture(error);}}
    public static void validate(JsonObject manifest){if(manifest.get("format").getAsInt()!=1||manifest.getAsJsonArray("entries").size()!=3||manifest.getAsJsonArray("sourceFiles").size()!=14)throw new IllegalArgumentException("Flux rift source manifest counts");var paths=new HashSet<String>();
        for(var item:manifest.getAsJsonArray("entries")){var row=item.getAsJsonObject();String path=row.get("path").getAsString();if(!path.matches("assets/thaumcraft/(blockstates|models/block|models/item)/void_siphon\\.json")||!paths.add(path)||!row.get("payload").isJsonObject())throw new IllegalArgumentException("Flux rift native path");}
        for(var item:manifest.getAsJsonArray("sourceFiles")){var row=item.getAsJsonObject();String path=row.get("path").getAsString();if(!path.startsWith("reference/tc6/src/")||path.contains("..")||!row.get("sha256").getAsString().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Flux rift provenance");}
    }
    @Override public String getName(){return "BETA26 flux rift/void siphon source assets";}
}
