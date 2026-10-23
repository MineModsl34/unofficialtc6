package thaumcraft.datagen;
import com.google.gson.*;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;
/** Exact source/raw and native GLSL snapshots. Does not start a client or compile shaders. */
public final class TCShaderResourcesProvider implements DataProvider {
    public static final String RESOURCE="assets/thaumcraft/porting/stage11_shader_resources.json";
    private final PackOutput output;
    public TCShaderResourcesProvider(PackOutput output){this.output=output;}
    public static JsonObject read(){try(var stream=TCShaderResourcesProvider.class.getClassLoader().getResourceAsStream(RESOURCE)){if(stream==null)throw new IOException("Missing source shader manifest");byte[] bytes=stream.readNBytes(131073);if(bytes.length>131072)throw new IOException("Source shader manifest bound");var manifest=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();validate(manifest);return manifest;}catch(IOException error){throw new IllegalArgumentException(error);}}
    public static void validate(JsonObject manifest){
        if(manifest.get("format").getAsInt()!=1||manifest.get("rawFileCount").getAsInt()!=5||manifest.get("sourceProgramCount").getAsInt()!=2)throw new IllegalArgumentException("Source shader inventory");Set<String> paths=new HashSet<>();
        if(manifest.getAsJsonArray("entries").size()!=15)throw new IllegalArgumentException("Shader resource count");
        for(var value:manifest.getAsJsonArray("entries")){var row=value.getAsJsonObject();String path=row.get("path").getAsString();if(!path.matches("assets/thaumcraft/(?:shader/(?:ender|sketch|test)\\.(?:vert|frag)|shaders/core/[a-z0-9_]+\\.(?:json|vsh|fsh))")||!paths.add(path)||!row.get("payload").isJsonPrimitive()||!row.get("sha256").getAsString().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Shader output path");}
        for(var value:manifest.getAsJsonArray("sourceFiles")){var row=value.getAsJsonObject();String path=row.get("path").getAsString();if(!path.startsWith("reference/tc6/src/")||path.contains("..")||!row.get("sha256").getAsString().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Shader source provenance");}
    }
    @Override public CompletableFuture<?> run(CachedOutput cache){try{for(var value:read().getAsJsonArray("entries")){var row=value.getAsJsonObject();byte[] bytes=row.get("payload").getAsString().getBytes(StandardCharsets.UTF_8);cache.writeIfNeeded(output.getOutputFolder().resolve(row.get("path").getAsString()),bytes,Hashing.sha1().hashBytes(bytes));}return CompletableFuture.completedFuture(null);}catch(IOException|RuntimeException error){return CompletableFuture.failedFuture(error);}}
    @Override public String getName(){return "BETA26 literal five raw shader files and native render programs";}
}
