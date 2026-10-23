package thaumcraft.client.renderers.entity.projectile;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.projectile.EntityGolemDart;

/** Source .033 scale and 32-pixel vanilla arrow UVs, including shake and four fins. */
public final class RenderDart extends EntityRenderer<EntityGolemDart> {
    private static final ResourceLocation TEXTURE=ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");
    public RenderDart(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(EntityGolemDart entity){return TEXTURE;}
    @Override public void render(EntityGolemDart dart,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial,dart.yRotO,dart.getYRot())-90));poses.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partial,dart.xRotO,dart.getXRot())));float shake=dart.shakeTime-partial;if(shake>0)poses.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(shake*3)*shake));poses.mulPose(Axis.XP.rotationDegrees(45));poses.scale(.033F,.033F,.033F);poses.translate(-4,0,0);
        var out=buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));vertex(out,poses.last(),-7,-2,-2,0,.15625F,light,1,0,0);vertex(out,poses.last(),-7,-2,2,.15625F,.15625F,light,1,0,0);vertex(out,poses.last(),-7,2,2,.15625F,.3125F,light,1,0,0);vertex(out,poses.last(),-7,2,-2,0,.3125F,light,1,0,0);
        vertex(out,poses.last(),-7,2,-2,0,.15625F,light,-1,0,0);vertex(out,poses.last(),-7,2,2,.15625F,.15625F,light,-1,0,0);vertex(out,poses.last(),-7,-2,2,.15625F,.3125F,light,-1,0,0);vertex(out,poses.last(),-7,-2,-2,0,.3125F,light,-1,0,0);
        for(int i=0;i<4;i++){poses.mulPose(Axis.XP.rotationDegrees(90));vertex(out,poses.last(),-8,-2,0,0,0,light,0,0,1);vertex(out,poses.last(),8,-2,0,.5F,0,light,0,0,1);vertex(out,poses.last(),8,2,0,.5F,.15625F,light,0,0,1);vertex(out,poses.last(),-8,2,0,0,.15625F,light,0,0,1);}poses.popPose();super.render(dart,yaw,partial,poses,buffers,light);
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,float x,float y,float z,float u,float v,int light,float nx,float ny,float nz){out.addVertex(pose,x,y,z).setColor(-1).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,nx,ny,nz);}
}
