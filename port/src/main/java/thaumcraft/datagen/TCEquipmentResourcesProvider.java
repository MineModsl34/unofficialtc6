package thaumcraft.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Source-owned item models, including both actual grapple-gun geometries.
 * Native item tags are merged by EquipmentRegistries.itemTags into the ONE shared tag provider.
 */
public final class TCEquipmentResourcesProvider implements DataProvider {
    public static final String RESOURCE="assets/thaumcraft/porting/equipment_resources.json";
    private final PackOutput output;
    public TCEquipmentResourcesProvider(PackOutput output){this.output=output;}
    @Override public CompletableFuture<?> run(CachedOutput cache){
        try(var input=getClass().getClassLoader().getResourceAsStream(RESOURCE)){
            if(input==null)throw new IOException("Missing reviewed equipment model definitions");
            byte[] bytes=input.readNBytes(262145);if(bytes.length>262144)throw new IOException("Equipment definitions exceed bound");
            JsonObject manifest=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();validate(manifest);
            var rows=manifest.getAsJsonArray("files");var writes=new CompletableFuture<?>[rows.size()];
            for(int index=0;index<rows.size();index++){var row=rows.get(index).getAsJsonObject();writes[index]=DataProvider.saveStable(cache,row.getAsJsonObject("payload"),output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(row.get("path").getAsString()));}
            return CompletableFuture.allOf(writes);
        } catch(IOException|RuntimeException error){return CompletableFuture.failedFuture(error);}
    }
    public static void validate(JsonObject manifest){
        if(manifest.get("format").getAsInt()!=1)throw new IllegalArgumentException("Unknown equipment definitions format");
        var rows=manifest.getAsJsonArray("files");if(rows.size()>64)throw new IllegalArgumentException("Equipment definition count exceeds bound");var names=new HashSet<String>();
        for(var entry:rows){var row=entry.getAsJsonObject();String path=row.get("path").getAsString();
            if(!row.get("target").getAsString().equals("resource_pack") || !path.matches("thaumcraft/models/item/[a-z0-9_]+\\.json") || !names.add(path) || !row.get("payload").isJsonObject())throw new IllegalArgumentException("Invalid or duplicate reviewed equipment model");
        }
    }
    @Override public String getName(){return "TC6 source equipment item geometry";}
}
