package thaumcraft.client.renderers.entity.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.SpiderEyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import thaumcraft.common.entities.monster.EntityMindSpider;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** Source viewer filter, 0.6 scale, 0.1 body opacity and vanilla spider geometry/eyes. */
public final class RenderMindSpider extends MobRenderer<EntityMindSpider, SpiderModel<EntityMindSpider>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/spider/spider.png");
    public RenderMindSpider(EntityRendererProvider.Context context) {
        super(context, new FaintModel(context.bakeLayer(ModelLayers.SPIDER)), 0.5F);
        addLayer(new FullEyes(this));
        addLayer(new FullEyes(this)); // Source RenderSpider's eyes + RenderMindSpider's additional layer.
    }
    private static boolean visibleToViewer(EntityMindSpider spider) {
        var viewer = Minecraft.getInstance().getCameraEntity();
        return spider.getViewer().isEmpty() || (viewer != null && spider.getViewer().equals(viewer.getName().getString()));
    }
    @Override public boolean shouldRender(EntityMindSpider spider, Frustum frustum, double x, double y, double z) {
        return visibleToViewer(spider) && super.shouldRender(spider, frustum, x, y, z);
    }
    @Override public void render(EntityMindSpider spider, float yaw, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light) {
        if (visibleToViewer(spider)) super.render(spider, yaw, partialTick, poses, buffers, light);
    }
    @Override protected float getFlipDegrees(EntityMindSpider spider) { return 180.0F; }
    @Override protected void scale(EntityMindSpider spider, PoseStack poses, float partialTick) { poses.scale(0.6F, 0.6F, 0.6F); }
    @Override protected RenderType getRenderType(EntityMindSpider spider, boolean visible, boolean translucent, boolean glowing) {
        return WarpEntityRenderTypes.body(TEXTURE, false);
    }
    @Override public ResourceLocation getTextureLocation(EntityMindSpider spider) { return TEXTURE; }
    private static final class FaintModel extends SpiderModel<EntityMindSpider> {
        private int ticks;
        private boolean eyes;
        FaintModel(ModelPart root) { super(root); }
        @Override public void prepareMobModel(EntityMindSpider spider, float limb, float amount, float partial) {
            super.prepareMobModel(spider, limb, amount, partial);
            ticks = spider.tickCount;
        }
        @Override public void renderToBuffer(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
            int alpha = eyes ? 255 : (int) (255.0F * Math.min(0.1F, ticks / 100.0F));
            super.renderToBuffer(poses, vertices, light, overlay, FastColor.ARGB32.color(alpha, color));
        }
    }
    private static final class FullEyes extends SpiderEyesLayer<EntityMindSpider, SpiderModel<EntityMindSpider>> {
        FullEyes(RenderMindSpider parent) { super(parent); }
        @Override public void render(PoseStack poses, MultiBufferSource buffers, int light, EntityMindSpider spider,
                float limb, float amount, float partial, float age, float yaw, float pitch) {
            FaintModel model = (FaintModel) getParentModel(); model.eyes = true;
            try { super.render(poses, buffers, light, spider, limb, amount, partial, age, yaw, pitch); }
            finally { model.eyes = false; }
        }
    }
}
