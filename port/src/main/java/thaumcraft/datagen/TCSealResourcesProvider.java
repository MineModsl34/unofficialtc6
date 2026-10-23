package thaumcraft.datagen;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;
/** Original seventeen icon models, also exposing seal sprites to the native block/item atlas. */
public final class TCSealResourcesProvider implements DataProvider {
 public static final String RESOURCE="assets/thaumcraft/porting/seal_resources.json";private final PackOutput output;
 public TCSealResourcesProvider(PackOutput output){this.output=output;}
 public CompletableFuture<?> run(CachedOutput cache){try(var in=getClass().getClassLoader().getResourceAsStream(RESOURCE)){if(in==null)throw new IOException("Missing seal models");byte[] raw=in.readNBytes(65537);if(raw.length>65536)throw new IOException("Seal model manifest exceeds bound");var manifest=JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject();validate(manifest);var rows=manifest.getAsJsonArray("files");CompletableFuture<?>[] writes=new CompletableFuture<?>[rows.size()];for(int i=0;i<rows.size();i++){var row=rows.get(i).getAsJsonObject();writes[i]=DataProvider.saveStable(cache,row.getAsJsonObject("payload"),output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(row.get("path").getAsString()));}return CompletableFuture.allOf(writes);}catch(IOException|RuntimeException error){return CompletableFuture.failedFuture(error);}}
 public static void validate(JsonObject manifest){if(manifest.get("format").getAsInt()!=1||manifest.get("registered_seal_count").getAsInt()!=16)throw new IllegalArgumentException("Seal manifest source count");var files=manifest.getAsJsonArray("files");if(files.size()!=17)throw new IllegalArgumentException("Seal item count");var seen=new HashSet<String>();for(var element:files){var row=element.getAsJsonObject();String path=row.get("path").getAsString();if(!row.get("target").getAsString().equals("resource_pack")||!path.matches("thaumcraft/models/item/seal_[a-z_]+\\.json")||!seen.add(path)||!row.get("payload").isJsonObject())throw new IllegalArgumentException("Seal item model path");}}
 public String getName(){return "Source golem seal atlas/item models";}
}

