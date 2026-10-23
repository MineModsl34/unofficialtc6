package thaumcraft.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original death-fluid atlas texture and true native fluid bucket; block itself renders from FluidState. */
public final class TCDeathFluidResourcesProvider implements DataProvider {
    public static final String RESOURCE = "assets/thaumcraft/porting/death_fluid_resources.json";
    private final PackOutput output;
    public TCDeathFluidResourcesProvider(PackOutput output) { this.output = output; }
    public static JsonObject read() {
        try (var input = TCDeathFluidResourcesProvider.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Missing death-fluid resource definitions");
            byte[] bytes = input.readNBytes(131073);
            if (bytes.length > 131072) throw new IOException("Death-fluid resource bound");
            var manifest = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            TCTransportResourcesProvider.validate(manifest);
            return manifest;
        } catch (IOException invalid) { throw new IllegalArgumentException("Invalid death-fluid resources", invalid); }
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var rows = read().getAsJsonArray("files"); var writes = new CompletableFuture<?>[rows.size()];
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index).getAsJsonObject();
            writes[index] = DataProvider.saveStable(cache, row.getAsJsonObject("payload"), output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(row.get("path").getAsString()));
        }
        return CompletableFuture.allOf(writes);
    }
    @Override public String getName() { return "Source Liquid Death and native fluid bucket assets"; }
}
