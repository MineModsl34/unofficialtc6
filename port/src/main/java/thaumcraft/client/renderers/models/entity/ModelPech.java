package thaumcraft.client.renderers.models.entity;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.monster.EntityPech;

/** Nine independently animated BETA26 source parts, including the jaw and both packs. */
public final class ModelPech extends HumanoidModel<EntityPech> {
    private final ModelPart root,jowls,lowerPack;
    public ModelPech(ModelPart root){super(root);this.root=root;jowls=root.getChild("jowls");lowerPack=root.getChild("lower_pack");}
    public static LayerDefinition createBodyLayer(){MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();
        part(root,"body",34,12,-3,0,0,6,10,6,0,9,-3,.3129957F,0,0,false);
        part(root,"right_leg",35,1,-2.9F,0,0,3,6,3,0,18,0,0,0,0,true);part(root,"left_leg",35,1,-.1F,0,0,3,6,3,0,18,0,0,0,0,false);
        part(root,"head",2,11,-3.5F,-5,-5,7,5,5,0,8,0,0,0,0,false);root.addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
        part(root,"jowls",1,21,-4,-1,-6,8,3,5,0,8,0,0,0,0,false);
        part(root,"lower_pack",0,0,-5,0,0,10,5,5,0,10,3.5F,.3013602F,0,0,false);part(root,"upper_pack",64,1,-7.5F,-14,0,15,14,11,0,10,3,.4537856F,0,0,false);
        part(root,"right_arm",52,2,-2,0,-1,2,6,2,-3,10,-1,0,0,0,true);part(root,"left_arm",52,2,0,0,-1,2,6,2,3,10,-1,0,0,0,false);return LayerDefinition.create(mesh,128,64);}
    private static void part(PartDefinition root,String name,int u,int v,float x,float y,float z,float w,float h,float d,float px,float py,float pz,float rx,float ry,float rz,boolean mirror){root.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).mirror(mirror).addBox(x,y,z,w,h,d),PartPose.offsetAndRotation(px,py,pz,rx,ry,rz));}
    @Override public void setupAnim(EntityPech entity,float limb,float amount,float age,float yaw,float pitch){head.yRot=yaw*Mth.DEG_TO_RAD;head.xRot=pitch*Mth.DEG_TO_RAD;jowls.yRot=head.yRot;jowls.xRot=head.xRot+Mth.PI/12+Mth.cos(limb*.6662F)*amount*.25F+.34906587F*Math.abs(Mth.sin(entity.mumble/8));rightArm.xRot=Mth.cos(limb*.6662F+Mth.PI)*amount;leftArm.xRot=Mth.cos(limb*.6662F)*amount;rightArm.zRot=leftArm.zRot=0;rightLeg.xRot=Mth.cos(limb*.6662F)*1.4F*amount;leftLeg.xRot=Mth.cos(limb*.6662F+Mth.PI)*1.4F*amount;rightLeg.yRot=leftLeg.yRot=0;lowerPack.yRot=lowerPack.zRot=Mth.cos(limb*.6662F)*amount*.25F;if(riding){rightArm.xRot-=Mth.PI/5;leftArm.xRot-=Mth.PI/5;rightLeg.xRot=leftLeg.xRot=-Mth.PI*2/5;rightLeg.yRot=Mth.PI/10;leftLeg.yRot=-Mth.PI/10;}rightArm.yRot=leftArm.yRot=0;
        float f=1-attackTime;f*=f;f*=f;f=1-f;float swing=Mth.sin(f*Mth.PI),bend=Mth.sin(attackTime*Mth.PI)*-(head.xRot-.7F)*.75F;rightArm.xRot-=(float)(swing*1.2+bend);rightArm.zRot=Mth.sin(attackTime*Mth.PI)*-.4F;if(entity.isCrouching()){rightArm.xRot+=.4F;leftArm.xRot+=.4F;}rightArm.zRot+=Mth.cos(age*.09F)*.05F+.05F;leftArm.zRot-=Mth.cos(age*.09F)*.05F+.05F;rightArm.xRot+=Mth.sin(age*.067F)*.05F;leftArm.xRot-=Mth.sin(age*.067F)*.05F;}
    @Override public void renderToBuffer(PoseStack stack,VertexConsumer vertices,int light,int overlay,int color){root.render(stack,vertices,light,overlay,color);}
}
