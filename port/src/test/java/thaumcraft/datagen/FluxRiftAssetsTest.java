package thaumcraft.datagen;
import com.google.gson.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class FluxRiftAssetsTest {
    private static Path root(){return Files.isDirectory(Path.of("src/main"))?Path.of(""):Path.of("port");}
    private static JsonObject manifest()throws Exception{return JsonParser.parseString(Files.readString(root().resolve("src/main/resources/"+TCFluxRiftAssetsProvider.RESOURCE))).getAsJsonObject();}
    @Test void allSnapshotsMatchTheNativeFiles()throws Exception{var source=manifest();TCFluxRiftAssetsProvider.validate(source);for(var item:source.getAsJsonArray("entries")){var row=item.getAsJsonObject();assertEquals(row.get("payload"),JsonParser.parseString(Files.readString(root().resolve("src/generated/resources/"+row.get("path").getAsString()))));}var payload=source.getAsJsonArray("entries").get(1).getAsJsonObject().getAsJsonObject("payload");assertEquals(10,payload.getAsJsonArray("elements").size());assertFalse(payload.get("ambientocclusion").getAsBoolean());}
    @Test void originalTexturesAndSourceHashesAreProvenanceChecked()throws Exception{Path repository=root().toAbsolutePath().normalize().getParent();for(var item:manifest().getAsJsonArray("sourceFiles")){var row=item.getAsJsonObject();Path file=repository.resolve(row.get("path").getAsString());assertEquals(row.get("sha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));if(file.toString().endsWith(".png")){String nativePath=row.get("path").getAsString().replace("reference/tc6/src/", "src/main/resources/").replace("textures/blocks/", "textures/block/");assertArrayEquals(Files.readAllBytes(file),Files.readAllBytes(root().resolve(nativePath)));}}}
}
