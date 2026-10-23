package thaumcraft.client.fx.particles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** Exact two source trail configurations, not a made-up vanilla flame substitute:
 * FXFireMote frame7/16ticks and FXGeneric frames448..455/8ticks on the source 64-grid. */
public final class FXAlumentumParticle extends Particle {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("thaumcraft:textures/misc/particles.png");
    private final boolean mote;
    private float angle = (float)(Math.PI * 2), previousAngle;
    public FXAlumentumParticle(ClientLevel world, double x, double y, double z, double dx, double dy, double dz, float red, float green, float blue, boolean mote) {
        super(world, x, y, z); this.mote = mote; setSize(.1F, .1F); xd = dx; yd = dy; zd = dz;
        lifetime = mote ? 16 : 8; rCol = red; gCol = green; bCol = blue; alpha = mote ? .5F : .7F;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) remove();
        move(xd, yd, zd); xd *= .9800000190734863D; yd *= .9800000190734863D; zd *= .9800000190734863D;
        if (!mote) { xd += level.random.nextGaussian() * 0F; yd += level.random.nextGaussian() * 0F; zd += level.random.nextGaussian() * 0F; }
        if (onGround) { xd *= .7F; zd *= .7F; }
        if (mote) {
            if (level.random.nextInt(6) == 0) age++;
            if (age >= lifetime) remove(); previousAngle = angle; angle++;
        }
    }
    @Override public void render(VertexConsumer ignored, Camera camera, float partial) {
        int frame = mote ? 7 : 448 + Math.min(age, 7);
        float u = frame % 64 / 64F, v = frame / 64 / 64F;
        float life = Mth.clamp((age + partial) / lifetime, 0, 1);
        float size = mote ? .4F * (1F - age / 16F) : .03F;
        float opacity = mote ? alpha * (1F - age / 16F) : alpha;
        float red = mote ? rCol : rCol * (1 - life), green = mote ? gCol : gCol * (1 - life), blue = mote ? bCol : bCol * (1 - life);
        PoseStack poses = new PoseStack(); var eye = camera.getPosition();
        poses.translate(Mth.lerp(partial, xo, x) - eye.x, Mth.lerp(partial, yo, y) - eye.y, Mth.lerp(partial, zo, z) - eye.z);
        poses.mulPose(camera.rotation());
        if (mote) poses.mulPose(new Quaternionf().rotationZ(angle + (angle - previousAngle) * partial));
        var type = WarpEntityRenderTypes.particle(TEXTURE); var buffers = Minecraft.getInstance().renderBuffers().bufferSource(); var vertices = buffers.getBuffer(type);
        int light = mote ? 255 : getLightColor(partial);
        vertex(vertices, poses.last(), -size, -size, u + 1/64F, v + 1/64F, red, green, blue, opacity, light);
        vertex(vertices, poses.last(), -size, size, u + 1/64F, v, red, green, blue, opacity, light);
        vertex(vertices, poses.last(), size, size, u, v, red, green, blue, opacity, light);
        vertex(vertices, poses.last(), size, -size, u, v + 1/64F, red, green, blue, opacity, light); buffers.endBatch(type);
    }
    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float u, float v, float r, float g, float b, float alpha, int light) {
        vertices.addVertex(pose, x, y, 0).setColor(r, g, b, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }
}
