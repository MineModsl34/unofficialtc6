package thaumcraft.client.fx.particles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** Literal source FXSlimyBubble frames, scale, fifteen-to-nineteen ticks and vertical movement. */
public final class FXSlimyBubble extends Particle implements thaumcraft.client.fx.SourceFxLayer {
    private static final RenderType TYPE = WarpEntityRenderTypes.particle(ResourceLocation.parse("thaumcraft:textures/misc/particles.png"));
    private int frame = 144;
    private final float scale;
    @Override public int getFXLayer(){return 1;}
    public FXSlimyBubble(ClientLevel level, double x, double y, double z, float scale) {
        super(level, x, y, z); this.scale = scale; xd = yd = zd = 0;
        lifetime = 15 + level.random.nextInt(5); setSize(.01F, .01F);
    }
    public FXSlimyBubble sourceColor(float red, float green, float blue, float opacity) {
        rCol = red; gCol = green; bCol = blue; alpha = opacity; return this;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) remove();
        if (age - 1 < 6) { frame = 144 + age / 2; if (age == 5) y += .1; }
        else if (age < lifetime - 4) { yd += .005; frame = 147 + age % 4 / 2; }
        else { yd /= 2; frame = 150 - (lifetime - age) / 2; }
        y += yd;
    }
    @Override public void render(VertexConsumer ignored, Camera camera, float partial) {
        // Preserve the source's actual %16/64 atlas indexing, including its unusual first-row offset.
        float u = frame % 16 / 64F, v = frame / 16 / 64F;
        PoseStack poses = new PoseStack(); var eye = camera.getPosition();
        poses.translate(Mth.lerp(partial, xo, x) - eye.x, Mth.lerp(partial, yo, y) - eye.y, Mth.lerp(partial, zo, z) - eye.z);
        poses.mulPose(camera.rotation());
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource(); var vertices = buffers.getBuffer(TYPE);
        vertex(vertices, poses.last(), -scale, -scale, u + 1 / 64F, v + 1 / 64F, partial);
        vertex(vertices, poses.last(), -scale, scale, u + 1 / 64F, v, partial);
        vertex(vertices, poses.last(), scale, scale, u, v, partial);
        vertex(vertices, poses.last(), scale, -scale, u, v + 1 / 64F, partial); buffers.endBatch(TYPE);
    }
    private void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float u, float v, float partial) {
        vertices.addVertex(pose, x, y, 0).setColor(rCol, gCol, bCol, alpha).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(getLightColor(partial)).setNormal(pose, 0, 1, 0);
    }
}
