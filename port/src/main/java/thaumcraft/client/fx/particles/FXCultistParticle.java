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
import thaumcraft.client.renderers.entity.CultRenderTypes;

/** Only the two literal FXGeneric configurations used by cultistSpawn() and orb burst().
 * Existing TC atlas, source grid16 animation, constant alpha/scale, endpoint RGB and .98
 * physics. No general particle engine or fabricated replacement sprites are introduced. */
public final class FXCultistParticle extends Particle {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("thaumcraft:textures/misc/particles.png");
    private final boolean spawn;
    private final float scale;
    public FXCultistParticle(ClientLevel world, double x, double y, double z, double dx, double dy, double dz, boolean spawn, float scale) {
        super(world, x, y, z); setSize(.1F, .1F); xd = dx; yd = dy; zd = dz; this.spawn = spawn;
        lifetime = spawn ? 10 + world.random.nextInt(10) : 31;
        this.scale = spawn ? 3 + world.random.nextFloat() * 2 : scale;
        rCol = gCol = bCol = 1; alpha = spawn ? .8F : 1;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    @Override public void tick() {
        xo = x; yo = y; zo = z; if (age++ >= lifetime) remove();
        move(xd, yd, zd); xd *= .9800000190734863D; yd *= .9800000190734863D; zd *= .9800000190734863D;
        // Source FXGeneric consumes these world RNG calls even at randomMovementScale=0.
        xd += level.random.nextGaussian() * 0F; yd += level.random.nextGaussian() * 0F; zd += level.random.nextGaussian() * 0F;
        if (onGround) { xd *= .7F; zd *= .7F; }
    }
    @Override public void render(VertexConsumer ignored, Camera camera, float partial) {
        int frames = spawn ? 6 : 31, first = spawn ? 160 : 208;
        int frame = (int)(first + Math.min(frames * ((float)age / lifetime), frames - 1));
        float u = frame % 16 / 16F, v = frame / 16 / 16F, size = .1F * scale;
        float progress = Mth.clamp((age + partial) / lifetime, 0, 1);
        // FXGeneric's default endpoint is black; cultistSpawn explicitly sets dark red.
        float red = 1 + ((spawn ? .6F : 0) - 1) * progress, greenBlue = 1 - progress;
        PoseStack poses = new PoseStack(); var eye = camera.getPosition();
        poses.translate(Mth.lerp(partial, xo, x) - eye.x, Mth.lerp(partial, yo, y) - eye.y, Mth.lerp(partial, zo, z) - eye.z); poses.mulPose(camera.rotation());
        RenderType type = spawn ? WarpEntityRenderTypes.particle(TEXTURE) : CultRenderTypes.electric(TEXTURE);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource(); var vertices = buffers.getBuffer(type);
        vertex(vertices, poses.last(), -size, -size, u + 1/16F, v + 1/16F, red, greenBlue, partial);
        vertex(vertices, poses.last(), -size, size, u + 1/16F, v, red, greenBlue, partial);
        vertex(vertices, poses.last(), size, size, u, v, red, greenBlue, partial);
        vertex(vertices, poses.last(), size, -size, u, v + 1/16F, red, greenBlue, partial); buffers.endBatch(type);
    }
    private void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float u, float v, float red, float greenBlue, float partial) {
        vertices.addVertex(pose, x, y, 0).setColor(red, greenBlue, greenBlue, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(getLightColor(partial)).setNormal(pose, 0, 1, 0);
    }
}
