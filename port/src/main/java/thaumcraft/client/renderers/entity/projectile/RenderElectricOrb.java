package thaumcraft.client.renderers.entity.projectile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.renderers.entity.CultRenderTypes;
import thaumcraft.common.entities.projectile.EntityGolemOrb;

/** Source ElectricOrb red/blue particle-atlas rows and six-frame bobbing billboard. */
public final class RenderElectricOrb extends EntityRenderer<EntityGolemOrb> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("thaumcraft:textures/misc/particles.png");
    public RenderElectricOrb(EntityRendererProvider.Context context) { super(context); shadowRadius = 0; }
    @Override public ResourceLocation getTextureLocation(EntityGolemOrb orb) { return TEXTURE; }
    @Override public void render(EntityGolemOrb orb, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        poses.pushPose(); poses.mulPose(entityRenderDispatcher.cameraOrientation()); float size = 1 + (float)Math.sin(orb.tickCount / 5F) * .2F + .2F; poses.scale(size, size, size);
        float u = (1 + orb.tickCount % 6) / 32F, v = orb.isRed() ? .1875F : .21875F;
        VertexConsumer vertices = buffers.getBuffer(CultRenderTypes.electric(TEXTURE));
        add(vertices, poses.last(), -.5F, -.5F, u, v + 1 / 32F); add(vertices, poses.last(), .5F, -.5F, u + 1 / 32F, v + 1 / 32F);
        add(vertices, poses.last(), .5F, .5F, u + 1 / 32F, v); add(vertices, poses.last(), -.5F, .5F, u, v); poses.popPose();
        super.render(orb, yaw, partial, poses, buffers, light);
    }
    private static void add(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float u, float v) {
        vertices.addVertex(pose, x, y, 0).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(220).setNormal(pose, 0, 1, 0);
    }
}
