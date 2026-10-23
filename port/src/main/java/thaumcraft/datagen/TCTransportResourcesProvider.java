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

/** Reviewed native connection states and source transport item graphics.
 * Common TCBlockLoot owns nine self-drops; common TCItemModelProvider owns resonator.
 */
public final class TCTransportResourcesProvider implements DataProvider {
    public static final String RESOURCE = "assets/thaumcraft/porting/transport_resources.json";
    private final PackOutput output;
    public TCTransportResourcesProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        try (var input = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Missing source transport native resource definitions");
            byte[] bytes = input.readNBytes(131073); if (bytes.length > 131072) throw new IOException("Transport resource definitions exceed bound");
            JsonObject manifest = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); validate(manifest);
            var rows = manifest.getAsJsonArray("files"); CompletableFuture<?>[] writes = new CompletableFuture<?>[rows.size()];
            for (int index = 0; index < rows.size(); index++) {
                JsonObject row = rows.get(index).getAsJsonObject(); PackOutput.Target target = row.get("target").getAsString().equals("resource_pack") ? PackOutput.Target.RESOURCE_PACK : PackOutput.Target.DATA_PACK;
                writes[index] = DataProvider.saveStable(cache, row.getAsJsonObject("payload"), output.getOutputFolder(target).resolve(row.get("path").getAsString()));
            }
            return CompletableFuture.allOf(writes);
        } catch (IOException | RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }
    public static void validate(JsonObject manifest) {
        if (manifest.get("format").getAsInt() != 1) throw new IllegalArgumentException("Unknown transport manifest format");
        var rows = manifest.getAsJsonArray("files"); if (rows.size() > 64) throw new IllegalArgumentException("Transport resource count exceeds bound");
        var names = new HashSet<String>();
        for (var entry : rows) {
            JsonObject row = entry.getAsJsonObject(); String target = row.get("target").getAsString(), path = row.get("path").getAsString();
            if ((!target.equals("resource_pack") && !target.equals("data_pack")) || !path.matches("thaumcraft/[a-z0-9_/]+\\.json") || path.contains("..") || !names.add(target + "/" + path))
                throw new IllegalArgumentException("Invalid or duplicate transport resource path");
            if (!row.get("payload").isJsonObject()) throw new IllegalArgumentException("Missing native resource payload");
        }
    }
    @Override public String getName() { return "Source essentia transport native assets"; }
}
