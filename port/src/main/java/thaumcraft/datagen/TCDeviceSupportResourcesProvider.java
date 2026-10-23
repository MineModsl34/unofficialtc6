package thaumcraft.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Source mirror icons, all powered-rail states and the real native purifying fluid bucket. */
public final class TCDeviceSupportResourcesProvider implements DataProvider {
    public static final String RESOURCE = "assets/thaumcraft/porting/device_support_resources.json";
    private final PackOutput output;
    public TCDeviceSupportResourcesProvider(PackOutput output) { this.output = output; }
    public static JsonObject read() {
        try (var input = TCDeviceSupportResourcesProvider.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Missing source device support resources");
            byte[] bytes = input.readNBytes(131073);
            if (bytes.length > 131072) throw new IOException("Device support resource bound");
            var manifest = JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            TCTransportResourcesProvider.validate(manifest);
            return manifest;
        } catch (IOException invalid) { throw new IllegalArgumentException("Invalid device resources",invalid); }
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var rows = read().getAsJsonArray("files");
        CompletableFuture<?>[] writes = new CompletableFuture<?>[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i).getAsJsonObject();
            writes[i] = DataProvider.saveStable(cache,row.getAsJsonObject("payload"),output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(row.get("path").getAsString()));
        }
        return CompletableFuture.allOf(writes);
    }
    @Override public String getName() { return "Thaumcraft source mirrors, rail and purifying fluid assets"; }
}
