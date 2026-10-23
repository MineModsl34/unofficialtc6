package thaumcraft.datagen;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;

/** Single reviewed source manifest for the construct item models and silk-API bridge tags. */
public final class TCConstructResourcesProvider implements DataProvider {
    public static final String RESOURCE="assets/thaumcraft/porting/construct_resources.json";
    private final PackOutput output;
    public TCConstructResourcesProvider(PackOutput output){this.output=output;}
    @Override public CompletableFuture<?> run(CachedOutput cache){try(var input=getClass().getClassLoader().getResourceAsStream(RESOURCE)){if(input==null)throw new IOException("Missing construct resources");byte[] bytes=input.readNBytes(65537);if(bytes.length>65536)throw new IOException("Construct resource bound");var manifest=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();validate(manifest);var rows=manifest.getAsJsonArray("files");var writes=new CompletableFuture<?>[rows.size()];for(int i=0;i<rows.size();i++){var row=rows.get(i).getAsJsonObject();var target=row.get("target").getAsString().equals("resource_pack")?PackOutput.Target.RESOURCE_PACK:PackOutput.Target.DATA_PACK;writes[i]=DataProvider.saveStable(cache,row.getAsJsonObject("payload"),output.getOutputFolder(target).resolve(row.get("path").getAsString()));}return CompletableFuture.allOf(writes);}catch(IOException|RuntimeException error){return CompletableFuture.failedFuture(error);}}
    public static void validate(JsonObject manifest){if(manifest.get("format").getAsInt()!=1)throw new IllegalArgumentException("Construct manifest format");var rows=manifest.getAsJsonArray("files");if(rows.size()!=5)throw new IllegalArgumentException("Construct manifest count");var names=new HashSet<String>();for(var entry:rows){var row=entry.getAsJsonObject();String target=row.get("target").getAsString(),path=row.get("path").getAsString();boolean allowed=target.equals("resource_pack")&&path.matches("thaumcraft/models/item/turret_(basic|advanced|bore)\\.json") || target.equals("data_pack")&&path.matches("thaumcraft/tags/block/bore_silk_(allowed|denied)\\.json");if(!allowed || !names.add(target+":"+path) || !row.get("payload").isJsonObject())throw new IllegalArgumentException("Invalid source construct resource");}}
    @Override public String getName(){return "TC6 construct placer models and legacy bore silk gate";}
}
