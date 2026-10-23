package thaumcraft.datagen;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;

/** BETA26 taint geometry, random weights, axis/facing rotations, and native item parents. */
public final class TCMob8BossAssetsProvider implements DataProvider {
    public static final String RESOURCE="assets/thaumcraft/porting/mob8_boss_assets.json";
    private final PackOutput output;
    public TCMob8BossAssetsProvider(PackOutput output){this.output=output;}
    public static JsonObject read(){try(var stream=TCMob8BossAssetsProvider.class.getClassLoader().getResourceAsStream(RESOURCE)){
        if(stream==null)throw new IllegalArgumentException("Missing stage8 boss assets");byte[] bytes=stream.readNBytes(262145);if(bytes.length>262144)throw new IllegalArgumentException("Boss asset manifest bound");
        JsonObject manifest=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();validate(manifest);return manifest;
    }catch(java.io.IOException error){throw new IllegalArgumentException(error);}}
    public static void validate(JsonObject manifest){
        if(manifest.get("format").getAsInt()!=1||!manifest.get("source").getAsString().equals("reference/tc6/src/assets/thaumcraft"))throw new IllegalArgumentException("Boss asset provenance");
        Set<String> paths=new HashSet<>();for(JsonElement entry:manifest.getAsJsonArray("entries")){JsonObject row=entry.getAsJsonObject();String path=row.get("path").getAsString();
            if(!path.matches("(?:blockstates|models/block|models/item)/[a-z0-9_]+\\.json")||!paths.add(path)||!row.get("payload").isJsonObject())throw new IllegalArgumentException("Boss native asset path");
            String payload=row.get("payload").toString();if(payload.contains("forge_marker")||payload.contains("thaumcraft:blocks/"))throw new IllegalArgumentException("Legacy boss asset syntax");}
    }
    @Override public CompletableFuture<?> run(CachedOutput cache){List<CompletableFuture<?>> futures=new ArrayList<>();for(JsonElement entry:read().getAsJsonArray("entries")){JsonObject row=entry.getAsJsonObject();futures.add(DataProvider.saveStable(cache,row.get("payload"),output.getOutputFolder().resolve("assets/thaumcraft/"+row.get("path").getAsString())));}return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));}
    @Override public String getName(){return "Thaumcraft BETA26 stage8 boss and taint block assets";}
}
