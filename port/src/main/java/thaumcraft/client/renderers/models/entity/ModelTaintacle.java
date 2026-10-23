package thaumcraft.client.renderers.models.entity;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.monster.tainted.EntityTaintacle;
/** Source6/10/14-segment hierarchy with recursive .88 taper, tip bulb/orb and arise/flail motion. */
public final class ModelTaintacle<T extends EntityTaintacle> extends EntityModel<T> {
    private final ModelPart base;private final ModelPart[] segments;private float height,rise;
    public ModelTaintacle(int length){segments=new ModelPart[length-1];SourceBossGeometry.Builder builder=new SourceBossGeometry.Builder();builder.part("base",0,0,-4,-4,-4,8,8,8,0,12,0,0,0,0,false,64,64);
        for(int i=0;i<length-1;i++){String name="s"+i;builder.part(name,0,16,-4,-4,-4,8,8,8,0,-8*.88F,0,0,0,0,false,64,64);builder.child(i==0?"base":"s"+(i-1),name);}
        builder.part("orb",0,56,-2,-2,-2,4,4,4,0,-8*.88F,0,0,0,0,false,64,64);builder.part("tip",0,32,-6,-6,-6,12,12,12,0,-8*.88F,0,0,0,0,false,64,64);builder.child("s"+(length-2),"orb");builder.child("s"+(length-2),"tip");var rig=builder.build();base=rig.parts().get("base");for(int i=0;i<length-1;i++){segments[i]=rig.parts().get("s"+i);segments[i].xScale=segments[i].yScale=segments[i].zScale=.88F;}for(String name:List.of("orb","tip")){ModelPart part=rig.parts().get(name);part.xScale=part.yScale=part.zScale=.88F;}}
    @Override public void setupAnim(T entity,float limb,float amount,float age,float yaw,float pitch){height=entity.getBbHeight();float clock=height*10;rise=entity.tickCount<clock?(clock-entity.tickCount)/clock*height:0;float flail=entity.flailIntensity,mod=.0625F*.2F,speed=flail>1?3:1-mod,intensity=flail+(entity.hurtTime<=0?-mod:mod);for(int i=0;i<segments.length;i++){segments[i].xRot=.15F*intensity*Mth.sin(age*.1F*speed-i/2F);segments[i].zRot=.1F/intensity*Mth.sin(age*.15F-i/2F);}}
    @Override public void renderToBuffer(PoseStack poses,VertexConsumer vertices,int light,int overlay,int color){poses.pushPose();poses.translate(0,(height==3?.6F:1.2F)+rise,0);poses.scale(height/3,height/3,height/3);base.render(poses,vertices,light,overlay,color);poses.popPose();}
}
