package thaumcraft.client.renderers.entity.mob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.renderers.models.entity.ModelEldritchWarden;
import thaumcraft.common.entities.monster.boss.EntityEldritchWarden;
public final class RenderEldritchWarden extends MobRenderer<EntityEldritchWarden,ModelEldritchWarden> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.parse("thaumcraft:eldritch_warden"),"main");private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/entity/eldritch_warden.png");
    public RenderEldritchWarden(EntityRendererProvider.Context context){super(context,new ModelEldritchWarden(context.bakeLayer(LAYER)),.6F);addLayer(new WardenEyes(this));}
    @Override public ResourceLocation getTextureLocation(EntityEldritchWarden entity){return TEXTURE;}
    @Override protected RenderType getRenderType(EntityEldritchWarden entity,boolean visible,boolean translucent,boolean glowing){return thaumcraft.client.lib.warp.WarpEntityRenderTypes.body(TEXTURE,true);}
    @Override public void render(EntityEldritchWarden entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){poses.pushPose();poses.translate(0,-entity.getBbHeight()*(entity.getSpawnTimer()/150F),0);super.render(entity,yaw,partial,poses,buffers,light);poses.popPose();}
    private static final class WardenEyes extends RenderLayer<EntityEldritchWarden,ModelEldritchWarden>{WardenEyes(RenderLayerParent<EntityEldritchWarden,ModelEldritchWarden> parent){super(parent);}@Override public void render(PoseStack poses,MultiBufferSource buffers,int light,EntityEldritchWarden entity,float limb,float amount,float partial,float age,float yaw,float pitch){poses.pushPose();poses.scale(1.01F,1.01F,1.01F);getParentModel().renderEyes(poses,buffers.getBuffer(RenderType.eyes(TEXTURE)),(int)(195+Math.sin(entity.tickCount/3F)*15+15),OverlayTexture.NO_OVERLAY);poses.popPose();}}
}
