package thaumcraft.datagen;

import com.google.common.hash.Hashing;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Main-resource snapshots that must survive resource regeneration, without a running client. */
public final class TCClientResourcesProvider implements DataProvider {
    public static final List<String> PATHS=List.of("assets/minecraft/atlases/blocks.json",
        "assets/thaumcraft/shaders/core/fx_gui.json","assets/thaumcraft/shaders/core/fx_gui.vsh","assets/thaumcraft/shaders/core/fx_gui.fsh");
    private final PackOutput output;
    public TCClientResourcesProvider(PackOutput output){this.output=output;}
    public static byte[] read(String path) throws IOException {
        if(!PATHS.contains(path))throw new IOException("Not an owned client resource");
        try(var stream=TCClientResourcesProvider.class.getClassLoader().getResourceAsStream(path)){
            if(stream==null)throw new IOException("Missing client resource: "+path);
            var bytes=stream.readNBytes(65537);if(bytes.length>65536)throw new IOException("Client resource bound");return bytes;
        }
    }
    @Override public CompletableFuture<?> run(CachedOutput cache){
        try{for(String path:PATHS){var bytes=read(path);cache.writeIfNeeded(output.getOutputFolder().resolve(path),bytes,Hashing.sha1().hashBytes(bytes));}
            return CompletableFuture.completedFuture(null);
        }catch(IOException error){return CompletableFuture.failedFuture(error);}
    }
    @Override public String getName(){return "TC6 client atlas and particle GUI shader snapshots";}
}
