package thaumcraft.client.renderers.models.entity;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeed;
/** Original8 segment source seed, nonuniform recursive pulse (.9 vertical) and hurt/attack displacement. */
public final class ModelTaintSeed<T extends EntityTaintSeed> extends EntityModel<T> {
    private final ModelPart base;private final ModelPart[] segments=new ModelPart[7],scales=new ModelPart[7];private float height,rise,age;
    public ModelTaintSeed(){var builder=new SourceBossGeometry.Builder();builder.part("base",0,0,-4,-4,-4,8,8,8,0,12,0,0,0,0,false,64,64);for(int i=0;i<7;i++){int size=i<4?8:4,uv=i<4?16:i==4?48:56;builder.empty("scale"+i);builder.part("s"+i,0,uv,-size/2F,-size/2F,-size/2F,size,size,size,0,i<=4?-8:-4,0,0,0,0,false,64,64);builder.child(i==0?"base":"s"+(i-1),"scale"+i);builder.child("scale"+i,"s"+i);}var rig=builder.build();base=rig.parts().get("base");for(int i=0;i<7;i++){segments[i]=rig.parts().get("s"+i);scales[i]=rig.parts().get("scale"+i);}}
    @Override public void setupAnim(T entity,float limb,float amount,float age,float yaw,float pitch){this.age=age;height=entity.getBbHeight();float clock=height*10;rise=entity.tickCount<clock?(clock-entity.tickCount)/clock*height:0;float hurt=entity.hurtTime/200F,mod=.0625F*.2F,intensity=(.1F+(hurt<=0?-mod:mod))*3;for(int i=0;i<7;i++){segments[i].xRot=.1F/intensity*Mth.sin(age*.06F-i/2F)/5+hurt+entity.attackAnim;segments[i].zRot=.1F/intensity*Mth.sin(age*.05F-i/2F)/5;float phase=(float)Math.PI*(i/7F),scale=1.6F-Mth.sin(phase),pulse=Mth.sin(age/12-phase)*.33F;pulse*=pulse;scales[i].xScale=scales[i].zScale=scale+pulse;scales[i].yScale=scale*.9F;}}
    @Override public void renderToBuffer(PoseStack poses,VertexConsumer vertices,int light,int overlay,int color){poses.pushPose();poses.translate(0,(height==3?.6F:1.2F)+rise,0);poses.scale(height/2,height/2,height/2);base.render(poses,vertices,light,overlay,color);poses.popPose();}
}
