package thaumcraft.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Source layered inventory models and empty BER block/loot snapshots for sixteen real banners. */
public final class TCBannerResourcesProvider implements DataProvider {
    public static final String RESOURCE="assets/thaumcraft/porting/banner_resources.json";
    private final PackOutput output;
    public TCBannerResourcesProvider(PackOutput output){this.output=output;}
    public static JsonObject read(){
        try(var input=TCBannerResourcesProvider.class.getClassLoader().getResourceAsStream(RESOURCE)){
            if(input==null)throw new IOException("Missing banner resources");var bytes=input.readNBytes(262145);
            if(bytes.length>262144)throw new IOException("Banner resource bound");
            var manifest=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();TCMob8AssetsProvider.validate(manifest);return manifest;
        }catch(IOException invalid){throw new IllegalArgumentException("Invalid banner resources",invalid);}
    }
    @Override public CompletableFuture<?> run(CachedOutput cache){
        var rows=read().getAsJsonArray("files");var writes=new CompletableFuture<?>[rows.size()];
        for(int n=0;n<rows.size();n++){var row=rows.get(n).getAsJsonObject();
            var target=row.get("target").getAsString().equals("resource_pack")?PackOutput.Target.RESOURCE_PACK:PackOutput.Target.DATA_PACK;
            writes[n]=DataProvider.saveStable(cache,row.getAsJsonObject("payload"),output.getOutputFolder(target).resolve(row.get("path").getAsString()));}
        return CompletableFuture.allOf(writes);
    }
    @Override public String getName(){return "TC6 source coloured banners";}
}
