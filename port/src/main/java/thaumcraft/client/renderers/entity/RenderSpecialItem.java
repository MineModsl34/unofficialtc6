package thaumcraft.client.renderers.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import thaumcraft.client.lib.ender.SourceRenderTypes;

/** Source seed-187 accumulated rotations and white-to-magenta additive triangle fans. */
public final class RenderSpecialItem extends ItemEntityRenderer {
    private static final RenderType GLOW=SourceRenderTypes.rays();
    public RenderSpecialItem(EntityRendererProvider.Context context){super(context);}
    @Override public void render(ItemEntity entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        Random random=new Random(187);poses.pushPose();poses.translate(0,Mth.sin((entity.getAge()+partial)/10F+entity.bobOffs)*.1F+.1F+.25F,0);
        var out=buffers.getBuffer(GLOW);int count=SpecialItemGeometry.rays(Minecraft.useFancyGraphics());
        for(int i=0;i<count;i++){poses.mulPose(Axis.XP.rotationDegrees(random.nextFloat()*360));poses.mulPose(Axis.YP.rotationDegrees(random.nextFloat()*360));poses.mulPose(Axis.ZP.rotationDegrees(random.nextFloat()*360));poses.mulPose(Axis.XP.rotationDegrees(random.nextFloat()*360));poses.mulPose(Axis.YP.rotationDegrees(random.nextFloat()*360));poses.mulPose(Axis.ZP.rotationDegrees(random.nextFloat()*360+entity.getAge()/500F*360));
            float growth=SpecialItemGeometry.growth(entity.getAge()),length=(random.nextFloat()*20+5)*growth,width=(random.nextFloat()*2+1)*growth;
            float[][] rim={{-.866F*width,length,-.5F*width},{.866F*width,length,-.5F*width},{0,length,width}};
            for(int j=0;j<3;j++){out.addVertex(poses.last(),0,0,0).setColor(1F,1F,1F,1F);var a=rim[j];var b=rim[(j+1)%3];out.addVertex(poses.last(),a[0],a[1],a[2]).setColor(1F,0F,1F,0F);out.addVertex(poses.last(),b[0],b[1],b[2]).setColor(1F,0F,1F,0F);}
        }poses.popPose();super.render(entity,yaw,partial,poses,buffers,light);
    }
}
