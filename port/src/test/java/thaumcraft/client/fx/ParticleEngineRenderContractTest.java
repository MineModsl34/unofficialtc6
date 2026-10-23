package thaumcraft.client.fx;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Headless rendering-scope contracts; deliberately does not claim GPU acceptance. */
class ParticleEngineRenderContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/thaumcraft/client/fx", name));
    }

    @Test void afterLevelRestoresCameraModelViewForEveryWorldLayer() throws Exception {
        String engine = source("ParticleEngine.java");
        String world = engine.substring(engine.indexOf("public static void renderWorld("),
            engine.indexOf("public static void renderGui("));
        assertTrue(world.contains("Stage.AFTER_LEVEL"));
        int push = world.indexOf("modelView.pushMatrix()");
        int applyView = world.indexOf("modelView.mul(event.getModelViewMatrix())");
        int uploadView = world.indexOf("RenderSystem.applyModelViewMatrix()", applyView);
        int render = world.indexOf("render(3,0,");
        int cleanup = world.indexOf("finally", render);
        int pop = world.indexOf("modelView.popMatrix()", cleanup);
        int uploadRestored = world.indexOf("RenderSystem.applyModelViewMatrix()", pop);
        assertTrue(push >= 0 && push < applyView && applyView < uploadView && uploadView < render);
        assertTrue(render < cleanup && cleanup < pop && pop < uploadRestored);
    }

    @Test void cameraTranslationAndViewRotationAreNotAppliedTwice() throws Exception {
        String engine = source("ParticleEngine.java");
        String world = engine.substring(engine.indexOf("public static void renderWorld("),
            engine.indexOf("public static void renderGui("));
        assertFalse(world.contains("poses.mulPose"));
        assertFalse(world.contains("event.getPoseStack().mulPose"));
        assertFalse(world.contains("modelView.translate("));
        String particle = source("SourceParticle.java");
        assertTrue(particle.contains("partial-interpPosX"));
        assertTrue(particle.contains("partial-interpPosY"));
        assertTrue(particle.contains("partial-interpPosZ"));
        // Vanilla CUSTOM particles are rendered inside the native camera scope.
        assertTrue(particle.contains("renderPrepared(camera,partial,new PoseStack())"));
    }
}
