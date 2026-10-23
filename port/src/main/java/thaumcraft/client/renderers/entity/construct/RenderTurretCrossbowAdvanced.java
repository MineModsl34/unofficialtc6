package thaumcraft.client.renderers.entity.construct;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import thaumcraft.client.renderers.models.entity.ConstructObjMesh;
import thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced;

/** Original eight-group OBJ, not a vanilla model or a cube approximation. */
public final class RenderTurretCrossbowAdvanced extends EntityRenderer<EntityTurretCrossbowAdvanced> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.parse("thaumcraft:turret_advanced"),"main");
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/entity/crossbow_advanced.png");
    private final ConstructObjMesh mesh=ConstructObjMesh.load();private final ModelPart transforms;
    public RenderTurretCrossbowAdvanced(EntityRendererProvider.Context context){super(context);shadowRadius=.5F;transforms=context.bakeLayer(LAYER);}
    @Override public ResourceLocation getTextureLocation(EntityTurretCrossbowAdvanced entity){return TEXTURE;}
    @Override public void render(EntityTurretCrossbowAdvanced entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        var vertices=buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));poses.pushPose();poses.translate(0,.75,0);
        poses.pushPose();if(entity.getVehicle() instanceof AbstractMinecart)poses.scale(.66F,.75F,.66F);group("legs",poses,vertices,light,-1);poses.popPose();
        poses.pushPose();int color=-1;if(entity.hurtTime>0){color=0xffff8080;float jiggle=entity.hurtTime/500F;poses.translate(entity.getRandom().nextGaussian()*jiggle,entity.getRandom().nextGaussian()*jiggle,entity.getRandom().nextGaussian()*jiggle);}
        poses.mulPose(Axis.YN.rotationDegrees(Mth.rotLerp(partial,entity.yHeadRotO,entity.yHeadRot)));poses.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial,entity.xRotO,entity.getXRot())));
        for(String group:new String[]{"mech","box","shield","brain"})group(group,poses,vertices,light,color);
        poses.pushPose();poses.translate(0,0,Mth.sin(Mth.sqrt(entity.getLoadProgress(partial))*(float)Math.PI*2F)/12F);group("loader",poses,vertices,light,color);poses.popPose();
        float swing=Mth.sin(Mth.sqrt(entity.getAttackAnim(partial))*(float)Math.PI*2F)*20F;poses.translate(0,0,.375);
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(swing));group("bow1",poses,vertices,light,color);poses.popPose();poses.pushPose();poses.mulPose(Axis.YN.rotationDegrees(swing));group("bow2",poses,vertices,light,color);poses.popPose();poses.popPose();poses.popPose();super.render(entity,yaw,partial,poses,buffers,light);
    }
    private void group(String name,PoseStack poses,VertexConsumer out,int light,int color){poses.pushPose();transforms.getChild(name).translateAndRotate(poses);for(var face:mesh.faces(name)){for(var vertex:face.vertices())vertex(out,poses.last(),vertex,face,light,color);if(face.vertices().size()==3)vertex(out,poses.last(),face.vertices().get(2),face,light,color);}poses.popPose();}
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,ConstructObjMesh.Vertex vertex,ConstructObjMesh.Face face,int light,int color){out.addVertex(pose,vertex.x(),vertex.y(),vertex.z()).setColor(color).setUv(vertex.u(),vertex.v()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,face.nx(),face.ny(),face.nz());}
}
