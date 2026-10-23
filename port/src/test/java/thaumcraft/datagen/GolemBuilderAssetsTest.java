package thaumcraft.datagen;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import thaumcraft.client.renderers.models.entity.ConstructObjMesh;
import static org.junit.jupiter.api.Assertions.*;

class GolemBuilderAssetsTest {
    private static Path root() { return Files.isDirectory(Path.of("src/main/resources")) ? Path.of("") : Path.of("port"); }
    private static Path repository() { return root().toAbsolutePath().normalize().getParent(); }
    private static JsonObject manifest() throws Exception { return JsonParser.parseString(Files.readString(root().resolve("src/main/resources/" + TCGolemBuilderAssetsProvider.RESOURCE))).getAsJsonObject(); }
    @Test void allSeventeenNativeSnapshotsMatchManifestAndResolveOwnParents() throws Exception {
        var manifest = manifest(); TCGolemBuilderAssetsProvider.validate(manifest);
        for (var value : manifest.getAsJsonArray("entries")) { var row = value.getAsJsonObject(); String path = row.get("path").getAsString();
            var snapshot = JsonParser.parseString(Files.readString(root().resolve("src/generated/resources/" + path))).getAsJsonObject(); assertEquals(row.getAsJsonObject("payload"),snapshot,path);
            String json = snapshot.toString(); assertFalse(json.contains("forge_marker")); assertFalse(json.contains(":blocks/")); assertFalse(json.contains(":items/"));
            if (snapshot.has("parent") && snapshot.get("parent").getAsString().startsWith("thaumcraft:")) { String parent = snapshot.get("parent").getAsString().substring(11);
                assertTrue(Files.isRegularFile(root().resolve("src/main/resources/assets/thaumcraft/models/" + parent + ".json")) || Files.isRegularFile(root().resolve("src/generated/resources/assets/thaumcraft/models/" + parent + ".json")),parent); }
        }
        assertEquals("minecraft:builtin/entity",JsonParser.parseString(Files.readString(root().resolve("src/generated/resources/assets/thaumcraft/models/item/golem_builder.json"))).getAsJsonObject().get("parent").getAsString());
    }
    @Test void allEighteenObjBindingsRetainSource1200FacesAndEveryGroup() throws Exception {
        int faces = 0;
        for (var value : manifest().getAsJsonArray("models")) { var row = value.getAsJsonObject(); String source = row.get("path").getAsString();
            Path original = repository().resolve(source),ported = root().resolve("src/main/resources/" + source.substring("reference/tc6/src/".length()));
            String originalText = Files.readString(original),portedText = Files.readString(ported); assertEquals(originalText.replace("\r\n","\n").stripTrailing(),portedText.replace("\r\n","\n").stripTrailing(),source);
            var mesh = ConstructObjMesh.read(Files.newBufferedReader(ported)); int count = mesh.groups().stream().mapToInt(group -> mesh.faces(group).size()).sum(); assertEquals(row.get("faces").getAsInt(),count,source); faces += count;
            for (var group : row.getAsJsonArray("groups")) assertTrue(mesh.groups().contains(group.getAsString()),source + ":" + group.getAsString());
        }
        assertEquals(1200,faces);
    }
    @Test void sourceProvenanceAndStableCatalogNumericOrderingRemainExact() throws Exception {
        var manifest = manifest();
        for (String array : List.of("sourceFiles","models")) for (var value : manifest.getAsJsonArray(array)) { var row = value.getAsJsonObject(); byte[] bytes = Files.readAllBytes(repository().resolve(row.get("path").getAsString())); assertEquals(row.get("sha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))); }
        var catalog = manifest.getAsJsonObject("catalog"); assertEquals("BIG_ENDIAN",catalog.get("byteOrder").getAsString());
        assertEquals(List.of("WOOD","IRON","CLAY","BRASS","THAUMIUM","VOID"),strings(catalog.getAsJsonArray("materials")));
        assertEquals(List.of("BASIC","SMART","SMART_ARMORED","SCOUT","SMART_SCOUT"),strings(catalog.getAsJsonArray("heads")));
        assertEquals(List.of("BASIC","FINE","CLAWS","BREAKERS","DARTS"),strings(catalog.getAsJsonArray("arms")));
        assertEquals(List.of("WALKER","ROLLER","CLIMBER","FLYER"),strings(catalog.getAsJsonArray("legs")));
        assertEquals(List.of("NONE","ARMORED","FIGHTER","HAULER"),strings(catalog.getAsJsonArray("addons")));
        String java = Files.readString(root().resolve("src/main/java/thaumcraft/common/golems/GolemProperties.java")); assertFalse(java.contains("thaumcraft.common.golems.client")); assertFalse(java.contains("net.minecraft.client"));
    }
    private static List<String> strings(JsonArray array) { var out = new ArrayList<String>(); for (var value : array) out.add(value.getAsString()); return out; }
}
