package thaumcraft.client.renderers.entity.construct;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.client.renderers.models.entity.ModelArcaneBore;
import thaumcraft.common.entities.construct.EntityArcaneBore;
import thaumcraft.client.lib.ender.SourceRenderTypes;

public final class RenderArcaneBore extends MobRenderer<EntityArcaneBore,ModelArcaneBore> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.parse("thaumcraft:arcane_bore"),"main");
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/entity/arcanebore.png"),BEAM=ResourceLocation.parse("thaumcraft:textures/misc/beam1.png"),NODE=ResourceLocation.parse("thaumcraft:textures/misc/auranodes.png");
    public RenderArcaneBore(EntityRendererProvider.Context context){super(context,new ModelArcaneBore(context.bakeLayer(LAYER)),.5F);}
    @Override public ResourceLocation getTextureLocation(EntityArcaneBore entity){return TEXTURE;}
    @Override protected RenderType getRenderType(EntityArcaneBore entity,boolean visible,boolean translucent,boolean glowing){return RenderType.entityTranslucent(TEXTURE);}
    @Override public void render(EntityArcaneBore bore,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        float body=bore.yBodyRot,old=bore.yBodyRotO;try{bore.yBodyRot=bore.yBodyRotO=0;super.render(bore,yaw,partial,poses,buffers,light);}finally{bore.yBodyRot=body;bore.yBodyRotO=old;}
        if(!bore.clientDigging || !bore.isActive() || !bore.validInventory())return;
        float ry=Mth.rotLerp(partial,bore.yRotO,bore.getYRot()),rp=Mth.lerp(partial,bore.xRotO,bore.getXRot());float pitch=bore.getXRot()/180F*(float)Math.PI,head=-(bore.yHeadRot+90)/180F*(float)Math.PI;
        // Original emitter vector (0.5,.075,0), with Utils' rotation signs.
        double xx=.5*Mth.cos(pitch)+.075*Mth.sin(pitch),yy=.075*Mth.cos(pitch)-.5*Mth.sin(pitch);double x=xx*Mth.cos(head),z=-xx*Mth.sin(head);
        poses.pushPose();poses.translate(x,bore.getEyeHeight()+yy,z);poses.pushPose();poses.mulPose(entityRenderDispatcher.cameraOrientation());var glow=buffers.getBuffer(SourceRenderTypes.additive(NODE,false));int frame=96+bore.tickCount%32;float u=frame%32/32F,v=frame/32/32F;
        vertex(glow,poses.last(),-.5F,-.5F,0,u+1/32F,v+1/32F,204,210);vertex(glow,poses.last(),-.5F,.5F,0,u+1/32F,v,204,210);vertex(glow,poses.last(),.5F,.5F,0,u,v,204,210);vertex(glow,poses.last(),.5F,-.5F,0,u,v+1/32F,204,210);poses.popPose();
        poses.mulPose(Axis.XN.rotationDegrees(90));poses.mulPose(Axis.ZN.rotationDegrees(180+ry));poses.mulPose(Axis.XN.rotationDegrees(rp));poses.mulPose(Axis.YP.rotationDegrees((bore.level().getDayTime()%72)*5+5*partial));var beam=buffers.getBuffer(SourceRenderTypes.additive(BEAM,false));float time=-(bore.tickCount+partial),scroll=-time*.2F-Mth.floor(-time*.1F);
        for(int i=0;i<3;i++){poses.mulPose(Axis.YP.rotationDegrees(60));float v0=-1+scroll+i/3F,v1=5+v0;vertex(beam,poses.last(),0,5,0,1,v1,102,200);vertex(beam,poses.last(),-.15F,0,0,1,v0,102,200);vertex(beam,poses.last(),.15F,0,0,0,v0,102,200);vertex(beam,poses.last(),0,5,0,0,v1,102,200);}poses.popPose();
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,float x,float y,float z,float u,float v,int alpha,int brightness){out.addVertex(pose,x,y,z).setColor(0,255,102,alpha).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(brightness).setNormal(pose,0,0,1);}
}
