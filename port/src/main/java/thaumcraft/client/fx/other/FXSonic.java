package thaumcraft.client.fx.other;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.IOException;
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
import net.minecraft.world.phys.AABB;
import thaumcraft.client.lib.warp.SourceSonicMesh;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** Source sonic attack: tracked origin, captured facing, fifteen ripple frames and original mesh. */
public final class FXSonic extends Particle {
    private static final ResourceLocation MODEL = ResourceLocation.parse("thaumcraft:models/obj/hemis.obj");
    private static SourceSonicMesh mesh;
    private final Entity target;
    private final float yaw, pitch;
    public FXSonic(ClientLevel level, Entity target, int age) {
        super(level, target.getX(), target.getEyeY(), target.getZ());
        this.target = target;
        yaw = target.getYHeadRot(); pitch = target.getXRot();
        lifetime = age + random.nextInt(age / 2);
        setSize(0.01F, 0.01F);
        hasPhysics = false;
    }
    public FXSonic(ClientLevel level,double x,double y,double z,Entity target,int age){this(level,target,age);}
    public static void reload() { mesh = null; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    @Override public AABB getRenderBoundingBox(float partial) { return AABB.INFINITE; }
    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) remove();
        setPos(target.getX(), target.getEyeY(), target.getZ());
    }
    @Override public void render(VertexConsumer ignored, Camera camera, float partial) {
        if (mesh == null) {
            try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(MODEL)) { mesh = SourceSonicMesh.read(reader); }
            catch (IOException | RuntimeException failure) { throw new IllegalStateException("Cannot load source sonic hemisphere", failure); }
        }
        PoseStack poses = new PoseStack();
        var position = camera.getPosition();
        poses.translate(Mth.lerp(partial, xo, x) - position.x, Mth.lerp(partial, yo, y) - position.y, Mth.lerp(partial, zo, z) - position.z);
        poses.mulPose(Axis.YP.rotationDegrees(-yaw)); poses.mulPose(Axis.XP.rotationDegrees(pitch));
        poses.translate(0, 0, 2 * target.getBbHeight() + target.getBbWidth() / 2);
        poses.scale(0.25F * target.getBbHeight(), 0.25F * target.getBbHeight(), -target.getBbHeight());
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var renderType = WarpEntityRenderTypes.particle(ResourceLocation.parse("thaumcraft:textures/models/ripple" + SourceSonicMesh.frame(age, partial, lifetime) + ".png"));
        VertexConsumer vertices = buffers.getBuffer(renderType);
        for (var triangle : mesh.triangles()) {
            vertex(vertices, poses.last(), triangle.a(), triangle);
            vertex(vertices, poses.last(), triangle.b(), triangle);
            vertex(vertices, poses.last(), triangle.c(), triangle);
            vertex(vertices, poses.last(), triangle.c(), triangle); // Second quad triangle degenerates; preserves the original 160 triangles.
        }
        buffers.endBatch(renderType);
    }
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, SourceSonicMesh.Corner corner, SourceSonicMesh.Triangle triangle) {
        buffer.addVertex(pose, corner.x(), corner.y(), corner.z()).setColor(128, 128, 128, 255).setUv(corner.u(), corner.v())
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(220).setNormal(pose, triangle.nx(), triangle.ny(), triangle.nz());
    }
}
