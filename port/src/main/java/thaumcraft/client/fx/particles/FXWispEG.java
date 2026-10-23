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
import net.minecraft.world.entity.Entity;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** Original dark guardian smoke particles, atlas animation, lifetime and target-motion inheritance. */
public final class FXWispEG extends Particle implements thaumcraft.client.fx.SourceFxLayer {
    private static final RenderType TYPE = WarpEntityRenderTypes.particle(ResourceLocation.parse("thaumcraft:textures/misc/particles.png"));
    private final Entity target;
    private final float particleScale;
    public int blendmode=771;
    @Override public int getFXLayer(){return blendmode==1?0:1;}
    public FXWispEG(ClientLevel level, double x, double y, double z, Entity target) {
        super(level, x, y, z);
        this.target = target;
        particleScale = (random.nextFloat() * 0.5F + 0.5F) * 2 * 0.4F;
        xd = random.nextGaussian() * 0.03; yd = -0.05; zd = random.nextGaussian() * 0.03;
        lifetime = (int)(40 / (Math.random() * 0.3 + 0.7));
        setSize(0.01F, 0.01F);
        rCol = random.nextFloat() * 0.05F; gCol = random.nextFloat() * 0.05F; bCol = random.nextFloat() * 0.05F;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    @Override public void tick() {
        // Old Particle continues its physics on the expiry tick; do not change that ordering.
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) remove();
        move(xd, yd, zd); xd *= 0.9800000190734863; yd *= 0.9800000190734863; zd *= 0.9800000190734863;
        if (onGround) { xd *= 0.699999988079071; zd *= 0.699999988079071; }
        if (target != null && !onGround) setPos(x + target.getDeltaMovement().x, y, z + target.getDeltaMovement().z);
    }
    @Override public void render(VertexConsumer ignored, Camera camera, float partial) {
        Entity viewer = Minecraft.getInstance().getCameraEntity();
        float base = viewer == null ? 0 : (float)(1 - Math.min(1024, viewer.distanceToSqr(x, y, z)) / 1024);
        float opacity = 0.2F * (1 - (float)age / lifetime) * base;
        PoseStack poses = new PoseStack();
        var position = camera.getPosition();
        poses.translate(Mth.lerp(partial, xo, x) - position.x, Mth.lerp(partial, yo, y) - position.y, Mth.lerp(partial, zo, z) - position.z);
        poses.mulPose(camera.rotation());
        float size = 0.5F * particleScale, u = age % 13 / 64.0F, v = 3 / 64.0F;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type=blendmode==1?thaumcraft.client.fx.FxRenderTypes.type(ResourceLocation.parse("thaumcraft:textures/misc/particles.png"),true,true,false):TYPE;
        VertexConsumer vertices = buffers.getBuffer(type);
        vertex(vertices, poses.last(), -size, -size, u + 1 / 64.0F, v + 1 / 64.0F, opacity);
        vertex(vertices, poses.last(), -size, size, u + 1 / 64.0F, v, opacity);
        vertex(vertices, poses.last(), size, size, u, v, opacity);
        vertex(vertices, poses.last(), size, -size, u, v + 1 / 64.0F, opacity);
        buffers.endBatch(type);
    }
    private void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float u, float v, float opacity) {
        // Source lightmap(j,k), j=0/k=240: old SHORT BufferBuilder stores blockLight(k) LOW, skyLight(j) HIGH.
        vertices.addVertex(pose, x, y, 0).setColor(rCol, gCol, bCol, opacity).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(240).setNormal(pose, 0, 1, 0);
    }
}
