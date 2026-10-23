package thaumcraft.client.renderers.entity.mob;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.renderers.models.entity.ModelEldritchGuardian;
import thaumcraft.common.entities.monster.EntityEldritchGuardian;
import thaumcraft.client.lib.warp.WarpEntityRenderTypes;

/** TC6 guardian geometry and distance-dependent opacity, rendered with modern vertex buffers. */
public final class RenderEldritchGuardian extends MobRenderer<EntityEldritchGuardian, ModelEldritchGuardian> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.parse("thaumcraft:eldritch_guardian"), "main");
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("thaumcraft:textures/entity/eldritch_guardian.png");
    public RenderEldritchGuardian(EntityRendererProvider.Context context) {
        super(context, new ModelEldritchGuardian(context.bakeLayer(LAYER)), 0.5F);
    }
    @Override protected RenderType getRenderType(EntityEldritchGuardian entity, boolean visible, boolean translucent, boolean glowing) {
        return WarpEntityRenderTypes.body(TEXTURE, true);
    }
    @Override public ResourceLocation getTextureLocation(EntityEldritchGuardian entity) { return TEXTURE; }
}
