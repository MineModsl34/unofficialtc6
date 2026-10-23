package thaumcraft.client.renderers.tile;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.renderer.MultiBufferSource;
import thaumcraft.client.lib.ender.SourceRenderMath;
import thaumcraft.client.lib.ender.SourceRenderTypes;
/** Literal random seeds/accumulated rotations and triangle fans, without GL fixed-function state. */
final class SourceRayMeshes {
    private SourceRayMeshes(){}
    static void halo(PoseStack poses,MultiBufferSource buffers,int count,boolean fancy){
        if(count<=0)return;var out=buffers.getBuffer(SourceRenderTypes.rays());Random random=new Random(245L);float time=count/500F,scale=SourceRenderMath.haloScale(count);
        poses.pushPose();poses.translate(.5,.5,.5);
        for(int i=0;i<(fancy?20:10);i++){
            poses.mulPose(Axis.XP.rotationDegrees(random.nextFloat()*360));poses.mulPose(Axis.YP.rotationDegrees(random.nextFloat()*360));poses.mulPose(Axis.ZP.rotationDegrees(random.nextFloat()*360));
            poses.mulPose(Axis.XP.rotationDegrees(random.nextFloat()*360));poses.mulPose(Axis.YP.rotationDegrees(random.nextFloat()*360));poses.mulPose(Axis.ZP.rotationDegrees(random.nextFloat()*360+time*360));
            float length=(random.nextFloat()*20+5)*scale,width=(random.nextFloat()*2+1)*scale;
            fan(out,poses,.866F,width,length,1,1,1,1-time,1,0,1);
        }
        poses.popPose();
    }
    static void focal(PoseStack poses,MultiBufferSource buffers,float angle,int number,float lift,float red,float green,float blue,float ticks){
        Random random=new Random(187L+number*number);float pan=(float)Math.sin((ticks+number*10)/15)*15,aperture=(float)Math.sin((ticks+number*10)/14)*2,scale=Math.min(ticks,10)/300;
        poses.pushPose();poses.translate(0,.475F+lift,0);poses.mulPose(Axis.XP.rotationDegrees(-90));poses.mulPose(Axis.YP.rotationDegrees(angle));poses.mulPose(Axis.YP.rotationDegrees(random.nextFloat()*360));poses.mulPose(Axis.XP.rotationDegrees(pan));
        float length=(random.nextFloat()*20+10)*scale,width=(random.nextFloat()*4+6+aperture)*scale;
        fan(buffers.getBuffer(SourceRenderTypes.rays()),poses,.8F,width,length,red,green,blue,.66F,red,green,blue);poses.popPose();
    }
    private static void fan(VertexConsumer out,PoseStack poses,float side,float width,float length,float r,float g,float b,float alpha,float edgeR,float edgeG,float edgeB){
        float[][] points={{-side*width,length,-.5F*width},{side*width,length,-.5F*width},{0,length,width},{-side*width,length,-.5F*width}};
        for(int i=0;i<3;i++){out.addVertex(poses.last(),0,0,0).setColor(r,g,b,alpha);out.addVertex(poses.last(),points[i][0],points[i][1],points[i][2]).setColor(edgeR,edgeG,edgeB,0);out.addVertex(poses.last(),points[i+1][0],points[i+1][1],points[i+1][2]).setColor(edgeR,edgeG,edgeB,0);}
    }
}
