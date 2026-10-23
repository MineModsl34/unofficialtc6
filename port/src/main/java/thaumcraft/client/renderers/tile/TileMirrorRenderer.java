package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import thaumcraft.client.lib.ender.SourceRenderTypes;
import thaumcraft.client.lib.ender.SourceRenderMath;
import thaumcraft.common.tiles.devices.TileMirrorBase;
import thaumcraft.common.tiles.devices.TileMirrorEssentia;

/** Original 16-layer projective tunnel, source frame extrusion and six wall orientations. */
public final class TileMirrorRenderer<T extends TileMirrorBase> implements BlockEntityRenderer<T>{
    public TileMirrorRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(T tile,float tick,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        poses.pushPose();
        switch(tile.facing()){
            case DOWN->{poses.translate(0,1,1);poses.mulPose(Axis.XP.rotationDegrees(-90));}
            case UP->poses.mulPose(Axis.XP.rotationDegrees(90));
            case NORTH->poses.translate(0,0,1);
            case SOUTH->{poses.translate(1,0,0);poses.mulPose(Axis.YP.rotationDegrees(180));}
            case WEST->{poses.translate(1,0,1);poses.mulPose(Axis.YP.rotationDegrees(90));}
            case EAST->poses.mulPose(Axis.YP.rotationDegrees(-90));
        }
        frame(poses,buffers,"block/"+(tile instanceof TileMirrorEssentia?"mirrorframe2":"mirrorframe"),light);
        poses.popPose();
        var mc=Minecraft.getInstance();boolean visible=tile.linked&&mc.player!=null&&mc.player.distanceToSqr(tile.getBlockPos().getCenter())<1024;
        if(visible)tunnel(tile,tick,poses,buffers);
        poses.pushPose();orient(tile.facing(),poses);plane(poses,buffers,"block/"+(visible?"mirrorpanetrans":"mirrorpane"),-.02F,light);poses.popPose();
    }
    private static void orient(Direction facing,PoseStack poses){switch(facing){case DOWN->{poses.translate(0,1,1);poses.mulPose(Axis.XP.rotationDegrees(-90));}case UP->poses.mulPose(Axis.XP.rotationDegrees(90));case NORTH->poses.translate(0,0,1);case SOUTH->{poses.translate(1,0,0);poses.mulPose(Axis.YP.rotationDegrees(180));}case WEST->{poses.translate(1,0,1);poses.mulPose(Axis.YP.rotationDegrees(90));}case EAST->poses.mulPose(Axis.YP.rotationDegrees(-90));}}
    private static void frame(PoseStack poses,MultiBufferSource buffers,String path,int light){
        var out=buffers.getBuffer(RenderType.entityTranslucent(ResourceLocation.parse("thaumcraft:textures/"+path+".png")));float front=-.01F,back=front-.0625F;
        frameQuad(out,poses,light,0,0,front,1,1,1,0,front,0,1,1,1,front,0,0,0,1,front,1,0,0,0,1);
        frameQuad(out,poses,light,0,1,back,1,0,1,1,back,0,0,1,0,back,0,1,0,0,back,1,1,0,0,-1);
        // UtilsFX.renderTextureIn3D uses exactly sixteen slices, irrespective of source image size.
        for(int i=0;i<16;i++){float lo=i/16F,hi=(i+1)/16F,u=1-lo-.03125F,v=1-lo-.03125F;
            frameQuad(out,poses,light,lo,0,back,u,1,lo,0,front,u,1,lo,1,front,u,0,lo,1,back,u,0,-1,0,0);
            frameQuad(out,poses,light,hi,1,back,u,0,hi,1,front,u,0,hi,0,front,u,1,hi,0,back,u,1,1,0,0);
            frameQuad(out,poses,light,0,hi,front,1,v,1,hi,front,0,v,1,hi,back,0,v,0,hi,back,1,v,0,1,0);
            frameQuad(out,poses,light,1,lo,front,0,v,0,lo,front,1,v,0,lo,back,1,v,1,lo,back,0,v,0,-1,0);
        }
    }
    private static void frameQuad(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack poses,int light,float x1,float y1,float z1,float u1,float v1,float x2,float y2,float z2,float u2,float v2,float x3,float y3,float z3,float u3,float v3,float x4,float y4,float z4,float u4,float v4,float nx,float ny,float nz){AlchemyRenderSupport.vertex(out,poses.last(),x1,y1,z1,u1,v1,-1,light,nx,ny,nz);AlchemyRenderSupport.vertex(out,poses.last(),x2,y2,z2,u2,v2,-1,light,nx,ny,nz);AlchemyRenderSupport.vertex(out,poses.last(),x3,y3,z3,u3,v3,-1,light,nx,ny,nz);AlchemyRenderSupport.vertex(out,poses.last(),x4,y4,z4,u4,v4,-1,light,nx,ny,nz);}
    private static void tunnel(TileMirrorBase tile,float partial,PoseStack poses,MultiBufferSource buffers){
        var mc=Minecraft.getInstance();var entity=mc.getCameraEntity();if(entity==null)return;var camera=mc.gameRenderer.getMainCamera();var cp=camera.getPosition();
        float px=(float)Mth.lerp(partial,entity.xo,entity.getX()),py=(float)Mth.lerp(partial,entity.yo,entity.getY()),pz=(float)Mth.lerp(partial,entity.zo,entity.getZ());
        var near=camera.getLookVector();float ex=(float)cp.x-px+near.x()*.05F,ey=(float)cp.y-py+near.y()*.05F,ez=(float)cp.z-pz+near.z()*.05F;
        float rx=tile.getBlockPos().getX()-px,ry=tile.getBlockPos().getY()-py,rz=tile.getBlockPos().getZ()-pz,p=.1875F;
        Direction face=tile.facing();int axis=face.getAxis()==Direction.Axis.X?0:face.getAxis()==Direction.Axis.Y?1:2;
        boolean positive=face==Direction.DOWN||face==Direction.WEST||face==Direction.NORTH;int sign=positive?1:-1;float offset=positive?.99F:.01F;
        float[][] corners=axis==0?new float[][]{{offset,1-p,p},{offset,1-p,1-p},{offset,p,1-p},{offset,p,p}}:axis==1?new float[][]{{p,offset,1-p},{p,offset,p},{1-p,offset,p},{1-p,offset,1-p}}:new float[][]{{p,p,offset},{p,1-p,offset},{1-p,1-p,offset},{1-p,p,offset}};
        float surface=(axis==0?rx:axis==1?ry:rz)+offset;long time=System.currentTimeMillis();
        for(var layer:SourceRenderMath.mirrorLayers()){var out=buffers.getBuffer(SourceRenderTypes.mirror(layer.index()==0));
            for(int i=0;i<4;i++){float[] c=corners[positive?i:3-i];var uv=SourceRenderMath.mirrorProjection(layer,axis,sign,rx+c[0],ry+c[1],rz+c[2],surface,px,py,pz,ex,ey,ez,time);int bits=Float.floatToRawIntBits(uv.w);
                out.addVertex(poses.last(),c[0],c[1],c[2]).setColor(layer.red(),layer.green(),layer.blue(),1).setUv(uv.x,uv.y).setUv1(bits&65535,bits>>>16).setLight(0).setNormal(0,1,0);
            }
        }
    }
    private static void plane(PoseStack poses,MultiBufferSource buffers,String path,float z,int light){
        var vertices=buffers.getBuffer(RenderType.entityTranslucent(ResourceLocation.parse("thaumcraft:textures/"+path+".png")));
        float[][] points={{0,0,1,1},{1,0,0,1},{1,1,0,0},{0,1,1,0}};
        for(float[] p:points)AlchemyRenderSupport.vertex(vertices,poses.last(),p[0],p[1],z,p[2],p[3],-1,light,0,0,-1);
    }
}
