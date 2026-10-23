package thaumcraft.client.renderers.tile;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guards for native buffer ownership and blend wiring; these do not claim GPU acceptance. */
class Stage11RendererContractsTest {
    private static final Path CLIENT = Path.of("src/main/java/thaumcraft/client");

    @Test void matrixDoesNotAcquireAnotherRenderTypeWhileAnEightPartConsumerIsInUse() throws Exception {
        String renderer = Files.readString(CLIENT.resolve("renderers/tile/TileInfusionMatrixRenderer.java"));
        int passLoop = renderer.indexOf("for (int pass =");
        int consumer = renderer.indexOf("buffers.getBuffer(");
        int partLoop = renderer.indexOf("for (int a =", passLoop);
        assertTrue(passLoop >= 0 && consumer > passLoop && consumer < partLoop,
            "Obtain a fresh shared-buffer consumer inside each pass, before drawing any parts");
        String parts = bracedBody(renderer, renderer.indexOf("for (int c =", partLoop));
        assertFalse(parts.contains("getBuffer("), "Switching RenderType ends the current shared-buffer consumer");
        assertTrue(parts.contains("cube.render(poses,vertices,"));
        assertTrue(parts.contains("glow.render(poses,vertices,"));
    }

    @Test void sourceAdditiveConsumersUseSrcAlphaOneRatherThanVanillaOneOne() throws Exception {
        String utils = compact(Files.readString(CLIENT.resolve("lib/UtilsFX.java")));
        assertTrue(utils.contains("RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE)"));
        assertTrue(utils.contains("setTransparencyState(additive?SOURCE_ADDITIVE:RenderStateShard.TRANSLUCENT_TRANSPARENCY)"));
        assertFalse(utils.contains("RenderStateShard.ADDITIVE_TRANSPARENCY"));

        String special = compact(Files.readString(CLIENT.resolve("renderers/entity/RenderSpecialItem.java")));
        assertTrue(special.contains("GLOW=SourceRenderTypes.rays()"));
        String sourceTypes = compact(Files.readString(CLIENT.resolve("lib/ender/SourceRenderTypes.java")));
        assertTrue(sourceTypes.contains("RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE)"));
        assertTrue(sourceTypes.contains("rays(){returnTYPES.computeIfAbsent(\"rays\""));
        String rays = sourceTypes.substring(sourceTypes.indexOf("rays(){"), sourceTypes.indexOf("publicstaticRenderTypemirror("));
        assertTrue(rays.contains("setTransparencyState(ADD)"));
    }

    private static String compact(String source) { return source.replaceAll("\\s+", ""); }

    private static String bracedBody(String source, int start) {
        assertTrue(start >= 0);
        int open = source.indexOf('{', start), depth = 1;
        assertTrue(open >= 0);
        for (int i = open + 1; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}' && --depth == 0) return source.substring(open + 1, i);
        }
        fail("Unclosed part loop");
        return "";
    }
}
