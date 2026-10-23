package thaumcraft.client.renderers.entity.projectile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.projectile.EntityEldritchOrb;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** Original seeded twelve triangular fans plus the animated TC particle-atlas billboard. */
public final class RenderEldritchOrb extends EntityRenderer<EntityEldritchOrb> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("thaumcraft:textures/misc/particles.png");
    public RenderEldritchOrb(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(EntityEldritchOrb orb) { return TEXTURE; }
    @Override public void render(EntityEldritchOrb orb, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        Random random = new Random(187L);
        VertexConsumer rays = buffers.getBuffer(RenderType.dragonRays());
        poses.pushPose();
        for (int i = 0; i < 12; i++) {
            poses.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360));
            poses.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360));
            poses.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360));
            poses.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360));
            poses.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360));
            poses.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360 + orb.tickCount / 80.0F * 360));
            float divisor = 30.0F / (Math.min(orb.tickCount, 10) / 10.0F);
            float length = (random.nextFloat() * 20 + 5) / divisor;
            float width = (random.nextFloat() * 2 + 1) / divisor;
            float[][] corners = {{-0.866F * width, length, -0.5F * width}, {0.866F * width, length, -0.5F * width}, {0, length, width}};
            for (int triangle = 0; triangle < 3; triangle++) {
                rays.addVertex(poses.last(), 0, 0, 0).setColor(255, 255, 255, 255);
                // Source float color(64,64,64,255) multiplied by 255 then stored in bytes:
                // retain the actual 192/192/192/1 result rather than correcting its apparent typo.
                float[] a = corners[triangle], b = corners[(triangle + 1) % 3];
                rays.addVertex(poses.last(), a[0], a[1], a[2]).setColor(192, 192, 192, 1);
                rays.addVertex(poses.last(), b[0], b[1], b[2]).setColor(192, 192, 192, 1);
            }
        }
        poses.popPose();
        poses.pushPose();
        poses.mulPose(entityRenderDispatcher.cameraOrientation());
        poses.scale(0.75F, 0.75F, 0.75F);
        VertexConsumer sprite = buffers.getBuffer(WarpEntityRenderTypes.particle(TEXTURE));
        float u = orb.tickCount % 13 / 64.0F, v = 3 / 64.0F;
        vertex(sprite, poses.last(), light, -0.5F, -0.5F, u, v + 1 / 64.0F);
        vertex(sprite, poses.last(), light, 0.5F, -0.5F, u + 1 / 64.0F, v + 1 / 64.0F);
        vertex(sprite, poses.last(), light, 0.5F, 0.5F, u + 1 / 64.0F, v);
        vertex(sprite, poses.last(), light, -0.5F, 0.5F, u, v);
        poses.popPose();
        super.render(orb, yaw, partial, poses, buffers, light);
    }
    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, int light, float x, float y, float u, float v) {
        vertices.addVertex(pose, x, y, 0).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(pose, 0, 1, 0);
    }
}
