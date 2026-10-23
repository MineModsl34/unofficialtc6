package thaumcraft.client.lib.warp;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Source shader resource/math closure checks, not an executed GL or pixel-equivalence claim. */
class WarpShaderResourcesTest {
    private static String resource(String name) throws IOException {
        try (var input = WarpShaderResourcesTest.class.getClassLoader().getResourceAsStream("assets/thaumcraft/shaders/" + name)) {
            assertNotNull(input, name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @Test void fourSourcePostChainsRetainPassOrderAndOriginalWarpUniformValues() throws IOException {
        var blur = JsonParser.parseString(resource("post/warp_blurtc.json")).getAsJsonObject().getAsJsonArray("passes");
        assertEquals(2, blur.size());
        for (var pass : blur) {
            var object = pass.getAsJsonObject();
            assertEquals("thaumcraft:warp_blur", object.get("name").getAsString());
            assertEquals(10.0, object.getAsJsonArray("uniforms").get(1).getAsJsonObject().getAsJsonArray("values").get(0).getAsDouble());
        }
        var desat = JsonParser.parseString(resource("post/warp_desaturatetc.json")).getAsJsonObject().getAsJsonArray("passes");
        assertEquals("minecraft:color_convolve", desat.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(0.8, desat.get(0).getAsJsonObject().getAsJsonArray("uniforms").get(0).getAsJsonObject().getAsJsonArray("values").get(0).getAsDouble());
        var hunger = JsonParser.parseString(resource("post/warp_hunger.json")).getAsJsonObject().getAsJsonArray("passes");
        assertEquals("thaumcraft:warp_color_convolve2", hunger.get(0).getAsJsonObject().get("name").getAsString());
        var sun = JsonParser.parseString(resource("post/warp_sunscorned.json")).getAsJsonObject().getAsJsonArray("passes");
        assertEquals("thaumcraft:warp_bloomcolor", sun.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("thaumcraft:warp_bloom2", sun.get(1).getAsJsonObject().get("name").getAsString());
    }
    @Test void everyCustomProgramHasCaseSafeNamespaceAndGlsl150InputsOutputs() throws IOException {
        for (String program : List.of("blur", "color_convolve2", "bloom2", "bloomcolor")) {
            var json = JsonParser.parseString(resource("program/warp_" + program + ".json")).getAsJsonObject();
            assertEquals("thaumcraft:warp_" + program, json.get("fragment").getAsString());
            assertTrue(json.get("vertex").getAsString().equals("minecraft:sobel")
                || json.get("vertex").getAsString().equals("thaumcraft:warp_bloomvertexbase"));
            String code = resource("program/warp_" + program + ".fsh");
            assertTrue(code.startsWith("#version 150"));
            assertTrue(code.contains("out vec4 fragColor;"));
            assertFalse(code.contains("texture2D("));
            assertFalse(code.contains("gl_FragColor"));
            assertFalse(code.contains("varying "));
        }
        assertTrue(resource("program/warp_bloomvertexbase.vsh").startsWith("#version 150"));
    }
    @Test void sourceLegacyBlurAndBloomSampleMathIsNotReplacedByModernBoxBlur() throws IOException {
        String blur = resource("program/warp_blur.fsh");
        assertTrue(blur.contains("r += 1.0"));
        assertTrue(blur.contains("blurred.rgb / (Radius * 2.0 + 1.0), totalAlpha"));
        String bloom = resource("program/warp_bloomcolor.fsh");
        assertEquals(81, bloom.split("texture\\(DiffuseSampler, vec2", -1).length - 1);
        assertTrue(bloom.indexOf("vec4 averageColor=(") > bloom.indexOf("void main()"));
        assertTrue(bloom.contains("min(max(0.3/averageBrightness,0.7),4.0)"));
        assertTrue(resource("program/warp_bloom2.fsh").contains("outputColor*0.9"));
    }
    @Test void privateEntityAndParticleShadersKeepSourceLowAlphaCutoffAfterVertexOpacity() throws IOException {
        String code=resource("core/warp_low_alpha.fsh");
        assertTrue(code.indexOf("color *= vertexColor * ColorModulator")<code.indexOf("color.a <= 0.003921569"));
        assertFalse(code.contains("color.a < 0.1"),"vanilla high alpha cutoff would erase faint mind spiders and source orb rays");
        for(String name:List.of("entity","particle")) {
            var json=JsonParser.parseString(resource("core/warp_"+name+".json")).getAsJsonObject();
            assertEquals("thaumcraft:warp_low_alpha",json.get("fragment").getAsString());
            assertEquals("thaumcraft:warp_"+name,json.get("vertex").getAsString());
        }
        assertTrue(resource("core/warp_particle.vsh").contains("vertexColor = Color;"),"source particles are not directional model-shaded");
    }
}
