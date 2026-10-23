package thaumcraft.client.renderers.entity.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.entities.monster.cult.EntityCultistCleric;

/** Original32px source biped/skin, actual held/equipped items and ritualist float/altar ribbons. */
public final class RenderCultist<T extends EntityCultist> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.parse("thaumcraft:cultist"), "main");
    private static final ResourceLocation SKIN = ResourceLocation.parse("thaumcraft:textures/entity/cultist.png"), BEAM = ResourceLocation.parse("thaumcraft:textures/misc/wispy.png");
    public RenderCultist(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), .5F);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON_INNER_ARMOR)), new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON_OUTER_ARMOR)), context.getModelManager()));
    }
    @Override public ResourceLocation getTextureLocation(T entity) { return SKIN; }
    @Override public void render(T entity, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        poses.pushPose(); float bob = 0;
        EntityCultistCleric ritual = entity instanceof EntityCultistCleric cleric && cleric.getIsRitualist() ? cleric : null;
        if (ritual != null) { int offset = new Random(entity.getId()).nextInt(1000); bob = (float)Math.sin((entity.tickCount + partial + offset) / 9F) * .1F + .21F; poses.translate(0, bob, 0); }
        super.render(entity, yaw, partial, poses, buffers, light);
        if (ritual != null) ribbons(ritual, bob, poses, buffers); poses.popPose();
    }
    private static void ribbons(EntityCultistCleric cleric, float bob, PoseStack poses, MultiBufferSource buffers) {
        Vec3 end = Vec3.atLowerCornerOf(cleric.ritualHome()).add(.5, 1.5 - bob, .5).subtract(cleric.position());
        Vec3 start = new Vec3(0, cleric.getEyeHeight() * 1.2F, 0), delta = start.subtract(end);
        float distance = (float)delta.length(), length = Math.round(distance) * 6F; if (length <= 0) return;
        float progress = Math.min(cleric.tickCount, 10) / 10F, time = (float)(System.nanoTime() / 30_000_000L);
        VertexConsumer vertices = buffers.getBuffer(WarpEntityRenderTypes.particle(BEAM));
        Vec3 previous = null; float previousU = 0;
        for (int index = 0; index <= length * progress; index++) {
            float part = index / length, taper = 1 - Math.abs(index - length / 2) / (length / 2);
            double dx = delta.x + Math.sin((cleric.getZ() % 16 + distance * (1 - part) * 6 - time % 32767 / 5) / 4) * .5F * taper;
            double dy = delta.y + Math.sin((cleric.getX() % 16 + distance * (1 - part) * 6 - time % 32767 / 5) / 3) * .5F * taper;
            double dz = delta.z + Math.sin((cleric.getY() % 16 + distance * (1 - part) * 6 - time % 32767 / 5) / 2) * .5F * taper;
            Vec3 point = end.add(dx * part, dy * part, dz * part); float u = (1 - part) * distance + time * .03F;
            if (previous != null) for (int ribbon = 0; ribbon < 2; ribbon++) {
                Vec3 width = ribbon == 0 ? new Vec3(0, .25, 0) : new Vec3(.25, 0, 0);
                vertex(vertices, poses.last(), previous.subtract(width), previousU, 1); vertex(vertices, poses.last(), previous.add(width), previousU, 0);
                vertex(vertices, poses.last(), point.add(width), u, 0); vertex(vertices, poses.last(), point.subtract(width), u, 1);
            }
            previous = point; previousU = u;
        }
    }
    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 point, float u, float v) {
        vertices.addVertex(pose, (float)point.x, (float)point.y, (float)point.z).setColor(17, 0, 17, 204).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(220).setNormal(pose, 0, 1, 0);
    }
}
