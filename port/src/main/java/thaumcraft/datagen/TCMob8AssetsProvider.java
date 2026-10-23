package thaumcraft.datagen;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;

/** Reviewed stage8 source snapshots; no world/JVM oracle is required to reproduce these definitions. */
public final class TCMob8AssetsProvider implements DataProvider {
    public static final String RESOURCE="assets/thaumcraft/porting/mob8_resources.json";
    private final PackOutput output;
    public TCMob8AssetsProvider(PackOutput output){this.output=output;}
    @Override public CompletableFuture<?> run(CachedOutput cache){
        try(var input=getClass().getClassLoader().getResourceAsStream(RESOURCE)){
            if(input==null)throw new IOException("Missing stage8 definitions");byte[] bytes=input.readNBytes(262145);if(bytes.length>262144)throw new IOException("Stage8 manifest bound exceeded");
            var manifest=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();validate(manifest);
            var rows=manifest.getAsJsonArray("files");var writes=new CompletableFuture<?>[rows.size()];
            for(int i=0;i<rows.size();i++){var row=rows.get(i).getAsJsonObject();var target=row.get("target").getAsString().equals("resource_pack")?PackOutput.Target.RESOURCE_PACK:PackOutput.Target.DATA_PACK;
                writes[i]=DataProvider.saveStable(cache,row.getAsJsonObject("payload"),output.getOutputFolder(target).resolve(row.get("path").getAsString()));}
            return CompletableFuture.allOf(writes);
        }catch(IOException|RuntimeException error){return CompletableFuture.failedFuture(error);}
    }
    public static void validate(JsonObject manifest){
        if(manifest.get("format").getAsInt()!=1)throw new IllegalArgumentException("Stage8 format");var rows=manifest.getAsJsonArray("files");if(rows.size()>128)throw new IllegalArgumentException("Stage8 count bound");var seen=new HashSet<String>();
        for(var entry:rows){var row=entry.getAsJsonObject();String path=row.get("path").getAsString(),target=row.get("target").getAsString();
            if(!java.util.Set.of("resource_pack","data_pack").contains(target)||!path.matches("thaumcraft/[a-z0-9_/]+\\.json")||!seen.add(target+":"+path)||!row.get("payload").isJsonObject())throw new IllegalArgumentException("Invalid stage8 path/duplicate/payload");}
    }
    @Override public String getName(){return "TC6 stage8 source mobs/common dependencies";}
}
