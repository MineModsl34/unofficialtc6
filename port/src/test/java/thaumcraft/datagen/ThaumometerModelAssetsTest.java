package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Structural resource checks only: no renderer, hand-pose or visual-parity assertion. */
class ThaumometerModelAssetsTest {
    private static final String ASSETS = "assets/thaumcraft/";

    @Test
    void runtimeObjRetainsSourceMeshCountsGroupsMaterialsAndValidTriangleIndices() throws IOException {
        List<String[]> lines = tokens("models/item/scanner.obj");
        int vertices = count(lines, "v");
        int normals = count(lines, "vn");
        int uvs = count(lines, "vt");
        assertEquals(36, vertices);
        assertEquals(10, normals);
        assertEquals(70, uvs);
        assertEquals(56, count(lines, "f"));
        assertEquals(List.of("scanner.mtl"), lines.stream().filter(line -> line[0].equals("mtllib"))
            .map(line -> { assertEquals(2, line.length); return line[1]; }).toList());

        Map<String, Integer> groupFaces = new LinkedHashMap<>();
        Map<String, String> groupMaterials = new LinkedHashMap<>();
        String group = null;
        String material = null;
        for (String[] line : lines) {
            switch (line[0]) {
                case "mtllib" -> {
                    assertEquals(2, line.length);
                    assertEquals("scanner.mtl", line[1]);
                }
                case "v", "vn", "vt" -> {
                    assertEquals(4, line.length, "source coordinate component count");
                    for (int i = 1; i < line.length; i++) {
                        assertTrue(Double.isFinite(Double.parseDouble(line[i])), "finite mesh coordinate");
                    }
                }
                case "g" -> {
                    assertEquals(2, line.length);
                    group = line[1];
                    assertNull(groupFaces.put(group, 0), "group names remain unique");
                    material = null;
                }
                case "usemtl" -> {
                    assertEquals(2, line.length);
                    assertNotNull(group, "material belongs to a declared mesh group");
                    material = line[1];
                    assertNull(groupMaterials.put(group, material), "one source material per group");
                }
                case "f" -> {
                    assertNotNull(group);
                    assertNotNull(material);
                    assertEquals(4, line.length, "original mesh contains triangles, not unresolved polygons");
                    for (int corner = 1; corner < line.length; corner++) {
                        String[] indices = line[corner].split("/", -1);
                        assertEquals(3, indices.length, "each source corner has position/UV/normal");
                        assertIndex(indices[0], vertices);
                        assertIndex(indices[1], uvs);
                        assertIndex(indices[2], normals);
                    }
                    groupFaces.compute(group, (key, value) -> value + 1);
                }
                default -> fail("Unexpected scanner OBJ directive: " + line[0]);
            }
        }
        assertEquals(List.of("scanner", "screen", "screen_back"), List.copyOf(groupFaces.keySet()));
        assertEquals(Map.of("scanner", 48, "screen", 4, "screen_back", 4), groupFaces);
        assertEquals(Map.of("scanner", "body", "screen", "pane", "screen_back", "pane"), groupMaterials);
    }

    @Test
    void runtimeMaterialAliasesResolveToTheTwoExistingPngTextures() throws IOException {
        Map<String, String> diffuse = new LinkedHashMap<>();
        Map<String, String> ambient = new LinkedHashMap<>();
        String material = null;
        for (String[] line : tokens("models/item/scanner.mtl")) {
            if (line[0].equals("newmtl")) {
                assertEquals(2, line.length);
                material = line[1];
                assertFalse(diffuse.containsKey(material), "material names remain unique");
            } else if (line[0].equals("map_Kd") || line[0].equals("map_Ka")) {
                assertNotNull(material);
                assertEquals(2, line.length);
                Map<String, String> target = line[0].equals("map_Kd") ? diffuse : ambient;
                assertNull(target.put(material, line[1]), "single texture alias per material channel");
            }
        }
        assertEquals(Map.of("body", "#body", "pane", "#pane"), diffuse);
        assertEquals(diffuse, ambient);
        assertPng("textures/item/scanner.png");
        assertPng("textures/item/scanscreen.png");
    }

    @Test
    void generatedModelUsesNativeObjLoaderAndBindsBothMaterialAliases() throws IOException {
        // runData must precede this verification; a missing generated model is a failure,
        // never an assumption/skip or a fallback to the datagen Java source.
        JsonObject model = JsonParser.parseString(text("models/item/thaumometer.json")).getAsJsonObject();
        assertEquals("neoforge:obj", model.get("loader").getAsString());
        assertEquals("thaumcraft:models/item/scanner.obj", model.get("model").getAsString());
        assertTrue(model.get("flip_v").getAsBoolean());
        assertFalse(model.get("automatic_culling").getAsBoolean());
        JsonObject textures = model.getAsJsonObject("textures");
        assertEquals(Set.of("body", "pane", "particle"), textures.keySet());
        assertEquals("thaumcraft:item/scanner", textures.get("body").getAsString());
        assertEquals("thaumcraft:item/scanscreen", textures.get("pane").getAsString());
        assertEquals("thaumcraft:item/scanner", textures.get("particle").getAsString());
    }

    private static List<String[]> tokens(String path) throws IOException {
        return text(path).lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .map(line -> line.split("\\s+")).toList();
    }

    private static int count(List<String[]> lines, String directive) {
        return Math.toIntExact(lines.stream().filter(line -> line[0].equals(directive)).count());
    }

    private static void assertIndex(String token, int maximum) {
        int index = Integer.parseInt(token);
        assertTrue(index > 0 && index <= maximum, "OBJ reference within declared source array: " + token);
    }

    private static void assertPng(String path) throws IOException {
        byte[] bytes = bytes(path);
        assertTrue(bytes.length >= 33, "PNG contains the IHDR chunk: " + path);
        assertArrayEquals(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10},
            java.util.Arrays.copyOf(bytes, 8), "PNG signature: " + path);
        ByteBuffer header = ByteBuffer.wrap(bytes);
        assertEquals(13, header.getInt(8), "IHDR byte length");
        assertEquals("IHDR", new String(bytes, 12, 4, StandardCharsets.US_ASCII));
        assertTrue(header.getInt(16) > 0 && header.getInt(20) > 0, "nonempty texture dimensions");
    }

    private static String text(String path) throws IOException {
        return new String(bytes(path), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String path) throws IOException {
        try (InputStream input = ThaumometerModelAssetsTest.class.getClassLoader().getResourceAsStream(ASSETS + path)) {
            assertNotNull(input, "Missing runtime resource: " + ASSETS + path);
            return input.readAllBytes();
        }
    }
}
