package thaumcraft.client.renderers.entity.construct;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;
import thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser;

/** Original16-frame source portal billboard, opening/hurt/pulse/health oscillation. */
public final class RenderCultistPortalLesser extends EntityRenderer<EntityCultistPortalLesser> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("thaumcraft:textures/misc/cultist_portal.png");
    public RenderCultistPortalLesser(EntityRendererProvider.Context context) { super(context); shadowRadius = 0; }
    @Override public ResourceLocation getTextureLocation(EntityCultistPortalLesser portal) { return TEXTURE; }
    @Override public void render(EntityCultistPortalLesser portal, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        if (portal.isActive()) {
            float height = 1.4F; int opening = (int)Math.min(50, portal.activeCounter + partial);
            if (portal.hurtTime > 0) { double sine = Math.sin(portal.hurtTime * 72 * Math.PI / 180); height -= (float)(sine / 4); opening = (int)(opening + 6 * sine); }
            if (portal.pulse > 0) { double sine = Math.sin(portal.pulse * 36 * Math.PI / 180); height += (float)(sine / 4); opening = (int)(opening + 12 * sine); }
            float scale = opening / 50F * 1.25F, missing = (1 - portal.getHealth() / portal.getMaxHealth()) / 3;
            float bob = (float)Math.sin(portal.activeCounter / (5 - 12 * missing)) * missing + missing;
            float bob2 = (float)Math.sin(portal.activeCounter / (6 - 15 * missing)) * missing + missing;
            float alpha = 1 - bob; height -= bob / 4; scale -= bob2 / 3;
            poses.pushPose(); poses.translate(0, portal.getBbHeight() / 2, 0); poses.mulPose(entityRenderDispatcher.cameraOrientation());
            VertexConsumer vertex = buffers.getBuffer(WarpEntityRenderTypes.particle(TEXTURE));
            int frame = 15 - (int)(System.nanoTime() / 50_000_000L % 16); float u = frame / 16F, right = u + 1 / 16F;
            add(vertex, poses.last(), -scale, -height, right, 0, alpha); add(vertex, poses.last(), scale, -height, u, 0, alpha);
            add(vertex, poses.last(), scale, height, u, 1, alpha); add(vertex, poses.last(), -scale, height, right, 1, alpha); poses.popPose();
        }
        super.render(portal, yaw, partial, poses, buffers, light);
    }
    private static void add(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float u, float v, float alpha) {
        vertices.addVertex(pose, x, y, 0).setColor(1, 1, 1, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(220).setNormal(pose, 0, 0, -1);
    }
}
